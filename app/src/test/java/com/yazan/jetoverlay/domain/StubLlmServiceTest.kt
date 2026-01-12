package com.yazan.jetoverlay.domain

import com.yazan.jetoverlay.data.Message
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StubLlmServiceTest {

    private lateinit var stubLlmService: StubLlmService

    @Before
    fun setup() {
        stubLlmService = StubLlmService()
    }

    // ==================== Response Content Tests ====================

    @Test
    fun `generateResponses returns contextual responses for URGENT bucket`() = runBlocking {
        val message = createMessage()
        val responses = stubLlmService.generateResponses(message, MessageBucket.URGENT)

        assertEquals(3, responses.size)
        assertTrue(responses.any { it.contains("right now", ignoreCase = true) })
        assertTrue(responses.any { it.contains("call in 5 mins", ignoreCase = true) })
    }

    @Test
    fun `generateResponses returns contextual responses for WORK bucket`() = runBlocking {
        val message = createMessage()
        val responses = stubLlmService.generateResponses(message, MessageBucket.WORK)

        assertEquals(3, responses.size)
        assertTrue(responses.any { it.contains("looking into this", ignoreCase = true) })
        assertTrue(responses.any { it.contains("EOD", ignoreCase = true) })
    }

    @Test
    fun `generateResponses returns contextual responses for SOCIAL bucket`() = runBlocking {
        val message = createMessage()
        val responses = stubLlmService.generateResponses(message, MessageBucket.SOCIAL)

        assertEquals(3, responses.size)
        assertTrue(responses.any { it.contains("awesome", ignoreCase = true) })
        assertTrue(responses.any { it.contains("soon", ignoreCase = true) })
    }

    @Test
    fun `generateResponses returns different responses for different buckets`() = runBlocking {
        val message = createMessage()
        
        val urgentResponses = stubLlmService.generateResponses(message, MessageBucket.URGENT)
        val socialResponses = stubLlmService.generateResponses(message, MessageBucket.SOCIAL)
        val workResponses = stubLlmService.generateResponses(message, MessageBucket.WORK)

        assertTrue("URGENT should differ from SOCIAL", urgentResponses != socialResponses)
        assertTrue("WORK should differ from SOCIAL", workResponses != socialResponses)
        assertTrue("URGENT should differ from WORK", urgentResponses != workResponses)
    }

    @Test
    fun `generateResponses returns same responses regardless of message content within same bucket`() = runBlocking {
        val messages = listOf(
            createMessage(content = "Hello world!"),
            createMessage(content = "Urgent meeting at 3pm"),
            createMessage(content = "A".repeat(10000))
        )
        
        val firstResponses = stubLlmService.generateResponses(messages[0], MessageBucket.WORK)

        for (message in messages) {
            val responses = stubLlmService.generateResponses(message, MessageBucket.WORK)
            assertEquals(
                "Responses for message '${message.originalContent.take(20)}...' should match expected",
                firstResponses,
                responses
            )
        }
    }

    // ==================== Delay Tests ====================

    @Test
    fun `generateResponses applies increased delay`() = runBlocking {
        val message = createMessage()
        val startTime = System.currentTimeMillis()

        stubLlmService.generateResponses(message, MessageBucket.URGENT)

        val elapsedTime = System.currentTimeMillis() - startTime
        assertTrue(
            "Expected delay of at least 700ms, but was ${elapsedTime}ms",
            elapsedTime >= 700
        )
    }

    // ==================== Interface Compliance Tests ====================

    @Test
    fun `StubLlmService implements LlmService interface`() {
        val service: LlmService = stubLlmService
        assertTrue("StubLlmService should implement LlmService", service is LlmService)
    }

    @Test
    fun `generateResponses returns non-empty list for all buckets`() = runBlocking {
        val message = createMessage()

        for (bucket in MessageBucket.entries) {
            val responses = stubLlmService.generateResponses(message, bucket)
            assertTrue(
                "Responses for bucket $bucket should not be empty",
                responses.isNotEmpty()
            )
        }
    }

    // ==================== Multiple Calls Tests ====================

    @Test
    fun `generateResponses is consistent across multiple calls`() = runBlocking {
        val message = createMessage()
        val firstResponses = stubLlmService.generateResponses(message, MessageBucket.SOCIAL)

        repeat(3) { iteration ->
            val responses = stubLlmService.generateResponses(message, MessageBucket.SOCIAL)
            assertEquals(
                "Responses should be consistent on iteration $iteration",
                firstResponses,
                responses
            )
        }
    }

    // ==================== Helper ====================

    private fun createMessage(
        id: Long = 1L,
        content: String = "Test message content",
        sender: String = "Test Sender",
        packageName: String = "com.test.app"
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
