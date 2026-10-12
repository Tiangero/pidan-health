package com.pidan.health.core.data.webdav

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * WebDAV 客户端模型与路径工具（T13 云同步）。
 *
 * 路径语义：所有 remote 路径均为相对 [WebDavConfig.baseUrl] 的相对路径，
 * 分段由 [encodeSegment] 单独编码后以 '/' 拼接（中文目录/文件名安全上送）。
 */

/** 云端同步配置（WebDAV 通用：坚果云为默认预设，地址可改为任意 WebDAV 服务） */
data class WebDavConfig(
    val baseUrl: String,
    val username: String,
    val password: String
)

/** PROPFIND 列目录结果中的单个文件项 */
data class WebDavFile(
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
)

/** WebDAV 操作异常：携带中文可读消息供 UI 直接展示 */
class WebDavException(message: String, cause: Throwable? = null) : Exception(message, cause)

object WebDavPaths {

    /** 路径段 URL 编码：空格 %20（URLEncoder 的 + 是表单语义，不适合路径） */
    fun encodeSegment(segment: String): String =
        URLEncoder.encode(segment, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("*", "%2A")

    /**
     * 拼接 baseUrl 与相对路径（每段独立编码）。
     *
     * 传入的 segment 内部允许含 '/'（如 "皮蛋健康/xxx.zip"）：
     * 必须按 '/' 拆开逐段编码，否则 '/' 会被编码成 %2F，
     * 上传路径变成 "<base>/皮蛋健康%2Fxxx.zip"，文件落不进目录。
     */
    fun join(baseUrl: String, vararg segments: String): String {
        val base = baseUrl.trimEnd('/')
        val path = segments
            .flatMap { it.split('/').filter(String::isNotBlank) }
            .joinToString("/") { encodeSegment(it) }
        return "$base/$path"
    }

    /** 服务端返回的 href（URL 编码）→ 文件名（解码、去路径） */
    fun decodeName(href: String): String {
        val cleaned = href.trimEnd('/')
        val rawName = cleaned.substringAfterLast('/')
        return runCatching { URLDecoder.decode(rawName, Charsets.UTF_8.name()) }
            .getOrDefault(rawName)
    }
}
