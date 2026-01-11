package com.yazan.jetoverlay.domain

import android.util.Log
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository
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
        private const val TAG = "MessageProcessor"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        repository.allMessages.onEach { messages ->
            messages.filter { it.status == "RECEIVED" }.forEach { message ->
                processMessage(message)
            }
        }.launchIn(scope)
    }

    private fun processMessage(message: Message) {
        scope.launch {
            try {
                Log.d(TAG, "Processing message ${message.id} from ${message.senderName}")

                // 1. Categorize message -> set bucket
                val bucket = categorizer.categorize(message)
                Log.d(TAG, "Message ${message.id} categorized as: ${bucket.name}")

                // 2. Generate veil -> set veiledContent
                val veiled = veilGenerator.generateVeil(message, bucket)
                Log.d(TAG, "Message ${message.id} veiled content: $veiled")

                // 3. Generate responses via LLM service
                val responses = llmService.generateResponses(message, bucket)
                Log.d(TAG, "Message ${message.id} generated ${responses.size} responses")

                // 4. Update Database (Atomic State Transition)
                repository.updateMessageState(
                    id = message.id,
                    status = "PROCESSED",
                    veiledContent = veiled,
                    generatedResponses = responses,
                    bucket = bucket.name
                )
                Log.d(TAG, "Message ${message.id} processing complete, status: PROCESSED")

            } catch (e: Exception) {
                Log.e(TAG, "Error processing message ${message.id}: ${e.message}", e)
                // Keep message in RECEIVED state so it can be retried
            }
        }
    }
}
