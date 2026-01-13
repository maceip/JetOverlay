package com.yazan.jetoverlay.api

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import com.yazan.jetoverlay.service.OverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object OverlaySdk {

    private const val TAG = "OverlaySdk"

    // Registry for multi-module support
    private val registry = java.util.Collections.synchronizedMap(mutableMapOf<String, @Composable (Any?) -> Unit>())

    internal var notificationConfig: OverlayNotificationConfig = OverlayNotificationConfig()

    // Internal state of active overlays
    private val _activeOverlays = MutableStateFlow<Map<String, ActiveOverlay>>(emptyMap())
    val activeOverlays = _activeOverlays.asStateFlow()

    data class ActiveOverlay(
        val config: OverlayConfig,
        val payload: Any?
    )

    /**
     * Register a composable content provider for a specific overlay type.
     * This allows multiple modules to contribute overlay content.
     */
    fun registerContent(type: String, content: @Composable (Any?) -> Unit) {
        registry[type] = content
    }

    /**
     * Unregister a previously registered composable content provider.
     */
    fun unregisterContent(type: String) {
        registry.remove(type)
    }

    /**
     * Check if content is registered for the given type.
     */
    fun isContentRegistered(type: String): Boolean = registry.containsKey(type)

    /**
     * Initialize the SDK.
     * @param notificationConfig Optional custom notification settings.
     */
    fun initialize(
        notificationConfig: OverlayNotificationConfig = OverlayNotificationConfig()
    ) {
        this.notificationConfig = notificationConfig
    }

    internal fun getContentFactory(): OverlayContentFactory {
        // Return a composite factory that ONLY checks registry
        return OverlayContentFactory { modifier, id, payload ->
            val activeOverlay = _activeOverlays.value[id]
            val type = activeOverlay?.config?.type ?: id

            val registeredContent = registry[type]
                ?: run {
                    Log.w(TAG, "No content registered for overlay type '$type'; rendering empty overlay")
                    null
                }

            androidx.compose.foundation.layout.Box(modifier = modifier) {
                registeredContent?.invoke(payload)
            }
        }
    }

    fun show(context: Context, config: OverlayConfig, payload: Any? = null) {
        _activeOverlays.update { current ->
            current + (config.id to ActiveOverlay(config, payload))
        }
        if (!isContentRegistered(config.type)) {
            Log.w(TAG, "show() called with unregistered type '${config.type}'")
        }
        try {
            startService(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start OverlayService; rolling back overlay id=${config.id}", e)
            _activeOverlays.update { current -> current - config.id }
        }
    }

    fun hide(id: String) {
        _activeOverlays.update { current ->
            current - id
        }
    }

    fun isOverlayActive(id: String): Boolean = _activeOverlays.value.containsKey(id)

    private fun startService(context: Context) {
        val intent = Intent(context, OverlayService::class.java)
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService failed", e)
            throw e
        }
    }
}
