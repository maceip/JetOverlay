package com.yazan.jetoverlay

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yazan.jetoverlay.data.AppDatabase
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.data.ReplyActionCache
import com.yazan.jetoverlay.domain.ResponseSender
import com.yazan.jetoverlay.service.notification.NotificationMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end stress tests for Phase 08: Notification Zero.
 *
 * Tests:
 * 1. Handle 50+ incoming notifications simultaneously without ANR
 * 2. Universal reply via RemoteInput (WhatsApp, Signal, Slack, etc.)
 * 3. Batch operations (Veil All, Auto-Reply All)
 * 4. Context grouping (Personal, Work, Social, Email)
 * 5. Message flow performance
 */
@RunWith(AndroidJUnit4::class)
class NotificationZeroE2ETest : BaseAndroidTest() {

    private lateinit var repository: MessageRepository
    private lateinit var responseSender: ResponseSender
    private val mapper = NotificationMapper()

    @Before
    override fun setUp() {
        super.setUp()
        val db = AppDatabase.getDatabase(context)
        repository = MessageRepository(db.messageDao())
        responseSender = ResponseSender(context)

        // Clear any existing data
        ReplyActionCache.clear()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        ReplyActionCache.clear()
    }

    /**
     * Test: Handle 50+ notifications rapidly without ANR.
     * Validates that the system can process a burst of notifications
     * without blocking the main thread for more than 5 seconds.
     */
    @Test
    fun testRapidNotificationBurst_50Plus() = runTest {
        val notificationCount = 60
        val processedCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()

        // Simulate rapid notification ingestion
        repeat(notificationCount) { index ->
            val packageName = when (index % 5) {
                0 -> NotificationMapper.PKG_WHATSAPP
                1 -> NotificationMapper.PKG_SLACK
                2 -> NotificationMapper.PKG_GMAIL
                3 -> NotificationMapper.PKG_SIGNAL
                else -> NotificationMapper.PKG_TELEGRAM
            }

            val contextTag = when (packageName) {
                NotificationMapper.PKG_WHATSAPP, NotificationMapper.PKG_SIGNAL -> "personal"
                NotificationMapper.PKG_SLACK -> "work"
                NotificationMapper.PKG_GMAIL -> "email"
                else -> "other"
            }

            val id = repository.ingestNotification(
                packageName = packageName,
                sender = "Sender $index",
                content = "Test message content #$index from rapid burst test",
                contextTag = contextTag
            )

            assertTrue("Message ID should be positive", id > 0)
            processedCount.incrementAndGet()
        }

        val processingTime = System.currentTimeMillis() - startTime

        // Verify all notifications were processed
        assertEquals(notificationCount, processedCount.get())

        // Verify processing time is reasonable (not ANR - must be under 5 seconds)
        assertTrue(
            "Processing $notificationCount notifications took ${processingTime}ms, should be < 5000ms",
            processingTime < 5000
        )

        // Verify messages are in database
        val messages = repository.allMessages.first()
        assertTrue(
            "Should have at least $notificationCount messages",
            messages.size >= notificationCount
        )
    }

    /**
     * Test: Context grouping of messages.
     * Validates that messages are correctly categorized by context.
     */
    @Test
    fun testContextGrouping() = runTest {
        // Insert messages from different apps
        val personalId = repository.ingestNotification(
            NotificationMapper.PKG_WHATSAPP, "Friend", "Personal message", "personal"
        )
        val workId = repository.ingestNotification(
            NotificationMapper.PKG_SLACK, "Coworker", "Work message", "work"
        )
        val socialId = repository.ingestNotification(
            NotificationMapper.PKG_INSTAGRAM, "Influencer", "Social message", "social"
        )
        val emailId = repository.ingestNotification(
            NotificationMapper.PKG_GMAIL, "Newsletter", "Email message", "email"
        )

        val messages = repository.allMessages.first()

        // Verify context tags
        val personalMsg = messages.find { it.id == personalId }
        val workMsg = messages.find { it.id == workId }
        val socialMsg = messages.find { it.id == socialId }
        val emailMsg = messages.find { it.id == emailId }

        assertNotNull("Personal message should exist", personalMsg)
        assertNotNull("Work message should exist", workMsg)
        assertNotNull("Social message should exist", socialMsg)
        assertNotNull("Email message should exist", emailMsg)

        assertEquals("personal", personalMsg?.contextTag)
        assertEquals("work", workMsg?.contextTag)
        assertEquals("social", socialMsg?.contextTag)
        assertEquals("email", emailMsg?.contextTag)
    }

    /**
     * Test: NotificationMapper extracts correct context for all supported apps.
     */
    @Test
    fun testNotificationMapperContextExtraction() {
        // Messaging apps should be PERSONAL
        assertEquals(
            NotificationMapper.MessageContext.PERSONAL,
            mapper.getContextCategory(NotificationMapper.PKG_WHATSAPP)
        )
        assertEquals(
            NotificationMapper.MessageContext.PERSONAL,
            mapper.getContextCategory(NotificationMapper.PKG_SIGNAL)
        )
        assertEquals(
            NotificationMapper.MessageContext.PERSONAL,
            mapper.getContextCategory(NotificationMapper.PKG_TELEGRAM)
        )

        // Work apps should be WORK
        assertEquals(
            NotificationMapper.MessageContext.WORK,
            mapper.getContextCategory(NotificationMapper.PKG_SLACK)
        )
        assertEquals(
            NotificationMapper.MessageContext.WORK,
            mapper.getContextCategory(NotificationMapper.PKG_TEAMS)
        )

        // Social apps should be SOCIAL
        assertEquals(
            NotificationMapper.MessageContext.SOCIAL,
            mapper.getContextCategory(NotificationMapper.PKG_INSTAGRAM)
        )
        assertEquals(
            NotificationMapper.MessageContext.SOCIAL,
            mapper.getContextCategory(NotificationMapper.PKG_TWITTER)
        )

        // Email apps should be EMAIL
        assertEquals(
            NotificationMapper.MessageContext.EMAIL,
            mapper.getContextCategory(NotificationMapper.PKG_GMAIL)
        )
        assertEquals(
            NotificationMapper.MessageContext.EMAIL,
            mapper.getContextCategory(NotificationMapper.PKG_OUTLOOK)
        )

        // Unknown app should be OTHER
        assertEquals(
            NotificationMapper.MessageContext.OTHER,
            mapper.getContextCategory("com.unknown.app")
        )
    }

    /**
     * Test: ReplyActionCache stores and retrieves actions correctly.
     */
    @Test
    fun testReplyActionCacheOperations() {
        val messageId = 123L

        // Initially empty
        assertTrue(!ReplyActionCache.hasReplyAction(messageId))

        // Create a mock notification action
        val mockAction = createMockReplyAction()

        // Save reply action
        ReplyActionCache.save(messageId, mockAction)
        assertTrue(ReplyActionCache.hasReplyAction(messageId))

        // Save mark-as-read action
        val markAsReadAction = createMockMarkAsReadAction()
        ReplyActionCache.saveMarkAsRead(messageId, markAsReadAction)
        assertNotNull(ReplyActionCache.getMarkAsRead(messageId))

        // Save notification key
        ReplyActionCache.saveNotificationKey(messageId, "test_notification_key")
        assertEquals("test_notification_key", ReplyActionCache.getNotificationKey(messageId))

        // Remove and verify
        ReplyActionCache.remove(messageId)
        assertTrue(!ReplyActionCache.hasReplyAction(messageId))
        assertTrue(ReplyActionCache.getMarkAsRead(messageId) == null)
        assertTrue(ReplyActionCache.getNotificationKey(messageId) == null)
    }

    /**
     * Test: ResponseSender validates input correctly.
     */
    @Test
    fun testResponseSenderInputValidation() {
        // Empty response should fail
        val emptyResult = responseSender.sendResponse(1L, "")
        assertTrue(emptyResult is ResponseSender.SendResult.Error)
        assertTrue((emptyResult as ResponseSender.SendResult.Error).message.contains("empty"))

        // Blank response should fail
        val blankResult = responseSender.sendResponse(1L, "   ")
        assertTrue(blankResult is ResponseSender.SendResult.Error)

        // No cached action should fail
        val noActionResult = responseSender.sendResponse(999L, "Test response")
        assertTrue(noActionResult is ResponseSender.SendResult.Error)
        assertTrue((noActionResult as ResponseSender.SendResult.Error).message.contains("No reply action"))
    }

    /**
     * Test: Batch response sending.
     */
    @Test
    fun testBatchResponseSending() {
        val responses = mapOf(
            1L to "Response 1",
            2L to "Response 2",
            3L to "Response 3"
        )

        // Without cached actions, all should fail
        val results = responseSender.sendBatchResponses(responses)

        assertEquals(3, results.size)
        results.values.forEach { result ->
            assertTrue(result is ResponseSender.SendResult.Error)
        }
    }

    /**
     * Test: Memory efficiency under load.
     * Validates that memory usage stays reasonable with many messages.
     */
    @Test
    fun testMemoryEfficiencyUnderLoad() = runTest {
        val runtime = Runtime.getRuntime()
        runtime.gc()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()

        // Insert 100 messages
        repeat(100) { index ->
            repository.ingestNotification(
                packageName = "com.test.app",
                sender = "Sender $index",
                content = "Message content $index with some additional text to make it realistic"
            )
        }

        runtime.gc()
        val afterInsertMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = afterInsertMemory - initialMemory

        // Memory increase should be reasonable (< 50MB for 100 messages)
        val maxExpectedIncrease = 50 * 1024 * 1024L // 50MB
        assertTrue(
            "Memory increase of ${memoryIncrease / 1024 / 1024}MB should be < 50MB",
            memoryIncrease < maxExpectedIncrease
        )
    }

    /**
     * Test: Concurrent message processing.
     * Validates thread safety of the notification processing pipeline.
     */
    @Test
    fun testConcurrentMessageProcessing() = runTest {
        val threadCount = 10
        val messagesPerThread = 10
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        // Launch multiple threads inserting messages concurrently
        repeat(threadCount) { threadIndex ->
            Thread {
                try {
                    runBlocking {
                        repeat(messagesPerThread) { msgIndex ->
                            val id = repository.ingestNotification(
                                packageName = "com.concurrent.test",
                                sender = "Thread $threadIndex",
                                content = "Message $msgIndex from thread $threadIndex"
                            )
                            if (id > 0) {
                                successCount.incrementAndGet()
                            }
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        // Wait for all threads to complete
        assertTrue(
            "All threads should complete within 10 seconds",
            latch.await(10, TimeUnit.SECONDS)
        )

        // All messages should have been inserted successfully
        assertEquals(
            threadCount * messagesPerThread,
            successCount.get()
        )
    }

    /**
     * Test: NotificationMapper identifies replyable apps correctly.
     */
    @Test
    fun testReplyableAppIdentification() {
        // All supported messaging apps should be replyable
        assertTrue(mapper.isReplyableApp(NotificationMapper.PKG_WHATSAPP))
        assertTrue(mapper.isReplyableApp(NotificationMapper.PKG_SIGNAL))
        assertTrue(mapper.isReplyableApp(NotificationMapper.PKG_TELEGRAM))
        assertTrue(mapper.isReplyableApp(NotificationMapper.PKG_MESSENGER))
        assertTrue(mapper.isReplyableApp(NotificationMapper.PKG_SLACK))
        assertTrue(mapper.isReplyableApp(NotificationMapper.PKG_TEAMS))
        assertTrue(mapper.isReplyableApp(NotificationMapper.PKG_INSTAGRAM))
        assertTrue(mapper.isReplyableApp(NotificationMapper.PKG_TWITTER))
        assertTrue(mapper.isReplyableApp(NotificationMapper.PKG_GMAIL))
        assertTrue(mapper.isReplyableApp(NotificationMapper.PKG_OUTLOOK))

        // Unknown apps should not be replyable (by default)
        assertTrue(!mapper.isReplyableApp("com.unknown.random.app"))
    }

    // --- Helper methods ---

    /**
     * Creates a mock notification action with RemoteInput for testing.
     */
    private fun createMockReplyAction(): Notification.Action {
        val intent = Intent("com.test.REPLY_ACTION")
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val remoteInput = android.app.RemoteInput.Builder("reply_key")
            .setLabel("Reply")
            .build()

        return Notification.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            pendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()
    }

    /**
     * Creates a mock mark-as-read action for testing.
     */
    private fun createMockMarkAsReadAction(): Notification.Action {
        val intent = Intent("com.test.MARK_READ_ACTION")
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Mark as Read",
            pendingIntent
        ).build()
    }
}
