package com.yazan.jetoverlay.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.yazan.jetoverlay.JetOverlayApplication
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.data.NotificationConfigManager
import com.yazan.jetoverlay.data.ReplyActionCache
import com.yazan.jetoverlay.service.notification.MessageNotificationFilter
import com.yazan.jetoverlay.service.notification.NotificationMapper
import com.yazan.jetoverlay.ui.SettingsManager
import com.yazan.jetoverlay.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * NotificationListenerService that intercepts notifications from messaging apps.
 *
 * Extracts message content and reply actions from notifications, caching them
 * for later use by ResponseSender. Supports all major messaging platforms
 * via NotificationMapper's app-specific extraction logic.
 */
class AppNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val COMPONENT = "NotificationListener"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: MessageRepository
    private val filter = MessageNotificationFilter()
    private val mapper = NotificationMapper()

    override fun onCreate() {
        super.onCreate()
        Logger.lifecycle(COMPONENT, "onCreate")
        repository = JetOverlayApplication.instance.repository

        // Register receiver for cancellation requests
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == "com.yazan.jetoverlay.ACTION_CANCEL_NOTIFICATION") {
                    val key = intent.getStringExtra("key")
                    if (key != null) {
                        try {
                            cancelNotification(key)
                            Logger.d(COMPONENT, "Cancelled notification via broadcast: $key")
                        } catch (e: Exception) {
                            Logger.e(COMPONENT, "Failed to cancel notification from broadcast", e)
                        }
                    }
                }
            }
        }
        val filter = android.content.IntentFilter("com.yazan.jetoverlay.ACTION_CANCEL_NOTIFICATION")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val notification: StatusBarNotification = sbn
        val packageName = sbn.packageName

        Logger.d(COMPONENT, "Notification posted: pkg=$packageName, id=${sbn.id}, ongoing=${sbn.isOngoing}")

        // Check configuration for this app
        val config = NotificationConfigManager.getConfig(packageName)

        if (!config.shouldVeil) {
            Logger.d(COMPONENT, "Veiling disabled for $packageName, passing through")
            return
        }

        if (com.yazan.jetoverlay.service.notification.NotificationSilencer.shouldSilence(packageName, notification.notification.extras)) {
            if (SettingsManager.isCancelNotificationsEnabled(applicationContext)) {
                try {
                    cancelNotification(sbn.key)
                    Logger.d(COMPONENT, "Silenced notification for $packageName (fast rule)")
                } catch (e: Exception) {
                    Logger.e(COMPONENT, "Failed to silence notification", e)
                }
            }
            return
        }

        if (filter.shouldProcess(notification)) {
            Logger.d(COMPONENT, "Filter PASSED for $packageName")

            // Check if this is a replyable app for enhanced extraction
            val isReplyable = mapper.isReplyableApp(packageName)
            val context = mapper.getContextCategory(packageName)
            Logger.d(COMPONENT, "App context: $context, replyable: $isReplyable")

            mapper.map(notification)?.let { message ->
                scope.launch {
                    val id = repository.ingestNotification(
                        packageName = message.packageName,
                        sender = message.senderName,
                        content = message.originalContent,
                        contextTag = message.contextTag,
                        threadKey = message.threadKey
                    )
                    Logger.processing(COMPONENT, "Ingested message", id)

                    // Extract and cache reply action using enhanced mapper
                    val replyInfo = mapper.extractReplyAction(notification.notification)
                    if (replyInfo != null) {
                        ReplyActionCache.save(id, replyInfo.action)
                        Logger.d(COMPONENT, "Reply action cached (key: ${replyInfo.resultKey}, label: ${replyInfo.label})")
                    } else {
                        Logger.d(COMPONENT, "No reply action found for $packageName")
                    }

                    // Extract and cache mark-as-read action
                    val markAsReadAction = mapper.extractMarkAsReadAction(notification.notification)
                    if (markAsReadAction != null) {
                        ReplyActionCache.saveMarkAsRead(id, markAsReadAction)
                        Logger.d(COMPONENT, "Mark-as-read action cached")
                    }

                    // Save notification key for later cancellation
                    ReplyActionCache.saveNotificationKey(id, sbn.key)

                    // Cancel (hide) the notification if configured to do so
                    if (config.shouldCancel && SettingsManager.isCancelNotificationsEnabled(applicationContext)) {
                        launch(Dispatchers.Main) {
                            try {
                                cancelNotification(sbn.key)
                                Logger.d(COMPONENT, "Notification cancelled for $packageName")
                                val stored = repository.getMessage(id)
                                if (stored != null) {
                                    val elapsedMs = System.currentTimeMillis() - stored.timestamp
                                    Logger.processing(COMPONENT, "Ingest->cancel ${elapsedMs}ms", id)
                                }
                            } catch (e: Exception) {
                                Logger.e(COMPONENT, "Failed to cancel notification", e)
                            }
                        }
                    }

                    // AUTO-TRIGGER: Wake up the overlay
                    launch(Dispatchers.Main) {
                        if (!com.yazan.jetoverlay.api.OverlaySdk.isOverlayActive("agent_bubble")) {
                            Logger.d(COMPONENT, "Triggering overlay request")
                            com.yazan.jetoverlay.util.OverlayLaunchCoordinator.requestOverlay(
                                context = applicationContext,
                                config = com.yazan.jetoverlay.api.OverlayConfig(
                                    id = "agent_bubble",
                                    type = "overlay_1",
                                    initialX = 0,
                                    initialY = 120
                                ),
                                source = "notification_listener"
                            )
                        } else {
                            Logger.d(COMPONENT, "Overlay already active")
                        }
                    }
                }
            }
        } else {
            Logger.d(COMPONENT, "Filter REJECTED for $packageName")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.lifecycle(COMPONENT, "onDestroy")
    }
}
