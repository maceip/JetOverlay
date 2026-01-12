package com.yazan.jetoverlay.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.yazan.jetoverlay.data.AppDatabase
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageDao
import com.yazan.jetoverlay.data.MessageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for the complete message processing pipeline.
 *
 * These tests verify the end-to-end flow:
 * 1. Insert a RECEIVED message into database
 * 2. MessageProcessor picks it up and processes it
 * 3. Bucket is assigned correctly based on heuristics
 * 4. VeiledContent is generated based on bucket
 * 5. GeneratedResponses are populated from StubLlmService
 * 6. Status transitions to PROCESSED
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MessageProcessorIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var repository: MessageRepository
    private lateinit var processor: MessageProcessor
    private lateinit var categorizer: MessageCategorizer
    private lateinit var veilGenerator: VeilGenerator
    private lateinit var llmService: StubLlmService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        messageDao = database.messageDao()
        repository = MessageRepository(messageDao)
        categorizer = MessageCategorizer()
        veilGenerator = VeilGenerator()
        llmService = StubLlmService()
        processor = MessageProcessor(repository, categorizer, veilGenerator, llmService)
    }

    @After
    fun tearDown() {
        // Stop the processor first to cancel all pending coroutines
        processor.stop()
        // Give a brief moment for coroutines to cancel gracefully
        Thread.sleep(100)
        database.close()
    }

    // ==================== End-to-End Processing Tests ====================

    @Test
    fun processor_processesReceivedMessage_andUpdatesAllFields() = runTest(timeout = 10.seconds) {
        // Start the processor
        processor.start()

        // Insert a RECEIVED message
        val message = createTestMessage(
            packageName = "com.whatsapp",
            senderName = "Alice",
            originalContent = "Hello, how are you?",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        // Wait for processing to complete by checking status transition
        repository.allMessages.test {
            var processed = false
            var attempts = 0
            val maxAttempts = 50 // 5 seconds with 100ms delays

            while (!processed && attempts < maxAttempts) {
                val messages = awaitItem()
                val processedMessage = messages.find { it.id == insertedId }
                if (processedMessage?.status == "PROCESSED") {
                    processed = true

                    // Verify all fields are properly set
                    assertEquals("PROCESSED", processedMessage.status)
                    assertNotNull("Bucket should be assigned", processedMessage.bucket)
                    assertNotNull("VeiledContent should be generated", processedMessage.veiledContent)
                    assertTrue(
                        "GeneratedResponses should not be empty",
                        processedMessage.generatedResponses.isNotEmpty()
                    )
                }
                attempts++
            }

            assertTrue("Message should be processed within timeout", processed)
            cancelAndConsumeRemainingEvents()
        }
    }

    // ==================== Bucket Assignment Tests ====================

    @Test
    fun processor_assignsUrgentBucket_forUrgentKeywords() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.test.app",
            senderName = "Boss",
            originalContent = "URGENT: Please respond immediately!",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("URGENT", processedMessage.bucket)
            assertEquals("Priority message from Boss", processedMessage.veiledContent)
        }
    }

    @Test
    fun processor_assignsWorkBucket_forSlackMessages() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.slack",
            senderName = "Team Channel",
            originalContent = "New message in #general",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("WORK", processedMessage.bucket)
            assertEquals("Work notification from Slack", processedMessage.veiledContent)
        }
    }

    @Test
    fun processor_assignsWorkBucket_forGitHubMessages() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.github.android",
            senderName = "GitHub",
            originalContent = "New PR opened in your repository",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("WORK", processedMessage.bucket)
            assertEquals("Work notification from GitHub", processedMessage.veiledContent)
        }
    }

    @Test
    fun processor_assignsSocialBucket_forWhatsAppMessages() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.whatsapp",
            senderName = "Mom",
            originalContent = "Call me when you get home",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("SOCIAL", processedMessage.bucket)
            assertEquals("New message from Mom", processedMessage.veiledContent)
        }
    }

    @Test
    fun processor_assignsSocialBucket_forTelegramMessages() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "org.telegram.messenger",
            senderName = "Friend Group",
            originalContent = "Check out this photo!",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("SOCIAL", processedMessage.bucket)
            assertEquals("New message from Friend Group", processedMessage.veiledContent)
        }
    }

    @Test
    fun processor_assignsPromotionalBucket_forSaleMessages() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.amazon.app",
            senderName = "Amazon",
            originalContent = "50% off sale! Limited time offer!",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("PROMOTIONAL", processedMessage.bucket)
            assertEquals("Promotional content", processedMessage.veiledContent)
        }
    }

    @Test
    fun processor_assignsTransactionalBucket_forOtpMessages() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.bank.app",
            senderName = "Bank",
            originalContent = "Your OTP code is 123456",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("TRANSACTIONAL", processedMessage.bucket)
            assertEquals("Account notification", processedMessage.veiledContent)
        }
    }

    @Test
    fun processor_assignsUnknownBucket_forUnclassifiableMessages() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.random.app",
            senderName = "Unknown App",
            originalContent = "Some random notification",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("UNKNOWN", processedMessage.bucket)
            assertEquals("New notification", processedMessage.veiledContent)
        }
    }

    // ==================== Response Generation Tests ====================

    @Test
    fun processor_generatesStubResponses_forAllBuckets() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.whatsapp",
            senderName = "Alice",
            originalContent = "Hey there!",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            val expectedResponses = listOf("hello", "Got it!", "Thanks!")
            assertEquals(expectedResponses, processedMessage.generatedResponses)
        }
    }

    @Test
    fun processor_generatesConsistentResponses_acrossBuckets() = runTest(timeout = 10.seconds) {
        processor.start()

        // Insert messages for different buckets
        val urgentId = messageDao.insert(
            createTestMessage(
                packageName = "com.test.app",
                originalContent = "URGENT matter",
                status = "RECEIVED"
            )
        )

        val workId = messageDao.insert(
            createTestMessage(
                packageName = "com.slack",
                originalContent = "Work stuff",
                status = "RECEIVED"
            )
        )

        val socialId = messageDao.insert(
            createTestMessage(
                packageName = "com.whatsapp",
                originalContent = "Social chat",
                status = "RECEIVED"
            )
        )

        val expectedResponses = listOf("hello", "Got it!", "Thanks!")

        waitForProcessed(urgentId) { msg -> assertEquals(expectedResponses, msg.generatedResponses) }
        waitForProcessed(workId) { msg -> assertEquals(expectedResponses, msg.generatedResponses) }
        waitForProcessed(socialId) { msg -> assertEquals(expectedResponses, msg.generatedResponses) }
    }

    // ==================== Status Transition Tests ====================

    @Test
    fun processor_ignoresAlreadyProcessedMessages() = runTest(timeout = 10.seconds) {
        processor.start()

        // Insert an already-processed message
        val message = createTestMessage(
            packageName = "com.whatsapp",
            senderName = "Alice",
            originalContent = "Already processed",
            status = "PROCESSED",
            veiledContent = "Existing veil",
            generatedResponses = listOf("existing response"),
            bucket = "SOCIAL"
        )
        val insertedId = messageDao.insert(message)

        // Wait a short period and verify message wasn't modified
        kotlinx.coroutines.delay(1000)

        val retrieved = messageDao.getMessageById(insertedId)
        assertNotNull(retrieved)
        assertEquals("PROCESSED", retrieved?.status)
        assertEquals("Existing veil", retrieved?.veiledContent)
        assertEquals(listOf("existing response"), retrieved?.generatedResponses)
        assertEquals("SOCIAL", retrieved?.bucket)
    }

    @Test
    fun processor_onlyProcessesReceivedStatus() = runTest(timeout = 10.seconds) {
        processor.start()

        // Insert messages with various non-RECEIVED statuses
        val queuedMessage = createTestMessage(
            originalContent = "Queued",
            status = "QUEUED"
        )
        val sentMessage = createTestMessage(
            originalContent = "Sent",
            status = "SENT"
        )

        val queuedId = messageDao.insert(queuedMessage)
        val sentId = messageDao.insert(sentMessage)

        // Wait a short period
        kotlinx.coroutines.delay(1000)

        // Verify these weren't processed
        val retrievedQueued = messageDao.getMessageById(queuedId)
        val retrievedSent = messageDao.getMessageById(sentId)

        assertEquals("QUEUED", retrievedQueued?.status)
        assertEquals("SENT", retrievedSent?.status)
    }

    // ==================== Multiple Message Processing Tests ====================

    @Test
    fun processor_processesMultipleMessages_concurrently() = runTest(timeout = 15.seconds) {
        processor.start()

        // Insert multiple messages
        val messageIds = listOf(
            messageDao.insert(createTestMessage(
                packageName = "com.whatsapp",
                senderName = "User1",
                originalContent = "Message 1",
                status = "RECEIVED"
            )),
            messageDao.insert(createTestMessage(
                packageName = "com.slack",
                senderName = "User2",
                originalContent = "Message 2",
                status = "RECEIVED"
            )),
            messageDao.insert(createTestMessage(
                packageName = "org.telegram.messenger",
                senderName = "User3",
                originalContent = "Message 3",
                status = "RECEIVED"
            ))
        )

        // Wait for all to be processed
        for (id in messageIds) {
            waitForProcessed(id) { msg ->
                assertEquals("PROCESSED", msg.status)
                assertNotNull(msg.veiledContent)
                assertTrue(msg.generatedResponses.isNotEmpty())
            }
        }
    }

    // ==================== Veil Content Security Tests ====================

    @Test
    fun processor_veiledContent_neverExposesOriginalContent() = runTest(timeout = 10.seconds) {
        processor.start()

        val sensitiveContent = "My password is secret123 and my SSN is 123-45-6789"
        val message = createTestMessage(
            packageName = "com.random.app",
            senderName = "Sensitive Sender",
            originalContent = sensitiveContent,
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            val veil = processedMessage.veiledContent ?: ""
            assertTrue(
                "Veiled content should not contain 'password'",
                !veil.contains("password", ignoreCase = true)
            )
            assertTrue(
                "Veiled content should not contain 'secret123'",
                !veil.contains("secret123")
            )
            assertTrue(
                "Veiled content should not contain SSN",
                !veil.contains("123-45-6789")
            )
            // Should be the generic "New notification" for UNKNOWN bucket
            assertEquals("New notification", veil)
        }
    }

    @Test
    fun processor_sanitizesSenderInVeiledContent() = runTest(timeout = 10.seconds) {
        processor.start()

        // Sender with special characters that should be sanitized
        val message = createTestMessage(
            packageName = "com.whatsapp",
            senderName = "John<script>alert('xss')</script>",
            originalContent = "Hello",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            val veil = processedMessage.veiledContent ?: ""
            // Script tags and special characters (<, >, ', () ) should be removed
            assertTrue(
                "Veiled content should not contain script tags",
                !veil.contains("<script>")
            )
            assertTrue(
                "Veiled content should not contain angle brackets",
                !veil.contains("<") && !veil.contains(">")
            )
            assertTrue(
                "Veiled content should not contain parentheses from XSS attempt",
                !veil.contains("(") && !veil.contains(")")
            )
            // The sanitized sender should only contain alphanumeric chars
            // "John<script>alert('xss')</script>" -> "Johnscriptalertxssscript"
            assertTrue(
                "Veiled content should contain sanitized sender name",
                veil.contains("John")
            )
        }
    }

    // ==================== Edge Case Tests ====================

    @Test
    fun processor_handlesEmptyContent() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.whatsapp",
            senderName = "Empty",
            originalContent = "",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("PROCESSED", processedMessage.status)
            assertNotNull(processedMessage.veiledContent)
            assertTrue(processedMessage.generatedResponses.isNotEmpty())
        }
    }

    @Test
    fun processor_handlesEmptySender() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.whatsapp",
            senderName = "",
            originalContent = "Message with no sender",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("PROCESSED", processedMessage.status)
            // For SOCIAL bucket (whatsapp), veil should use "Unknown" for empty sender
            // Resulting in "New message from Unknown"
            val veil = processedMessage.veiledContent ?: ""
            val handledGracefully = veil.contains("Unknown") || veil == "New notification"
            assertTrue(
                "Veil should handle empty sender gracefully, got: $veil",
                handledGracefully
            )
        }
    }

    @Test
    fun processor_handlesLongContent() = runTest(timeout = 10.seconds) {
        processor.start()

        val longContent = "A".repeat(10000)
        val message = createTestMessage(
            packageName = "com.whatsapp",
            senderName = "LongContentSender",
            originalContent = longContent,
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("PROCESSED", processedMessage.status)
            assertNotNull(processedMessage.veiledContent)
            // Veiled content should be short regardless of original length
            assertTrue(
                "Veiled content should be reasonably short",
                (processedMessage.veiledContent?.length ?: 0) < 100
            )
        }
    }

    @Test
    fun processor_handlesUnicodeContent() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.whatsapp",
            senderName = "Unicode Friend",
            originalContent = "Hello! 你好! こんにちは! 🎉🎊",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("PROCESSED", processedMessage.status)
            assertNotNull(processedMessage.veiledContent)
            assertTrue(processedMessage.generatedResponses.isNotEmpty())
        }
    }

    // ==================== Helper Methods ====================

    private fun createTestMessage(
        id: Long = 0,
        packageName: String = "com.test.app",
        senderName: String = "Test Sender",
        originalContent: String = "Test content",
        veiledContent: String? = null,
        generatedResponses: List<String> = emptyList(),
        selectedResponse: String? = null,
        status: String = "RECEIVED",
        bucket: String = "UNKNOWN",
        timestamp: Long = System.currentTimeMillis()
    ): Message {
        return Message(
            id = id,
            packageName = packageName,
            senderName = senderName,
            originalContent = originalContent,
            veiledContent = veiledContent,
            generatedResponses = generatedResponses,
            selectedResponse = selectedResponse,
            status = status,
            bucket = bucket,
            timestamp = timestamp
        )
    }

    /**
     * Helper to wait for a message to be processed and run assertions.
     */
    private suspend fun waitForProcessed(
        messageId: Long,
        assertions: (Message) -> Unit
    ) {
        repository.allMessages.test {
            var processed = false
            var attempts = 0
            val maxAttempts = 50 // 5 seconds with 100ms delays

            while (!processed && attempts < maxAttempts) {
                val messages = awaitItem()
                val message = messages.find { it.id == messageId }
                if (message?.status == "PROCESSED") {
                    processed = true
                    assertions(message)
                }
                attempts++
            }

            assertTrue("Message $messageId should be processed within timeout", processed)
            cancelAndConsumeRemainingEvents()
        }
    }
}
