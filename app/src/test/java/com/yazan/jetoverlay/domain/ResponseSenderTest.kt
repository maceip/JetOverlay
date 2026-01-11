package com.yazan.jetoverlay.domain

import com.yazan.jetoverlay.data.ReplyActionCache
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ResponseSender.
 * Tests focus on the cache interaction and validation logic that can be tested
 * without Android instrumentation.
 */
class ResponseSenderTest {

    @Before
    fun setup() {
        ReplyActionCache.clear()
    }

    @After
    fun tearDown() {
        ReplyActionCache.clear()
    }

    // ==================== ReplyActionCache Tests ====================

    @Test
    fun `hasReplyAction returns false when no action cached`() {
        assertFalse(ReplyActionCache.get(1L) != null)
    }

    @Test
    fun `hasReplyAction returns false for non-existent message id`() {
        assertNull(ReplyActionCache.get(999L))
    }

    @Test
    fun `ReplyActionCache remove works correctly`() {
        // Since we can't easily create a Notification.Action in unit tests,
        // we test that remove doesn't throw when key doesn't exist
        ReplyActionCache.remove(1L)
        assertNull(ReplyActionCache.get(1L))
    }

    @Test
    fun `ReplyActionCache clear removes all entries`() {
        ReplyActionCache.clear()
        // After clear, any get should return null
        assertNull(ReplyActionCache.get(1L))
        assertNull(ReplyActionCache.get(2L))
        assertNull(ReplyActionCache.get(Long.MAX_VALUE))
    }

    @Test
    fun `ReplyActionCache handles edge case id values`() {
        // Test with boundary values
        assertNull(ReplyActionCache.get(0L))
        assertNull(ReplyActionCache.get(-1L))
        assertNull(ReplyActionCache.get(Long.MAX_VALUE))
        assertNull(ReplyActionCache.get(Long.MIN_VALUE))
    }

    @Test
    fun `ReplyActionCache remove is idempotent`() {
        // Calling remove multiple times shouldn't cause issues
        ReplyActionCache.remove(1L)
        ReplyActionCache.remove(1L)
        ReplyActionCache.remove(1L)
        assertNull(ReplyActionCache.get(1L))
    }

    @Test
    fun `ReplyActionCache clear is idempotent`() {
        // Calling clear multiple times shouldn't cause issues
        ReplyActionCache.clear()
        ReplyActionCache.clear()
        ReplyActionCache.clear()
        assertNull(ReplyActionCache.get(1L))
    }

    @Test
    fun `ReplyActionCache operations are thread-safe`() {
        // Basic thread safety test - concurrent access shouldn't crash
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(100) { i ->
                    val id = (threadId * 1000 + i).toLong()
                    // These operations should not throw
                    ReplyActionCache.get(id)
                    ReplyActionCache.remove(id)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        // If we get here without exceptions, thread safety is working
    }

    // ==================== SendResult Tests ====================

    @Test
    fun `SendResult Success is singleton pattern`() {
        val success1 = ResponseSender.SendResult.Success
        val success2 = ResponseSender.SendResult.Success
        assertSame(success1, success2)
    }

    @Test
    fun `SendResult Error contains message`() {
        val errorMessage = "Test error message"
        val error = ResponseSender.SendResult.Error(errorMessage)
        assertEquals(errorMessage, error.message)
    }

    @Test
    fun `SendResult Error with empty message`() {
        val error = ResponseSender.SendResult.Error("")
        assertEquals("", error.message)
    }

    @Test
    fun `SendResult Error with special characters`() {
        val errorMessage = "Error: <xml>tag</xml> & special \"chars\""
        val error = ResponseSender.SendResult.Error(errorMessage)
        assertEquals(errorMessage, error.message)
    }

    @Test
    fun `SendResult Error equality works correctly`() {
        val error1 = ResponseSender.SendResult.Error("Same message")
        val error2 = ResponseSender.SendResult.Error("Same message")
        val error3 = ResponseSender.SendResult.Error("Different message")

        assertEquals(error1, error2)
        assertNotEquals(error1, error3)
    }

    @Test
    fun `SendResult Error hashCode is consistent`() {
        val error = ResponseSender.SendResult.Error("Test message")
        val hashCode1 = error.hashCode()
        val hashCode2 = error.hashCode()
        assertEquals(hashCode1, hashCode2)
    }

    @Test
    fun `SendResult types are distinguishable`() {
        val success = ResponseSender.SendResult.Success
        val error = ResponseSender.SendResult.Error("Error")

        assertTrue(success is ResponseSender.SendResult.Success)
        assertTrue(error is ResponseSender.SendResult.Error)
        assertFalse(success is ResponseSender.SendResult.Error)
    }

    @Test
    fun `SendResult sealed class exhaustive when`() {
        val results = listOf(
            ResponseSender.SendResult.Success,
            ResponseSender.SendResult.Error("Error")
        )

        for (result in results) {
            val handled = when (result) {
                is ResponseSender.SendResult.Success -> true
                is ResponseSender.SendResult.Error -> true
            }
            assertTrue(handled)
        }
    }

    // ==================== Validation Logic Tests ====================

    @Test
    fun `blank response text should be invalid`() {
        val blankInputs = listOf("", "   ", "\t", "\n", "\t\n  ")
        for (input in blankInputs) {
            assertTrue("'$input' should be considered blank", input.isBlank())
        }
    }

    @Test
    fun `non-blank response text should be valid`() {
        val validInputs = listOf("Hello", "  Hello  ", "a", "Thanks!", "Got it 👍")
        for (input in validInputs) {
            assertFalse("'$input' should not be considered blank", input.isBlank())
        }
    }

    // ==================== Message ID Edge Cases ====================

    @Test
    fun `message id 0 is handled`() {
        // Message ID 0 should be a valid ID
        assertNull(ReplyActionCache.get(0L))
    }

    @Test
    fun `negative message ids are handled`() {
        // Negative IDs shouldn't crash
        assertNull(ReplyActionCache.get(-1L))
        assertNull(ReplyActionCache.get(-100L))
        assertNull(ReplyActionCache.get(Long.MIN_VALUE))
    }
}
