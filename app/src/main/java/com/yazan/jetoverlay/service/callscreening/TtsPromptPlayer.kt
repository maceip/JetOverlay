package com.yazan.jetoverlay.service.callscreening

import android.content.Context
import com.yazan.jetoverlay.util.Logger
import kotlinx.coroutines.delay

/**
 * Stubbed TTS player. Replace with Android TextToSpeech implementation.
 */
class TtsPromptPlayer(private val context: Context) {
    companion object {
        private const val COMPONENT = "TtsPromptPlayer"
    }

    suspend fun speak(text: String) {
        Logger.d(COMPONENT, "TTS speak (STUB): $text")
        // Approximate duration to simulate speech so we don't start listening too early.
        val estimatedMs = (text.length * 30L).coerceAtLeast(600L)
        delay(estimatedMs)
    }
}
