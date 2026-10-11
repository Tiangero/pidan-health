package com.pidan.health.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pidan.health.app.navigation.PidanNavHost
import com.pidan.health.core.designsystem.theme.PidanHealthTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 开屏页：必须在 super.onCreate 之前安装（API 26+ 兼容，31+ 走系统 SplashScreen）
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 系统栏强制浅色样式：App 主题固定浅色（不随系统深色模式），
        // 这里若用默认 auto，系统深色时状态栏图标会变浅色压在白底上看不见
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            PidanHealthTheme {
                PidanNavHost()
            }
        }
    }
}
