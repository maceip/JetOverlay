package com.yazan.jetoverlay.domain.litert

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.yazan.jetoverlay.util.Logger

/**
 * Concrete implementation of a Tool Bridge.
 * Defines the actual functions the LLM can call using the @Tool annotation.
 */
class AppToolBridge : ToolRegistry {
    
    companion object {
        private const val COMPONENT = "AppToolBridge"
    }

    /**
     * Returns the list of tool objects containing @Tool annotated methods.
     */
    override fun getTools(): List<Any> {
        return listOf(this)
    }

    // ==================== Defined Tools ====================

    @Tool(description = "Returns the current system time in ISO format.")
    fun get_current_time(): String {
        Logger.i(COMPONENT, "Tool called: get_current_time")
        return java.time.ZonedDateTime.now().toString()
    }

    @Tool(description = "Calculates the sum of two numbers.")
    fun calculate_sum(
        @ToolParam(description = "The first number") a: Double,
        @ToolParam(description = "The second number") b: Double
    ): Double {
        Logger.i(COMPONENT, "Tool called: calculate_sum($a, $b)")
        return a + b
    }
    
    @Tool(description = "Marks a specific message as read.")
    fun mark_as_read(
        @ToolParam(description = "The ID of the message to mark as read") messageId: String
    ): String {
        Logger.i(COMPONENT, "Tool called: mark_as_read($messageId)")
        // In a real app, this would call the repository
        return "Message $messageId marked as read."
    }
}
