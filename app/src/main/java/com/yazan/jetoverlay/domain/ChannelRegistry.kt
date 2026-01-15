package com.yazan.jetoverlay.domain

import android.content.Context
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.util.Logger

/**
 * Registry for channel-specific reply handlers.
 * Add new handlers here to support more integrations.
 */
object ChannelRegistry {
    private const val TAG = "ChannelRegistry"

    fun resolve(context: Context, sender: ResponseSender, message: Message): ReplyHandler? {
        val handlers = listOf(
            EmailReplyHandler(context),
            SlackReplyHandler(),
            NotificationReplyHandler(context, sender) // fallback to notification reply/PendingIntent
        )
        val handler = handlers.firstOrNull { it.canHandle(message) }
        if (handler == null) {
            Logger.w(TAG, "No handler matched message ${message.id} (${message.packageName}), falling back to notification reply")
        }
        return handler ?: NotificationReplyHandler(context, sender)
    }
}

interface ReplyHandler {
    fun canHandle(message: Message): Boolean
    fun send(message: Message, responseText: String, markAsRead: Boolean = true): ResponseSender.SendResult
}
