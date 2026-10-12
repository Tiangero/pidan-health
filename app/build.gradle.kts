import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 正式签名：从根目录 keystore.properties 读取（该文件与 jks 均被 .gitignore 忽略）。
// 属性文件不存在时（如他人 clone 项目）release 自动回退 debug 签名，保证本地能装能跑。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.isFile) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.pidan.health"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.pidan.health"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // T8：从项目根目录 .env 读取百度 OCR 密钥注入 BuildConfig（.env 已被 .gitignore 忽略）
        val envFile = rootProject.file(".env")
        val envVars: Map<String, String> = if (envFile.isFile) {
            envFile.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
                .associate { line ->
                    val idx = line.indexOf('=')
                    line.take(idx).trim() to line.substring(idx + 1).trim()
                }
        } else {
            emptyMap()
        }
        buildConfigField("String", "BAIDU_OCR_AK", "\"${envVars["BAIDU_OCR_AK"] ?: ""}\"")
        buildConfigField("String", "BAIDU_OCR_SK", "\"${envVars["BAIDU_OCR_SK"] ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.isFile) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true // R8 算法，缩小包体积
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropsFile.isFile) {
                signingConfigs.getByName("release")
            } else {
                // 无正式 keystore 时回退 debug 签名（产物名为 app-release-unsigned 的场景已消除）
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // JVM 单测中 android.util.Log 等框架方法调用降级为 no-op（T8 OCR 失败路径打日志用）
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // 开屏页：兼容 API 26+ 的系统 SplashScreen（橙色底 + 品牌图标）
    implementation(libs.androidx.core.splashscreen)
    // 裁剪库 CropImageActivity 是 AppCompatActivity，需要 AppCompat 主题与运行时
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DI - Koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 图片加载
    implementation(libs.coil.compose)

    // 图表
    implementation(libs.vico.compose.m3)

    // 网络
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.image.cropper)
    implementation(libs.kotlinx.serialization.json)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
