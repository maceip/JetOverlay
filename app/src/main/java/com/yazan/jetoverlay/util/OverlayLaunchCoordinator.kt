package com.yazan.jetoverlay.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yazan.jetoverlay.MainActivity
import com.yazan.jetoverlay.app.R
import com.yazan.jetoverlay.api.OverlayConfig
import com.yazan.jetoverlay.api.OverlaySdk

/**
 * Centralized overlay/app transition handling to keep behavior consistent and resilient.
 */
object OverlayLaunchCoordinator {
    private const val COMPONENT = "OverlayLaunchCoordinator"
    private const val CHANNEL_ID = "overlay_entry_channel"
    private const val CHANNEL_NAME = "Overlay Entry"
    private const val NOTIFICATION_ID = 402

    fun requestOverlay(
        context: Context,
        config: OverlayConfig,
        source: String
    ) {
        val appContext = context.applicationContext
        val permissionManager = PermissionManager(appContext)
        if (!permissionManager.hasOverlayPermission()) {
            Logger.w(COMPONENT, "Overlay request blocked: missing permission (source=$source)")
            showOverlayPermissionNotification(appContext)
            return
        }
        try {
            OverlaySdk.show(appContext, config)
        } catch (t: Throwable) {
            Logger.e(COMPONENT, "Overlay request crashed (source=$source)", t)
            OverlaySdk.reportOverlayError(COMPONENT, "Overlay request crashed (source=$source)", t)
        }
    }

    private fun showOverlayPermissionNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Logger.w(COMPONENT, "Cannot post notification; POST_NOTIFICATIONS not granted")
                return
            }
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)

        val settingsIntent = PermissionManager(context).getOverlayPermissionIntent().apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val settingsPending = PendingIntent.getActivity(
            context,
            0,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPending = PendingIntent.getActivity(
            context,
            1,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Overlay permission required")
            .setContentText("Tap to enable the overlay so JetOverlay can handle messages.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Enable Overlay", settingsPending)
            .addAction(0, "Open App", appPending)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
