package com.pidan.health.core.data.webdav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OkHttp WebDAV 客户端（T13）：PROPFIND / PUT / GET / MKCOL / DELETE + Basic 认证。
 *
 * 与 OcrStrategy 同款策略：业务只依赖 [WebDavClient] 接口，
 * 换云存储/协议时新增实现类即可，业务代码零改动。
 * 全部方法 Result 化，失败消息为中文可读文案（UI 可直接展示）。
 *
 * 调试：所有请求走 [WebDavLog]（tag=CloudSync），打印方法+完整 URL+响应码。
 */
interface WebDavClient {

    /** 连接测试：PROPFIND 根目录 Depth 0，207 即成功 */
    suspend fun testConnection(config: WebDavConfig): Result<Unit>

    /** 列目录（Depth 1）；目录不存在返回空列表 */
    suspend fun list(config: WebDavConfig, remoteDir: String): Result<List<WebDavFile>>

    /** 确保目录存在：不存在则逐段 MKCOL（405=已存在，视为成功） */
    suspend fun ensureDirectory(config: WebDavConfig, remoteDir: String): Result<Unit>

    /** 上传字节（PUT，覆盖同名） */
    suspend fun upload(config: WebDavConfig, remotePath: String, bytes: ByteArray): Result<Unit>

    /** 下载字节（GET） */
    suspend fun download(config: WebDavConfig, remotePath: String): Result<ByteArray>

    /** 删除远端文件 */
    suspend fun delete(config: WebDavConfig, remotePath: String): Result<Unit>
}

class OkHttpWebDavClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) : WebDavClient {

    override suspend fun testConnection(config: WebDavConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            guarded(config) {
                val url = config.baseUrl.trimEnd('/')
                val request = baseRequest(config, url)
                    .header("Depth", "0")
                    .method("PROPFIND", propfindBody())
                    .build()
                callAndLog("PROPFIND Depth0", request).use { it.toUnitResult() }
            }
        }

    override suspend fun list(config: WebDavConfig, remoteDir: String): Result<List<WebDavFile>> =
        withContext(Dispatchers.IO) {
            guarded(config) {
                val url = WebDavPaths.join(config.baseUrl, remoteDir)
                val request = baseRequest(config, url)
                    .header("Depth", "1")
                    .method("PROPFIND", propfindBody())
                    .build()
                callAndLog("PROPFIND Depth1", request).use { response ->
                    when {
                        response.code == HTTP_NOT_FOUND ->
                            Result.success(emptyList<WebDavFile>()).also { logResult(url, 404, "目录不存在，按空列表处理") }
                        response.code == HTTP_MULTI_STATUS || response.code == HTTP_OK -> {
                            val body = response.body?.string()
                                ?: return@use Result.failure(WebDavException("云端响应为空"))
                            val files = WebDavResponseParser.parse(body)
                            logResult(
                                url,
                                response.code,
                                "解析到 ${files.size} 项: " +
                                    files.joinToString(", ") { "${it.name}(${it.sizeBytes}B)" }
                            )
                            Result.success(files)
                        }
                        else -> {
                            logResult(url, response.code, "列目录失败")
                            Result.failure(response.toException())
                        }
                    }
                }
            }
        }

    override suspend fun ensureDirectory(config: WebDavConfig, remoteDir: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            guarded(config) {
                val segments = remoteDir.split('/').filter { it.isNotBlank() }
                var url = config.baseUrl.trimEnd('/')
                for (segment in segments) {
                    url = "$url/${WebDavPaths.encodeSegment(segment)}"
                    val request = baseRequest(config, url).method("MKCOL", null).build()
                    callAndLog("MKCOL", request).use { resp ->
                        // 201=新建成功；405=目录已存在（继续下一段即可）
                        if (resp.code != HTTP_CREATED && resp.code != HTTP_METHOD_NOT_ALLOWED) {
                            logResult(url, resp.code, "建目录失败")
                            return@guarded Result.failure(resp.toException())
                        }
                        if (resp.code == HTTP_CREATED) {
                            logResult(url, resp.code, "目录新建成功")
                        }
                    }
                }
                Result.success(Unit)
            }
        }

    override suspend fun upload(
        config: WebDavConfig,
        remotePath: String,
        bytes: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        guarded(config) {
            val body: RequestBody = bytes.toRequestBody("application/octet-stream".toMediaType())
            val url = WebDavPaths.join(config.baseUrl, remotePath)
            WebDavLog.d("PUT $url (bytes=${bytes.size}, remotePath=[$remotePath])")
            val request = baseRequest(config, url).put(body).build()
            callAndLog("PUT", request).use { response ->
                if (response.code in SUCCESS_CODES) {
                    logResult(url, response.code, "上传成功 bytes=${bytes.size}")
                    Result.success(Unit)
                } else {
                    logResult(url, response.code, "上传失败 bytes=${bytes.size}")
                    Result.failure(response.toException())
                }
            }
        }
    }

    override suspend fun download(config: WebDavConfig, remotePath: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            guarded(config) {
                val url = WebDavPaths.join(config.baseUrl, remotePath)
                WebDavLog.d("GET $url (remotePath=[$remotePath])")
                val request = baseRequest(config, url).get().build()
                callAndLog("GET", request).use { response ->
                    if (response.code == HTTP_OK) {
                        val bytes = response.body?.bytes() ?: ByteArray(0)
                        logResult(url, response.code, "下载成功 bytes=${bytes.size}")
                        Result.success(bytes)
                    } else {
                        logResult(url, response.code, "下载失败")
                        Result.failure(response.toException())
                    }
                }
            }
        }

    override suspend fun delete(config: WebDavConfig, remotePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            guarded(config) {
                val url = WebDavPaths.join(config.baseUrl, remotePath)
                val request = baseRequest(config, url).delete().build()
                callAndLog("DELETE", request).use { it.toUnitResult() }
            }
        }

    // ---- 内部细节 ----

    /** 统一异常包装：配置错误 / IO 异常 / 其他异常 → 中文 WebDavException */
    private suspend fun <T> guarded(
        config: WebDavConfig,
        block: suspend () -> Result<T>
    ): Result<T> {
        val baseUrl = config.baseUrl.trim()
        if (!baseUrl.startsWith("http")) {
            return Result.failure(WebDavException("服务器地址无效，需以 http(s):// 开头"))
        }
        if (config.username.isBlank() || config.password.isBlank()) {
            return Result.failure(WebDavException("请先填写账号与应用密码"))
        }
        return try {
            block()
        } catch (e: IOException) {
            WebDavLog.d("IO 异常: ${e.javaClass.simpleName}: ${e.message}")
            Result.failure(WebDavException("网络连接失败：${e.message ?: "请检查网络"}", e))
        } catch (e: Exception) {
            WebDavLog.d("未知异常: ${e.javaClass.simpleName}: ${e.message}")
            Result.failure(WebDavException("云端操作失败：${e.message ?: "未知错误"}", e))
        }
    }

    private fun baseRequest(config: WebDavConfig, url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))

    /** 发请求 + 记录「方法 URL -> 响应码」日志 */
    private fun callAndLog(method: String, request: Request): okhttp3.Response {
        WebDavLog.d("$method ${request.url}")
        return client.newCall(request).execute().also {
            WebDavLog.d("$method ${request.url} -> HTTP ${it.code}")
        }
    }

    private fun logResult(url: String, code: Int, detail: String) =
        WebDavLog.d("HTTP $code $url | $detail")

    private fun propfindBody(): RequestBody =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <D:propfind xmlns:D="DAV:">
          <D:prop>
            <D:resourcetype/>
            <D:getcontentlength/>
            <D:getlastmodified/>
          </D:prop>
        </D:propfind>
        """.trimIndent().toRequestBody("application/xml".toMediaType())

    private fun okhttp3.Response.toException(): WebDavException = WebDavException(
        when (code) {
            401 -> "账号或应用密码错误"
            403 -> "云端拒绝访问（权限不足）"
            404 -> "云端文件不存在"
            507 -> "云端空间不足"
            else -> "云端返回错误（HTTP $code）"
        }
    )

    private fun okhttp3.Response.toUnitResult(): Result<Unit> = when {
        code in SUCCESS_CODES -> Result.success(Unit)
        else -> Result.failure(toException())
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_CREATED = 201
        const val HTTP_NO_CONTENT = 204
        const val HTTP_MULTI_STATUS = 207
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NOT_ALLOWED = 405

        val SUCCESS_CODES = listOf(HTTP_OK, HTTP_CREATED, HTTP_NO_CONTENT, HTTP_MULTI_STATUS)
    }
}
