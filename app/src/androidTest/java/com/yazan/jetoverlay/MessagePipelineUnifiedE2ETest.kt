package com.yazan.jetoverlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.data.ReplyActionCache
import com.yazan.jetoverlay.domain.LlmService
import com.yazan.jetoverlay.domain.MessageBucket
import com.yazan.jetoverlay.domain.MessageProcessor
import com.yazan.jetoverlay.ui.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessagePipelineUnifiedE2ETest : BaseAndroidTest() {

    @After
    override fun tearDown() {
        ReplyActionCache.clear()
        super.tearDown()
    }

    @Test
    fun notification_ingestion_caches_reply_and_cancels() {
        assumeTrue("Notification listener not enabled", isNotificationListenerEnabled())

        val channelId = "e2e_test_channel"
        val notificationId = 9001
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "E2E Test", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val replyIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent("com.yazan.jetoverlay.TEST_REPLY"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val remoteInput = RemoteInput.Builder("reply_key")
            .setLabel("Reply")
            .build()

        val markReadIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent("com.yazan.jetoverlay.TEST_MARK_READ"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("E2E Sender")
            .setContentText("E2E message content")
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    "Reply",
                    replyIntent
                ).addRemoteInput(remoteInput).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_delete,
                    "Mark read",
                    markReadIntent
                ).build()
            )
            .build()

        SettingsManager.setCancelNotificationsEnabled(context, true)
        manager.notify(notificationId, notification)

        val repository = JetOverlayApplication.instance.repository
        val message = runBlocking {
            TestUtils.awaitFlowCondition(
                flow = repository.allMessages,
                predicate = { messages ->
                    messages.any { it.originalContent.contains("E2E message content") }
                }
            )
        }

        assertNotNull("Message should be ingested from notification", message)
        val ingested = message!!.first { it.originalContent.contains("E2E message content") }

        val replyCached = TestUtils.waitForConditionSync(TestConstants.EXTENDED_TIMEOUT_MS) {
            ReplyActionCache.hasReplyAction(ingested.id)
        }
        assertTrue("Reply action should be cached", replyCached)

        val markReadCached = ReplyActionCache.getMarkAsRead(ingested.id)
        assertNotNull("Mark-as-read action should be cached", markReadCached)

        val canceled = TestUtils.waitForConditionSync(TestConstants.EXTENDED_TIMEOUT_MS) {
            manager.activeNotifications.none { it.id == notificationId }
        }
        assertTrue("Notification should be canceled after ingestion", canceled)
    }

    @Test
    fun snooze_reappears_after_expiry_simulated() = runBlocking {
        val db = TestUtils.createInMemoryDatabase(context)
        val repository = MessageRepository(db.messageDao())

        val messageId = db.messageDao().insert(
            Message(
                packageName = "com.test.app",
                senderName = "Tester",
                originalContent = "Snooze test",
                status = "PROCESSED"
            )
        )

        val snoozeUntil = System.currentTimeMillis() + (20 * 60 * 1000L)
        repository.snoozeMessage(messageId, snoozeUntil)

        val hiddenWhileSnoozed = TestUtils.waitForConditionSync(TestConstants.SHORT_TIMEOUT_MS) {
            runBlocking {
                val messages = repository.allMessages.first()
                val now = System.currentTimeMillis()
                messages.none { it.id == messageId && (it.snoozedUntil == 0L || it.snoozedUntil <= now) }
            }
        }
        assertTrue("Message should be hidden while snoozed", hiddenWhileSnoozed)

        repository.updateMessageState(
            id = messageId,
            status = "PROCESSED",
            snoozedUntil = System.currentTimeMillis() - 1000L
        )

        val visibleAfterExpiry = TestUtils.waitForConditionSync(TestConstants.SHORT_TIMEOUT_MS) {
            runBlocking {
                val messages = repository.allMessages.first()
                val now = System.currentTimeMillis()
                messages.any { it.id == messageId && (it.snoozedUntil == 0L || it.snoozedUntil <= now) }
            }
        }
        assertTrue("Message should reappear after snooze expiry", visibleAfterExpiry)

        db.close()
    }

    @Test
    fun retry_queue_kicks_in_on_send_failure() = runBlocking {
        SettingsManager.setAutomationEnabled(context, true)

        val db = TestUtils.createInMemoryDatabase(context)
        val repository = MessageRepository(db.messageDao())
        val fakeLlm = object : LlmService {
            override suspend fun generateResponses(
                message: Message,
                bucket: MessageBucket
            ): List<String> = listOf("OK", "Got it")

            override suspend fun closeSession(messageId: Long) = Unit
        }

        val processor = MessageProcessor(
            repository = repository,
            context = context,
            llmService = fakeLlm
        )
        processor.start()

        val messageId = db.messageDao().insert(
            Message(
                packageName = "com.unknown.app",
                senderName = "Unknown",
                originalContent = "Retry test",
                status = "RECEIVED",
                timestamp = System.currentTimeMillis() - 6000L
            )
        )

        val retried = TestUtils.waitForConditionSync(TestConstants.EXTENDED_TIMEOUT_MS) {
            runBlocking {
                val message = repository.getMessage(messageId)
                message != null &&
                    message.status == "RETRY" &&
                    message.retryCount >= 1 &&
                    message.snoozedUntil > System.currentTimeMillis()
            }
        }
        assertTrue("Message should be queued for retry after send failure", retried)

        processor.stop()
        db.close()
        SettingsManager.setAutomationEnabled(context, false)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
        ) ?: return false
        return enabled.contains(context.packageName)
    }
}
