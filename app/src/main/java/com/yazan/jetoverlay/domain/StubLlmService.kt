package com.yazan.jetoverlay.domain

import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.util.Logger
import kotlinx.coroutines.delay

/**
 * Evolved implementation of LlmService for development and testing.
 */
class StubLlmService : LlmService {

    companion object {
        private const val COMPONENT = "StubLlmService"
        private const val SIMULATED_DELAY_MS = 800L
    }

    override suspend fun generateResponses(message: Message, bucket: MessageBucket): List<String> {
        Logger.processing(COMPONENT, "Generating contextual responses for bucket: ${bucket.name}", message.id)

        delay(SIMULATED_DELAY_MS)

        return when (bucket) {
            MessageBucket.URGENT -> listOf(
                "I'm on it right now!",
                "Got your message, will call in 5 mins.",
                "Acknowledged. Sending requested info soon."
            )
            MessageBucket.WORK -> listOf(
                "Got it, looking into this now.",
                "Thanks for the update. Will review by EOD.",
                "Can we discuss this in our next sync?"
            )
            MessageBucket.SOCIAL -> listOf(
                "Sounds great! Looking forward to it.",
                "Haha, that's awesome!",
                "Thanks for sharing! See you soon."
            )
            MessageBucket.PROMOTIONAL -> listOf(
                "Not interested right now, thanks.",
                "Please unsubscribe me from these alerts.",
                "Thanks for the offer, I'll keep it in mind."
            )
            MessageBucket.TRANSACTIONAL -> listOf(
                "Confirmed, thank you.",
                "I've received the confirmation.",
                "Payment/Order acknowledged."
            )
            MessageBucket.UNKNOWN -> listOf(
                "Received your message.",
                "Got it, thanks!",
                "I'll get back to you shortly."
            )
        }
    }

    override suspend fun closeSession(messageId: Long) {
        Logger.d(COMPONENT, "Closing stub session for message $messageId")
    }
}
