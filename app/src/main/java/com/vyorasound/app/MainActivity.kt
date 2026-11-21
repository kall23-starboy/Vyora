package com.vyorasound.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vyorasound.app.presentation.navigation.NexaAppShell
import com.vyorasound.app.presentation.theme.NexaMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val app = application as VyoraApplication
        setContent {
            NexaMusicTheme {
                NexaAppShell(container = app.container)
            }
        }
    }
}
