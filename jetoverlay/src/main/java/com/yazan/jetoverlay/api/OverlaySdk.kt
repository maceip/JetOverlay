package com.yazan.jetoverlay.api

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.yazan.jetoverlay.service.OverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference

object OverlaySdk {

    private const val TAG = "OverlaySdk"

    // Registry for multi-module support (weak refs to avoid leaking Compose lambdas)
    private val registry = java.util.Collections.synchronizedMap(
        mutableMapOf<String, WeakReference<@Composable (Any?) -> Unit>>()
    )

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
        pruneRegistry()
        registry[type] = WeakReference(content)
    }

    /**
     * Unregister a previously registered composable content provider.
     */
    fun unregisterContent(type: String) {
        registry.remove(type)
    }

    /**
     * Remove any cleared registry entries to prevent leaks.
     */
    fun pruneRegistry() {
        val iterator = registry.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.get() == null) {
                iterator.remove()
            }
        }
    }

    /**
     * Check if content is registered for the given type.
     */
    fun isContentRegistered(type: String): Boolean = registry[type]?.get() != null

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

            val registeredContent = registry[type]?.get()
                ?: run {
                    pruneRegistry()
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
        val started = startService(context)
        if (!started) {
            Log.w(TAG, "OverlayService not started; rolling back overlay id=${config.id}")
            _activeOverlays.update { current -> current - config.id }
        }
    }

    fun hide(id: String) {
        _activeOverlays.update { current ->
            current - id
        }
    }

    fun isOverlayActive(id: String): Boolean = _activeOverlays.value.containsKey(id)

    private fun startService(context: Context): Boolean {
        val intent = Intent(context, OverlayService::class.java)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canStartForegroundService()) {
                Log.w(TAG, "Foreground service start skipped: app not in foreground")
                false
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            }
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.e(TAG, "Foreground service start not allowed", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService failed", e)
            false
        }
    }

    private fun canStartForegroundService(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return try {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read lifecycle state", e)
            false
        }
    }
}
