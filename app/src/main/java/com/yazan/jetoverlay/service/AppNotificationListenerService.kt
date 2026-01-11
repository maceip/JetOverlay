package com.yazan.jetoverlay.service

import com.yazan.jetoverlay.data.AppDatabase
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.data.NotificationConfigManager
import com.yazan.jetoverlay.data.ReplyActionCache
import com.yazan.jetoverlay.domain.MessageProcessor
import com.yazan.jetoverlay.service.notification.MessageNotificationFilter
import com.yazan.jetoverlay.service.notification.NotificationMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.yazan.jetoverlay.JetOverlayApplication

class AppNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: MessageRepository
    private lateinit var processor: MessageProcessor
    private val filter = MessageNotificationFilter()
    private val mapper = NotificationMapper()

    override fun onCreate() {
        super.onCreate()
        repository = JetOverlayApplication.instance.repository
        
        // Start the Intelligence Layer
        processor = MessageProcessor(repository)
        processor.start()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val notification: StatusBarNotification = sbn
        val packageName = sbn.packageName

        android.util.Log.d("JetOverlayDebug", "DEBUG: Notification posted: pkg=$packageName, id=${sbn.id}, ongoing=${sbn.isOngoing}")

        // Check configuration for this app
        val config = NotificationConfigManager.getConfig(packageName)

        if (!config.shouldVeil) {
            android.util.Log.d("JetOverlayDebug", "DEBUG: Veiling disabled for $packageName, passing through")
            return
        }

        if (filter.shouldProcess(notification)) {
            android.util.Log.d("JetOverlayDebug", "DEBUG: Filter PASSED for $packageName")
            mapper.map(notification)?.let { message ->
                scope.launch {
                    val id = repository.ingestNotification(
                        packageName = message.packageName,
                        sender = message.senderName,
                        content = message.originalContent
                    )
                    android.util.Log.d("JetOverlayDebug", "DEBUG: Ingested message ID=$id. Content='${message.originalContent}'")

                    // Extract and Cache Reply Action
                    val replyAction = findReplyAction(notification.notification)
                    if (replyAction != null) {
                        ReplyActionCache.save(id, replyAction)
                        android.util.Log.d("JetOverlayDebug", "DEBUG: Reply action cached.")
                    } else {
                        android.util.Log.d("JetOverlayDebug", "DEBUG: No reply action found.")
                    }

                    // Cancel (hide) the notification if configured to do so
                    if (config.shouldCancel) {
                        launch(Dispatchers.Main) {
                            try {
                                cancelNotification(sbn.key)
                                android.util.Log.d("JetOverlayDebug", "DEBUG: Notification cancelled for $packageName")
                            } catch (e: Exception) {
                                android.util.Log.e("JetOverlayDebug", "DEBUG: Failed to cancel notification: ${e.message}")
                            }
                        }
                    }

                    // AUTO-TRIGGER: Wake up the overlay
                    launch(Dispatchers.Main) {
                        if (!com.yazan.jetoverlay.api.OverlaySdk.isOverlayActive("agent_bubble")) {
                            android.util.Log.d("JetOverlayDebug", "DEBUG: Triggering OverlaySdk.show()")
                            com.yazan.jetoverlay.api.OverlaySdk.show(
                                context = applicationContext,
                                config = com.yazan.jetoverlay.api.OverlayConfig(
                                    id = "agent_bubble",
                                    type = "overlay_1",
                                    initialX = 100,
                                    initialY = 300
                                )
                            )
                        } else {
                            android.util.Log.d("JetOverlayDebug", "DEBUG: Overlay already active.")
                        }
                    }
                }
            }
        } else {
            android.util.Log.d("JetOverlayDebug", "DEBUG: Filter REJECTED for $packageName")
        }
    }
    
    private fun findReplyAction(notification: android.app.Notification): android.app.Notification.Action? {
        notification.actions?.forEach { action ->
            if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                // Heuristic: Actions with remote input are usually reply actions
                return action
            }
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        ReplyActionCache.clear()
    }
}
