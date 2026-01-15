package com.yazan.jetoverlay.domain

import android.app.PendingIntent
import android.content.Context
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.ReplyActionCache
import com.yazan.jetoverlay.util.Logger

/**
 * Best-effort mark-as-read API. Uses notification actions when available,
 * otherwise falls back to integration-specific handlers.
 */
private const val MARK_AS_READ_TAG = "MarkAsReadService"

object MarkAsReadService {

    private val handlers: List<MarkAsReadHandler> = listOf(
        NotificationMarkAsReadHandler(),
        SlackMarkAsReadHandler(),
        EmailMarkAsReadHandler(),
        GitHubMarkAsReadHandler(),
        NotionMarkAsReadHandler()
    )

    fun markAsRead(context: Context, message: Message): ResponseSender.SendResult {
        val handler = handlers.firstOrNull { it.canHandle(message) }
            ?: return ResponseSender.SendResult.Error("No mark-as-read handler for ${message.packageName}")
        return handler.markAsRead(context, message)
    }
}

interface MarkAsReadHandler {
    fun canHandle(message: Message): Boolean
    fun markAsRead(context: Context, message: Message): ResponseSender.SendResult
}

class NotificationMarkAsReadHandler : MarkAsReadHandler {
    override fun canHandle(message: Message): Boolean {
        return ReplyActionCache.getMarkAsRead(message.id) != null
    }

    override fun markAsRead(context: Context, message: Message): ResponseSender.SendResult {
        val action = ReplyActionCache.getMarkAsRead(message.id)
            ?: return ResponseSender.SendResult.Error("No notification mark-as-read action")
        return try {
            action.actionIntent.send()
            Logger.d(MARK_AS_READ_TAG, "Marked as read via notification action: ${message.id}")
            ResponseSender.SendResult.Success
        } catch (e: PendingIntent.CanceledException) {
            Logger.w(MARK_AS_READ_TAG, "Mark-as-read action canceled for ${message.id}")
            ResponseSender.SendResult.Error("Mark-as-read action canceled")
        } catch (e: Exception) {
            Logger.e(MARK_AS_READ_TAG, "Failed to mark-as-read via notification action", e)
            ResponseSender.SendResult.Error("Failed to mark-as-read: ${e.message}")
        }
    }
}

class SlackMarkAsReadHandler : MarkAsReadHandler {
    override fun canHandle(message: Message): Boolean {
        val tag = message.contextTag?.lowercase()
        val pkg = message.packageName.lowercase()
        return tag == "slack" || pkg.contains("slack")
    }

    override fun markAsRead(context: Context, message: Message): ResponseSender.SendResult {
        Logger.w(MARK_AS_READ_TAG, "Slack mark-as-read not implemented for ${message.id}")
        return ResponseSender.SendResult.Error("Slack mark-as-read not implemented")
    }
}

class EmailMarkAsReadHandler : MarkAsReadHandler {
    override fun canHandle(message: Message): Boolean {
        val tag = message.contextTag?.lowercase()
        val pkg = message.packageName.lowercase()
        return tag == "email" || pkg.contains("gmail") || pkg.contains("outlook") || pkg == "email"
    }

    override fun markAsRead(context: Context, message: Message): ResponseSender.SendResult {
        Logger.w(MARK_AS_READ_TAG, "Email mark-as-read not implemented for ${message.id}")
        return ResponseSender.SendResult.Error("Email mark-as-read not implemented")
    }
}

class GitHubMarkAsReadHandler : MarkAsReadHandler {
    override fun canHandle(message: Message): Boolean {
        val tag = message.contextTag?.lowercase()
        val pkg = message.packageName.lowercase()
        return tag == "github" || pkg.contains("github")
    }

    override fun markAsRead(context: Context, message: Message): ResponseSender.SendResult {
        Logger.w(MARK_AS_READ_TAG, "GitHub mark-as-read not implemented for ${message.id}")
        return ResponseSender.SendResult.Error("GitHub mark-as-read not implemented")
    }
}

class NotionMarkAsReadHandler : MarkAsReadHandler {
    override fun canHandle(message: Message): Boolean {
        val tag = message.contextTag?.lowercase()
        val pkg = message.packageName.lowercase()
        return tag == "notion" || pkg.contains("notion")
    }

    override fun markAsRead(context: Context, message: Message): ResponseSender.SendResult {
        Logger.w(MARK_AS_READ_TAG, "Notion mark-as-read not implemented for ${message.id}")
        return ResponseSender.SendResult.Error("Notion mark-as-read not implemented")
    }
}
