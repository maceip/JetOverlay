package com.yazan.jetoverlay.api

import androidx.annotation.DrawableRes

/**
 * Configuration for the Foreground Service Notification.
 *
 * @param title The title of the notification (e.g., "Live Scores").
 * @param message The body text of the notification (e.g., "Overlays are active").
 * @param iconResId The resource ID for the small icon. Defaults to a generic system icon if not provided.
 * @param channelId The ID for the notification channel.
 * @param channelName The user-visible name of the channel in System Settings.
 */
data class OverlayNotificationConfig(
    val title: String = "Overlay Active",
    val message: String = "Tap to manage active overlays",
    @DrawableRes val iconResId: Int? = null, // If null, we use a default
    val channelId: String = "overlay_service_channel",
    val channelName: String = "Overlay Service"
)