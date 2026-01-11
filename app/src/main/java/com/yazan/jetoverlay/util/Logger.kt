package com.yazan.jetoverlay.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized logging utility for JetOverlay.
 * Provides consistent tagging and formatting across the app.
 */
object Logger {

    private const val TAG_PREFIX = "JetOverlay"
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /**
     * Log levels that can be filtered.
     */
    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * Minimum log level to output. Set to INFO in release builds.
     */
    var minLevel: Level = Level.DEBUG

    /**
     * Whether to include timestamps in log messages.
     */
    var includeTimestamp: Boolean = true

    /**
     * Debug log.
     */
    fun d(component: String, message: String, throwable: Throwable? = null) {
        if (minLevel <= Level.DEBUG) {
            val formattedMessage = format(component, message)
            if (throwable != null) {
                Log.d(tag(component), formattedMessage, throwable)
            } else {
                Log.d(tag(component), formattedMessage)
            }
        }
    }

    /**
     * Info log.
     */
    fun i(component: String, message: String, throwable: Throwable? = null) {
        if (minLevel <= Level.INFO) {
            val formattedMessage = format(component, message)
            if (throwable != null) {
                Log.i(tag(component), formattedMessage, throwable)
            } else {
                Log.i(tag(component), formattedMessage)
            }
        }
    }

    /**
     * Warning log.
     */
    fun w(component: String, message: String, throwable: Throwable? = null) {
        if (minLevel <= Level.WARN) {
            val formattedMessage = format(component, message)
            if (throwable != null) {
                Log.w(tag(component), formattedMessage, throwable)
            } else {
                Log.w(tag(component), formattedMessage)
            }
        }
    }

    /**
     * Error log.
     */
    fun e(component: String, message: String, throwable: Throwable? = null) {
        if (minLevel <= Level.ERROR) {
            val formattedMessage = format(component, message)
            if (throwable != null) {
                Log.e(tag(component), formattedMessage, throwable)
            } else {
                Log.e(tag(component), formattedMessage)
            }
        }
    }

    /**
     * Logs service lifecycle events.
     */
    fun lifecycle(component: String, event: String) {
        i(component, "LIFECYCLE: $event")
    }

    /**
     * Logs message processing steps.
     */
    fun processing(component: String, step: String, messageId: Long? = null) {
        val idSuffix = messageId?.let { " [msg:$it]" } ?: ""
        d(component, "PROCESSING: $step$idSuffix")
    }

    /**
     * Logs UI state changes.
     */
    fun uiState(component: String, state: String) {
        d(component, "UI_STATE: $state")
    }

    private fun tag(component: String): String {
        return "$TAG_PREFIX.$component"
    }

    private fun format(component: String, message: String): String {
        return if (includeTimestamp) {
            val timestamp = dateFormat.format(Date())
            "[$timestamp] $message"
        } else {
            message
        }
    }
}
