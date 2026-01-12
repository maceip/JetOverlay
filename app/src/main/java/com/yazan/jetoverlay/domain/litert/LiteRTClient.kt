package com.yazan.jetoverlay.domain.litert

import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.yazan.jetoverlay.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Client for interacting with the LiteRT-LM Engine.
 * Manages model initialization, multiple stateful conversations, and thread-safe execution.
 */
class LiteRTClient(
    private val modelPath: String,
    private val toolRegistry: ToolRegistry? = null
) : AutoCloseable {

    companion object {
        private const val COMPONENT = "LiteRTClient"
    }

    private var engine: Engine? = null
    
    // Map to track multiple stateful conversations (one per message/session)
    private val conversations = ConcurrentHashMap<String, Conversation>()
    private val mutex = Mutex()

    /**
     * Initializes the LiteRT-LM engine.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (engine != null) return@withContext

        try {
            Logger.i(COMPONENT, "Initializing LiteRT Engine with model: $modelPath")
            
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                maxNumTokens = 512
            )
            
            engine = Engine(engineConfig).also { it.initialize() }
            Logger.i(COMPONENT, "LiteRT Engine initialized successfully")
            
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Failed to initialize LiteRT Engine", e)
            throw e
        }
    }

    /**
     * Sends a message to a specific stateful conversation.
     * If the conversation doesn't exist, it is created.
     */
    suspend fun sendMessage(conversationId: String, text: String): Flow<String> = mutex.withLock {
        val activeEngine = engine 
            ?: throw IllegalStateException("Client not initialized. Call initialize() first.")
        
        val conversation = conversations.getOrPut(conversationId) {
            Logger.d(COMPONENT, "Creating new stateful conversation for ID: $conversationId")
            val config = ConversationConfig(
                tools = toolRegistry?.getTools() ?: emptyList()
            )
            activeEngine.createConversation(config)
        }

        Logger.d(COMPONENT, "Sending message to conversation [$conversationId]: $text")
        
        return conversation.sendMessageAsync(Message.of(text))
            .map { it.toString() }
    }

    /**
     * Closes and removes a specific conversation.
     */
    suspend fun closeConversation(conversationId: String) = mutex.withLock {
        conversations.remove(conversationId)?.close()
        Logger.i(COMPONENT, "Conversation [$conversationId] closed and removed")
    }

    override fun close() {
        conversations.values.forEach { it.close() }
        conversations.clear()
        engine?.close()
        engine = null
        Logger.i(COMPONENT, "LiteRT Client closed")
    }
}
