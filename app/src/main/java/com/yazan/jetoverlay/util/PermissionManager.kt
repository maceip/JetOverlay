package com.yazan.jetoverlay.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Centralized permission management for JetOverlay.
 * Handles permission checking, status persistence, and Settings navigation.
 */
class PermissionManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "jetoverlay_permissions"
        private const val KEY_LAST_CHECK_TIMESTAMP = "last_permission_check"
        private const val KEY_PERMISSION_PREFIX = "permission_"
        private const val KEY_DENIED_COUNT_PREFIX = "denied_count_"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Represents a required permission with its metadata.
     */
    data class PermissionInfo(
        val id: String,
        val title: String,
        val description: String,
        val isSpecialPermission: Boolean = false,
        val isGranted: Boolean = false,
        val deniedCount: Int = 0
    )

    /**
     * All permissions required by the app, in order of request.
     */
    enum class RequiredPermission(
        val id: String,
        val title: String,
        val description: String,
        val isSpecial: Boolean = false
    ) {
        OVERLAY(
            id = "overlay",
            title = "Display Over Other Apps",
            description = "Required to show the floating chat bubble and response interface over other apps.",
            isSpecial = true
        ),
        NOTIFICATION_POST(
            id = "notification_post",
            title = "Send Notifications",
            description = "Required to show service status and keep the agent running in the background.",
            isSpecial = false
        ),
        NOTIFICATION_LISTENER(
            id = "notification_listener",
            title = "Notification Access",
            description = "Required to read incoming messages and provide intelligent responses.",
            isSpecial = true
        ),
        RECORD_AUDIO(
            id = "record_audio",
            title = "Microphone Access",
            description = "Optional: Enables voice input for composing responses.",
            isSpecial = false
        ),
        PHONE(
            id = "phone",
            title = "Phone Access",
            description = "Optional: Enables call screening and phone-related features.",
            isSpecial = false
        ),
        CALL_SCREENING(
            id = "call_screening",
            title = "Call Screening",
            description = "Optional: Allows the agent to screen incoming calls.",
            isSpecial = true
        ),
        SMS(
            id = "sms",
            title = "SMS Access",
            description = "Optional: Enables reading and responding to text messages.",
            isSpecial = false
        );

        companion object {
            /**
             * Returns all required permissions in order.
             */
            fun allRequired(): List<RequiredPermission> = listOf(
                OVERLAY, NOTIFICATION_POST, NOTIFICATION_LISTENER
            )

            /**
             * Returns all optional permissions.
             */
            fun allOptional(): List<RequiredPermission> = listOf(
                RECORD_AUDIO, PHONE, CALL_SCREENING, SMS
            )

            /**
             * Returns all permissions.
             */
            fun all(): List<RequiredPermission> = allRequired() + allOptional()
        }
    }

    /**
     * Check if overlay permission is granted.
     */
    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Check if notification post permission is granted (Android 13+).
     */
    fun hasNotificationPostPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Implicitly granted below API 33
        }
    }

    /**
     * Check if notification listener access is enabled.
     */
    fun hasNotificationListenerAccess(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        return enabledListeners.contains(context.packageName)
    }

    /**
     * Check if audio recording permission is granted.
     */
    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if phone permissions are granted.
     */
    fun hasPhonePermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if call screening role is held.
     */
    fun hasCallScreeningRole(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)
            } catch (e: Exception) {
                false
            }
        } else {
            true // Not applicable below Android Q
        }
    }

    /**
     * Check if SMS permissions are granted.
     */
    fun hasSmsPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if a specific permission is granted.
     */
    fun isPermissionGranted(permission: RequiredPermission): Boolean {
        return when (permission) {
            RequiredPermission.OVERLAY -> hasOverlayPermission()
            RequiredPermission.NOTIFICATION_POST -> hasNotificationPostPermission()
            RequiredPermission.NOTIFICATION_LISTENER -> hasNotificationListenerAccess()
            RequiredPermission.RECORD_AUDIO -> hasRecordAudioPermission()
            RequiredPermission.PHONE -> hasPhonePermissions()
            RequiredPermission.CALL_SCREENING -> hasCallScreeningRole()
            RequiredPermission.SMS -> hasSmsPermissions()
        }
    }

    /**
     * Get the current status of all permissions.
     */
    fun getPermissionStatus(): List<PermissionInfo> {
        return RequiredPermission.all().map { permission ->
            PermissionInfo(
                id = permission.id,
                title = permission.title,
                description = permission.description,
                isSpecialPermission = permission.isSpecial,
                isGranted = isPermissionGranted(permission),
                deniedCount = getDeniedCount(permission)
            )
        }
    }

    /**
     * Get status of only required permissions.
     */
    fun getRequiredPermissionStatus(): List<PermissionInfo> {
        return RequiredPermission.allRequired().map { permission ->
            PermissionInfo(
                id = permission.id,
                title = permission.title,
                description = permission.description,
                isSpecialPermission = permission.isSpecial,
                isGranted = isPermissionGranted(permission),
                deniedCount = getDeniedCount(permission)
            )
        }
    }

    /**
     * Check if all required permissions are granted.
     */
    fun areAllRequiredPermissionsGranted(): Boolean {
        return RequiredPermission.allRequired().all { isPermissionGranted(it) }
    }

    /**
     * Get the first missing required permission, if any.
     */
    fun getFirstMissingRequiredPermission(): RequiredPermission? {
        return RequiredPermission.allRequired().firstOrNull { !isPermissionGranted(it) }
    }

    /**
     * Get the next permission to request (required first, then optional).
     */
    fun getNextPermissionToRequest(): RequiredPermission? {
        return getFirstMissingRequiredPermission()
            ?: RequiredPermission.allOptional().firstOrNull { !isPermissionGranted(it) }
    }

    /**
     * Record that a permission was denied by the user.
     */
    fun recordPermissionDenied(permission: RequiredPermission) {
        val key = KEY_DENIED_COUNT_PREFIX + permission.id
        val currentCount = prefs.getInt(key, 0)
        prefs.edit().putInt(key, currentCount + 1).apply()
    }

    /**
     * Get the number of times a permission has been denied.
     */
    fun getDeniedCount(permission: RequiredPermission): Int {
        return prefs.getInt(KEY_DENIED_COUNT_PREFIX + permission.id, 0)
    }

    /**
     * Check if we should show rationale (denied multiple times).
     */
    fun shouldShowRationale(permission: RequiredPermission): Boolean {
        return getDeniedCount(permission) >= 1
    }

    /**
     * Record the timestamp of the last permission check.
     */
    fun recordPermissionCheck() {
        prefs.edit().putLong(KEY_LAST_CHECK_TIMESTAMP, System.currentTimeMillis()).apply()
    }

    /**
     * Get the last permission check timestamp.
     */
    fun getLastCheckTimestamp(): Long {
        return prefs.getLong(KEY_LAST_CHECK_TIMESTAMP, 0)
    }

    /**
     * Save the granted status of a permission (for persistence across restarts).
     */
    fun savePermissionStatus(permission: RequiredPermission, isGranted: Boolean) {
        prefs.edit().putBoolean(KEY_PERMISSION_PREFIX + permission.id, isGranted).apply()
    }

    /**
     * Get the last known granted status of a permission.
     */
    fun getLastKnownStatus(permission: RequiredPermission): Boolean {
        return prefs.getBoolean(KEY_PERMISSION_PREFIX + permission.id, false)
    }

    /**
     * Check if permission status changed since last check.
     */
    fun hasPermissionStatusChanged(permission: RequiredPermission): Boolean {
        val currentStatus = isPermissionGranted(permission)
        val lastKnownStatus = getLastKnownStatus(permission)
        return currentStatus != lastKnownStatus
    }

    /**
     * Update all permission statuses in persistent storage.
     */
    fun updateAllPermissionStatuses() {
        RequiredPermission.all().forEach { permission ->
            savePermissionStatus(permission, isPermissionGranted(permission))
        }
        recordPermissionCheck()
    }

    /**
     * Get an intent to open the overlay permission settings.
     */
    fun getOverlayPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri()
        )
    }

    /**
     * Get an intent to open the notification listener settings.
     */
    fun getNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    /**
     * Get an intent to open app settings.
     */
    fun getAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
    }

    /**
     * Get an intent to request call screening role (Android Q+).
     */
    fun getCallScreeningRoleIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING)
            } catch (e: Exception) {
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            }
        } else {
            null
        }
    }

    /**
     * Get the runtime permissions for a RequiredPermission.
     */
    fun getRuntimePermissions(permission: RequiredPermission): Array<String> {
        return when (permission) {
            RequiredPermission.NOTIFICATION_POST -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyArray()
                }
            }
            RequiredPermission.RECORD_AUDIO -> arrayOf(Manifest.permission.RECORD_AUDIO)
            RequiredPermission.PHONE -> arrayOf(
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS
            )
            RequiredPermission.SMS -> arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            )
            else -> emptyArray() // Special permissions don't use runtime permission API
        }
    }

    /**
     * Check if a permission requires navigating to settings (special permission).
     */
    fun requiresSettingsNavigation(permission: RequiredPermission): Boolean {
        return permission.isSpecial
    }
}
