package com.yazan.jetoverlay.service.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import android.widget.Toast
import com.yazan.jetoverlay.app.BuildConfig
import com.yazan.jetoverlay.JetOverlayApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GitHub integration stub for GitHub API.
 * Currently returns mock data - full implementation pending.
 *
 * Follows the SlackIntegration pattern with OAuth flow and polling.
 */
object GitHubIntegration {

    private const val TAG = "GitHubIntegration"
    private val CLIENT_ID = BuildConfig.GITHUB_CLIENT_ID
    private val CLIENT_SECRET = BuildConfig.GITHUB_CLIENT_SECRET
    private val REDIRECT_URI = BuildConfig.GITHUB_REDIRECT_URI.ifBlank { "jetoverlay://github-callback" }
    const val GITHUB_PACKAGE_NAME = "github"

    // OAuth URL for GitHub
    private const val GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize"

    // Polling state
    private var isPolling = false
    private const val POLL_INTERVAL_MS = 30000L // 30 seconds

    // In-memory token storage (stub - in production would use GitHubConfig like SlackConfig)
    private var accessToken: String? = null

    /**
     * Initiates the OAuth flow for GitHub access.
     * Opens a browser/custom tab with GitHub's authorization page.
     */
    fun startOAuth(context: Context) {
        if (CLIENT_ID.isBlank() || CLIENT_SECRET.isBlank()) {
            Log.e(TAG, "GitHub OAuth not configured: set GITHUB_CLIENT_ID/SECRET in gradle properties or env.")
            Toast.makeText(
                context,
                "GitHub OAuth not configured. Set GITHUB_CLIENT_ID/SECRET.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        Log.d(TAG, "GitHub integration not yet implemented - OAuth flow is stubbed")

        val authUrl = "$GITHUB_AUTH_URL" +
            "?client_id=$CLIENT_ID" +
            "&redirect_uri=$REDIRECT_URI" +
            "&scope=notifications,repo" +
            "&state=${System.currentTimeMillis()}"

        try {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, Uri.parse(authUrl))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch OAuth flow", e)
            // Fallback to regular browser
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            context.startActivity(browserIntent)
        }
    }

    /**
     * Handles the OAuth callback from GitHub authorization.
     * Exchanges the authorization code for access token.
     *
     * @param code The authorization code from the callback
     * @return true if token exchange was successful, false otherwise
     */
    suspend fun handleOAuthCallback(code: String): Boolean {
        Log.d(TAG, "GitHub integration not yet implemented - token exchange stubbed")
        Log.d(TAG, "Received authorization code: ${code.take(10)}...")

        return withContext(Dispatchers.IO) {
            try {
                // TODO: Implement actual token exchange with GitHub
                // For now, simulate a successful token exchange
                val mockAccessToken = "mock_github_token_${System.currentTimeMillis()}"

                // Save mock token
                accessToken = mockAccessToken

                Log.d(TAG, "Mock GitHub token saved successfully")

                // Start polling for notifications
                startPolling()

                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle OAuth callback", e)
                false
            }
        }
    }

    /**
     * Starts polling for GitHub notifications (PR reviews, mentions, issues, etc.).
     * Currently returns mock notification messages every 30 seconds.
     */
    fun startPolling() {
        if (isPolling) {
            Log.d(TAG, "Polling already active")
            return
        }

        if (accessToken == null) {
            Log.w(TAG, "Cannot start polling - no access token")
            return
        }

        isPolling = true
        Log.d(TAG, "Starting GitHub polling (mock data every ${POLL_INTERVAL_MS / 1000}s)")

        CoroutineScope(Dispatchers.IO).launch {
            while (isPolling) {
                pollForNotifications()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Stops polling for GitHub notifications.
     */
    fun stopPolling() {
        Log.d(TAG, "Stopping GitHub polling")
        isPolling = false
    }

    /**
     * Polls for new GitHub notifications (PR reviews, mentions, issues, etc.).
     * Currently returns mock notification messages.
     */
    suspend fun pollForNotifications(): List<MockGitHubNotification> {
        Log.d(TAG, "GitHub integration not yet implemented - returning mock data")

        if (accessToken == null) {
            Log.w(TAG, "No valid token for GitHub polling")
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            // Generate mock GitHub notification data
            val mockNotifications = listOf(
                MockGitHubNotification(
                    id = "github_${System.currentTimeMillis()}",
                    type = GitHubNotificationType.PR_REVIEW,
                    repository = "user/mock-repo",
                    author = "octocat",
                    title = "Review requested on PR #42",
                    content = "@you requested your review on pull request: Add new feature"
                )
            )

            // Ingest mock notifications into the repository
            try {
                val repository = JetOverlayApplication.instance.repository

                for (notification in mockNotifications) {
                    val content = "[${notification.type.displayName}] ${notification.repository}\n" +
                        "${notification.title}\n\n${notification.content}"
                    repository.ingestNotification(
                        packageName = GITHUB_PACKAGE_NAME,
                        sender = notification.author,
                        content = content,
                        threadKey = "$GITHUB_PACKAGE_NAME:${notification.author.lowercase()}"
                    )
                    Log.d(TAG, "Ingested mock GitHub notification from ${notification.author}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to ingest mock GitHub notifications", e)
            }

            mockNotifications
        }
    }

    /**
     * Checks if the integration is currently connected (has valid token).
     */
    fun isConnected(): Boolean {
        return accessToken != null
    }

    /**
     * Checks if the integration is currently polling.
     */
    fun isPollingActive(): Boolean {
        return isPolling
    }

    /**
     * Disconnects the GitHub integration by clearing stored token.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting GitHub integration")
        stopPolling()
        accessToken = null
    }

    /**
     * Enum representing types of GitHub notifications.
     */
    enum class GitHubNotificationType(val displayName: String) {
        PR_REVIEW("PR Review"),
        PR_COMMENT("PR Comment"),
        ISSUE_COMMENT("Issue Comment"),
        MENTION("Mention"),
        ASSIGN("Assignment"),
        CI_FAILURE("CI Failure"),
        RELEASE("Release")
    }

    /**
     * Data class representing a mock GitHub notification.
     */
    data class MockGitHubNotification(
        val id: String,
        val type: GitHubNotificationType,
        val repository: String,
        val author: String,
        val title: String,
        val content: String
    )

    /**
     * Performs a single sync cycle for WorkManager-based scheduling.
     * Unlike continuous polling, this fetches notifications once and returns.
     */
    suspend fun syncOnce() {
        Log.d(TAG, "Performing single GitHub sync cycle")
        pollForNotifications()
    }
}
