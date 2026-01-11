package com.yazan.jetoverlay.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yazan.jetoverlay.BaseAndroidTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for SlackConfig.
 * Tests OAuth token storage, workspace info, and backoff state via SharedPreferences.
 */
@RunWith(AndroidJUnit4::class)
class SlackConfigTest : BaseAndroidTest() {

    @Before
    override fun setUp() {
        super.setUp()
        // Clear any existing configuration before each test
        SlackConfig.clearAll()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        // Clean up after tests
        SlackConfig.clearAll()
    }

    // Token storage tests

    @Test
    fun saveTokens_shouldStoreAccessToken() {
        val accessToken = "test_access_token_xoxb"

        SlackConfig.saveTokens(accessToken)

        assertEquals(accessToken, SlackConfig.getAccessToken())
    }

    @Test
    fun saveTokens_shouldStoreBotToken_whenProvided() {
        val accessToken = "test_access_token"
        val botToken = "test_bot_token_xoxb"

        SlackConfig.saveTokens(accessToken, botToken)

        assertEquals(accessToken, SlackConfig.getAccessToken())
        assertEquals(botToken, SlackConfig.getBotToken())
    }

    @Test
    fun hasValidTokens_shouldReturnFalse_whenNoTokensStored() {
        SlackConfig.clearAll()
        assertFalse(SlackConfig.hasValidTokens())
    }

    @Test
    fun hasValidTokens_shouldReturnTrue_whenAccessTokenExists() {
        SlackConfig.saveTokens("valid_token")

        assertTrue(SlackConfig.hasValidTokens())
    }

    // Workspace info tests

    @Test
    fun saveWorkspaceInfo_shouldStoreAllValues() {
        val workspaceId = "T12345678"
        val workspaceName = "Test Workspace"
        val userId = "U12345678"

        SlackConfig.saveWorkspaceInfo(workspaceId, workspaceName, userId)

        assertEquals(workspaceId, SlackConfig.getWorkspaceId())
        assertEquals(workspaceName, SlackConfig.getWorkspaceName())
        assertEquals(userId, SlackConfig.getUserId())
    }

    @Test
    fun workspaceInfo_shouldReturnNull_whenNotSet() {
        SlackConfig.clearAll()

        assertNull(SlackConfig.getWorkspaceId())
        assertNull(SlackConfig.getWorkspaceName())
        assertNull(SlackConfig.getUserId())
    }

    // Last poll timestamp tests

    @Test
    fun saveLastPollTimestamp_shouldStoreTimestamp() {
        val timestamp = System.currentTimeMillis()

        SlackConfig.saveLastPollTimestamp(timestamp)

        assertEquals(timestamp, SlackConfig.getLastPollTimestamp())
    }

    @Test
    fun getLastPollTimestamp_shouldReturnZero_whenNeverPolled() {
        SlackConfig.clearAll()

        assertEquals(0L, SlackConfig.getLastPollTimestamp())
    }

    @Test
    fun getLastPollTimestampForApi_shouldReturnFormattedString() {
        val timestamp = 1700000000000L // Known timestamp in milliseconds

        SlackConfig.saveLastPollTimestamp(timestamp)

        val apiTimestamp = SlackConfig.getLastPollTimestampForApi()

        // Should be seconds with microseconds: "1700000000.000000"
        assertEquals("1700000000.000000", apiTimestamp)
    }

    @Test
    fun getLastPollTimestampForApi_shouldReturnZero_whenNeverPolled() {
        SlackConfig.clearAll()

        assertEquals("0", SlackConfig.getLastPollTimestampForApi())
    }

    // Exponential backoff tests

    @Test
    fun recordFailureAndGetBackoff_shouldReturnInitialBackoff_onFirstFailure() {
        SlackConfig.clearAll()

        val backoff = SlackConfig.recordFailureAndGetBackoff()

        assertEquals(SlackConfig.INITIAL_BACKOFF_MS, backoff)
    }

    @Test
    fun recordFailureAndGetBackoff_shouldDoubleBackoff_onSubsequentFailures() {
        SlackConfig.clearAll()

        // First failure returns initial (5s), sets next to 10s
        val backoff1 = SlackConfig.recordFailureAndGetBackoff()
        assertEquals(5000L, backoff1)

        // Second failure returns 10s, sets next to 20s
        val backoff2 = SlackConfig.recordFailureAndGetBackoff()
        assertEquals(10000L, backoff2)

        // Third failure returns 20s, sets next to 40s
        val backoff3 = SlackConfig.recordFailureAndGetBackoff()
        assertEquals(20000L, backoff3)
    }

    @Test
    fun recordFailureAndGetBackoff_shouldCapAtMaxBackoff() {
        SlackConfig.clearAll()

        // Call multiple times to exceed max
        repeat(10) {
            SlackConfig.recordFailureAndGetBackoff()
        }

        val currentBackoff = SlackConfig.getCurrentBackoff()
        assertEquals(SlackConfig.MAX_BACKOFF_MS, currentBackoff)
    }

    @Test
    fun resetBackoff_shouldResetToInitialValue() {
        SlackConfig.clearAll()

        // Increase backoff
        SlackConfig.recordFailureAndGetBackoff()
        SlackConfig.recordFailureAndGetBackoff()
        SlackConfig.recordFailureAndGetBackoff()

        // Reset
        SlackConfig.resetBackoff()

        assertEquals(SlackConfig.INITIAL_BACKOFF_MS, SlackConfig.getCurrentBackoff())
    }

    @Test
    fun getCurrentBackoff_shouldReturnInitial_whenNoFailures() {
        SlackConfig.clearAll()

        assertEquals(SlackConfig.INITIAL_BACKOFF_MS, SlackConfig.getCurrentBackoff())
    }

    @Test
    fun getLastErrorTimestamp_shouldReturnZero_whenNoErrors() {
        SlackConfig.clearAll()

        assertEquals(0L, SlackConfig.getLastErrorTimestamp())
    }

    @Test
    fun recordFailureAndGetBackoff_shouldUpdateLastErrorTimestamp() {
        SlackConfig.clearAll()
        val beforeTimestamp = System.currentTimeMillis()

        SlackConfig.recordFailureAndGetBackoff()

        val lastError = SlackConfig.getLastErrorTimestamp()
        assertTrue(lastError >= beforeTimestamp)
        assertTrue(lastError <= System.currentTimeMillis())
    }

    // Clear all tests

    @Test
    fun clearAll_shouldRemoveAllStoredData() {
        // Store various data
        SlackConfig.saveTokens("access", "bot")
        SlackConfig.saveWorkspaceInfo("T123", "Workspace", "U123")
        SlackConfig.saveLastPollTimestamp(System.currentTimeMillis())
        SlackConfig.recordFailureAndGetBackoff()

        // Clear everything
        SlackConfig.clearAll()

        // Verify all cleared
        assertNull(SlackConfig.getAccessToken())
        assertNull(SlackConfig.getBotToken())
        assertNull(SlackConfig.getWorkspaceId())
        assertNull(SlackConfig.getWorkspaceName())
        assertNull(SlackConfig.getUserId())
        assertEquals(0L, SlackConfig.getLastPollTimestamp())
        assertEquals(SlackConfig.INITIAL_BACKOFF_MS, SlackConfig.getCurrentBackoff())
        assertEquals(0L, SlackConfig.getLastErrorTimestamp())
    }

    // Debug info tests

    @Test
    fun getDebugInfo_shouldReturnCorrectInformation() {
        val accessToken = "debug_access_token_xoxb"
        SlackConfig.saveTokens(accessToken, "bot_token")
        SlackConfig.saveWorkspaceInfo("T123456", "Debug Workspace", "U789")
        SlackConfig.saveLastPollTimestamp(1700000000000L)

        val debugInfo = SlackConfig.getDebugInfo()

        assertEquals("true", debugInfo["hasAccessToken"])
        assertEquals("debug_acce", debugInfo["accessTokenPreview"])
        assertEquals("true", debugInfo["hasBotToken"])
        assertEquals("T123456", debugInfo["workspaceId"])
        assertEquals("Debug Workspace", debugInfo["workspaceName"])
        assertEquals("U789", debugInfo["userId"])
        assertEquals("1700000000000", debugInfo["lastPollTimestamp"])
    }

    @Test
    fun getDebugInfo_shouldHandleNullValues() {
        SlackConfig.clearAll()

        val debugInfo = SlackConfig.getDebugInfo()

        assertEquals("false", debugInfo["hasAccessToken"])
        assertEquals("null", debugInfo["accessTokenPreview"])
        assertEquals("false", debugInfo["hasBotToken"])
        assertEquals("null", debugInfo["workspaceId"])
        assertEquals("null", debugInfo["workspaceName"])
        assertEquals("null", debugInfo["userId"])
        assertEquals("0", debugInfo["lastPollTimestamp"])
    }

    // Persistence tests

    @Test
    fun tokensShouldPersistAcrossReads() {
        SlackConfig.saveTokens("persistent_token_123")

        val token1 = SlackConfig.getAccessToken()
        val token2 = SlackConfig.getAccessToken()
        val token3 = SlackConfig.getAccessToken()

        assertEquals(token1, token2)
        assertEquals(token2, token3)
        assertEquals("persistent_token_123", token1)
    }

    @Test
    fun workspaceInfoShouldPersistAcrossReads() {
        SlackConfig.saveWorkspaceInfo("T111", "Persistent WS", "U222")

        val id1 = SlackConfig.getWorkspaceId()
        val name1 = SlackConfig.getWorkspaceName()
        val id2 = SlackConfig.getWorkspaceId()
        val name2 = SlackConfig.getWorkspaceName()

        assertEquals(id1, id2)
        assertEquals(name1, name2)
    }
}
