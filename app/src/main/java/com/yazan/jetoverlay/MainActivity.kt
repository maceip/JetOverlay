package com.yazan.jetoverlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yazan.jetoverlay.api.OverlayNotificationConfig
import com.yazan.jetoverlay.api.OverlaySdk
import com.yazan.jetoverlay.ui.theme.JetoverlayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the SDK
        OverlaySdk.initialize(
            notificationConfig = OverlayNotificationConfig(),
            factory = { modifier, id, payload ->
                val color = (payload as? Long)?.let { Color(it.toULong()) } ?: Color.Gray
                OverlayShapeContent(modifier = modifier, id = id, color = color)
            }
        )

        setContent {
            JetoverlayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OverlayControlPanel(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}