package com.yazan.jetoverlay

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.yazan.jetoverlay.api.OverlayNotificationConfig
import com.yazan.jetoverlay.api.OverlaySdk
import com.yazan.jetoverlay.data.AppDatabase
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.ui.FloatingBubble
import com.yazan.jetoverlay.ui.OverlayUiState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class JetOverlayApplication : Application() {

    companion object {
        lateinit var instance: JetOverlayApplication
            private set
    }

    private val applicationScope = MainScope()
    lateinit var repository: MessageRepository
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            android.util.Log.d("JetOverlayDebug", "JetOverlayApplication: onCreate started")
            instance = this

            // Initialize Data Layer globally
            val db = AppDatabase.getDatabase(applicationContext)
            repository = MessageRepository(db.messageDao())
            android.util.Log.d("JetOverlayDebug", "JetOverlayApplication: Repository initialized")

            // Initialize SDK
            OverlaySdk.initialize(
                notificationConfig = OverlayNotificationConfig()
            )
            android.util.Log.d("JetOverlayDebug", "JetOverlayApplication: SDK initialized")

            // Register default agent overlay content (overlay_1)
            OverlaySdk.registerContent("overlay_1") { payload ->
                android.util.Log.d("JetOverlayDebug", "OverlayContent: Composing overlay_1")
                
                // Note: In a real app, you might want to inject the repository or scope differently.
                val messages by repository.allMessages.collectAsState(initial = emptyList())
                android.util.Log.d("JetOverlayDebug", "OverlayContent: Messages count: ${messages.size}")

                val activeMessage = messages.lastOrNull { it.status != "SENT" && it.status != "DISMISSED" }
                android.util.Log.d("JetOverlayDebug", "OverlayContent: Active message found: ${activeMessage?.id}")

                if (activeMessage != null) {
                    android.util.Log.d("JetOverlayDebug", "OverlayContent: Rendering Active Bubble")
                    val uiState = remember(activeMessage.id) { OverlayUiState(activeMessage) }
                    LaunchedEffect(activeMessage) {
                        uiState.updateMessage(activeMessage)
                    }
                    FloatingBubble(modifier = Modifier, uiState = uiState)
                } else {
                    android.util.Log.d("JetOverlayDebug", "OverlayContent: Rendering Idle Bubble")
                    val idleMessage = Message(
                        packageName = "",
                        senderName = "System",
                        originalContent = "No new notifications. The agent is listening.",
                        status = "IDLE",
                        veiledContent = "Agent Active"
                    )
                    val uiState = remember { OverlayUiState(idleMessage) }
                    FloatingBubble(modifier = Modifier, uiState = uiState)
                }
            }
            
            // Also register the demo options if needed, or keep them for the UI control panel only.
            // For safety, registering them here ensures they work if triggered from background too.
            com.yazan.jetoverlay.options.forEach { option ->
                OverlaySdk.registerContent(option.id) {
                // ... redundant for now unless we want to support demo shapes from background
                // keeping it simple for now, focusing on the agent bubble.
                }
            }
            android.util.Log.d("JetOverlayDebug", "JetOverlayApplication: onCreate completed successfully")
        } catch (e: Throwable) {
            android.util.Log.e("JetOverlayCrash", "CRITICAL: Application onCreate failed", e)
            throw e
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }
}
