package com.yazan.jetoverlay.domain

import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.domain.litert.AppToolBridge
import com.yazan.jetoverlay.domain.litert.LiteRTClient
import com.yazan.jetoverlay.util.Logger
import kotlinx.coroutines.flow.toList

/**
 * Implementation of LlmService that uses the LiteRT-LM engine.
 */
class LiteRTLlmService(
    private val stubService: StubLlmService = StubLlmService()
) : LlmService {

    companion object {
        private const val COMPONENT = "LiteRTLlmService"
    }

    private var client: LiteRTClient? = null
    private val toolRegistry = AppToolBridge()

    suspend fun initialize(): Boolean {
        if (client != null) return true

        val modelPath = ModelManager.getModelPath()
        if (modelPath == null) {
            Logger.w(COMPONENT, "Model file not found. Falling back to Stub.")
            return false
        }

        return try {
            client = LiteRTClient(modelPath, toolRegistry)
            client?.initialize()
            Logger.i(COMPONENT, "LiteRTLlmService initialized successfully")
            true
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Failed to initialize LiteRTClient", e)
            client = null
            false
        }
    }

    override suspend fun generateResponses(message: Message, bucket: MessageBucket): List<String> {
        if (client == null) {
            val initialized = initialize()
            if (!initialized) {
                return stubService.generateResponses(message, bucket)
            }
        }

        return try {
            val sessionKey = message.id.toString()
            val prompt = buildPrompt(message, bucket)
            val fullResponse = client!!.sendMessage(sessionKey, prompt).toList().joinToString("")
            parseResponses(fullResponse)
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Error generating responses via LiteRT", e)
            stubService.generateResponses(message, bucket)
        }
    }

    override suspend fun closeSession(messageId: Long) {
        client?.closeConversation(messageId.toString())
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
    }
}
