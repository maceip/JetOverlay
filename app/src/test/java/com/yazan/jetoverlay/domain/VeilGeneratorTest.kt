package com.yazan.jetoverlay.domain

import com.yazan.jetoverlay.data.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VeilGeneratorTest {

    private lateinit var veilGenerator: VeilGenerator

    @Before
    fun setup() {
        veilGenerator = VeilGenerator()
    }

    // ==================== URGENT Bucket Tests ====================

    @Test
    fun `URGENT bucket generates priority message veil`() {
        val message = createMessage(
            content = "This is an emergency! Call me now!",
            sender = "John"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.URGENT)
        assertEquals("Priority message from John", veil)
    }

    @Test
    fun `URGENT bucket does not leak original content`() {
        val message = createMessage(
            content = "URGENT: Your account has been compromised! Password: secret123",
            sender = "Security"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.URGENT)
        assertFalse("Veil should not contain 'compromised'", veil.contains("compromised"))
        assertFalse("Veil should not contain 'Password'", veil.contains("Password"))
        assertFalse("Veil should not contain 'secret123'", veil.contains("secret123"))
    }

    // ==================== WORK Bucket Tests ====================

    @Test
    fun `WORK bucket generates work notification veil with app name for Slack`() {
        val message = createMessage(
            content = "You have a new message in #engineering",
            sender = "Slack Bot",
            packageName = "com.slack"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.WORK)
        assertEquals("Work notification from Slack", veil)
    }

    @Test
    fun `WORK bucket generates work notification veil with app name for GitHub`() {
        val message = createMessage(
            content = "PR #123 needs review",
            sender = "GitHub",
            packageName = "com.github.android"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.WORK)
        assertEquals("Work notification from GitHub", veil)
    }

    @Test
    fun `WORK bucket generates work notification veil with app name for Teams`() {
        val message = createMessage(
            content = "Meeting starting in 5 minutes",
            sender = "Teams",
            packageName = "com.microsoft.teams"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.WORK)
        assertEquals("Work notification from Teams", veil)
    }

    @Test
    fun `WORK bucket falls back to sender when app unknown`() {
        val message = createMessage(
            content = "New task assigned to you",
            sender = "Manager",
            packageName = "com.unknown.work.app"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.WORK)
        assertEquals("Work notification from Manager", veil)
    }

    @Test
    fun `WORK bucket does not leak original content`() {
        val message = createMessage(
            content = "Confidential: Q3 revenue dropped by 50%",
            sender = "CEO",
            packageName = "com.slack"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.WORK)
        assertFalse("Veil should not contain 'Confidential'", veil.contains("Confidential"))
        assertFalse("Veil should not contain 'revenue'", veil.contains("revenue"))
        assertFalse("Veil should not contain '50%'", veil.contains("50%"))
    }

    // ==================== SOCIAL Bucket Tests ====================

    @Test
    fun `SOCIAL bucket generates new message veil`() {
        val message = createMessage(
            content = "Hey! Are you coming to the party tonight?",
            sender = "Sarah",
            packageName = "com.whatsapp"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.SOCIAL)
        assertEquals("New message from Sarah", veil)
    }

    @Test
    fun `SOCIAL bucket does not leak original content`() {
        val message = createMessage(
            content = "I broke up with Mike, feeling terrible...",
            sender = "Best Friend",
            packageName = "org.telegram.messenger"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.SOCIAL)
        assertFalse("Veil should not contain 'broke up'", veil.contains("broke up"))
        assertFalse("Veil should not contain 'Mike'", veil.contains("Mike"))
        assertFalse("Veil should not contain 'terrible'", veil.contains("terrible"))
    }

    // ==================== PROMOTIONAL Bucket Tests ====================

    @Test
    fun `PROMOTIONAL bucket generates generic promotional veil`() {
        val message = createMessage(
            content = "50% OFF EVERYTHING! Use code SAVE50",
            sender = "Store"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.PROMOTIONAL)
        assertEquals("Promotional content", veil)
    }

    @Test
    fun `PROMOTIONAL bucket does not include sender`() {
        val message = createMessage(
            content = "Flash sale ends tonight!",
            sender = "Super Amazing Store"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.PROMOTIONAL)
        assertFalse("Veil should not contain sender name", veil.contains("Super"))
        assertFalse("Veil should not contain sender name", veil.contains("Amazing"))
        assertFalse("Veil should not contain sender name", veil.contains("Store"))
    }

    @Test
    fun `PROMOTIONAL bucket does not leak discount codes`() {
        val message = createMessage(
            content = "Use promo code SECRETDEAL99 for exclusive discount",
            sender = "Brand"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.PROMOTIONAL)
        assertFalse("Veil should not contain promo code", veil.contains("SECRETDEAL99"))
        assertFalse("Veil should not contain 'exclusive'", veil.contains("exclusive"))
    }

    // ==================== TRANSACTIONAL Bucket Tests ====================

    @Test
    fun `TRANSACTIONAL bucket generates account notification veil`() {
        val message = createMessage(
            content = "Your OTP is 847291",
            sender = "Bank"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.TRANSACTIONAL)
        assertEquals("Account notification", veil)
    }

    @Test
    fun `TRANSACTIONAL bucket does not leak OTP`() {
        val message = createMessage(
            content = "Your verification code is 123456. Do not share with anyone.",
            sender = "SecureBank"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.TRANSACTIONAL)
        assertFalse("Veil should not contain OTP", veil.contains("123456"))
        assertFalse("Veil should not contain 'verification'", veil.contains("verification"))
    }

    @Test
    fun `TRANSACTIONAL bucket does not leak financial information`() {
        val message = createMessage(
            content = "Your account balance is $15,420.50",
            sender = "MyBank"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.TRANSACTIONAL)
        assertFalse("Veil should not contain balance", veil.contains("15,420"))
        assertFalse("Veil should not contain dollar amount", veil.contains("$"))
    }

    @Test
    fun `TRANSACTIONAL bucket does not leak tracking numbers`() {
        val message = createMessage(
            content = "Your package shipped! Tracking: 1Z999AA10123456784",
            sender = "UPS"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.TRANSACTIONAL)
        assertFalse("Veil should not contain tracking number", veil.contains("1Z999AA"))
    }

    // ==================== UNKNOWN Bucket Tests ====================

    @Test
    fun `UNKNOWN bucket generates generic notification veil`() {
        val message = createMessage(
            content = "Random notification content",
            sender = "Unknown App"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.UNKNOWN)
        assertEquals("New notification", veil)
    }

    @Test
    fun `UNKNOWN bucket does not leak any content`() {
        val message = createMessage(
            content = "This is sensitive information that should be hidden",
            sender = "Mystery Sender"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.UNKNOWN)
        assertFalse("Veil should not contain 'sensitive'", veil.contains("sensitive"))
        assertFalse("Veil should not contain 'hidden'", veil.contains("hidden"))
        assertFalse("Veil should not contain sender", veil.contains("Mystery"))
    }

    // ==================== Sender Sanitization Tests ====================

    @Test
    fun `sender name with special characters is sanitized`() {
        val message = createMessage(
            content = "Hello!",
            sender = "John <script>alert('xss')</script>"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.SOCIAL)
        assertFalse("Veil should not contain script tag", veil.contains("<script>"))
        assertFalse("Veil should not contain angle brackets", veil.contains("<"))
        assertFalse("Veil should not contain angle brackets", veil.contains(">"))
        assertFalse("Veil should not contain parentheses", veil.contains("("))
        assertFalse("Veil should not contain single quotes", veil.contains("'"))
        assertTrue("Veil should contain sanitized name", veil.contains("John"))
    }

    @Test
    fun `empty sender name is handled`() {
        val message = createMessage(
            content = "Message content",
            sender = ""
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.SOCIAL)
        assertEquals("New message from Unknown", veil)
    }

    @Test
    fun `whitespace only sender name is handled`() {
        val message = createMessage(
            content = "Message content",
            sender = "   "
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.SOCIAL)
        assertEquals("New message from Unknown", veil)
    }

    @Test
    fun `sender name with emojis is sanitized`() {
        val message = createMessage(
            content = "Hello",
            sender = "John 😊👍"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.SOCIAL)
        assertTrue("Veil should contain John", veil.contains("John"))
        assertFalse("Veil should not contain emoji", veil.contains("😊"))
    }

    // ==================== App Name Extraction Tests ====================

    @Test
    fun `extracts Notion app name`() {
        val message = createMessage(
            content = "Page updated",
            sender = "Notion",
            packageName = "com.notion.id"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.WORK)
        assertEquals("Work notification from Notion", veil)
    }

    @Test
    fun `extracts Jira app name`() {
        val message = createMessage(
            content = "Issue assigned",
            sender = "Jira",
            packageName = "com.atlassian.android.jira.core"
        )
        val veil = veilGenerator.generateVeil(message, MessageBucket.WORK)
        assertEquals("Work notification from Jira", veil)
    }

    @Test
    fun `extracts WhatsApp app name for social messages`() {
        val message = createMessage(
            content = "Hey!",
            sender = "Friend",
            packageName = "com.whatsapp"
        )
        // For SOCIAL, we use sender name not app name
        val veil = veilGenerator.generateVeil(message, MessageBucket.SOCIAL)
        assertEquals("New message from Friend", veil)
    }

    // ==================== All Buckets Never Leak Content ====================

    @Test
    fun `no bucket leaks email addresses`() {
        val sensitiveContent = "Contact me at secret@company.com"
        for (bucket in MessageBucket.entries) {
            val message = createMessage(content = sensitiveContent, sender = "Test")
            val veil = veilGenerator.generateVeil(message, bucket)
            assertFalse(
                "Bucket $bucket should not leak email",
                veil.contains("secret@company.com")
            )
        }
    }

    @Test
    fun `no bucket leaks phone numbers`() {
        val sensitiveContent = "Call me at 555-123-4567"
        for (bucket in MessageBucket.entries) {
            val message = createMessage(content = sensitiveContent, sender = "Test")
            val veil = veilGenerator.generateVeil(message, bucket)
            assertFalse(
                "Bucket $bucket should not leak phone number",
                veil.contains("555-123-4567")
            )
        }
    }

    @Test
    fun `no bucket leaks passwords`() {
        val sensitiveContent = "Your temporary password is: P@ssw0rd123!"
        for (bucket in MessageBucket.entries) {
            val message = createMessage(content = sensitiveContent, sender = "Test")
            val veil = veilGenerator.generateVeil(message, bucket)
            assertFalse(
                "Bucket $bucket should not leak password",
                veil.contains("P@ssw0rd123!")
            )
        }
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
