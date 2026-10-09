package com.pidan.health.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pidan.health.app.navigation.PidanNavHost
import com.pidan.health.core.designsystem.theme.PidanHealthTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PidanHealthTheme {
                PidanNavHost()
            }
        }
    }
}
