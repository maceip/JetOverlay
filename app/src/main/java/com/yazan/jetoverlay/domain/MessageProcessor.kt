package com.yazan.jetoverlay.domain

import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The "Brain" of the system.
 * Observes the Repository for new received messages and 'enriches' them
 * with Veiled content and Smart Replies (simulated LLM).
 */
class MessageProcessor(
    private val repository: MessageRepository,
    private val categorizer: MessageCategorizer = MessageCategorizer(),
    private val veilGenerator: VeilGenerator = VeilGenerator(),
    private val llmService: LlmService = StubLlmService()
) {
    companion object {
        private const val COMPONENT = "MessageProcessor"
    }

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)

    fun start() {
        Logger.lifecycle(COMPONENT, "start")
        repository.allMessages.onEach { messages ->
            messages.filter { it.status == "RECEIVED" }.forEach { message ->
                processMessage(message)
            }
        }.launchIn(scope)
    }

    /**
     * Stops the message processor and cancels all pending coroutines.
     * Call this before closing the database to avoid race conditions.
     */
    fun stop() {
        Logger.lifecycle(COMPONENT, "stop")
        supervisorJob.cancel()
    }

    private fun processMessage(message: Message) {
        scope.launch {
            try {
                Logger.processing(COMPONENT, "Starting processing from ${message.senderName}", message.id)

                // 1. Categorize message -> set bucket
                val bucket = categorizer.categorize(message)
                Logger.processing(COMPONENT, "Categorized as: ${bucket.name}", message.id)

                // 2. Generate veil -> set veiledContent
                val veiled = veilGenerator.generateVeil(message, bucket)
                Logger.processing(COMPONENT, "Veiled content generated", message.id)

                // 3. Generate responses via LLM service
                val responses = llmService.generateResponses(message, bucket)
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

            } catch (e: Exception) {
                Logger.e(COMPONENT, "Error processing message ${message.id}: ${e.message}", e)
                // Keep message in RECEIVED state so it can be retried
            }
        }
    }
}
