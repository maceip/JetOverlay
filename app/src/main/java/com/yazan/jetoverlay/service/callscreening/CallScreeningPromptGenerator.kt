package com.yazan.jetoverlay.service.callscreening

/**
 * Generates TTS greeting prompts for call screening.
 */
class CallScreeningPromptGenerator {
    fun buildPrompt(assistantName: String): String {
        return "Hi, this is $assistantName's executive assistant. How can I help you today?"
    }
}
