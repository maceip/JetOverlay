package com.yazan.jetoverlay.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.yazan.jetoverlay.JetOverlayApplication

/**
 * Configuration storage for Slack integration OAuth tokens and state.
 * Stores access token, workspace info, last poll timestamp, and backoff state.
 *
 * Note: Per requirements, no encryption is used for token storage.
 */
object SlackConfig {

    private const val TAG = "SlackConfig"
    private const val PREFS_NAME = "slack_integration_prefs"

    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_BOT_TOKEN = "bot_token"
    private const val KEY_WORKSPACE_ID = "workspace_id"
    private const val KEY_WORKSPACE_NAME = "workspace_name"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_LAST_POLL_TIMESTAMP = "last_poll_timestamp"
    private const val KEY_CURRENT_BACKOFF_MS = "current_backoff_ms"
    private const val KEY_LAST_ERROR_TIMESTAMP = "last_error_timestamp"

    // Exponential backoff constants
    const val INITIAL_BACKOFF_MS = 5000L // 5 seconds
    const val MAX_BACKOFF_MS = 60000L // 60 seconds
    const val BACKOFF_MULTIPLIER = 2.0

    // Default polling interval
    const val DEFAULT_POLL_INTERVAL_MS = 15000L // 15 seconds for production

    private val prefs: SharedPreferences by lazy {
        JetOverlayApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves the OAuth access token.
     *
     * @param accessToken The user access token from OAuth flow
     * @param botToken Optional bot token if using bot scopes
     */
    fun saveTokens(accessToken: String, botToken: String? = null) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .apply {
                if (botToken != null) {
                    putString(KEY_BOT_TOKEN, botToken)
                }
            }
            .apply()

        Log.d(TAG, "OAuth tokens saved")
    }

    /**
     * Gets the current user access token.
     * @return The access token or null if not set
     */
    fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    /**
     * Gets the bot token if available.
     * @return The bot token or null if not set
     */
    fun getBotToken(): String? {
        return prefs.getString(KEY_BOT_TOKEN, null)
    }

    /**
     * Checks if valid tokens exist.
     * @return true if an access token exists, false otherwise
     */
    fun hasValidTokens(): Boolean {
        return !getAccessToken().isNullOrBlank()
    }

    /**
     * Saves workspace information from the OAuth response.
     *
     * @param workspaceId The team/workspace ID
     * @param workspaceName The team/workspace name
     * @param userId The authenticated user's ID
     */
    fun saveWorkspaceInfo(workspaceId: String, workspaceName: String, userId: String) {
        prefs.edit()
            .putString(KEY_WORKSPACE_ID, workspaceId)
            .putString(KEY_WORKSPACE_NAME, workspaceName)
            .putString(KEY_USER_ID, userId)
            .apply()

        Log.d(TAG, "Workspace info saved: $workspaceName ($workspaceId)")
    }

    /**
     * Gets the workspace ID.
     * @return The workspace ID or null if not set
     */
    fun getWorkspaceId(): String? {
        return prefs.getString(KEY_WORKSPACE_ID, null)
    }

    /**
     * Gets the workspace name.
     * @return The workspace name or null if not set
     */
    fun getWorkspaceName(): String? {
        return prefs.getString(KEY_WORKSPACE_NAME, null)
    }

    /**
     * Gets the authenticated user's ID.
     * @return The user ID or null if not set
     */
    fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }

    /**
     * Saves the timestamp of the last successful poll.
     * Used to avoid fetching duplicate messages.
     *
     * @param timestamp The timestamp of the last successful poll in milliseconds
     */
    fun saveLastPollTimestamp(timestamp: Long) {
        prefs.edit()
            .putLong(KEY_LAST_POLL_TIMESTAMP, timestamp)
            .apply()

        Log.d(TAG, "Last poll timestamp saved: $timestamp")
    }

    /**
     * Gets the timestamp of the last successful poll.
     * @return The last poll timestamp in milliseconds, or 0 if never polled
     */
    fun getLastPollTimestamp(): Long {
        return prefs.getLong(KEY_LAST_POLL_TIMESTAMP, 0L)
    }

    /**
     * Gets the last poll timestamp as Slack API format (Unix timestamp in seconds with microseconds).
     * Returns "0" if never polled.
     * @return The Slack-formatted timestamp string
     */
    fun getLastPollTimestampForApi(): String {
        val timestamp = getLastPollTimestamp()
        return if (timestamp > 0) {
            // Convert milliseconds to seconds for Slack API
            "${timestamp / 1000}.000000"
        } else {
            "0"
        }
    }

    /**
     * Records an API failure and calculates the next backoff delay.
     * Uses exponential backoff starting at INITIAL_BACKOFF_MS up to MAX_BACKOFF_MS.
     *
     * @return The current backoff delay to use in milliseconds
     */
    fun recordFailureAndGetBackoff(): Long {
        val currentBackoff = prefs.getLong(KEY_CURRENT_BACKOFF_MS, INITIAL_BACKOFF_MS)
        val nextBackoff = (currentBackoff * BACKOFF_MULTIPLIER).toLong().coerceAtMost(MAX_BACKOFF_MS)

        prefs.edit()
            .putLong(KEY_CURRENT_BACKOFF_MS, nextBackoff)
            .putLong(KEY_LAST_ERROR_TIMESTAMP, System.currentTimeMillis())
            .apply()

        Log.d(TAG, "API failure recorded. Backoff: ${currentBackoff}ms -> ${nextBackoff}ms")
        return currentBackoff
    }

    /**
     * Gets the current backoff delay without incrementing.
     * @return The current backoff delay in milliseconds
     */
    fun getCurrentBackoff(): Long {
        return prefs.getLong(KEY_CURRENT_BACKOFF_MS, INITIAL_BACKOFF_MS)
    }

    /**
     * Gets the timestamp of the last error.
     * @return The last error timestamp in milliseconds, or 0 if no errors
     */
    fun getLastErrorTimestamp(): Long {
        return prefs.getLong(KEY_LAST_ERROR_TIMESTAMP, 0L)
    }

    /**
     * Resets the backoff to the initial value after a successful API call.
     */
    fun resetBackoff() {
        val hadBackoff = prefs.getLong(KEY_CURRENT_BACKOFF_MS, INITIAL_BACKOFF_MS) > INITIAL_BACKOFF_MS

        prefs.edit()
            .putLong(KEY_CURRENT_BACKOFF_MS, INITIAL_BACKOFF_MS)
            .apply()

        if (hadBackoff) {
            Log.d(TAG, "Backoff reset to initial value after successful call")
        }
    }

    /**
     * Clears all stored tokens, workspace info, and state.
     * Used when disconnecting the integration.
     */
    fun clearAll() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_BOT_TOKEN)
            .remove(KEY_WORKSPACE_ID)
            .remove(KEY_WORKSPACE_NAME)
            .remove(KEY_USER_ID)
            .remove(KEY_LAST_POLL_TIMESTAMP)
            .remove(KEY_CURRENT_BACKOFF_MS)
            .remove(KEY_LAST_ERROR_TIMESTAMP)
            .apply()

        Log.d(TAG, "All Slack configuration cleared")
    }

    /**
     * Gets all stored configuration for debugging purposes.
     * @return Map of all stored key-value pairs (tokens are partially masked)
     */
    fun getDebugInfo(): Map<String, String> {
        val accessToken = getAccessToken()
        val botToken = getBotToken()

        return mapOf(
            "hasAccessToken" to (accessToken != null).toString(),
            "accessTokenPreview" to (accessToken?.take(10) ?: "null"),
            "hasBotToken" to (botToken != null).toString(),
            "workspaceId" to (getWorkspaceId() ?: "null"),
            "workspaceName" to (getWorkspaceName() ?: "null"),
            "userId" to (getUserId() ?: "null"),
            "lastPollTimestamp" to getLastPollTimestamp().toString(),
            "currentBackoff" to getCurrentBackoff().toString(),
            "lastErrorTimestamp" to getLastErrorTimestamp().toString()
        )
    }
}
