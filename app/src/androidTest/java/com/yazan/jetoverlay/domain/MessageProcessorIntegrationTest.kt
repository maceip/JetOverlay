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
        processor.stop()
        Thread.sleep(100)
        database.close()
    }

    @Test
    fun processor_updatesAllFields_withContextualResponses() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.whatsapp",
            senderName = "Alice",
            originalContent = "Hey! How is it going?",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("PROCESSED", processedMessage.status)
            assertEquals("SOCIAL", processedMessage.bucket)
            assertTrue(processedMessage.veiledContent?.contains("Alice") == true)
            
            // Verify contextual response (Social responses should contain "awesome" or "great")
            val responses = processedMessage.generatedResponses
            assertTrue("Should have 3 responses", responses.size == 3)
            assertTrue(
                "Should contain social-appropriate text",
                responses.any { it.contains("great", ignoreCase = true) || it.contains("awesome", ignoreCase = true) }
            )
        }
    }

    @Test
    fun processor_assignsUrgentBucket_andUrgentResponses() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.test.app",
            senderName = "Boss",
            originalContent = "URGENT: Meeting now!",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("URGENT", processedMessage.bucket)
            
            // Verify contextual response (Urgent responses should contain "now" or "call")
            val responses = processedMessage.generatedResponses
            assertTrue(
                "Should contain urgent-appropriate text",
                responses.any { it.contains("now", ignoreCase = true) || it.contains("call", ignoreCase = true) }
            )
        }
    }

    @Test
    fun processor_assignsWorkBucket_andWorkResponses() = runTest(timeout = 10.seconds) {
        processor.start()

        val message = createTestMessage(
            packageName = "com.slack",
            senderName = "Team",
            originalContent = "Please review the PR",
            status = "RECEIVED"
        )
        val insertedId = messageDao.insert(message)

        waitForProcessed(insertedId) { processedMessage ->
            assertEquals("WORK", processedMessage.bucket)
            
            // Verify contextual response (Work responses should contain "review" or "sync")
            val responses = processedMessage.generatedResponses
            assertTrue(
                "Should contain work-appropriate text",
                responses.any { it.contains("review", ignoreCase = true) || it.contains("sync", ignoreCase = true) }
            )
        }
    }

    // ==================== Helper Methods ====================

    private fun createTestMessage(
        id: Long = 0,
        packageName: String = "com.test.app",
        senderName: String = "Test Sender",
        originalContent: String = "Test content",
        status: String = "RECEIVED"
    ): Message {
        return Message(
            id = id,
            packageName = packageName,
            senderName = senderName,
            originalContent = originalContent,
            status = status,
            timestamp = System.currentTimeMillis()
        )
    }

    private suspend fun waitForProcessed(
        messageId: Long,
        assertions: (Message) -> Unit
    ) {
        repository.allMessages.test {
            var processed = false
            var attempts = 0
            val maxAttempts = 70 // 7 seconds

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
