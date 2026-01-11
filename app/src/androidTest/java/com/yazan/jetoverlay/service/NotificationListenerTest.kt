package com.yazan.jetoverlay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yazan.jetoverlay.BaseAndroidTest
import com.yazan.jetoverlay.TestUtils
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.data.NotificationConfigManager
import com.yazan.jetoverlay.runBlockingTest
import com.yazan.jetoverlay.service.notification.MessageNotificationFilter
import com.yazan.jetoverlay.service.notification.NotificationMapper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Instrumented tests for NotificationListener and related notification processing components.
 * Tests the notification ingestion pipeline from filtering to database storage.
 *
 * Note: Full notification listener testing requires:
 * 1. The app to have NotificationListenerService permission (granted via Settings)
 * 2. A separate app or shell commands to send test notifications
 *
 * These tests focus on the testable components: filtering, mapping, config, and database integration.
 */
@RunWith(AndroidJUnit4::class)
class NotificationListenerTest : BaseAndroidTest() {

    private lateinit var repository: MessageRepository
    private lateinit var testDb: com.yazan.jetoverlay.data.AppDatabase
    private lateinit var notificationManager: NotificationManager
    private val filter = MessageNotificationFilter()
    private val mapper = NotificationMapper()

    companion object {
        private const val TEST_CHANNEL_ID = "test_notification_channel"
        private const val TEST_CHANNEL_NAME = "Test Notifications"
    }

    @Before
    override fun setUp() {
        super.setUp()
        testDb = TestUtils.createInMemoryDatabase(context)
        repository = MessageRepository(testDb.messageDao())
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TEST_CHANNEL_ID,
                TEST_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Reset NotificationConfigManager to default state
        NotificationConfigManager.removeConfig("com.test.app")
        NotificationConfigManager.removeConfig("com.whatsapp")
    }

    @After
    override fun tearDown() {
        super.tearDown()
        testDb.close()
        // Clean up notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(TEST_CHANNEL_ID)
        }
    }

    // ==================== NotificationConfigManager Tests ====================

    @Test
    fun notificationConfig_defaultConfig_veilsAndCancels() {
        val config = NotificationConfigManager.getConfig("com.random.app")

        assertTrue("Default should veil", config.shouldVeil)
        assertTrue("Default should cancel", config.shouldCancel)
    }

    @Test
    fun notificationConfig_systemApps_neverVeiledOrCancelled() {
        val systemPackages = listOf(
            "android",
            "com.android.systemui",
            "com.android.providers.downloads"
        )

        for (packageName in systemPackages) {
            val config = NotificationConfigManager.getConfig(packageName)

            assertFalse("System app $packageName should not be veiled", config.shouldVeil)
            assertFalse("System app $packageName should not be cancelled", config.shouldCancel)
        }
    }

    @Test
    fun notificationConfig_customConfig_overridesDefault() {
        NotificationConfigManager.setConfig(
            packageName = "com.custom.app",
            shouldVeil = false,
            shouldCancel = true
        )

        val config = NotificationConfigManager.getConfig("com.custom.app")

        assertFalse("Custom config should not veil", config.shouldVeil)
        assertTrue("Custom config should cancel", config.shouldCancel)

        // Clean up
        NotificationConfigManager.removeConfig("com.custom.app")
    }

    @Test
    fun notificationConfig_removeConfig_revertsToDefault() {
        NotificationConfigManager.setConfig(
            packageName = "com.temporary.app",
            shouldVeil = false,
            shouldCancel = false
        )

        NotificationConfigManager.removeConfig("com.temporary.app")

        val config = NotificationConfigManager.getConfig("com.temporary.app")

        assertTrue("After removal should use default veil", config.shouldVeil)
        assertTrue("After removal should use default cancel", config.shouldCancel)
    }

    @Test
    fun notificationConfig_shouldVeil_helperMethod() {
        assertTrue(NotificationConfigManager.shouldVeil("com.any.app"))
        assertFalse(NotificationConfigManager.shouldVeil("android"))
    }

    @Test
    fun notificationConfig_shouldCancel_helperMethod() {
        assertTrue(NotificationConfigManager.shouldCancel("com.any.app"))
        assertFalse(NotificationConfigManager.shouldCancel("com.android.systemui"))
    }

    // ==================== NotificationFilter Tests ====================

    @Test
    fun filter_normalNotification_shouldProcess() {
        val sbn = createMockStatusBarNotification(
            packageName = "com.whatsapp",
            title = "John Doe",
            text = "Hello there!",
            flags = 0
        )

        assertTrue("Normal notification should be processed", filter.shouldProcess(sbn))
    }

    @Test
    fun filter_ongoingNotification_shouldNotProcess() {
        val sbn = createMockStatusBarNotification(
            packageName = "com.spotify.music",
            title = "Now Playing",
            text = "Song Title",
            flags = Notification.FLAG_ONGOING_EVENT
        )

        assertFalse("Ongoing notification should be filtered", filter.shouldProcess(sbn))
    }

    @Test
    fun filter_foregroundServiceNotification_shouldNotProcess() {
        val sbn = createMockStatusBarNotification(
            packageName = "com.example.service",
            title = "Service Running",
            text = "Background process active",
            flags = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE
        )

        assertFalse("Foreground service notification should be filtered", filter.shouldProcess(sbn))
    }

    @Test
    fun filter_noClearNotification_shouldProcess() {
        // NO_CLEAR flag is different from ONGOING_EVENT
        val sbn = createMockStatusBarNotification(
            packageName = "com.messaging.app",
            title = "New Message",
            text = "Message content",
            flags = Notification.FLAG_NO_CLEAR
        )

        assertTrue("NO_CLEAR notification should still be processed", filter.shouldProcess(sbn))
    }

    // ==================== NotificationMapper Tests ====================

    @Test
    fun mapper_validNotification_extractsCorrectly() {
        val sbn = createMockStatusBarNotification(
            packageName = "com.whatsapp",
            title = "Jane Doe",
            text = "How are you?"
        )

        val message = mapper.map(sbn)

        assertNotNull("Mapper should return a message", message)
        assertEquals("com.whatsapp", message?.packageName)
        assertEquals("Jane Doe", message?.senderName)
        assertEquals("How are you?", message?.originalContent)
        assertEquals("RECEIVED", message?.status)
    }

    @Test
    fun mapper_notificationWithoutTitle_returnsNull() {
        val sbn = createMockStatusBarNotification(
            packageName = "com.app",
            title = null,
            text = "Some text"
        )

        val message = mapper.map(sbn)

        assertNull("Notification without title should return null", message)
    }

    @Test
    fun mapper_notificationWithoutText_returnsNull() {
        val sbn = createMockStatusBarNotification(
            packageName = "com.app",
            title = "Title",
            text = null
        )

        val message = mapper.map(sbn)

        assertNull("Notification without text should return null", message)
    }

    @Test
    fun mapper_specialCharactersInContent_preserved() {
        val sbn = createMockStatusBarNotification(
            packageName = "com.messenger",
            title = "User 日本語",
            text = "Message with emoji 🎉 and unicode"
        )

        val message = mapper.map(sbn)

        assertNotNull(message)
        assertEquals("User 日本語", message?.senderName)
        assertEquals("Message with emoji 🎉 and unicode", message?.originalContent)
    }

    // ==================== Repository Integration Tests ====================

    @Test
    fun repository_ingestsNotification_correctly() = runBlockingTest {
        val messageId = repository.ingestNotification(
            packageName = "com.whatsapp",
            sender = "Test User",
            content = "Test notification content"
        )

        assertTrue("Message ID should be positive", messageId > 0)

        val messages = repository.allMessages.first()
        val storedMessage = messages.find { it.id == messageId }

        assertNotNull("Message should be in database", storedMessage)
        assertEquals("com.whatsapp", storedMessage?.packageName)
        assertEquals("Test User", storedMessage?.senderName)
        assertEquals("Test notification content", storedMessage?.originalContent)
        assertEquals("RECEIVED", storedMessage?.status)
    }

    @Test
    fun repository_multipleNotifications_allStored() = runBlockingTest {
        val packages = listOf("com.whatsapp", "com.telegram", "com.signal")
        val ids = mutableListOf<Long>()

        for (pkg in packages) {
            val id = repository.ingestNotification(
                packageName = pkg,
                sender = "Sender from $pkg",
                content = "Message from $pkg"
            )
            ids.add(id)
        }

        val messages = repository.allMessages.first()

        assertEquals(3, messages.size)
        for ((index, pkg) in packages.withIndex()) {
            val message = messages.find { it.id == ids[index] }
            assertNotNull("Message from $pkg should exist", message)
            assertEquals(pkg, message?.packageName)
        }
    }

    @Test
    fun repository_handlesLongContent_correctly() = runBlockingTest {
        val longContent = "A".repeat(5000)

        val messageId = repository.ingestNotification(
            packageName = "com.test.app",
            sender = "Sender",
            content = longContent
        )

        val messages = repository.allMessages.first()
        val storedMessage = messages.find { it.id == messageId }

        assertNotNull(storedMessage)
        assertEquals(longContent, storedMessage?.originalContent)
    }

    @Test
    fun repository_handlesEmptyContent_correctly() = runBlockingTest {
        val messageId = repository.ingestNotification(
            packageName = "com.test.app",
            sender = "Sender",
            content = ""
        )

        val messages = repository.allMessages.first()
        val storedMessage = messages.find { it.id == messageId }

        assertNotNull(storedMessage)
        assertEquals("", storedMessage?.originalContent)
    }

    // ==================== End-to-End Pipeline Tests ====================

    @Test
    fun pipeline_filterThenMap_worksCorrectly() {
        val sbn = createMockStatusBarNotification(
            packageName = "com.messaging.app",
            title = "Contact Name",
            text = "Hello!",
            flags = 0
        )

        // Step 1: Filter
        val shouldProcess = filter.shouldProcess(sbn)
        assertTrue("Should pass filter", shouldProcess)

        // Step 2: Map
        val message = mapper.map(sbn)
        assertNotNull("Should map to message", message)
        assertEquals("Contact Name", message?.senderName)
        assertEquals("Hello!", message?.originalContent)
    }

    @Test
    fun pipeline_filterThenMapThenIngest_worksCorrectly() = runBlockingTest {
        val sbn = createMockStatusBarNotification(
            packageName = "com.test.messenger",
            title = "Friend",
            text = "Hey, how's it going?",
            flags = 0
        )

        // Step 1: Filter
        assertTrue(filter.shouldProcess(sbn))

        // Step 2: Map
        val message = mapper.map(sbn)
        assertNotNull(message)

        // Step 3: Ingest
        val messageId = repository.ingestNotification(
            packageName = message!!.packageName,
            sender = message.senderName,
            content = message.originalContent
        )

        // Verify
        val messages = repository.allMessages.first()
        val storedMessage = messages.find { it.id == messageId }

        assertNotNull(storedMessage)
        assertEquals("com.test.messenger", storedMessage?.packageName)
        assertEquals("Friend", storedMessage?.senderName)
        assertEquals("Hey, how's it going?", storedMessage?.originalContent)
    }

    @Test
    fun pipeline_ongoingNotification_filteredOut() {
        val sbn = createMockStatusBarNotification(
            packageName = "com.music.player",
            title = "Now Playing",
            text = "Song Name - Artist",
            flags = Notification.FLAG_ONGOING_EVENT
        )

        // Filter should reject
        assertFalse(filter.shouldProcess(sbn))

        // Mapper would still work but we don't call it due to filter
        // This simulates the real flow where rejected notifications are ignored
    }

    @Test
    fun pipeline_configuredApp_respectsSettings() {
        // Configure an app to not be veiled
        NotificationConfigManager.setConfig(
            packageName = "com.trusted.app",
            shouldVeil = false,
            shouldCancel = false
        )

        val config = NotificationConfigManager.getConfig("com.trusted.app")

        assertFalse("Configured app should not be veiled", config.shouldVeil)
        assertFalse("Configured app should not be cancelled", config.shouldCancel)

        // Clean up
        NotificationConfigManager.removeConfig("com.trusted.app")
    }

    // ==================== Notification Cancellation Tests ====================
    // Note: Actually cancelling notifications requires the NotificationListenerService
    // to be enabled and active. These tests verify the configuration logic.

    @Test
    fun cancellation_defaultBehavior_shouldCancel() {
        val config = NotificationConfigManager.getConfig("com.any.messaging.app")
        assertTrue("Default should cancel notifications", config.shouldCancel)
    }

    @Test
    fun cancellation_systemApp_shouldNotCancel() {
        val config = NotificationConfigManager.getConfig("android")
        assertFalse("System apps should not have notifications cancelled", config.shouldCancel)
    }

    @Test
    fun cancellation_customConfig_canDisable() {
        NotificationConfigManager.setConfig(
            packageName = "com.important.app",
            shouldVeil = true,
            shouldCancel = false  // Don't cancel, just veil
        )

        val config = NotificationConfigManager.getConfig("com.important.app")

        assertTrue("Should still veil", config.shouldVeil)
        assertFalse("Should not cancel", config.shouldCancel)

        // Clean up
        NotificationConfigManager.removeConfig("com.important.app")
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a mock StatusBarNotification for testing.
     * Uses MockK to create a properly structured mock since StatusBarNotification
     * cannot be directly instantiated with a simple constructor.
     */
    private fun createMockStatusBarNotification(
        packageName: String,
        title: String?,
        text: String?,
        flags: Int = 0
    ): StatusBarNotification {
        // Create a real notification using the builder
        val notificationBuilder = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        title?.let { notificationBuilder.setContentTitle(it) }
        text?.let { notificationBuilder.setContentText(it) }

        val notification = notificationBuilder.build()
        notification.flags = flags

        // Create MockK mock for StatusBarNotification
        val sbn = mockk<StatusBarNotification>(relaxed = true)
        every { sbn.packageName } returns packageName
        every { sbn.notification } returns notification
        every { sbn.id } returns Random.nextInt()
        every { sbn.key } returns "$packageName|${Random.nextInt()}"
        every { sbn.isOngoing } returns ((flags and Notification.FLAG_ONGOING_EVENT) != 0)

        return sbn
    }
}
