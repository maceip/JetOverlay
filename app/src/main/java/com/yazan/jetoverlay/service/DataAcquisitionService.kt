package com.yazan.jetoverlay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.yazan.jetoverlay.JetOverlayApplication
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.service.integration.EmailIntegration
import com.yazan.jetoverlay.service.integration.GitHubIntegration
import com.yazan.jetoverlay.service.integration.NotionIntegration
import com.yazan.jetoverlay.service.integration.SlackIntegration
import com.yazan.jetoverlay.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Unified Data Acquisition Service that coordinates all data sources.
 *
 * This foreground service manages polling intervals for each integration:
 * - Slack (15s polling when connected)
 * - Email/Gmail (30s polling when connected)
 * - Notion (30s polling when connected)
 * - GitHub (30s polling when connected)
 *
 * SMS and Notifications are handled separately by their respective receivers/services.
 *
 * Uses a SupervisorJob to isolate failures - if one integration fails,
 * others continue to operate.
 */
class DataAcquisitionService : Service() {

    companion object {
        private const val COMPONENT = "DataAcquisitionService"
        private const val CHANNEL_ID = "data_acquisition_channel"
        private const val CHANNEL_NAME = "Data Acquisition"
        private const val NOTIFICATION_ID = 201

        private var isRunning = false

        /**
         * Checks if the service is currently running.
         */
        fun isServiceRunning(): Boolean = isRunning

        /**
         * Starts the DataAcquisitionService if not already running.
         */
        fun start(context: Context) {
            if (isRunning) {
                Logger.d(COMPONENT, "Service already running")
                return
            }

            val intent = Intent(context, DataAcquisitionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the DataAcquisitionService.
         */
        fun stop(context: Context) {
            val intent = Intent(context, DataAcquisitionService::class.java)
            context.stopService(intent)
        }
    }

    // Coroutine scope with SupervisorJob for failure isolation
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var repository: MessageRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Logger.lifecycle(COMPONENT, "onCreate")

        isRunning = true
        repository = JetOverlayApplication.instance.repository

        startForegroundNotification()
        startAllIntegrations()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.lifecycle(COMPONENT, "onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.lifecycle(COMPONENT, "onDestroy")

        isRunning = false
        stopAllIntegrations()
        serviceScope.cancel()
    }

    /**
     * Starts polling for all connected integrations.
     */
    private fun startAllIntegrations() {
        Logger.d(COMPONENT, "Starting all data integrations")

        // Start Slack polling if connected
        if (SlackIntegration.isConnected()) {
            Logger.d(COMPONENT, "Starting Slack polling")
            SlackIntegration.startPolling(repository)
        } else {
            Logger.d(COMPONENT, "Slack not connected, skipping")
        }

        // Start Email polling if connected
        if (EmailIntegration.isConnected()) {
            Logger.d(COMPONENT, "Starting Email polling")
            EmailIntegration.startPolling()
        } else {
            Logger.d(COMPONENT, "Email not connected, skipping")
        }

        // Start Notion polling if connected
        if (NotionIntegration.isConnected()) {
            Logger.d(COMPONENT, "Starting Notion polling")
            NotionIntegration.startPolling()
        } else {
            Logger.d(COMPONENT, "Notion not connected, skipping")
        }

        // Start GitHub polling if connected
        if (GitHubIntegration.isConnected()) {
            Logger.d(COMPONENT, "Starting GitHub polling")
            GitHubIntegration.startPolling()
        } else {
            Logger.d(COMPONENT, "GitHub not connected, skipping")
        }

        Logger.d(COMPONENT, "Integration startup complete")
    }

    /**
     * Stops polling for all integrations.
     */
    private fun stopAllIntegrations() {
        Logger.d(COMPONENT, "Stopping all data integrations")

        SlackIntegration.stopPolling()
        EmailIntegration.stopPolling()
        NotionIntegration.stopPolling()
        GitHubIntegration.stopPolling()

        Logger.d(COMPONENT, "All integrations stopped")
    }

    /**
     * Restarts a specific integration's polling.
     * Call this after a successful OAuth connection.
     */
    fun restartIntegration(integrationName: String) {
        Logger.d(COMPONENT, "Restarting integration: $integrationName")

        when (integrationName.lowercase()) {
            "slack" -> {
                SlackIntegration.stopPolling()
                if (SlackIntegration.isConnected()) {
                    SlackIntegration.startPolling(repository)
                }
            }
            "email", "gmail" -> {
                EmailIntegration.stopPolling()
                if (EmailIntegration.isConnected()) {
                    EmailIntegration.startPolling()
                }
            }
            "notion" -> {
                NotionIntegration.stopPolling()
                if (NotionIntegration.isConnected()) {
                    NotionIntegration.startPolling()
                }
            }
            "github" -> {
                GitHubIntegration.stopPolling()
                if (GitHubIntegration.isConnected()) {
                    GitHubIntegration.startPolling()
                }
            }
            else -> {
                Logger.w(COMPONENT, "Unknown integration: $integrationName")
            }
        }
    }

    /**
     * Gets the current status of all integrations.
     */
    fun getIntegrationStatus(): Map<String, IntegrationStatus> {
        return mapOf(
            "slack" to IntegrationStatus(
                isConnected = SlackIntegration.isConnected(),
                isPolling = SlackIntegration.isPollingActive()
            ),
            "email" to IntegrationStatus(
                isConnected = EmailIntegration.isConnected(),
                isPolling = EmailIntegration.isPollingActive()
            ),
            "notion" to IntegrationStatus(
                isConnected = NotionIntegration.isConnected(),
                isPolling = NotionIntegration.isPollingActive()
            ),
            "github" to IntegrationStatus(
                isConnected = GitHubIntegration.isConnected(),
                isPolling = GitHubIntegration.isPollingActive()
            )
        )
    }

    /**
     * Creates and shows the foreground notification required for foreground services.
     */
    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)

        // Create notification channel for Android O+
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background data collection from connected services"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("JetOverlay Active")
            .setContentText("Monitoring connected services for messages")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Logger.d(COMPONENT, "Foreground notification started")
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Failed to start foreground notification", e)
        }
    }

    /**
     * Data class representing the status of an integration.
     */
    data class IntegrationStatus(
        val isConnected: Boolean,
        val isPolling: Boolean
    )
}
