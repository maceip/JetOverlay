package com.yazan.jetoverlay.service.integration

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.data.SlackConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object SlackIntegration {

    // TODO: move secret to secure storage/remote config before release
    private const val CLIENT_ID = "8516887257863.10240039617412"
    private const val CLIENT_SECRET = "97709708281e3aa889287a06c3da203f"
    // Slack app should redirect to this page, which must forward to jetoverlay://slack-callback
    private const val REDIRECT_URI = "https://maceip.github.io/id/slack-oauth.html"
    private const val TAG = "SlackIntegration"
    const val SLACK_PACKAGE_NAME = "com.slack"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // For Demo: In-memory running flag
    private var isPolling = false

    // Track consecutive failures for backoff
    private var consecutiveFailures = 0

    fun startOAuth(context: Context) {
        Log.d(TAG, "Starting Slack OAuth flow")
        val encodedRedirect = Uri.encode(REDIRECT_URI)
        val url = "https://slack.com/oauth/v2/authorize?client_id=$CLIENT_ID&scope=channels:history,groups:history,im:history,mpim:history&user_scope=&redirect_uri=$encodedRedirect"

        val intent = CustomTabsIntent.Builder().build()
        intent.launchUrl(context, Uri.parse(url))
    }

    suspend fun handleCallback(uri: Uri, repository: MessageRepository): Boolean {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            Log.d(TAG, "Received authorization code: ${code.take(10)}...")
            return exchangeCodeForToken(code, repository)
        } else {
            val error = uri.getQueryParameter("error")
            val errorDescription = uri.getQueryParameter("error_description")
            Log.e(TAG, "OAuth Error: $error")
            Log.e(TAG, "OAuth Error Description: $errorDescription")
            Log.e(TAG, "Full callback URI: $uri")
            return false
        }
    }

    private suspend fun exchangeCodeForToken(code: String, repository: MessageRepository): Boolean {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Exchanging authorization code for access token...")

                val formBody = FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .build()

                val request = Request.Builder()
                    .url("https://slack.com/api/oauth.v2.access")
                    .post(formBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    if (json.optBoolean("ok")) {
                        // Extract access token (try authed_user first, then access_token)
                        var accessToken = json.optString("authed_user_access_token")
                        if (accessToken.isNullOrEmpty()) {
                            accessToken = json.optString("access_token")
                        }

                        if (accessToken.isNullOrEmpty()) {
                            Log.e(TAG, "OAuth token exchange succeeded but no token found in response")
                            Log.e(TAG, "Response keys: ${json.keys().asSequence().toList()}")
                            return@withContext false
                        }

                        // Save the token
                        SlackConfig.saveTokens(accessToken)
                        Log.d(TAG, "Access token saved: ${accessToken.take(10)}...")

                        // Extract and save workspace info if available
                        val team = json.optJSONObject("team")
                        if (team != null) {
                            val workspaceId = team.optString("id", "")
                            val workspaceName = team.optString("name", "")
                            val userId = json.optJSONObject("authed_user")?.optString("id", "") ?: ""

                            if (workspaceId.isNotEmpty()) {
                                SlackConfig.saveWorkspaceInfo(workspaceId, workspaceName, userId)
                                Log.d(TAG, "Workspace info saved: $workspaceName")
                            }
                        }

                        startPolling(repository)
                        return@withContext true
                    } else {
                        val errorMsg = json.optString("error", "unknown")
                        val errorDetail = json.optString("error_description", "")
                        Log.e(TAG, "Slack API Error during token exchange: $errorMsg")
                        if (errorDetail.isNotEmpty()) {
                            Log.e(TAG, "Error detail: $errorDetail")
                        }
                        Log.e(TAG, "Full error response: $responseBody")
                    }
                } else {
                    Log.e(TAG, "HTTP Error during token exchange: ${response.code}")
                    Log.e(TAG, "Response message: ${response.message}")
                    if (responseBody != null) {
                        Log.e(TAG, "Response body: $responseBody")
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error during token exchange", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during token exchange", e)
            }
            return@withContext false
        }
    }

    fun startPolling(repository: MessageRepository) {
        if (isPolling) {
            Log.d(TAG, "Polling already active")
            return
        }

        val accessToken = SlackConfig.getAccessToken()
        if (accessToken.isNullOrEmpty()) {
            Log.w(TAG, "Cannot start polling - no access token available")
            return
        }

        isPolling = true
        consecutiveFailures = 0

        Log.d(TAG, "Starting Slack polling (interval: ${SlackConfig.DEFAULT_POLL_INTERVAL_MS}ms)")

        CoroutineScope(Dispatchers.IO).launch {
            while (isPolling) {
                val success = fetchMessages(repository)

                if (success) {
                    // Reset backoff on success
                    SlackConfig.resetBackoff()
                    consecutiveFailures = 0
                    delay(SlackConfig.DEFAULT_POLL_INTERVAL_MS) // 15 seconds
                } else {
                    // Apply exponential backoff on failure
                    consecutiveFailures++
                    val backoffDelay = SlackConfig.recordFailureAndGetBackoff()
                    Log.w(TAG, "Polling failed (consecutive: $consecutiveFailures). Backing off for ${backoffDelay}ms")
                    delay(backoffDelay)
                }
            }
        }
    }

    fun stopPolling() {
        Log.d(TAG, "Stopping Slack polling")
        isPolling = false
    }

    /**
     * Fetches messages from Slack API.
     * Uses last poll timestamp to avoid duplicate messages.
     *
     * @return true if fetch was successful, false on error
     */
    private fun fetchMessages(repository: MessageRepository): Boolean {
        // Mock implementation for "conversations.history"
        // In a real app we need to know WHICH channel to poll or list all channels first.

        // Simulating a fetch for the demo if creds aren't real yet
        if (CLIENT_ID == "YOUR_SLACK_CLIENT_ID") {
            // Update last poll timestamp even for mock data
            SlackConfig.saveLastPollTimestamp(System.currentTimeMillis())
            Log.d(TAG, "Using mock mode - no real API calls")
            return true
        }

        val accessToken = SlackConfig.getAccessToken()
        if (accessToken.isNullOrEmpty()) {
            Log.e(TAG, "No access token available for fetching messages")
            return false
        }

        try {
            // Get the oldest timestamp from last successful poll
            val lastPollTimestamp = SlackConfig.getLastPollTimestampForApi()
            Log.d(TAG, "Fetching messages newer than: $lastPollTimestamp")

            // TODO: Real Implementation using 'conversations.list' and 'conversations.history'
            // This requires 'channels:read' scope as well.
            // Example:
            // 1. GET https://slack.com/api/conversations.list to get channel IDs
            // 2. For each channel, GET https://slack.com/api/conversations.history?channel=CHANNEL_ID&oldest=$lastPollTimestamp
            // 3. Parse messages and ingest via repository

            // For now, mark as successful and update timestamp
            SlackConfig.saveLastPollTimestamp(System.currentTimeMillis())
            return true

        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching Slack messages", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Slack messages", e)
            return false
        }
    }

    /**
     * Checks if the integration is currently connected (has valid tokens).
     */
    fun isConnected(): Boolean {
        return SlackConfig.hasValidTokens()
    }

    /**
     * Checks if polling is currently active.
     */
    fun isPollingActive(): Boolean {
        return isPolling
    }

    /**
     * Disconnects the Slack integration by clearing stored tokens.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting Slack integration")
        stopPolling()
        SlackConfig.clearAll()
    }

    /**
     * Gets debug information about the current state.
     */
    fun getDebugInfo(): Map<String, String> {
        return SlackConfig.getDebugInfo() + mapOf(
            "isPolling" to isPolling.toString(),
            "consecutiveFailures" to consecutiveFailures.toString()
        )
    }

    /**
     * Performs a single sync cycle for WorkManager-based scheduling.
     * Unlike continuous polling, this fetches messages once and returns.
     */
    suspend fun syncOnce(repository: MessageRepository): Boolean {
        Log.d(TAG, "Performing single sync cycle")
        return fetchMessages(repository)
    }
}
