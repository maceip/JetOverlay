package com.yazan.jetoverlay.domain

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
class MessageProcessor(private val repository: MessageRepository) {

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
            // 1. Simulate "Thinking" (Network/LLM latency)
            // In a real app, this is where we'd call the LLM API.
            
            // 2. Generate Content
            val veiled = "New message from ${message.senderName}"
            val responses = listOf(
                "Got it, thanks!", 
                "Can't talk right now.", 
                "Call me later?"
            )

            // 3. Update Database (Atomic State Transition)
            // We use a custom update function in Repo to handle this transactionally if needed,
            // but for now we update fields and status.
            repository.updateMessageState(
                id = message.id,
                status = "PROCESSED",
                veiledContent = veiled,
                generatedResponses = responses
            )
        }
    }
}
