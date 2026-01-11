package com.yazan.jetoverlay.domain

import com.yazan.jetoverlay.data.Message

/**
 * Interface for LLM-based response generation.
 * Implementations generate contextual responses for messages based on their content and bucket.
 */
interface LlmService {
    /**
     * Generates a list of possible responses for the given message.
     *
     * @param message The message to generate responses for
     * @param bucket The categorized bucket for the message
     * @return A list of suggested responses
     */
    suspend fun generateResponses(message: Message, bucket: MessageBucket): List<String>
}
