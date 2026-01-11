package com.yazan.jetoverlay.service.integration

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SlackIntegration constants and configuration.
 * Note: Full OAuth, polling, backoff, and debug info tests require instrumented tests
 * with proper Android context since they depend on SlackConfig (SharedPreferences).
 */
class SlackIntegrationTest {

    @Test
    fun `SLACK_PACKAGE_NAME constant should be correct`() {
        assertEquals("com.slack", SlackIntegration.SLACK_PACKAGE_NAME)
    }

    @Test
    fun `SlackIntegration should be a singleton object`() {
        val instance1 = SlackIntegration
        val instance2 = SlackIntegration
        assertSame(instance1, instance2)
    }

    // Note: getDebugInfo and isPollingActive require instrumented tests
    // because they access SlackConfig which needs Android context
}
