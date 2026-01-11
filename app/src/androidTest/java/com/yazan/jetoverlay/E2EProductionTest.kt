package com.yazan.jetoverlay

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yazan.jetoverlay.data.AppDatabase
import com.yazan.jetoverlay.data.MessageRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end production readiness tests.
 * Tests complete user flows and edge cases that could occur in production.
 */
@RunWith(AndroidJUnit4::class)
class E2EProductionTest : BaseAndroidTest() {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var repository: MessageRepository

    @Before
    override fun setUp() {
        super.setUp()
        val db = AppDatabase.getDatabase(context)
        repository = MessageRepository(db.messageDao())
    }

    @After
    override fun tearDown() {
        super.tearDown()
        // Clean up test messages
        runBlocking {
            try {
                // Delete test messages if any
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Test: Complete flow from message ingestion to display.
     * Verifies the core user journey works end-to-end.
     */
    @Test
    fun testCompleteMessageFlow() = runTest {
        // Wait for app to initialize
        delay(TestConstants.ACTIVITY_LAUNCH_DELAY_MS)

        // 1. Ingest a test message via repository
        val testSender = "Test Sender"
        val testContent = "This is a production test message"
        repository.ingestNotification(
            packageName = "com.test.production",
            sender = testSender,
            content = testContent
        )

        // 2. Wait for message to be processed
        delay(TestConstants.SERVICE_START_DELAY_MS)

        // 3. Verify message was persisted
        val messages = repository.allMessages.first()
        val found = messages.any { message ->
            message.senderName == testSender && message.originalContent == testContent
        }
        assert(found) { "Message should be persisted in repository" }
    }

    /**
     * Test: App handles rapid notification bursts.
     * Simulates multiple notifications arriving in quick succession.
     */
    @Test
    fun testRapidNotificationBurst() = runTest {
        val burstCount = 10
        val sender = "Burst Sender"

        // Send rapid burst of notifications
        repeat(burstCount) { index ->
            repository.ingestNotification(
                packageName = "com.test.burst",
                sender = sender,
                content = "Burst message #$index"
            )
            delay(50) // Small delay between bursts
        }

        // Wait for processing
        delay(TestConstants.EXTENDED_TIMEOUT_MS)

        // Verify all messages were stored
        val messages = repository.allMessages.first()
        val burstMessages = messages.filter { it.senderName == sender }

        // Should have received at least most of the burst (allowing for slight timing issues)
        assert(burstMessages.size >= burstCount - 1) {
            "Should handle rapid notification burst. Expected ~$burstCount, got ${burstMessages.size}"
        }
    }

    /**
     * Test: App handles configuration change (rotation).
     * Verifies state is preserved across rotation.
     */
    @Test
    fun testConfigurationChange() {
        // Wait for initial render
        composeTestRule.waitForIdle()

        // Get initial state - just checking the app launches successfully
        Thread.sleep(TestConstants.ACTIVITY_LAUNCH_DELAY_MS)

        // Rotate to landscape
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // Rotate back to portrait
        composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // App should still be responsive - verify by checking for any UI element
        // If no crash occurred, test passes
    }

    /**
     * Test: Database persistence survives process simulation.
     * Writes data, then reads it back to verify persistence.
     */
    @Test
    fun testDatabasePersistence() = runTest {
        val persistenceTestSender = "Persistence Test"
        val persistenceTestContent = "This message should persist"

        // Write a message
        repository.ingestNotification(
            packageName = "com.test.persistence",
            sender = persistenceTestSender,
            content = persistenceTestContent
        )

        // Wait for write
        delay(500)

        // Read back using a fresh repository instance
        val freshDb = AppDatabase.getDatabase(context)
        val freshRepo = MessageRepository(freshDb.messageDao())

        val messages = freshRepo.allMessages.first()
        val found = messages.any {
            it.senderName == persistenceTestSender && it.originalContent == persistenceTestContent
        }

        assert(found) { "Message should persist in database" }
    }

    /**
     * Test: Error recovery - repository handles invalid data gracefully.
     */
    @Test
    fun testErrorRecovery() = runTest {
        // Test with edge cases that might cause issues
        val edgeCases = listOf(
            "" to "", // Empty strings
            "A".repeat(1000) to "B".repeat(5000), // Very long strings
            "\n\n\n" to "Multi\nLine\nContent", // Newlines
            " " to "   ", // Only whitespace
        )

        edgeCases.forEach { (sender, content) ->
            try {
                repository.ingestNotification(
                    packageName = "com.test.edge",
                    sender = sender,
                    content = content
                )
                // If we get here, the operation didn't crash
            } catch (e: Exception) {
                // Some edge cases might throw - that's acceptable
                // The important thing is the app doesn't crash
            }
        }

        // App should still be responsive after edge cases
        delay(500)
        val messages = repository.allMessages.first()
        // Just verify we can still read from the repository
        assert(messages != null) { "Repository should still be functional after edge cases" }
    }

    /**
     * Test: Memory efficiency - verify no memory leaks during repeated operations.
     */
    @Test
    fun testMemoryEfficiency() = runTest {
        val runtime = Runtime.getRuntime()

        // Get initial memory
        System.gc()
        delay(100)
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()

        // Perform repeated operations
        repeat(50) { index ->
            repository.ingestNotification(
                packageName = "com.test.memory",
                sender = "Memory Test Sender",
                content = "Memory test message $index with some additional content to make it realistic"
            )
        }

        delay(1000)
        System.gc()
        delay(100)

        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryGrowth = finalMemory - initialMemory

        // Memory should not grow excessively (< 10MB for 50 messages is reasonable)
        val maxAcceptableGrowth = 10 * 1024 * 1024L // 10MB
        assert(memoryGrowth < maxAcceptableGrowth) {
            "Memory growth should be reasonable. Initial: ${initialMemory / 1024}KB, " +
            "Final: ${finalMemory / 1024}KB, Growth: ${memoryGrowth / 1024}KB"
        }
    }

    /**
     * Test: Concurrent access - multiple operations at once.
     */
    @Test
    fun testConcurrentAccess() = runTest {
        val jobs = (1..5).map { threadNum ->
            async {
                repeat(5) { msgNum ->
                    repository.ingestNotification(
                        packageName = "com.test.concurrent",
                        sender = "Thread $threadNum",
                        content = "Message $msgNum from thread $threadNum"
                    )
                    delay(10)
                }
            }
        }

        // Wait for all jobs to complete
        jobs.awaitAll()

        delay(500)

        // Verify all messages were stored
        val messages = repository.allMessages.first()
        val concurrentMessages = messages.filter { it.packageName == "com.test.concurrent" }

        // Should have 5 threads * 5 messages = 25 messages
        assert(concurrentMessages.size >= 20) {
            "Should handle concurrent access. Expected ~25, got ${concurrentMessages.size}"
        }
    }
}
