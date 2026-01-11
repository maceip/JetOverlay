package com.yazan.jetoverlay.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SlackConfig constants.
 * Note: Full SharedPreferences tests require instrumented tests
 * with proper Android context.
 */
class SlackConfigTest {

    @Test
    fun `INITIAL_BACKOFF_MS should be 5 seconds`() {
        assertEquals(5000L, SlackConfig.INITIAL_BACKOFF_MS)
    }

    @Test
    fun `MAX_BACKOFF_MS should be 60 seconds`() {
        assertEquals(60000L, SlackConfig.MAX_BACKOFF_MS)
    }

    @Test
    fun `BACKOFF_MULTIPLIER should be 2`() {
        assertEquals(2.0, SlackConfig.BACKOFF_MULTIPLIER, 0.001)
    }

    @Test
    fun `DEFAULT_POLL_INTERVAL_MS should be 15 seconds`() {
        assertEquals(15000L, SlackConfig.DEFAULT_POLL_INTERVAL_MS)
    }

    @Test
    fun `SlackConfig should be a singleton object`() {
        val instance1 = SlackConfig
        val instance2 = SlackConfig
        assertSame(instance1, instance2)
    }

    @Test
    fun `backoff multiplier should double the value`() {
        val initial = SlackConfig.INITIAL_BACKOFF_MS
        val expected = (initial * SlackConfig.BACKOFF_MULTIPLIER).toLong()
        assertEquals(10000L, expected)
    }

    @Test
    fun `max backoff should cap exponential growth`() {
        var current = SlackConfig.INITIAL_BACKOFF_MS
        var iterations = 0

        // Simulate exponential backoff until hitting max
        while (current < SlackConfig.MAX_BACKOFF_MS) {
            current = (current * SlackConfig.BACKOFF_MULTIPLIER).toLong()
            iterations++
            if (iterations > 10) break // Safety limit
        }

        // After sufficient iterations, we should hit max
        val capped = current.coerceAtMost(SlackConfig.MAX_BACKOFF_MS)
        assertEquals(SlackConfig.MAX_BACKOFF_MS, capped)
    }
}
