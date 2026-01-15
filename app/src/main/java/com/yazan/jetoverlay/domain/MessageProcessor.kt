package com.yazan.jetoverlay.domain

import android.content.Context
import com.yazan.jetoverlay.api.OverlaySdk
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.domain.ResponseSender
import com.yazan.jetoverlay.ui.SettingsManager
import com.yazan.jetoverlay.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The "Brain" of the system.
 * Observes the Repository for new received messages and 'enriches' them
 * with Veiled content and Smart Replies.
 */
class MessageProcessor(
    private val repository: MessageRepository,
    private val context: Context,
    private val categorizer: MessageCategorizer = MessageCategorizer(),
    private val veilGenerator: VeilGenerator = VeilGenerator(),
    // LiteRTLlmService is the default; it falls back to deterministic canned replies if the model is unavailable
    private val llmService: LlmService = LiteRTLlmService()
) {
    companion object {
        private const val COMPONENT = "MessageProcessor"
        private const val AUTO_REPLY_WINDOW_MS = 5000L
        private const val LLM_TIMEOUT_MS = 2500L
        private val RETRY_BACKOFF_MS = listOf(60_000L, 5 * 60_000L, 15 * 60_000L)
    }

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)

    fun start() {
        Logger.lifecycle(COMPONENT, "start")
        
        // Initialize LLM service (loads model if available)
        scope.launch {
            if (llmService is LiteRTLlmService) {
                llmService.initialize()
            }
        }

        repository.allMessages.onEach { messages ->
            val now = System.currentTimeMillis()
            messages.filter { it.status == "RECEIVED" }.forEach { message ->
                processMessage(message)
            }
            messages.filter { it.status == "RETRY" && it.snoozedUntil <= now }.forEach { message ->
                retrySend(message)
            }
        }.launchIn(scope)
    }

    /**
     * Stops the message processor and cancels all pending coroutines.
     * Call this before closing the database to avoid race conditions.
     */
    fun stop() {
        Logger.lifecycle(COMPONENT, "stop")
        if (llmService is LiteRTLlmService) {
            llmService.close()
        }
        supervisorJob.cancel()
    }

    private fun processMessage(message: Message) {
        scope.launch {
            try {
                Logger.processing(COMPONENT, "Starting processing from ${message.senderName}", message.id)       

                repository.updateMessageState(
                    id = message.id,
                    status = "PROCESSING"
                )

                // 1. Categorize message -> set bucket
                val bucket = categorizer.categorize(message)
                Logger.processing(COMPONENT, "Categorized as: ${bucket.name}", message.id)

                // 2. Generate veil -> set veiledContent
                val veiled = veilGenerator.generateVeil(message, bucket)
                Logger.processing(COMPONENT, "Veiled content generated", message.id)

                // 3. Generate responses via LLM service
                val responses = withTimeoutOrNull(LLM_TIMEOUT_MS) {
                    llmService.generateResponses(message, bucket)
                } ?: listOf(
                    "Got it, I'll follow up soon.",
                    "Received your message, replying shortly.",
                    "Thanks for reaching out—I'll respond in a bit."
                )
                Logger.processing(COMPONENT, "Generated ${responses.size} responses", message.id)

                // 4. Update Database (Atomic State Transition)
                repository.updateMessageState(
                    id = message.id,
                    status = "PROCESSED",
                    veiledContent = veiled,
                    generatedResponses = responses,
                    bucket = bucket.name
                )
                Logger.processing(COMPONENT, "Complete, status: PROCESSED", message.id)

                if (SettingsManager.isAutomationEnabled(context)) {
                    scheduleAutoReply(message.id)
                }

            } catch (e: Exception) {
                Logger.e(COMPONENT, "Error processing message ${message.id}: ${e.message}", e)
                OverlaySdk.reportOverlayError(COMPONENT, "Message processing failed for ${message.id}", e)
                // Keep message in RECEIVED state so it can be retried
            }
        }
    }

    private suspend fun scheduleAutoReply(messageId: Long) {
        val message = repository.getMessage(messageId) ?: return
        val elapsed = System.currentTimeMillis() - message.timestamp
        val delayMs = (AUTO_REPLY_WINDOW_MS - elapsed).coerceAtLeast(0)
        if (delayMs > 0) {
            delay(delayMs)
        }

        val latest = repository.getMessage(messageId) ?: return
        if (latest.status != "PROCESSED") {
            Logger.d(COMPONENT, "Skipping auto-reply; status=${latest.status}")
            return
        }
        if (latest.userInteracted) {
            Logger.d(COMPONENT, "Skipping auto-reply; user already interacted")
            return
        }

        val responseText = latest.generatedResponses.firstOrNull()
            ?: latest.veiledContent
            ?: "Received."
        val sender = ResponseSender(context)
        val result = sender.sendResponse(latest, responseText)
        if (result is ResponseSender.SendResult.Success) {
            repository.markAsSent(messageId)
            val elapsedMs = System.currentTimeMillis() - latest.timestamp
            Logger.processing(COMPONENT, "Auto-reply sent in ${elapsedMs}ms", messageId)
        } else if (result is ResponseSender.SendResult.Error) {
            Logger.w(COMPONENT, "Auto-reply failed: ${result.message}")
            scheduleRetry(latest)
        }
    }

    private suspend fun retrySend(message: Message) {
        val sender = ResponseSender(context)
        val responseText = message.generatedResponses.firstOrNull()
            ?: message.veiledContent
            ?: "Received."
        val result = sender.sendResponse(message, responseText)
        if (result is ResponseSender.SendResult.Success) {
            repository.markAsSent(message.id)
            Logger.processing(COMPONENT, "Retry send succeeded", message.id)
        } else if (result is ResponseSender.SendResult.Error) {
            Logger.w(COMPONENT, "Retry send failed: ${result.message}")
            scheduleRetry(message)
        }
    }

    private suspend fun scheduleRetry(message: Message) {
        val index = message.retryCount.coerceAtMost(RETRY_BACKOFF_MS.lastIndex)
        val delayMs = RETRY_BACKOFF_MS[index]
        val nextAttemptAt = System.currentTimeMillis() + delayMs
        repository.markRetry(message.id, nextAttemptAt)
        Logger.processing(COMPONENT, "Queued retry in ${delayMs}ms", message.id)
    }
}
