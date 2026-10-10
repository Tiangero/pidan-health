package com.pidan.health.core.ocr.di

import com.pidan.health.BuildConfig
import com.pidan.health.core.ocr.BaiduOcrApi
import com.pidan.health.core.ocr.BaiduOcrStrategy
import com.pidan.health.core.ocr.BaiduTokenApi
import com.pidan.health.core.ocr.OcrStrategy
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.Module
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * OCR 层 Koin 模块：百度 OCR 网络栈 + OcrStrategy 绑定。
 * AK/SK 经 BuildConfig 注入（.env 读取，见 app/build.gradle.kts），不入仓库。
 * 换 OCR 供应商：新增实现类后改此处一行绑定即可，业务代码零改动。
 */
val ocrModule: Module = module {

    single<OkHttpClient> {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    // BASIC 只打请求行/响应码，避免 BODY 把 base64 图片刷爆 logcat
                    addInterceptor(
                        HttpLoggingInterceptor { message -> Log.d("BaiduOcr", message) }
                            .apply { level = HttpLoggingInterceptor.Level.BASIC }
                    )
                }
            }
            .build()
    }

    single { Json { ignoreUnknownKeys = true } }

    single {
        Retrofit.Builder()
            .baseUrl("https://aip.baidubce.com/")
            .client(get())
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single<BaiduTokenApi> { get<Retrofit>().create(BaiduTokenApi::class.java) }
    single<BaiduOcrApi> { get<Retrofit>().create(BaiduOcrApi::class.java) }

    single<OcrStrategy> {
        BaiduOcrStrategy(
            apiKey = BuildConfig.BAIDU_OCR_AK,
            secretKey = BuildConfig.BAIDU_OCR_SK,
            tokenApi = get(),
            ocrApi = get()
        )
    }
}
