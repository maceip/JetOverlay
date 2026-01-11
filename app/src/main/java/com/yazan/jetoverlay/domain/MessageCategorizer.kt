package com.yazan.jetoverlay.domain

import com.yazan.jetoverlay.data.Message

/**
 * Categorizes messages into buckets using simple heuristics.
 * Will be enhanced with ML-based categorization in future phases.
 */
class MessageCategorizer {

    companion object {
        // Package names for work-related apps
        private val WORK_PACKAGES = setOf(
            "com.slack",
            "com.Slack",
            "com.github.android",
            "com.notion.id",
            "com.microsoft.teams",
            "com.google.android.apps.docs",
            "com.atlassian.android.jira.core"
        )

        // Package names for social messaging apps
        private val SOCIAL_PACKAGES = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.facebook.orca",
            "com.instagram.android",
            "com.snapchat.android",
            "com.discord",
            "com.viber.voip"
        )

        // Keywords that indicate urgency
        private val URGENT_KEYWORDS = setOf(
            "urgent",
            "emergency",
            "asap",
            "immediately",
            "critical",
            "important",
            "911"
        )

        // Package names commonly used for promotional content
        private val PROMOTIONAL_PACKAGES = setOf(
            "com.google.android.apps.shopping",
            "com.amazon.mShop.android.shopping",
            "com.alibaba.aliexpresshd"
        )

        // Keywords that indicate promotional content
        private val PROMOTIONAL_KEYWORDS = setOf(
            "sale",
            "discount",
            "% off",
            "offer",
            "deal",
            "promo",
            "limited time",
            "subscribe",
            "unsubscribe"
        )

        // Keywords that indicate transactional messages
        private val TRANSACTIONAL_KEYWORDS = setOf(
            "otp",
            "verification code",
            "verify",
            "receipt",
            "invoice",
            "order",
            "shipped",
            "delivered",
            "tracking",
            "payment",
            "transaction",
            "balance",
            "account"
        )
    }

    /**
     * Categorizes a message into a bucket based on heuristics.
     */
    fun categorize(message: Message): MessageBucket {
        val content = message.originalContent.lowercase()
        val sender = message.senderName.lowercase()
        val packageName = message.packageName.lowercase()

        // Check for urgency first (highest priority)
        if (isUrgent(content, sender)) {
            return MessageBucket.URGENT
        }

        // Check for work-related sources
        if (isWork(packageName, sender)) {
            return MessageBucket.WORK
        }

        // Check for promotional content before transactional
        // (promotional keywords like "% off" are more specific)
        if (isPromotional(content, packageName)) {
            return MessageBucket.PROMOTIONAL
        }

        // Check for transactional messages
        if (isTransactional(content, packageName)) {
            return MessageBucket.TRANSACTIONAL
        }

        // Check for social messaging apps
        if (isSocial(packageName)) {
            return MessageBucket.SOCIAL
        }

        return MessageBucket.UNKNOWN
    }

    private fun isUrgent(content: String, sender: String): Boolean {
        return URGENT_KEYWORDS.any { keyword ->
            content.contains(keyword) || sender.contains(keyword)
        }
    }

    private fun isWork(packageName: String, sender: String): Boolean {
        // Check against known work packages
        if (WORK_PACKAGES.any { packageName.contains(it.lowercase()) }) {
            return true
        }

        // Check for work email domains
        val workDomains = listOf("@company.com", "@work.", "@corp.", "@enterprise.")
        if (workDomains.any { sender.contains(it) }) {
            return true
        }

        return false
    }

    private fun isTransactional(content: String, packageName: String): Boolean {
        // Bank apps are typically transactional
        if (packageName.contains("bank") || packageName.contains("finance")) {
            return true
        }

        return TRANSACTIONAL_KEYWORDS.any { keyword ->
            content.contains(keyword)
        }
    }

    private fun isPromotional(content: String, packageName: String): Boolean {
        if (PROMOTIONAL_PACKAGES.any { packageName.contains(it.lowercase()) }) {
            return true
        }

        return PROMOTIONAL_KEYWORDS.any { keyword ->
            content.contains(keyword)
        }
    }

    private fun isSocial(packageName: String): Boolean {
        return SOCIAL_PACKAGES.any { packageName.contains(it.lowercase()) }
    }
}
