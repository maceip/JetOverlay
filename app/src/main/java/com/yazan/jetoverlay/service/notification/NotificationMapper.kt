package com.yazan.jetoverlay.service.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.yazan.jetoverlay.data.Message
// import com.yazan.jetoverlay.data.MessageStatus

class NotificationMapper {

    fun map(sbn: StatusBarNotification): Message? {
        val extras = sbn.notification.extras
        
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null
        
        // Basic ETL: extract package, sender (title), and content (text)
        return Message(
            packageName = sbn.packageName,
            senderName = title,
            originalContent = text,
            status = "RECEIVED" // MessageStatus.RECEIVED.name
        )
    }
}
