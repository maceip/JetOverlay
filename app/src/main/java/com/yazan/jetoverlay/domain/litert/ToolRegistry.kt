package com.yazan.jetoverlay.domain.litert

/**
 * Interface for providing a list of tool objects to the LiteRT engine.
 */
interface ToolRegistry {
    fun getTools(): List<Any>
}



