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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.yazan.jetoverlay.api.OverlayNotificationConfig
import com.yazan.jetoverlay.api.OverlaySdk
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.ui.FloatingBubble
import com.yazan.jetoverlay.ui.OverlayUiState
import com.yazan.jetoverlay.ui.theme.JetoverlayTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Data Layer
        // val db = com.yazan.jetoverlay.data.AppDatabase.getDatabase(applicationContext)
        val repository = JetOverlayApplication.instance.repository

        // Handle Slack OAuth Callback
        if (intent?.action == android.content.Intent.ACTION_VIEW &&
            intent.data?.scheme == "jetoverlay" &&
            intent.data?.host == "slack" &&
            intent.data?.path == "/oauth") {
            lifecycleScope.launch {
                com.yazan.jetoverlay.service.integration.SlackIntegration.handleCallback(intent.data!!, repository)
            }
        }

        // Handle GitHub OAuth Callback
        if (intent?.action == android.content.Intent.ACTION_VIEW &&
            intent.data?.scheme == "jetoverlay" &&
            intent.data?.host == "github-callback") {
            val code = intent.data?.getQueryParameter("code")
            if (!code.isNullOrBlank()) {
                lifecycleScope.launch {
                    com.yazan.jetoverlay.service.integration.GitHubIntegration.handleOAuthCallback(code)
                }
            }
        }

        // Initialize the SDK
        // OverlaySdk.initialize() is now handled in JetOverlayApplication

        // Register Content with Real Data
        // Registration is now handled in JetOverlayApplication to ensure availability for Services


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
