package com.yazan.jetoverlay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yazan.jetoverlay.api.OverlayConfig
import com.yazan.jetoverlay.api.OverlaySdk
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.domain.LlmService
import com.yazan.jetoverlay.domain.MessageBucket
import com.yazan.jetoverlay.domain.MessageProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessagePipelineE2ETest : BaseAndroidTest() {

    @Test
    fun overlay_crash_guard_handles_throwing_content() {
        // Requires overlay permission on device
        if (!hasOverlayPermission()) {
            val granted = grantOverlayPermissionViaShell()
            assumeTrue("Overlay permission required for crash guard test", granted)
        }

        val overlayId = "crashy_overlay_id"
        val overlayType = "crashy_overlay_type"
        OverlaySdk.registerContent(overlayType) {
            error("Intentional crash for test")
        }

        OverlaySdk.show(
            context = context,
            config = OverlayConfig(id = overlayId, type = overlayType)
        )

        val active = TestUtils.waitForOverlayActive(overlayId, TestConstants.EXTENDED_TIMEOUT_MS)
        assertTrue("Overlay should remain active despite crashing content", active)

        TestUtils.hideTestOverlay(overlayId)
    }

    @Test
    fun messageProcessor_processes_received_message_with_fake_llm() = runBlocking {
        val db = TestUtils.createInMemoryDatabase(context)
        val repository = MessageRepository(db.messageDao())
        val fakeLlm = object : LlmService {
            override suspend fun generateResponses(
                message: com.yazan.jetoverlay.data.Message,
                bucket: MessageBucket
            ): List<String> {
                return listOf("OK", "Got it", "On it")
            }

            override suspend fun closeSession(messageId: Long) {
                // no-op
            }
        }

        val processor = MessageProcessor(
            repository = repository,
            context = context,
            llmService = fakeLlm
        )
        processor.start()

        val id = repository.ingestNotification(
            packageName = "com.test.app",
            sender = "Tester",
            content = "Hello from test"
        )

        val processed = TestUtils.waitForConditionSync(TestConstants.EXTENDED_TIMEOUT_MS) {
            runBlocking {
                val message = repository.getMessage(id)
                message != null &&
                    message.status == "PROCESSED" &&
                    message.generatedResponses.isNotEmpty()
            }
        }

        assertTrue("Message should be processed with generated responses", processed)

        processor.stop()
        db.close()
    }
}
