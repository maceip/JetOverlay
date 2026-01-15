package com.yazan.jetoverlay.service.notification

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.yazan.jetoverlay.data.Message

/**
 * Maps StatusBarNotifications to Message objects, with app-specific handling
 * for extracting sender, content, and reply actions from various messaging apps.
 *
 * Supported apps:
 * - Messaging: WhatsApp, Signal, Telegram, Facebook Messenger
 * - Work: Slack, Linear, Microsoft Teams
 * - Social: Instagram DM, Twitter/X DMs
 * - Email: Gmail, Outlook
 */
class NotificationMapper {

    companion object {
        // Package name constants for supported apps
        const val PKG_WHATSAPP = "com.whatsapp"
        const val PKG_SIGNAL = "org.thoughtcrime.securesms"
        const val PKG_TELEGRAM = "org.telegram.messenger"
        const val PKG_MESSENGER = "com.facebook.orca"
        const val PKG_SLACK = "com.Slack"
        const val PKG_LINEAR = "com.linear"
        const val PKG_TEAMS = "com.microsoft.teams"
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_TWITTER = "com.twitter.android"
        const val PKG_GMAIL = "com.google.android.gm"
        const val PKG_OUTLOOK = "com.microsoft.office.outlook"

        // Categories for grouping
        val MESSAGING_APPS = setOf(PKG_WHATSAPP, PKG_SIGNAL, PKG_TELEGRAM, PKG_MESSENGER)
        val WORK_APPS = setOf(PKG_SLACK, PKG_LINEAR, PKG_TEAMS)
        val SOCIAL_APPS = setOf(PKG_INSTAGRAM, PKG_TWITTER)
        val EMAIL_APPS = setOf(PKG_GMAIL, PKG_OUTLOOK)

        val ALL_REPLYABLE_APPS = MESSAGING_APPS + WORK_APPS + SOCIAL_APPS + EMAIL_APPS
    }

    /**
     * Maps a StatusBarNotification to a Message object.
     */
    fun map(sbn: StatusBarNotification): Message? {
        val extras = sbn.notification.extras
        val packageName = sbn.packageName
        // Use app-specific extraction for known apps
        return when (packageName) {
            PKG_WHATSAPP -> mapWhatsApp(sbn, extras)
            PKG_SIGNAL -> mapSignal(sbn, extras)
            PKG_TELEGRAM -> mapTelegram(sbn, extras)
            PKG_MESSENGER -> mapMessenger(sbn, extras)
            PKG_SLACK -> mapSlack(sbn, extras)
            PKG_TEAMS -> mapTeams(sbn, extras)
            PKG_INSTAGRAM -> mapInstagram(sbn, extras)
            PKG_TWITTER -> mapTwitter(sbn, extras)
            PKG_GMAIL -> mapGmail(sbn, extras)
            PKG_OUTLOOK -> mapOutlook(sbn, extras)
            else -> mapGeneric(sbn, extras)
        }
    }

    /**
     * Extracts the reply action from a notification.
     * Returns the action containing RemoteInput for inline reply.
     */
    fun extractReplyAction(notification: Notification): ReplyActionInfo? {
        notification.actions?.forEach { action ->
            if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                val remoteInput = action.remoteInputs[0]
                return ReplyActionInfo(
                    action = action,
                    resultKey = remoteInput.resultKey,
                    label = action.title?.toString() ?: "Reply",
                    allowedMimeTypes = remoteInput.allowedDataTypes?.toSet() ?: emptySet()
                )
            }
        }
        return null
    }

    /**
     * Extracts any "Mark as Read" or dismiss actions from a notification.
     */
    fun extractMarkAsReadAction(notification: Notification): Notification.Action? {
        notification.actions?.forEach { action ->
            val title = action.title?.toString()?.lowercase() ?: ""
            if (title.contains("read") || title.contains("mark") || title.contains("dismiss")) {
                // Actions without RemoteInput are usually mark-as-read or dismiss
                if (action.remoteInputs.isNullOrEmpty()) {
                    return action
                }
            }
        }
        return null
    }

    /**
     * Determines the context category for a package.
     */
    fun getContextCategory(packageName: String): MessageContext {
        return when (packageName) {
            in MESSAGING_APPS -> MessageContext.PERSONAL
            in WORK_APPS -> MessageContext.WORK
            in SOCIAL_APPS -> MessageContext.SOCIAL
            in EMAIL_APPS -> MessageContext.EMAIL
            else -> MessageContext.OTHER
        }
    }

    /**
     * Checks if an app supports inline reply via RemoteInput.
     */
    fun isReplyableApp(packageName: String): Boolean {
        return packageName in ALL_REPLYABLE_APPS
    }

    // --- App-specific mappers ---

    private fun mapWhatsApp(sbn: StatusBarNotification, extras: Bundle): Message? {
        // WhatsApp uses EXTRA_TITLE for sender, EXTRA_TEXT for message
        // Group chats: EXTRA_TITLE = "Group Name", EXTRA_SUMMARY_TEXT = "Sender: Message"
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null

        // Check for group chat indicator
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
        val isGroup = summaryText != null || extras.containsKey(Notification.EXTRA_MESSAGES)

        // For group chats, extract actual sender from message lines
        val (sender, content) = if (isGroup) {
            extractGroupMessage(text, title)
        } else {
            title to text
        }

        return Message(
            packageName = sbn.packageName,
            senderName = sender,
            originalContent = content,
            status = "RECEIVED",
            contextTag = "personal",
            threadKey = buildSenderThreadKey(sbn.packageName, sender)
        )
    }

    private fun mapSignal(sbn: StatusBarNotification, extras: Bundle): Message? {
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null

        return Message(
            packageName = sbn.packageName,
            senderName = title,
            originalContent = text,
            status = "RECEIVED",
            contextTag = "personal",
            threadKey = buildSenderThreadKey(sbn.packageName, title)
        )
    }

    private fun mapTelegram(sbn: StatusBarNotification, extras: Bundle): Message? {
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null

        // Telegram shows "Name: message" in text for groups
        val (sender, content) = if (text.contains(": ")) {
            val parts = text.split(": ", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else title to text
        } else {
            title to text
        }

        return Message(
            packageName = sbn.packageName,
            senderName = sender,
            originalContent = content,
            status = "RECEIVED",
            contextTag = "personal",
            threadKey = buildSenderThreadKey(sbn.packageName, sender)
        )
    }

    private fun mapMessenger(sbn: StatusBarNotification, extras: Bundle): Message? {
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null

        return Message(
            packageName = sbn.packageName,
            senderName = title,
            originalContent = text,
            status = "RECEIVED",
            contextTag = "social",
            threadKey = buildSenderThreadKey(sbn.packageName, title)
        )
    }

    private fun mapSlack(sbn: StatusBarNotification, extras: Bundle): Message? {
        // Slack: EXTRA_TITLE = "#channel" or "DM with Name", EXTRA_TEXT = "Sender: message"
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null

        val (sender, content) = if (text.contains(": ")) {
            val parts = text.split(": ", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else title to text
        } else {
            title to text
        }

        return Message(
            packageName = sbn.packageName,
            senderName = "$sender ($title)",
            originalContent = content,
            status = "RECEIVED",
            contextTag = "work",
            threadKey = buildSenderThreadKey(sbn.packageName, sender)
        )
    }

    private fun mapTeams(sbn: StatusBarNotification, extras: Bundle): Message? {
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null

        return Message(
            packageName = sbn.packageName,
            senderName = title,
            originalContent = text,
            status = "RECEIVED",
            contextTag = "work",
            threadKey = buildSenderThreadKey(sbn.packageName, title)
        )
    }

    private fun mapInstagram(sbn: StatusBarNotification, extras: Bundle): Message? {
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null

        return Message(
            packageName = sbn.packageName,
            senderName = title,
            originalContent = text,
            status = "RECEIVED",
            contextTag = "social",
            threadKey = buildSenderThreadKey(sbn.packageName, title)
        )
    }

    private fun mapTwitter(sbn: StatusBarNotification, extras: Bundle): Message? {
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null

        return Message(
            packageName = sbn.packageName,
            senderName = title,
            originalContent = text,
            status = "RECEIVED",
            contextTag = "social",
            threadKey = buildSenderThreadKey(sbn.packageName, title)
        )
    }

    private fun mapGmail(sbn: StatusBarNotification, extras: Bundle): Message? {
        // Gmail: EXTRA_TITLE = sender, EXTRA_TEXT = subject, EXTRA_BIG_TEXT = preview
        val sender = extras.getString(Notification.EXTRA_TITLE) ?: return null
        val subject = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val preview = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: subject

        return Message(
            packageName = sbn.packageName,
            senderName = sender,
            originalContent = "Subject: $subject\n$preview",
            status = "RECEIVED",
            contextTag = "email",
            threadKey = buildSenderThreadKey(sbn.packageName, sender)
        )
    }

    private fun mapOutlook(sbn: StatusBarNotification, extras: Bundle): Message? {
        val sender = extras.getString(Notification.EXTRA_TITLE) ?: return null
        val subject = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val preview = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: subject

        return Message(
            packageName = sbn.packageName,
            senderName = sender,
            originalContent = "Subject: $subject\n$preview",
            status = "RECEIVED",
            contextTag = "email",
            threadKey = buildSenderThreadKey(sbn.packageName, sender)
        )
    }

    private fun mapGeneric(sbn: StatusBarNotification, extras: Bundle): Message? {
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null

        return Message(
            packageName = sbn.packageName,
            senderName = title,
            originalContent = text,
            status = "RECEIVED",
            threadKey = buildSenderThreadKey(sbn.packageName, title)
        )
    }

    private fun buildSenderThreadKey(packageName: String, sender: String): String? {
        val normalized = sender.trim().lowercase()
        return if (normalized.isNotEmpty()) "$packageName:$normalized" else null
    }

    // --- Helper methods ---

    /**
     * Extracts sender and content from group message format "Sender: Content"
     */
    private fun extractGroupMessage(text: String, fallbackSender: String): Pair<String, String> {
        return if (text.contains(": ")) {
            val parts = text.split(": ", limit = 2)
            if (parts.size == 2) {
                parts[0] to parts[1]
            } else {
                fallbackSender to text
            }
        } else {
            fallbackSender to text
        }
    }

    /**
     * Information about a reply action extracted from a notification.
     */
    data class ReplyActionInfo(
        val action: Notification.Action,
        val resultKey: String,
        val label: String,
        val allowedMimeTypes: Set<String>
    )

    /**
     * Context categories for message grouping.
     */
    enum class MessageContext {
        PERSONAL,  // Friends, family - WhatsApp, Signal, Telegram
        WORK,      // Colleagues - Slack, Teams, Linear
        SOCIAL,    // Social media - Instagram, Twitter
        EMAIL,     // Email - Gmail, Outlook
        OTHER      // Unknown apps
    }
}
