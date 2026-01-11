package com.yazan.jetoverlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.yazan.jetoverlay.api.OverlayConfig
import com.yazan.jetoverlay.api.OverlaySdk
import com.yazan.jetoverlay.data.AppDatabase
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Utility functions for instrumented tests.
 * Provides helpers for waiting on UI state changes, simulating notifications,
 * and working with test data.
 */
object TestUtils {

    /**
     * Create an in-memory Room database for test isolation.
     * The database is destroyed when the process is killed.
     */
    fun createInMemoryDatabase(context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries() // Allow queries on main thread for testing
            .build()
    }

    /**
     * Wait for a Flow to emit a value matching the predicate.
     * @param flow The Flow to observe
     * @param timeoutMs Maximum time to wait
     * @param predicate Condition to check on emitted values
     * @return The matching value, or null if timeout
     */
    suspend fun <T> awaitFlowCondition(
        flow: Flow<T>,
        timeoutMs: Long = TestConstants.DEFAULT_TIMEOUT_MS,
        predicate: (T) -> Boolean
    ): T? {
        return withTimeoutOrNull(timeoutMs) {
            flow.first { predicate(it) }
        }
    }

    /**
     * Wait for overlay to become active.
     * @param overlayId The overlay ID to check
     * @param timeoutMs Maximum time to wait
     * @return true if overlay became active, false on timeout
     */
    fun waitForOverlayActive(
        overlayId: String = TestConstants.TEST_OVERLAY_ID,
        timeoutMs: Long = TestConstants.DEFAULT_TIMEOUT_MS
    ): Boolean {
        return waitForConditionSync(timeoutMs) {
            OverlaySdk.isOverlayActive(overlayId)
        }
    }

    /**
     * Wait for overlay to become inactive.
     * @param overlayId The overlay ID to check
     * @param timeoutMs Maximum time to wait
     * @return true if overlay became inactive, false on timeout
     */
    fun waitForOverlayInactive(
        overlayId: String = TestConstants.TEST_OVERLAY_ID,
        timeoutMs: Long = TestConstants.DEFAULT_TIMEOUT_MS
    ): Boolean {
        return waitForConditionSync(timeoutMs) {
            !OverlaySdk.isOverlayActive(overlayId)
        }
    }

    /**
     * Synchronous condition waiter with polling.
     */
    fun waitForConditionSync(
        timeoutMs: Long = TestConstants.DEFAULT_TIMEOUT_MS,
        intervalMs: Long = TestConstants.DEFAULT_POLL_INTERVAL_MS,
        condition: () -> Boolean
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) return true
            Thread.sleep(intervalMs)
        }
        return false
    }

    /**
     * Show an overlay for testing purposes.
     * @param context Application context
     * @param overlayId The overlay ID
     * @param type The overlay type (must be registered)
     * @return true if overlay was shown successfully
     */
    fun showTestOverlay(
        context: Context,
        overlayId: String = TestConstants.TEST_OVERLAY_ID,
        type: String = TestConstants.TEST_OVERLAY_ID
    ): Boolean {
        return try {
            val config = OverlayConfig(
                id = overlayId,
                type = type
            )
            OverlaySdk.show(context, config)
            waitForOverlayActive(overlayId)
        } catch (e: Exception) {
            android.util.Log.e("TestUtils", "Failed to show overlay", e)
            false
        }
    }

    /**
     * Hide an overlay and wait for it to become inactive.
     * @param overlayId The overlay ID to hide
     * @return true if overlay was hidden successfully
     */
    fun hideTestOverlay(
        overlayId: String = TestConstants.TEST_OVERLAY_ID
    ): Boolean {
        return try {
            OverlaySdk.hide(overlayId)
            waitForOverlayInactive(overlayId)
        } catch (e: Exception) {
            android.util.Log.e("TestUtils", "Failed to hide overlay", e)
            false
        }
    }

    /**
     * Create a test Message object with default values.
     */
    fun createTestMessage(
        id: Long = 0,
        packageName: String = TestConstants.TestMessages.PACKAGE_NAME,
        senderName: String = TestConstants.TestMessages.SENDER_NAME,
        originalContent: String = TestConstants.TestMessages.ORIGINAL_CONTENT,
        veiledContent: String? = null,
        status: String = "RECEIVED",
        generatedResponses: List<String> = emptyList(),
        selectedResponse: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Message {
        return Message(
            id = id,
            packageName = packageName,
            senderName = senderName,
            originalContent = originalContent,
            veiledContent = veiledContent,
            status = status,
            generatedResponses = generatedResponses,
            selectedResponse = selectedResponse,
            timestamp = timestamp
        )
    }

    /**
     * Insert multiple test messages into the database.
     */
    suspend fun insertTestMessages(
        dao: MessageDao,
        count: Int,
        packageName: String = TestConstants.TestMessages.PACKAGE_NAME
    ): List<Long> {
        return (1..count).map { index ->
            val message = createTestMessage(
                packageName = packageName,
                senderName = "Sender $index",
                originalContent = "Test message $index",
                timestamp = System.currentTimeMillis() - (count - index) * 1000
            )
            dao.insert(message)
        }
    }

    /**
     * Post a test notification.
     * Note: This posts a notification from the test app itself.
     * It won't trigger NotificationListenerService unless the test app
     * is also listening to its own notifications.
     *
     * For full notification testing, use adb commands or a separate test helper app.
     */
    fun postTestNotification(
        context: Context,
        title: String = "Test Notification",
        content: String = "This is a test notification",
        notificationId: Int = Random.nextInt()
    ): Int {
        val channelId = "test_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        // Create notification channel (required for Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Test Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
        return notificationId
    }

    /**
     * Cancel a test notification.
     */
    fun cancelTestNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        notificationManager.cancel(notificationId)
    }

    /**
     * Retry a block with exponential backoff.
     * Useful for flaky UI operations.
     */
    inline fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 100,
        maxDelayMs: Long = 2000,
        block: () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(maxRetries - 1) { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                android.util.Log.w("TestUtils", "Attempt ${attempt + 1} failed: ${e.message}")
                Thread.sleep(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
            }
        }
        return block() // Last attempt - let it throw if it fails
    }

    /**
     * Run a block with a timeout, throwing if the timeout is exceeded.
     */
    fun <T> runWithTimeout(
        timeoutMs: Long = TestConstants.DEFAULT_TIMEOUT_MS,
        block: () -> T
    ): T {
        val latch = CountDownLatch(1)
        var result: T? = null
        var exception: Throwable? = null

        Thread {
            try {
                result = block()
            } catch (e: Throwable) {
                exception = e
            } finally {
                latch.countDown()
            }
        }.start()

        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw AssertionError("Operation timed out after ${timeoutMs}ms")
        }

        exception?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}

/**
 * Extension function to run a suspend function in a blocking test.
 * Convenience wrapper around runBlocking with timeout.
 */
fun <T> runBlockingTest(
    timeoutMs: Long = TestConstants.DEFAULT_TIMEOUT_MS,
    block: suspend () -> T
): T = runBlocking {
    withTimeoutOrNull(timeoutMs) {
        block()
    } ?: throw AssertionError("Test timed out after ${timeoutMs}ms")
}
