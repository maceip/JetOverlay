package com.yazan.jetoverlay.service.integration

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SmsIntegration constants and configuration.
 * Note: Full SMS broadcast receiver testing requires instrumented tests
 * with proper Android context, covered in SmsIntegrationInstrumentedTest.
 */
class SmsIntegrationTest {

    @Test
    fun `SMS_PACKAGE_NAME constant should be correct`() {
        assertEquals("sms", SmsIntegration.SMS_PACKAGE_NAME)
    }

    @Test
    fun `SmsIntegration should be instantiable`() {
        val receiver = SmsIntegration()
        assertNotNull(receiver)
    }
}
