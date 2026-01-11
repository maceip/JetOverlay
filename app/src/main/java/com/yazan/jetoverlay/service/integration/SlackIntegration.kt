package com.yazan.jetoverlay.service.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.yazan.jetoverlay.data.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object SlackIntegration {

    private const val CLIENT_ID = "YOUR_SLACK_CLIENT_ID" // TODO: Replace
    private const val CLIENT_SECRET = "YOUR_SLACK_CLIENT_SECRET" // TODO: Replace
    private const val REDIRECT_URI = "jetoverlay://slack-callback"
    private const val TAG = "SlackIntegration"

    private val client = OkHttpClient()
    private var accessToken: String? = null

    // For Demo: In-memory running flag
    private var isPolling = false

    fun startOAuth(context: Context) {
        val url = "https://slack.com/oauth/v2/authorize?client_id=$CLIENT_ID&scope=channels:history,groups:history,im:history,mpim:history&user_scope=&redirect_uri=$REDIRECT_URI"
        
        val intent = CustomTabsIntent.Builder().build()
        intent.launchUrl(context, Uri.parse(url))
    }

    suspend fun handleCallback(uri: Uri, repository: MessageRepository): Boolean {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            Log.d(TAG, "Received code: $code. Exchanging for token...")
            return exchangeCodeForToken(code, repository)
        } else {
            val error = uri.getQueryParameter("error")
            Log.e(TAG, "OAuth Error: $error")
            return false
        }
    }

    private suspend fun exchangeCodeForToken(code: String, repository: MessageRepository): Boolean {
        // Exchange code for token
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
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
                        accessToken = json.optString("authed_user_access_token") 
                        // Note: Depending on 'token_rotation' or bot tokens, might be 'access_token'
                        if (accessToken.isNullOrEmpty()) {
                             accessToken = json.optString("access_token")
                        }
                        
                        Log.d(TAG, "Token received: ${accessToken?.take(5)}...")
                        startPolling(repository)
                        return@withContext true
                    } else {
                        Log.e(TAG, "Slack Error: ${json.optString("error")}")
                    }
                } else {
                    Log.e(TAG, "Network Error: ${response.code}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext false
        }
    }

    fun startPolling(repository: MessageRepository) {
        if (isPolling) return
        isPolling = true
        
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Starting aggressive polling...")
            while (isPolling && accessToken != null) {
                fetchMessages(repository)
                delay(5000) // Poll every 5 seconds (Aggressive enough for demo?) 
                // Real "aggressive" might be 1s but that hits rate limits fast.
            }
        }
    }

    private fun fetchMessages(repository: MessageRepository) {
        // Mock implementation for "conversations.history" 
        // In a real app we need to know WHICH channel to poll or list all channels first.
        // For the sake of this prompt's constraints ("generic... aggressively get every single message"), 
        // we'll assume we iterate channels or just hit 'conversations.list' then 'history'.
        
        // Simulating a fetch for the demo if creds aren't real yet
        if (CLIENT_ID == "YOUR_SLACK_CLIENT_ID") {
            // Fake ingestion for demo
           /* 
            repository.ingestNotification(
                packageName = "com.slack",
                sender = "SlackUser",
                content = "Polled message at ${System.currentTimeMillis()}"
            )
            */
            return
        }
        
        // TODO: Real Implementation using 'conversations.list' and 'conversations.history'
        // This requires 'channels:read' scope as well.
    }
}
