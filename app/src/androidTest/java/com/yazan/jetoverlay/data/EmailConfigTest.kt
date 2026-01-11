package com.yazan.jetoverlay.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yazan.jetoverlay.BaseAndroidTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for EmailConfig.
 * Tests OAuth token storage and retrieval via SharedPreferences.
 */
@RunWith(AndroidJUnit4::class)
class EmailConfigTest : BaseAndroidTest() {

    @Before
    override fun setUp() {
        super.setUp()
        // Clear any existing tokens before each test
        EmailConfig.clearTokens()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        // Clean up after tests
        EmailConfig.clearTokens()
    }

    @Test
    fun saveTokens_shouldStoreAllTokenValues() {
        val accessToken = "test_access_token_123"
        val refreshToken = "test_refresh_token_456"
        val expiryTime = System.currentTimeMillis() + 3600000 // 1 hour from now

        EmailConfig.saveTokens(accessToken, refreshToken, expiryTime)

        assertEquals(accessToken, EmailConfig.getAccessToken())
        assertEquals(refreshToken, EmailConfig.getRefreshToken())
        assertEquals(expiryTime, EmailConfig.getExpiryTime())
    }

    @Test
    fun hasValidTokens_shouldReturnFalse_whenNoTokensStored() {
        EmailConfig.clearTokens()
        assertFalse(EmailConfig.hasValidTokens())
    }

    @Test
    fun hasValidTokens_shouldReturnTrue_whenTokensAreValid() {
        val futureExpiry = System.currentTimeMillis() + 3600000 // 1 hour from now
        EmailConfig.saveTokens("access", "refresh", futureExpiry)

        assertTrue(EmailConfig.hasValidTokens())
    }

    @Test
    fun hasValidTokens_shouldReturnFalse_whenTokenIsExpired() {
        val pastExpiry = System.currentTimeMillis() - 1000 // 1 second ago
        EmailConfig.saveTokens("access", "refresh", pastExpiry)

        assertFalse(EmailConfig.hasValidTokens())
    }

    @Test
    fun hasValidTokens_shouldReturnFalse_whenTokenExpiresWithin5Minutes() {
        // Token expires in 4 minutes (less than 5-minute buffer)
        val nearExpiry = System.currentTimeMillis() + (4 * 60 * 1000)
        EmailConfig.saveTokens("access", "refresh", nearExpiry)

        assertFalse(EmailConfig.hasValidTokens())
    }

    @Test
    fun hasValidTokens_shouldReturnTrue_whenTokenExpiresAfter5Minutes() {
        // Token expires in 10 minutes (more than 5-minute buffer)
        val futureExpiry = System.currentTimeMillis() + (10 * 60 * 1000)
        EmailConfig.saveTokens("access", "refresh", futureExpiry)

        assertTrue(EmailConfig.hasValidTokens())
    }

    @Test
    fun hasRefreshToken_shouldReturnTrue_whenRefreshTokenExists() {
        EmailConfig.saveTokens("access", "refresh_token_here", System.currentTimeMillis())

        assertTrue(EmailConfig.hasRefreshToken())
    }

    @Test
    fun hasRefreshToken_shouldReturnFalse_whenNoRefreshToken() {
        EmailConfig.clearTokens()
        assertFalse(EmailConfig.hasRefreshToken())
    }

    @Test
    fun clearTokens_shouldRemoveAllStoredData() {
        EmailConfig.saveTokens("access", "refresh", System.currentTimeMillis() + 3600000)
        EmailConfig.saveUserEmail("test@example.com")

        EmailConfig.clearTokens()

        assertNull(EmailConfig.getAccessToken())
        assertNull(EmailConfig.getRefreshToken())
        assertEquals(0L, EmailConfig.getExpiryTime())
        assertNull(EmailConfig.getUserEmail())
    }

    @Test
    fun saveUserEmail_shouldStoreEmail() {
        val email = "user@gmail.com"
        EmailConfig.saveUserEmail(email)

        assertEquals(email, EmailConfig.getUserEmail())
    }

    @Test
    fun getDebugInfo_shouldReturnCorrectInformation() {
        val accessToken = "debug_access_token"
        val expiryTime = System.currentTimeMillis() + 3600000
        EmailConfig.saveTokens(accessToken, "refresh", expiryTime)
        EmailConfig.saveUserEmail("debug@test.com")

        val debugInfo = EmailConfig.getDebugInfo()

        assertEquals("true", debugInfo["hasAccessToken"])
        assertEquals("debug_acce", debugInfo["accessTokenPreview"])
        assertEquals("true", debugInfo["hasRefreshToken"])
        assertEquals(expiryTime.toString(), debugInfo["expiryTime"])
        assertEquals("false", debugInfo["isExpired"])
        assertEquals("debug@test.com", debugInfo["userEmail"])
    }

    @Test
    fun getDebugInfo_shouldHandleNullValues() {
        EmailConfig.clearTokens()

        val debugInfo = EmailConfig.getDebugInfo()

        assertEquals("false", debugInfo["hasAccessToken"])
        assertEquals("null", debugInfo["accessTokenPreview"])
        assertEquals("false", debugInfo["hasRefreshToken"])
        assertEquals("0", debugInfo["expiryTime"])
        assertEquals("null", debugInfo["userEmail"])
    }

    @Test
    fun tokensShould_persistAcrossReads() {
        // Save tokens
        EmailConfig.saveTokens("persistent_token", "persistent_refresh", System.currentTimeMillis() + 7200000)

        // Read multiple times to ensure persistence
        val token1 = EmailConfig.getAccessToken()
        val token2 = EmailConfig.getAccessToken()
        val token3 = EmailConfig.getAccessToken()

        assertEquals(token1, token2)
        assertEquals(token2, token3)
        assertEquals("persistent_token", token1)
    }
}
