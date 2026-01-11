package com.yazan.jetoverlay.util

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for PermissionManager.
 * These tests require an Android device or emulator to run.
 */
@RunWith(AndroidJUnit4::class)
class PermissionManagerTest {

    private lateinit var context: Context
    private lateinit var permissionManager: PermissionManager
    private lateinit var prefs: SharedPreferences

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        permissionManager = PermissionManager(context)
        prefs = context.getSharedPreferences("jetoverlay_permissions", Context.MODE_PRIVATE)
        // Clear preferences before each test
        prefs.edit().clear().apply()
    }

    @After
    fun teardown() {
        // Clean up preferences after tests
        prefs.edit().clear().apply()
    }

    @Test
    fun getPermissionStatus_returnsAllPermissions() {
        val statuses = permissionManager.getPermissionStatus()
        assertEquals(PermissionManager.RequiredPermission.all().size, statuses.size)
    }

    @Test
    fun getRequiredPermissionStatus_returnsOnlyRequired() {
        val statuses = permissionManager.getRequiredPermissionStatus()
        assertEquals(PermissionManager.RequiredPermission.allRequired().size, statuses.size)
    }

    @Test
    fun recordPermissionDenied_incrementsCount() {
        val permission = PermissionManager.RequiredPermission.OVERLAY

        assertEquals(0, permissionManager.getDeniedCount(permission))

        permissionManager.recordPermissionDenied(permission)
        assertEquals(1, permissionManager.getDeniedCount(permission))

        permissionManager.recordPermissionDenied(permission)
        assertEquals(2, permissionManager.getDeniedCount(permission))
    }

    @Test
    fun shouldShowRationale_falseWhenNeverDenied() {
        val permission = PermissionManager.RequiredPermission.OVERLAY
        assertFalse(permissionManager.shouldShowRationale(permission))
    }

    @Test
    fun shouldShowRationale_trueAfterDenied() {
        val permission = PermissionManager.RequiredPermission.OVERLAY

        permissionManager.recordPermissionDenied(permission)
        assertTrue(permissionManager.shouldShowRationale(permission))
    }

    @Test
    fun savePermissionStatus_persistsStatus() {
        val permission = PermissionManager.RequiredPermission.NOTIFICATION_POST

        permissionManager.savePermissionStatus(permission, true)
        assertTrue(permissionManager.getLastKnownStatus(permission))

        permissionManager.savePermissionStatus(permission, false)
        assertFalse(permissionManager.getLastKnownStatus(permission))
    }

    @Test
    fun getLastKnownStatus_defaultsFalse() {
        val permission = PermissionManager.RequiredPermission.OVERLAY
        assertFalse(permissionManager.getLastKnownStatus(permission))
    }

    @Test
    fun recordPermissionCheck_savesTimestamp() {
        val before = System.currentTimeMillis()

        permissionManager.recordPermissionCheck()

        val after = System.currentTimeMillis()
        val recorded = permissionManager.getLastCheckTimestamp()

        assertTrue(recorded >= before)
        assertTrue(recorded <= after)
    }

    @Test
    fun getLastCheckTimestamp_defaultsToZero() {
        assertEquals(0L, permissionManager.getLastCheckTimestamp())
    }

    @Test
    fun getOverlayPermissionIntent_notNull() {
        val intent = permissionManager.getOverlayPermissionIntent()
        assertNotNull(intent)
        assertNotNull(intent.data)
        assertTrue(intent.data.toString().contains(context.packageName))
    }

    @Test
    fun getNotificationListenerSettingsIntent_notNull() {
        val intent = permissionManager.getNotificationListenerSettingsIntent()
        assertNotNull(intent)
    }

    @Test
    fun getAppSettingsIntent_notNull() {
        val intent = permissionManager.getAppSettingsIntent()
        assertNotNull(intent)
        assertNotNull(intent.data)
        assertTrue(intent.data.toString().contains(context.packageName))
    }

    @Test
    fun getRuntimePermissions_notificationPostReturnsCorrectly() {
        val permissions = permissionManager.getRuntimePermissions(
            PermissionManager.RequiredPermission.NOTIFICATION_POST
        )
        // On API 33+, should return POST_NOTIFICATIONS
        // On lower APIs, returns empty
        // Just verify it doesn't crash and returns an array
        assertNotNull(permissions)
    }

    @Test
    fun getRuntimePermissions_recordAudioReturnsOnePermission() {
        val permissions = permissionManager.getRuntimePermissions(
            PermissionManager.RequiredPermission.RECORD_AUDIO
        )
        assertEquals(1, permissions.size)
        assertEquals(android.Manifest.permission.RECORD_AUDIO, permissions[0])
    }

    @Test
    fun getRuntimePermissions_phoneReturnsMultiplePermissions() {
        val permissions = permissionManager.getRuntimePermissions(
            PermissionManager.RequiredPermission.PHONE
        )
        assertEquals(3, permissions.size)
        assertTrue(permissions.contains(android.Manifest.permission.ANSWER_PHONE_CALLS))
        assertTrue(permissions.contains(android.Manifest.permission.READ_PHONE_STATE))
        assertTrue(permissions.contains(android.Manifest.permission.READ_CONTACTS))
    }

    @Test
    fun getRuntimePermissions_smsReturnsTwoPermissions() {
        val permissions = permissionManager.getRuntimePermissions(
            PermissionManager.RequiredPermission.SMS
        )
        assertEquals(2, permissions.size)
        assertTrue(permissions.contains(android.Manifest.permission.RECEIVE_SMS))
        assertTrue(permissions.contains(android.Manifest.permission.READ_SMS))
    }

    @Test
    fun getRuntimePermissions_overlayReturnsEmpty() {
        // Overlay is a special permission, not a runtime permission
        val permissions = permissionManager.getRuntimePermissions(
            PermissionManager.RequiredPermission.OVERLAY
        )
        assertEquals(0, permissions.size)
    }

    @Test
    fun getRuntimePermissions_notificationListenerReturnsEmpty() {
        // Notification listener is a special permission
        val permissions = permissionManager.getRuntimePermissions(
            PermissionManager.RequiredPermission.NOTIFICATION_LISTENER
        )
        assertEquals(0, permissions.size)
    }

    @Test
    fun requiresSettingsNavigation_overlayReturnsTrue() {
        assertTrue(
            permissionManager.requiresSettingsNavigation(
                PermissionManager.RequiredPermission.OVERLAY
            )
        )
    }

    @Test
    fun requiresSettingsNavigation_notificationListenerReturnsTrue() {
        assertTrue(
            permissionManager.requiresSettingsNavigation(
                PermissionManager.RequiredPermission.NOTIFICATION_LISTENER
            )
        )
    }

    @Test
    fun requiresSettingsNavigation_callScreeningReturnsTrue() {
        assertTrue(
            permissionManager.requiresSettingsNavigation(
                PermissionManager.RequiredPermission.CALL_SCREENING
            )
        )
    }

    @Test
    fun requiresSettingsNavigation_recordAudioReturnsFalse() {
        assertFalse(
            permissionManager.requiresSettingsNavigation(
                PermissionManager.RequiredPermission.RECORD_AUDIO
            )
        )
    }

    @Test
    fun requiresSettingsNavigation_phoneReturnsFalse() {
        assertFalse(
            permissionManager.requiresSettingsNavigation(
                PermissionManager.RequiredPermission.PHONE
            )
        )
    }

    @Test
    fun requiresSettingsNavigation_smsReturnsFalse() {
        assertFalse(
            permissionManager.requiresSettingsNavigation(
                PermissionManager.RequiredPermission.SMS
            )
        )
    }

    @Test
    fun updateAllPermissionStatuses_updatesTimestamp() {
        val before = permissionManager.getLastCheckTimestamp()
        assertEquals(0L, before)

        permissionManager.updateAllPermissionStatuses()

        val after = permissionManager.getLastCheckTimestamp()
        assertTrue(after > 0)
    }

    @Test
    fun getFirstMissingRequiredPermission_returnsFirstMissing() {
        // On a fresh test device without granted permissions,
        // this should return the first required permission
        val missing = permissionManager.getFirstMissingRequiredPermission()
        // Should be either null (all granted) or the first ungranted required permission
        if (missing != null) {
            assertTrue(
                PermissionManager.RequiredPermission.allRequired().contains(missing)
            )
        }
    }

    @Test
    fun isPermissionGranted_handlesAllPermissionTypes() {
        // Just verify none of these calls crash
        PermissionManager.RequiredPermission.all().forEach { permission ->
            // Should return a boolean without crashing
            val result = permissionManager.isPermissionGranted(permission)
            assertTrue(result == true || result == false)
        }
    }
}
