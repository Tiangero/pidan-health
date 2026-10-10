package com.pidan.health.core.ocr

import java.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query

// ---- 百度鉴权与 OCR 接口定义（Retrofit + Kotlinx Serialization）----

interface BaiduTokenApi {

    /** client_credentials 换 access_token，有效期约 30 天 */
    @POST("oauth/2.0/token")
    suspend fun fetchToken(
        @Query("grant_type") grantType: String = GRANT_TYPE,
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String
    ): BaiduTokenResponse

    private companion object {
        const val GRANT_TYPE = "client_credentials"
    }
}

interface BaiduOcrApi {

    /** 通用文字识别（标准版）：image 为 base64 编码的图片字节（≤8M，长边 ≤4096px） */
    @POST("rest/2.0/ocr/v1/general_basic")
    @FormUrlEncoded
    suspend fun recognize(
        @Query("access_token") accessToken: String,
        @Field("image") imageBase64: String
    ): BaiduOcrResponse
}

@Serializable
data class BaiduTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0
)

@Serializable
data class BaiduOcrWord(val words: String? = null)

@Serializable
data class BaiduOcrResponse(
    @SerialName("log_id") val logId: Long = 0,
    @SerialName("words_result_num") val wordsResultNum: Int = 0,
    @SerialName("words_result") val wordsResult: List<BaiduOcrWord>? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    @SerialName("error_msg") val errorMsg: String? = null
)

/**
 * 百度 OCR 策略实现：access_token 内存缓存（过期前 1 天余量刷新，双检防并发重复取）。
 * 密钥来自 BuildConfig（.env 注入），缺配置时快速失败（调用方降级手动录入）。
 * 换 OCR 供应商 = 新增一个 OcrStrategy 实现类（架构验证点，见任务清单 T8）。
 */
class BaiduOcrStrategy(
    private val apiKey: String,
    private val secretKey: String,
    private val tokenApi: BaiduTokenApi,
    private val ocrApi: BaiduOcrApi,
    private val clock: () -> Long = System::currentTimeMillis
) : OcrStrategy {

    private val tokenMutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAtMs = 0L

    override suspend fun recognize(imageBytes: ByteArray): Result<List<String>> = try {
        require(imageBytes.isNotEmpty()) { "图片数据为空" }
        val token = validToken()
        val imageBase64 = Base64.getEncoder().encodeToString(imageBytes)
        val response = ocrApi.recognize(accessToken = token, imageBase64 = imageBase64)
        response.errorCode?.let {
            throw IllegalStateException("百度 OCR 接口错误 $it: ${response.errorMsg ?: "未知"}")
        }
        val lines = response.wordsResult.orEmpty()
            .mapNotNull { it.words?.trim()?.takeIf(String::isNotEmpty) }
        if (lines.isEmpty()) throw IllegalStateException("未识别到任何文字")
        Result.success(lines)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun validToken(): String {
        if (apiKey.isBlank() || secretKey.isBlank()) {
            throw IllegalStateException("未配置百度 OCR AK/SK（请在项目根目录 .env 填写）")
        }
        cachedToken?.takeIf { now() < tokenExpiresAtMs }?.let { return it }
        return tokenMutex.withLock {
            cachedToken?.takeIf { now() < tokenExpiresAtMs }?.let { return@withLock it }
            val response = tokenApi.fetchToken(clientId = apiKey, clientSecret = secretKey)
            val token = response.accessToken.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("获取 access_token 失败")
            cachedToken = token
            tokenExpiresAtMs = now() + (response.expiresIn - TOKEN_EXPIRY_MARGIN_SECONDS) * 1000
            token
        }
    }

    private fun now(): Long = clock()

    private companion object {
        /** 过期前 1 天余量刷新，避免临界点请求失败 */
        const val TOKEN_EXPIRY_MARGIN_SECONDS = 24 * 3600L
    }
}
