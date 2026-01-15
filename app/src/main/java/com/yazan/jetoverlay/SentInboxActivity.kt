package com.yazan.jetoverlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.yazan.jetoverlay.ui.inbox.SentInboxScreen
import com.yazan.jetoverlay.ui.theme.JetoverlayTheme

class SentInboxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = JetOverlayApplication.instance.repository
        setContent {
            JetoverlayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    SentInboxScreen(
                        repository = repository,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
