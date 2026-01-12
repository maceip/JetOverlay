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
    fun `generateResponses returns expected mock responses`() = runBlocking {
        val message = createMessage()
        val responses = stubLlmService.generateResponses(message, MessageBucket.SOCIAL)

        assertEquals(3, responses.size)
        assertEquals("hello", responses[0])
        assertEquals("Got it!", responses[1])
        assertEquals("Thanks!", responses[2])
    }

    @Test
    fun `generateResponses returns same responses for all bucket types`() = runBlocking {
        val message = createMessage()
        val expectedResponses = listOf("hello", "Got it!", "Thanks!")

        for (bucket in MessageBucket.entries) {
            val responses = stubLlmService.generateResponses(message, bucket)
            assertEquals(
                "Responses for bucket $bucket should match expected",
                expectedResponses,
                responses
            )
        }
    }

    @Test
    fun `generateResponses returns same responses regardless of message content`() = runBlocking {
        val messages = listOf(
            createMessage(content = "Hello world!"),
            createMessage(content = "Urgent meeting at 3pm"),
            createMessage(content = "Your OTP is 123456"),
            createMessage(content = "50% off sale today!"),
            createMessage(content = ""),
            createMessage(content = "A".repeat(10000)) // Very long content
        )
        val expectedResponses = listOf("hello", "Got it!", "Thanks!")

        for (message in messages) {
            val responses = stubLlmService.generateResponses(message, MessageBucket.UNKNOWN)
            assertEquals(
                "Responses for message '${message.originalContent.take(20)}...' should match expected",
                expectedResponses,
                responses
            )
        }
    }

    @Test
    fun `generateResponses returns same responses regardless of sender`() = runBlocking {
        val messages = listOf(
            createMessage(sender = "John"),
            createMessage(sender = "Slack Bot"),
            createMessage(sender = "Unknown"),
            createMessage(sender = ""),
            createMessage(sender = "User with émojis 😊")
        )
        val expectedResponses = listOf("hello", "Got it!", "Thanks!")

        for (message in messages) {
            val responses = stubLlmService.generateResponses(message, MessageBucket.SOCIAL)
            assertEquals(
                "Responses for sender '${message.senderName}' should match expected",
                expectedResponses,
                responses
            )
        }
    }

    // ==================== Delay Tests ====================

    @Test
    fun `generateResponses applies delay for URGENT bucket`() = runBlocking {
        val message = createMessage()
        val startTime = System.currentTimeMillis()

        stubLlmService.generateResponses(message, MessageBucket.URGENT)

        val elapsedTime = System.currentTimeMillis() - startTime
        assertTrue(
            "Expected delay of at least 400ms, but was ${elapsedTime}ms",
            elapsedTime >= 400
        )
    }

    @Test
    fun `generateResponses applies delay for SOCIAL bucket`() = runBlocking {
        val message = createMessage()
        val startTime = System.currentTimeMillis()

        stubLlmService.generateResponses(message, MessageBucket.SOCIAL)

        val elapsedTime = System.currentTimeMillis() - startTime
        assertTrue(
            "Expected delay of at least 400ms, but was ${elapsedTime}ms",
            elapsedTime >= 400
        )
    }

    @Test
    fun `generateResponses applies delay for WORK bucket`() = runBlocking {
        val message = createMessage()
        val startTime = System.currentTimeMillis()

        stubLlmService.generateResponses(message, MessageBucket.WORK)

        val elapsedTime = System.currentTimeMillis() - startTime
        assertTrue(
            "Expected delay of at least 400ms, but was ${elapsedTime}ms",
            elapsedTime >= 400
        )
    }

    @Test
    fun `generateResponses applies delay for PROMOTIONAL bucket`() = runBlocking {
        val message = createMessage()
        val startTime = System.currentTimeMillis()

        stubLlmService.generateResponses(message, MessageBucket.PROMOTIONAL)

        val elapsedTime = System.currentTimeMillis() - startTime
        assertTrue(
            "Expected delay of at least 400ms, but was ${elapsedTime}ms",
            elapsedTime >= 400
        )
    }

    @Test
    fun `generateResponses applies delay for TRANSACTIONAL bucket`() = runBlocking {
        val message = createMessage()
        val startTime = System.currentTimeMillis()

        stubLlmService.generateResponses(message, MessageBucket.TRANSACTIONAL)

        val elapsedTime = System.currentTimeMillis() - startTime
        assertTrue(
            "Expected delay of at least 400ms, but was ${elapsedTime}ms",
            elapsedTime >= 400
        )
    }

    @Test
    fun `generateResponses applies delay for UNKNOWN bucket`() = runBlocking {
        val message = createMessage()
        val startTime = System.currentTimeMillis()

        stubLlmService.generateResponses(message, MessageBucket.UNKNOWN)

        val elapsedTime = System.currentTimeMillis() - startTime
        assertTrue(
            "Expected delay of at least 400ms, but was ${elapsedTime}ms",
            elapsedTime >= 400
        )
    }

    // ==================== Interface Compliance Tests ====================

    @Test
    fun `StubLlmService implements LlmService interface`() {
        val service: LlmService = stubLlmService
        assertTrue("StubLlmService should implement LlmService", service is LlmService)
    }

    @Test
    fun `generateResponses returns non-empty list`() = runBlocking {
        val message = createMessage()

        for (bucket in MessageBucket.entries) {
            val responses = stubLlmService.generateResponses(message, bucket)
            assertTrue(
                "Responses for bucket $bucket should not be empty",
                responses.isNotEmpty()
            )
        }
    }

    @Test
    fun `generateResponses returns List of Strings`() = runBlocking {
        val message = createMessage()
        val responses = stubLlmService.generateResponses(message, MessageBucket.SOCIAL)

        assertTrue("Responses should be a List", responses is List<*>)
        for (response in responses) {
            assertTrue("Each response should be a String", response is String)
        }
    }

    // ==================== Edge Case Tests ====================

    @Test
    fun `generateResponses handles message with id 0`() = runBlocking {
        val message = createMessage(id = 0)
        val responses = stubLlmService.generateResponses(message, MessageBucket.UNKNOWN)

        assertEquals(3, responses.size)
        assertEquals("hello", responses[0])
    }

    @Test
    fun `generateResponses handles message with large id`() = runBlocking {
        val message = createMessage(id = Long.MAX_VALUE)
        val responses = stubLlmService.generateResponses(message, MessageBucket.UNKNOWN)

        assertEquals(3, responses.size)
        assertEquals("hello", responses[0])
    }

    @Test
    fun `generateResponses handles message with special characters in content`() = runBlocking {
        val message = createMessage(content = "<script>alert('xss')</script>")
        val responses = stubLlmService.generateResponses(message, MessageBucket.UNKNOWN)

        assertEquals(3, responses.size)
        assertEquals("hello", responses[0])
    }

    @Test
    fun `generateResponses handles message with null-like string values`() = runBlocking {
        val message = createMessage(content = "null", sender = "null")
        val responses = stubLlmService.generateResponses(message, MessageBucket.UNKNOWN)

        assertEquals(3, responses.size)
        assertEquals("hello", responses[0])
    }

    // ==================== Multiple Calls Tests ====================

    @Test
    fun `generateResponses is consistent across multiple calls`() = runBlocking {
        val message = createMessage()
        val expectedResponses = listOf("hello", "Got it!", "Thanks!")

        repeat(5) { iteration ->
            val responses = stubLlmService.generateResponses(message, MessageBucket.SOCIAL)
            assertEquals(
                "Responses should be consistent on iteration $iteration",
                expectedResponses,
                responses
            )
        }
    }

    @Test
    fun `generateResponses works with different message instances`() = runBlocking {
        val expectedResponses = listOf("hello", "Got it!", "Thanks!")

        for (i in 1L..10L) {
            val message = createMessage(id = i, content = "Message $i", sender = "Sender $i")
            val responses = stubLlmService.generateResponses(message, MessageBucket.SOCIAL)
            assertEquals(
                "Responses for message $i should match expected",
                expectedResponses,
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
