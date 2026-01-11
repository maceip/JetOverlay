package com.yazan.jetoverlay.domain

import com.yazan.jetoverlay.data.Message
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MessageCategorizerTest {

    private lateinit var categorizer: MessageCategorizer

    @Before
    fun setup() {
        categorizer = MessageCategorizer()
    }

    // ==================== URGENT Tests ====================

    @Test
    fun `message with urgent keyword is categorized as URGENT`() {
        val message = createMessage(
            content = "This is urgent! Please respond ASAP.",
            sender = "Boss"
        )
        assertEquals(MessageBucket.URGENT, categorizer.categorize(message))
    }

    @Test
    fun `message with emergency keyword is categorized as URGENT`() {
        val message = createMessage(
            content = "Emergency: Please call immediately",
            sender = "Security"
        )
        assertEquals(MessageBucket.URGENT, categorizer.categorize(message))
    }

    @Test
    fun `message with asap keyword is categorized as URGENT`() {
        val message = createMessage(
            content = "Need this done ASAP",
            sender = "Manager"
        )
        assertEquals(MessageBucket.URGENT, categorizer.categorize(message))
    }

    @Test
    fun `message with critical keyword is categorized as URGENT`() {
        val message = createMessage(
            content = "Critical bug found in production",
            sender = "DevOps"
        )
        assertEquals(MessageBucket.URGENT, categorizer.categorize(message))
    }

    // ==================== WORK Tests ====================

    @Test
    fun `message from Slack is categorized as WORK`() {
        val message = createMessage(
            content = "New message in #general",
            sender = "Slack",
            packageName = "com.slack"
        )
        assertEquals(MessageBucket.WORK, categorizer.categorize(message))
    }

    @Test
    fun `message from GitHub is categorized as WORK`() {
        val message = createMessage(
            content = "New pull request review request",
            sender = "GitHub",
            packageName = "com.github.android"
        )
        assertEquals(MessageBucket.WORK, categorizer.categorize(message))
    }

    @Test
    fun `message from Notion is categorized as WORK`() {
        val message = createMessage(
            content = "You were mentioned in a page",
            sender = "Notion",
            packageName = "com.notion.id"
        )
        assertEquals(MessageBucket.WORK, categorizer.categorize(message))
    }

    @Test
    fun `message from Microsoft Teams is categorized as WORK`() {
        val message = createMessage(
            content = "Meeting starting soon",
            sender = "Teams",
            packageName = "com.microsoft.teams"
        )
        assertEquals(MessageBucket.WORK, categorizer.categorize(message))
    }

    // ==================== SOCIAL Tests ====================

    @Test
    fun `message from WhatsApp is categorized as SOCIAL`() {
        val message = createMessage(
            content = "Hey, what's up?",
            sender = "John",
            packageName = "com.whatsapp"
        )
        assertEquals(MessageBucket.SOCIAL, categorizer.categorize(message))
    }

    @Test
    fun `message from Telegram is categorized as SOCIAL`() {
        val message = createMessage(
            content = "Check out this photo",
            sender = "Friend",
            packageName = "org.telegram.messenger"
        )
        assertEquals(MessageBucket.SOCIAL, categorizer.categorize(message))
    }

    @Test
    fun `message from Facebook Messenger is categorized as SOCIAL`() {
        val message = createMessage(
            content = "Happy birthday!",
            sender = "Family",
            packageName = "com.facebook.orca"
        )
        assertEquals(MessageBucket.SOCIAL, categorizer.categorize(message))
    }

    @Test
    fun `message from Discord is categorized as SOCIAL`() {
        val message = createMessage(
            content = "Join the voice channel",
            sender = "Gaming Group",
            packageName = "com.discord"
        )
        assertEquals(MessageBucket.SOCIAL, categorizer.categorize(message))
    }

    // ==================== PROMOTIONAL Tests ====================

    @Test
    fun `message with sale keyword is categorized as PROMOTIONAL`() {
        val message = createMessage(
            content = "Big sale this weekend! 50% off everything",
            sender = "Store"
        )
        assertEquals(MessageBucket.PROMOTIONAL, categorizer.categorize(message))
    }

    @Test
    fun `message with discount keyword is categorized as PROMOTIONAL`() {
        val message = createMessage(
            content = "Use code SAVE20 for a special discount",
            sender = "Brand"
        )
        assertEquals(MessageBucket.PROMOTIONAL, categorizer.categorize(message))
    }

    @Test
    fun `message with percent off is categorized as PROMOTIONAL`() {
        val message = createMessage(
            content = "Get 30% off your next order",
            sender = "Shop"
        )
        assertEquals(MessageBucket.PROMOTIONAL, categorizer.categorize(message))
    }

    @Test
    fun `message from Amazon shopping is categorized as PROMOTIONAL`() {
        val message = createMessage(
            content = "Your wishlist item is on sale",
            sender = "Amazon",
            packageName = "com.amazon.mShop.android.shopping"
        )
        assertEquals(MessageBucket.PROMOTIONAL, categorizer.categorize(message))
    }

    // ==================== TRANSACTIONAL Tests ====================

    @Test
    fun `message with OTP is categorized as TRANSACTIONAL`() {
        val message = createMessage(
            content = "Your OTP is 123456",
            sender = "Bank"
        )
        assertEquals(MessageBucket.TRANSACTIONAL, categorizer.categorize(message))
    }

    @Test
    fun `message with verification code is categorized as TRANSACTIONAL`() {
        val message = createMessage(
            content = "Your verification code is 789012",
            sender = "Service"
        )
        assertEquals(MessageBucket.TRANSACTIONAL, categorizer.categorize(message))
    }

    @Test
    fun `message about shipping is categorized as TRANSACTIONAL`() {
        val message = createMessage(
            content = "Your package has shipped! Tracking: ABC123",
            sender = "FedEx"
        )
        assertEquals(MessageBucket.TRANSACTIONAL, categorizer.categorize(message))
    }

    @Test
    fun `message about delivery is categorized as TRANSACTIONAL`() {
        val message = createMessage(
            content = "Your order has been delivered",
            sender = "UPS"
        )
        assertEquals(MessageBucket.TRANSACTIONAL, categorizer.categorize(message))
    }

    @Test
    fun `message with payment info is categorized as TRANSACTIONAL`() {
        val message = createMessage(
            content = "Payment of $50 received",
            sender = "PayPal"
        )
        assertEquals(MessageBucket.TRANSACTIONAL, categorizer.categorize(message))
    }

    @Test
    fun `message from bank app is categorized as TRANSACTIONAL`() {
        val message = createMessage(
            content = "Login detected from new device",
            sender = "MyBank",
            packageName = "com.mybank.mobile"
        )
        assertEquals(MessageBucket.TRANSACTIONAL, categorizer.categorize(message))
    }

    // ==================== UNKNOWN Tests ====================

    @Test
    fun `generic message is categorized as UNKNOWN`() {
        val message = createMessage(
            content = "Hello there",
            sender = "Unknown Sender",
            packageName = "com.random.app"
        )
        assertEquals(MessageBucket.UNKNOWN, categorizer.categorize(message))
    }

    @Test
    fun `empty content message is categorized as UNKNOWN`() {
        val message = createMessage(
            content = "",
            sender = "Sender",
            packageName = "com.some.app"
        )
        assertEquals(MessageBucket.UNKNOWN, categorizer.categorize(message))
    }

    // ==================== Priority Tests ====================

    @Test
    fun `urgent keyword takes priority over work package`() {
        val message = createMessage(
            content = "URGENT: Slack channel needs attention",
            sender = "Slack",
            packageName = "com.slack"
        )
        assertEquals(MessageBucket.URGENT, categorizer.categorize(message))
    }

    @Test
    fun `urgent keyword takes priority over social package`() {
        val message = createMessage(
            content = "Emergency! Call me now!",
            sender = "Friend",
            packageName = "com.whatsapp"
        )
        assertEquals(MessageBucket.URGENT, categorizer.categorize(message))
    }

    // ==================== Edge Cases ====================

    @Test
    fun `case insensitive keyword matching`() {
        val message = createMessage(
            content = "URGENT URGENT urgent",
            sender = "sender"
        )
        assertEquals(MessageBucket.URGENT, categorizer.categorize(message))
    }

    @Test
    fun `case insensitive package name matching`() {
        val message = createMessage(
            content = "Hello",
            sender = "sender",
            packageName = "COM.WHATSAPP"
        )
        assertEquals(MessageBucket.SOCIAL, categorizer.categorize(message))
    }

    // ==================== Helper ====================

    private fun createMessage(
        content: String,
        sender: String,
        packageName: String = "com.test.app",
        id: Long = 1L
    ): Message {
        return Message(
            id = id,
            packageName = packageName,
            senderName = sender,
            originalContent = content,
            status = "RECEIVED"
        )
    }
}
