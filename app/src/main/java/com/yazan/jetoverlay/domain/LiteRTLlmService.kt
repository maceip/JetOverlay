package com.yazan.jetoverlay.domain

import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.domain.litert.AppToolBridge
import com.yazan.jetoverlay.domain.litert.LiteRTClient
import com.yazan.jetoverlay.util.Logger
import kotlinx.coroutines.flow.toList

/**
 * LiteRT-backed LlmService.
 * If initialization or inference fails, returns a deterministic fallback set instead of delegating to a stub.
 */
class LiteRTLlmService : LlmService {
    companion object {
        private const val COMPONENT = "LiteRTLlmService"
    }

    private var client: LiteRTClient? = null
    private val toolRegistry = AppToolBridge()
    private var disabledUntilMs = 0L
    private var consecutiveFailures = 0
    private val sessionIdleMs = 30 * 60 * 1000L
    private val maxCooldownMs = 60 * 1000L
    private val fallbackResponses = listOf(
        "Got it—I'll follow up soon.",
        "Received, working on it.",
        "Thanks, I'll respond shortly."
    )

    suspend fun initialize(): Boolean {
        val now = System.currentTimeMillis()
        if (now < disabledUntilMs) return false
        if (client != null) return true

        val modelPath = ModelManager.getModelPath()
        if (modelPath == null) {
            Logger.w(COMPONENT, "Model file not found. Using fallback responses.")
            disabledForSession = true
            return false
        }

        return try {
            client = LiteRTClient(modelPath, toolRegistry)
            client?.initialize()
            Logger.i(COMPONENT, "LiteRTLlmService initialized successfully")
            consecutiveFailures = 0
            true
        } catch (e: NoClassDefFoundError) {
            Logger.e(COMPONENT, "LiteRT runtime dependency missing (kotlin-reflect/native libs). Using fallback responses.", e)
            client = null
            recordFailure()
            false
        } catch (e: UnsatisfiedLinkError) {
            Logger.e(COMPONENT, "LiteRT native library missing. Using fallback responses.", e)
            client = null
            recordFailure()
            false
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Failed to initialize LiteRTClient", e)
            client = null
            recordFailure()
            false
        }
    }

    override suspend fun generateResponses(message: Message, bucket: MessageBucket): List<String> {
        if (client == null) {
            val initialized = initialize()
            if (!initialized) {
                return fallbackResponses
            }
        }

        return try {
            val sessionKey = buildSessionKey(message)
            val prompt = buildPrompt(message, bucket)
            val fullResponse = client!!.sendMessage(sessionKey, prompt).toList().joinToString("")
            consecutiveFailures = 0
            client?.pruneIdleConversations(sessionIdleMs)
            parseResponses(fullResponse).ifEmpty { fallbackResponses }
        } catch (t: Throwable) {
            Logger.e(COMPONENT, "Error generating responses via LiteRT", t)
            recordFailure()
            fallbackResponses
        }
    }

    override suspend fun closeSession(message: Message) {
        client?.closeConversation(buildSessionKey(message))
    }

    private fun buildSessionKey(message: Message): String {
        val sender = message.senderName.trim().lowercase()
        return when {
            !message.threadKey.isNullOrBlank() -> "thread:${message.threadKey}"
            sender.isNotBlank() -> "sender:$sender"
            !message.contextTag.isNullOrBlank() -> "tag:${message.contextTag!!.lowercase()}:${message.packageName.lowercase()}"
            else -> "pkg:${message.packageName.lowercase()}"
        }
    }

    private fun buildPrompt(message: Message, bucket: MessageBucket): String {
        return """
            Context: You are a helpful assistant assisting a user with their notifications.
            Task: Generate 3 distinct, short, and polite replies for the following message.
            Category: ${bucket.name}
            Sender: ${message.senderName}
            Message: "${message.originalContent}"

            Format: Return only the 3 replies, one per line. Do not number them.
        """.trimIndent()
    }

    private fun parseResponses(rawResponse: String): List<String> {
        return rawResponse.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
    }

    fun close() {
        client?.close()
        client = null
        disabledUntilMs = 0L
        consecutiveFailures = 0
    }

    private fun recordFailure() {
        consecutiveFailures += 1
        val backoff = (1_000L shl (consecutiveFailures - 1)).coerceAtMost(maxCooldownMs)
        disabledUntilMs = System.currentTimeMillis() + backoff
    }
}
