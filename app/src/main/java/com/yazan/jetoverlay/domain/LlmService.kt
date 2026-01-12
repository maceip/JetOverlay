package com.yazan.jetoverlay.domain

import com.yazan.jetoverlay.data.Message

/**
 * Interface for LLM-based response generation.
 */
interface LlmService {
    /**
     * Generates a list of possible responses for the given message.
     */
    suspend fun generateResponses(message: Message, bucket: MessageBucket): List<String>

    /**
     * Closes the session associated with the specific message ID.
     */
    suspend fun closeSession(messageId: Long)
}
