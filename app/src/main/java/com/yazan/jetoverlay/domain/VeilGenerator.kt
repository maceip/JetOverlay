package com.yazan.jetoverlay.domain

import com.yazan.jetoverlay.data.Message

/**
 * Generates contextual veiled content based on message bucket.
 * The veil hides emotionally charged or sensitive text while still
 * providing useful context about the message type and sender.
 */
class VeilGenerator {

    /**
     * Generates a veiled representation of the message.
     * The veil provides context without revealing actual message content.
     *
     * @param message The original message
     * @param bucket The categorized bucket for the message
     * @return A veiled string that hides the actual content
     */
    fun generateVeil(message: Message, bucket: MessageBucket): String {
        val sanitizedSender = sanitizeSender(message.senderName)
        val appName = extractAppName(message.packageName)

        return when (bucket) {
            MessageBucket.URGENT -> "Priority message from $sanitizedSender"
            MessageBucket.WORK -> if (appName.isNotEmpty()) {
                "Work notification from $appName"
            } else {
                "Work notification from $sanitizedSender"
            }
            MessageBucket.SOCIAL -> "New message from $sanitizedSender"
            MessageBucket.PROMOTIONAL -> "Promotional content"
            MessageBucket.TRANSACTIONAL -> "Account notification"
            MessageBucket.UNKNOWN -> "New notification"
        }
    }

    /**
     * Sanitizes the sender name to ensure no sensitive content leaks.
     * Only keeps alphanumeric characters, spaces, and common punctuation.
     */
    private fun sanitizeSender(senderName: String): String {
        if (senderName.isBlank()) {
            return "Unknown"
        }
        // Remove potentially sensitive characters, keep only safe ones
        val sanitized = senderName.replace(Regex("[^a-zA-Z0-9 .\\-_]"), "").trim()
        return if (sanitized.isNotEmpty()) sanitized else "Unknown"
    }

    /**
     * Extracts a human-readable app name from the package name.
     */
    private fun extractAppName(packageName: String): String {
        return when {
            packageName.contains("slack", ignoreCase = true) -> "Slack"
            packageName.contains("teams", ignoreCase = true) -> "Teams"
            packageName.contains("github", ignoreCase = true) -> "GitHub"
            packageName.contains("notion", ignoreCase = true) -> "Notion"
            packageName.contains("jira", ignoreCase = true) -> "Jira"
            packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
            packageName.contains("telegram", ignoreCase = true) -> "Telegram"
            packageName.contains("discord", ignoreCase = true) -> "Discord"
            packageName.contains("instagram", ignoreCase = true) -> "Instagram"
            packageName.contains("facebook", ignoreCase = true) -> "Facebook"
            else -> ""
        }
    }
}
