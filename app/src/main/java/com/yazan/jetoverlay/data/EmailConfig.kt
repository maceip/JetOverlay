package com.yazan.jetoverlay.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.yazan.jetoverlay.JetOverlayApplication

/**
 * Configuration storage for Email/Gmail integration OAuth tokens.
 * Stores access token, refresh token, and expiry time in SharedPreferences.
 *
 * Note: Per requirements, no encryption is used for token storage.
 */
object EmailConfig {

    private const val TAG = "EmailConfig"
    private const val PREFS_NAME = "email_integration_prefs"

    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRY_TIME = "expiry_time"
    private const val KEY_USER_EMAIL = "user_email"

    private val prefs: SharedPreferences by lazy {
        JetOverlayApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves OAuth tokens to SharedPreferences.
     *
     * @param accessToken The access token from OAuth flow
     * @param refreshToken The refresh token for obtaining new access tokens
     * @param expiryTime Token expiration timestamp in milliseconds
     */
    fun saveTokens(accessToken: String, refreshToken: String, expiryTime: Long) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRY_TIME, expiryTime)
            .apply()

        Log.d(TAG, "OAuth tokens saved. Expires at: $expiryTime")
    }

    /**
     * Gets the current access token.
     * @return The access token or null if not set
     */
    fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    /**
     * Gets the current refresh token.
     * @return The refresh token or null if not set
     */
    fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Gets the token expiration time.
     * @return The expiry timestamp in milliseconds, or 0 if not set
     */
    fun getExpiryTime(): Long {
        return prefs.getLong(KEY_EXPIRY_TIME, 0L)
    }

    /**
     * Checks if stored tokens exist and are still valid (not expired).
     * @return true if valid tokens exist, false otherwise
     */
    fun hasValidTokens(): Boolean {
        val accessToken = getAccessToken()
        val expiryTime = getExpiryTime()

        if (accessToken.isNullOrBlank()) {
            return false
        }

        // Check if token is expired (with 5-minute buffer)
        val bufferMs = 5 * 60 * 1000 // 5 minutes
        val isExpired = System.currentTimeMillis() > (expiryTime - bufferMs)

        if (isExpired) {
            Log.d(TAG, "Access token is expired or about to expire")
            return false
        }

        return true
    }

    /**
     * Checks if a refresh token is available for obtaining new access tokens.
     * @return true if a refresh token exists, false otherwise
     */
    fun hasRefreshToken(): Boolean {
        return !getRefreshToken().isNullOrBlank()
    }

    /**
     * Saves the authenticated user's email address.
     * @param email The user's email address
     */
    fun saveUserEmail(email: String) {
        prefs.edit()
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    /**
     * Gets the stored user email address.
     * @return The user's email or null if not set
     */
    fun getUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    /**
     * Clears all stored tokens and user info.
     * Used when disconnecting the integration.
     */
    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRY_TIME)
            .remove(KEY_USER_EMAIL)
            .apply()

        Log.d(TAG, "OAuth tokens cleared")
    }

    /**
     * Gets all stored configuration for debugging purposes.
     * @return Map of all stored key-value pairs (tokens are partially masked)
     */
    fun getDebugInfo(): Map<String, String> {
        val accessToken = getAccessToken()
        val refreshToken = getRefreshToken()

        return mapOf(
            "hasAccessToken" to (accessToken != null).toString(),
            "accessTokenPreview" to (accessToken?.take(10) ?: "null"),
            "hasRefreshToken" to (refreshToken != null).toString(),
            "expiryTime" to getExpiryTime().toString(),
            "isExpired" to (!hasValidTokens()).toString(),
            "userEmail" to (getUserEmail() ?: "null")
        )
    }
}
