package com.yazan.jetoverlay.service

import android.Manifest
import android.content.Intent
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.yazan.jetoverlay.BaseAndroidTest
import com.yazan.jetoverlay.TestUtils
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.runBlockingTest
import com.yazan.jetoverlay.service.integration.SmsIntegration
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for SmsIntegration BroadcastReceiver.
 * Tests the SMS ingestion pipeline from broadcast reception to database storage.
 *
 * Note: Full SMS broadcast testing requires system privileges or shell commands.
 * These tests focus on verifying the integration components are correctly configured.
 */
@RunWith(AndroidJUnit4::class)
class SmsIntegrationTest : BaseAndroidTest() {

    private lateinit var repository: MessageRepository
    private lateinit var testDb: com.yazan.jetoverlay.data.AppDatabase

    @get:Rule
    val smsPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS
    )

    @Before
    override fun setUp() {
        super.setUp()
        testDb = TestUtils.createInMemoryDatabase(context)
        repository = MessageRepository(testDb.messageDao())
    }

    @After
    override fun tearDown() {
        super.tearDown()
        testDb.close()
    }

    @Test
    fun smsIntegration_packageName_isCorrect() {
        assertEquals("sms", SmsIntegration.SMS_PACKAGE_NAME)
    }

    @Test
    fun smsIntegration_receiver_canBeInstantiated() {
        val receiver = SmsIntegration()
        assertNotNull(receiver)
    }

    @Test
    fun smsIntegration_onReceive_handlesNullContext() {
        val receiver = SmsIntegration()
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)

        // Should not throw
        receiver.onReceive(null, intent)
    }

    @Test
    fun smsIntegration_onReceive_handlesNullIntent() {
        val receiver = SmsIntegration()

        // Should not throw
        receiver.onReceive(context, null)
    }

    @Test
    fun smsIntegration_onReceive_ignoresNonSmsIntents() {
        val receiver = SmsIntegration()
        val intent = Intent("com.example.SOME_OTHER_ACTION")

        // Should not throw and should be ignored
        receiver.onReceive(context, intent)
    }

    @Test
    fun smsIntegration_onReceive_handlesSmsIntentWithoutPdu() {
        val receiver = SmsIntegration()
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        // Intent has no PDU data - should be handled gracefully

        receiver.onReceive(context, intent)
        // Should not throw
    }

    @Test
    fun messageRepository_ingestsSmsMessage_correctly() = runBlockingTest {
        // Directly test that the repository can handle SMS-type messages
        val messageId = repository.ingestNotification(
            packageName = SmsIntegration.SMS_PACKAGE_NAME,
            sender = "+15551234567",
            content = "Test SMS message content"
        )

        assertTrue("Message ID should be positive", messageId > 0)

        // Verify the message was stored correctly
        val messages = repository.allMessages.first()
        val smsMessage = messages.find { it.id == messageId }

        assertNotNull("SMS message should be in database", smsMessage)
        smsMessage?.let {
            assertEquals(SmsIntegration.SMS_PACKAGE_NAME, it.packageName)
            assertEquals("+15551234567", it.senderName)
            assertEquals("Test SMS message content", it.originalContent)
            assertEquals("RECEIVED", it.status)
        }
    }

    @Test
    fun messageRepository_distinguishesSmsFromNotifications() = runBlockingTest {
        // Ingest an SMS message
        val smsId = repository.ingestNotification(
            packageName = SmsIntegration.SMS_PACKAGE_NAME,
            sender = "+15559876543",
            content = "This is an SMS"
        )

        // Ingest a notification
        val notificationId = repository.ingestNotification(
            packageName = "com.whatsapp",
            sender = "John Doe",
            content = "This is a WhatsApp notification"
        )

        val messages = repository.allMessages.first()

        val smsMessage = messages.find { it.id == smsId }
        val notificationMessage = messages.find { it.id == notificationId }

        // Both should exist
        assertNotNull(smsMessage)
        assertNotNull(notificationMessage)

        // SMS should have "sms" package name
        assertEquals(SmsIntegration.SMS_PACKAGE_NAME, smsMessage?.packageName)
        assertEquals("com.whatsapp", notificationMessage?.packageName)
    }

    @Test
    fun messageRepository_handlesPhoneNumberFormats() = runBlockingTest {
        // Test various phone number formats as sender names
        val phoneFormats = listOf(
            "+15551234567",
            "555-123-4567",
            "(555) 123-4567",
            "15551234567",
            "1234"  // Short codes
        )

        val ids = mutableListOf<Long>()
        for (phone in phoneFormats) {
            val id = repository.ingestNotification(
                packageName = SmsIntegration.SMS_PACKAGE_NAME,
                sender = phone,
                content = "Message from $phone"
            )
            ids.add(id)
        }

        val messages = repository.allMessages.first()

        // All messages should be stored with their original sender format
        for ((index, phone) in phoneFormats.withIndex()) {
            val message = messages.find { it.id == ids[index] }
            assertNotNull("Message with phone $phone should exist", message)
            assertEquals(phone, message?.senderName)
        }
    }
}
