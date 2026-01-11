package com.yazan.jetoverlay.service.notification

import android.app.Notification
import android.service.notification.StatusBarNotification

fun interface NotificationFilter {
    fun shouldProcess(sbn: StatusBarNotification): Boolean
}

class MessageNotificationFilter : NotificationFilter {
    override fun shouldProcess(sbn: StatusBarNotification): Boolean {
        android.util.Log.d("JetOverlayDebug", "NotificationFilter: Checking notification from ${sbn.packageName}")
        val notification = sbn.notification ?: return false
        
        // DEBUG: Accept almost everything to verify ingestion works
        // We just filter out ongoing events (like music players, downloads)
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            android.util.Log.d("JetOverlayDebug", "NotificationFilter: Ignoring ongoing event: ${sbn.packageName}")
            return false
        }
        
        android.util.Log.d("JetOverlayDebug", "NotificationFilter: Allowing notification: ${sbn.packageName}")
        return true
    }
}
