package com.yazan.jetoverlay.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for PermissionManager.RequiredPermission enum and utility methods.
 * Note: Full integration tests require Android context and are in androidTest.
 */
class PermissionManagerTest {

    @Test
    fun requiredPermission_allRequired_returnsCorrectCount() {
        val required = PermissionManager.RequiredPermission.allRequired()
        assertEquals(3, required.size)
    }

    @Test
    fun requiredPermission_allRequired_containsOverlay() {
        val required = PermissionManager.RequiredPermission.allRequired()
        assertTrue(required.contains(PermissionManager.RequiredPermission.OVERLAY))
    }

    @Test
    fun requiredPermission_allRequired_containsNotificationPost() {
        val required = PermissionManager.RequiredPermission.allRequired()
        assertTrue(required.contains(PermissionManager.RequiredPermission.NOTIFICATION_POST))
    }

    @Test
    fun requiredPermission_allRequired_containsNotificationListener() {
        val required = PermissionManager.RequiredPermission.allRequired()
        assertTrue(required.contains(PermissionManager.RequiredPermission.NOTIFICATION_LISTENER))
    }

    @Test
    fun requiredPermission_allOptional_returnsCorrectCount() {
        val optional = PermissionManager.RequiredPermission.allOptional()
        assertEquals(4, optional.size)
    }

    @Test
    fun requiredPermission_allOptional_containsRecordAudio() {
        val optional = PermissionManager.RequiredPermission.allOptional()
        assertTrue(optional.contains(PermissionManager.RequiredPermission.RECORD_AUDIO))
    }

    @Test
    fun requiredPermission_allOptional_containsPhone() {
        val optional = PermissionManager.RequiredPermission.allOptional()
        assertTrue(optional.contains(PermissionManager.RequiredPermission.PHONE))
    }

    @Test
    fun requiredPermission_allOptional_containsCallScreening() {
        val optional = PermissionManager.RequiredPermission.allOptional()
        assertTrue(optional.contains(PermissionManager.RequiredPermission.CALL_SCREENING))
    }

    @Test
    fun requiredPermission_allOptional_containsSms() {
        val optional = PermissionManager.RequiredPermission.allOptional()
        assertTrue(optional.contains(PermissionManager.RequiredPermission.SMS))
    }

    @Test
    fun requiredPermission_all_returnsCombinedList() {
        val all = PermissionManager.RequiredPermission.all()
        val required = PermissionManager.RequiredPermission.allRequired()
        val optional = PermissionManager.RequiredPermission.allOptional()

        assertEquals(required.size + optional.size, all.size)
        assertTrue(all.containsAll(required))
        assertTrue(all.containsAll(optional))
    }

    @Test
    fun requiredPermission_all_requiredComesFirst() {
        val all = PermissionManager.RequiredPermission.all()
        val required = PermissionManager.RequiredPermission.allRequired()

        // First N items should be the required permissions
        val firstN = all.take(required.size)
        assertEquals(required, firstN)
    }

    @Test
    fun requiredPermission_overlay_isSpecial() {
        assertTrue(PermissionManager.RequiredPermission.OVERLAY.isSpecial)
    }

    @Test
    fun requiredPermission_notificationPost_isNotSpecial() {
        assertFalse(PermissionManager.RequiredPermission.NOTIFICATION_POST.isSpecial)
    }

    @Test
    fun requiredPermission_notificationListener_isSpecial() {
        assertTrue(PermissionManager.RequiredPermission.NOTIFICATION_LISTENER.isSpecial)
    }

    @Test
    fun requiredPermission_recordAudio_isNotSpecial() {
        assertFalse(PermissionManager.RequiredPermission.RECORD_AUDIO.isSpecial)
    }

    @Test
    fun requiredPermission_phone_isNotSpecial() {
        assertFalse(PermissionManager.RequiredPermission.PHONE.isSpecial)
    }

    @Test
    fun requiredPermission_callScreening_isSpecial() {
        assertTrue(PermissionManager.RequiredPermission.CALL_SCREENING.isSpecial)
    }

    @Test
    fun requiredPermission_sms_isNotSpecial() {
        assertFalse(PermissionManager.RequiredPermission.SMS.isSpecial)
    }

    @Test
    fun requiredPermission_hasUniqueIds() {
        val all = PermissionManager.RequiredPermission.all()
        val ids = all.map { it.id }
        val uniqueIds = ids.toSet()
        assertEquals(ids.size, uniqueIds.size)
    }

    @Test
    fun requiredPermission_hasTitles() {
        val all = PermissionManager.RequiredPermission.all()
        all.forEach { permission ->
            assertNotNull(permission.title)
            assertTrue(permission.title.isNotEmpty())
        }
    }

    @Test
    fun requiredPermission_hasDescriptions() {
        val all = PermissionManager.RequiredPermission.all()
        all.forEach { permission ->
            assertNotNull(permission.description)
            assertTrue(permission.description.isNotEmpty())
        }
    }

    @Test
    fun permissionInfo_dataClassProperties() {
        val info = PermissionManager.PermissionInfo(
            id = "test_id",
            title = "Test Title",
            description = "Test Description",
            isSpecialPermission = true,
            isGranted = false,
            deniedCount = 2
        )

        assertEquals("test_id", info.id)
        assertEquals("Test Title", info.title)
        assertEquals("Test Description", info.description)
        assertTrue(info.isSpecialPermission)
        assertFalse(info.isGranted)
        assertEquals(2, info.deniedCount)
    }

    @Test
    fun permissionInfo_defaultValues() {
        val info = PermissionManager.PermissionInfo(
            id = "test",
            title = "Test",
            description = "Desc"
        )

        assertFalse(info.isSpecialPermission)
        assertFalse(info.isGranted)
        assertEquals(0, info.deniedCount)
    }

    @Test
    fun permissionInfo_copy() {
        val original = PermissionManager.PermissionInfo(
            id = "test",
            title = "Test",
            description = "Desc",
            isGranted = false
        )

        val modified = original.copy(isGranted = true)

        assertFalse(original.isGranted)
        assertTrue(modified.isGranted)
        assertEquals(original.id, modified.id)
    }
}
