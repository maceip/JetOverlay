package com.yazan.jetoverlay.domain

import android.util.Log
import com.yazan.jetoverlay.data.Message
import kotlinx.coroutines.delay

/**
 * Stub implementation of LlmService for development and testing.
 * Returns fixed responses after a simulated processing delay.
 * This will be replaced with the real LLM implementation from google-ai-edge/gallery.
 */
class StubLlmService : LlmService {

    companion object {
        private const val TAG = "StubLlmService"
        private const val SIMULATED_DELAY_MS = 500L
    }

    /**
     * Returns mock responses after a simulated delay.
     * Always returns: ["hello", "Got it!", "Thanks!"]
     */
    override suspend fun generateResponses(message: Message, bucket: MessageBucket): List<String> {
        Log.d(TAG, "StubLlmService: Returning mock responses for message ${message.id}")

        // Simulate LLM processing time
        delay(SIMULATED_DELAY_MS)

        return listOf("hello", "Got it!", "Thanks!")
    }
}
