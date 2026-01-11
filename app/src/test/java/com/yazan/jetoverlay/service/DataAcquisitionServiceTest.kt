package com.yazan.jetoverlay.service

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DataAcquisitionService constants and data classes.
 * Note: Full service lifecycle tests require instrumented tests with proper Android context.
 */
class DataAcquisitionServiceTest {

    @Test
    fun `IntegrationStatus data class should hold correct values`() {
        val status = DataAcquisitionService.IntegrationStatus(
            isConnected = true,
            isPolling = true
        )

        assertTrue(status.isConnected)
        assertTrue(status.isPolling)
    }

    @Test
    fun `IntegrationStatus data class should work with false values`() {
        val status = DataAcquisitionService.IntegrationStatus(
            isConnected = false,
            isPolling = false
        )

        assertFalse(status.isConnected)
        assertFalse(status.isPolling)
    }

    @Test
    fun `IntegrationStatus data class copy should work correctly`() {
        val original = DataAcquisitionService.IntegrationStatus(
            isConnected = true,
            isPolling = false
        )

        val copied = original.copy(isPolling = true)

        assertTrue(copied.isConnected)
        assertTrue(copied.isPolling)
    }

    @Test
    fun `IntegrationStatus equality should work correctly`() {
        val status1 = DataAcquisitionService.IntegrationStatus(
            isConnected = true,
            isPolling = true
        )

        val status2 = DataAcquisitionService.IntegrationStatus(
            isConnected = true,
            isPolling = true
        )

        assertEquals(status1, status2)
        assertEquals(status1.hashCode(), status2.hashCode())
    }

    @Test
    fun `IntegrationStatus inequality should work correctly`() {
        val status1 = DataAcquisitionService.IntegrationStatus(
            isConnected = true,
            isPolling = true
        )

        val status2 = DataAcquisitionService.IntegrationStatus(
            isConnected = false,
            isPolling = true
        )

        assertNotEquals(status1, status2)
    }

    @Test
    fun `IntegrationStatus toString should contain field values`() {
        val status = DataAcquisitionService.IntegrationStatus(
            isConnected = true,
            isPolling = false
        )

        val stringRepresentation = status.toString()
        assertTrue(stringRepresentation.contains("isConnected=true"))
        assertTrue(stringRepresentation.contains("isPolling=false"))
    }

    @Test
    fun `isServiceRunning should return false initially`() {
        // Before any service starts, it should report not running
        // Note: This test may not be reliable in isolation due to static state,
        // but serves as documentation of expected initial state
        assertFalse(DataAcquisitionService.isServiceRunning())
    }
}
