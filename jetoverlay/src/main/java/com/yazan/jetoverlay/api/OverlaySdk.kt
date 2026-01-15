package com.yazan.jetoverlay.api

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yazan.jetoverlay.service.OverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object OverlaySdk {

    private const val TAG = "OverlaySdk"
    private const val MAX_PENDING_PER_TYPE = 10
    private const val PENDING_SHOW_TTL_MS = 2 * 60 * 1000L
    private const val START_BACKOFF_MAX_MS = 60_000L

    data class OverlayHealth(
        val isDegraded: Boolean = false,
        val message: String? = null,
        val source: String? = null,
        val lastErrorAt: Long = 0L
    )

    private val _overlayHealth = MutableStateFlow(OverlayHealth())
    val overlayHealth = _overlayHealth.asStateFlow()

    // Registry for multi-module support (strong refs so entries are stable until explicitly unregistered)
    private val registry = java.util.Collections.synchronizedMap(
        mutableMapOf<String, @Composable (Any?) -> Unit>()
    )

    internal var notificationConfig: OverlayNotificationConfig = OverlayNotificationConfig()

    // Internal state of active overlays
    private val _activeOverlays = MutableStateFlow<Map<String, ActiveOverlay>>(emptyMap())
    val activeOverlays = _activeOverlays.asStateFlow()
    private val pendingShows = java.util.Collections.synchronizedMap(
        mutableMapOf<String, MutableList<PendingShow>>()
    )
    private var startBackoffUntilMs: Long = 0L
    private var startFailureCount: Int = 0

    private data class PendingShow(
        val context: Context,
        val config: OverlayConfig,
        val payload: Any?,
        val queuedAt: Long = System.currentTimeMillis()
    )

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
        registry[type] = content
        Log.i(TAG, "registerContent: type=$type")
        flushPendingShows(type)
    }

    /**
     * Unregister a previously registered composable content provider.
     */
    fun unregisterContent(type: String) {
        registry.remove(type)
        Log.i(TAG, "unregisterContent: type=$type")
    }

    /**
     * Remove any cleared registry entries to prevent leaks.
     */
    fun pruneRegistry() {
        // No-op for now: registry holds strong refs; explicit unregister handles cleanup.
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
                    pruneRegistry()
                    Log.w(TAG, "No content registered for overlay type '$type'; rendering empty overlay")
                    null
                }

            androidx.compose.foundation.layout.Box(modifier = modifier) {
                if (registeredContent == null) {
                    FallbackOverlay("Overlay content missing")
                } else {
                    registeredContent.invoke(payload)
                }
            }
        }
    }

    @Composable
    private fun FallbackOverlay(message: String) {
        Box(
            modifier = Modifier
                .background(Color(0xCC000000))
                .padding(8.dp)
        ) {
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    fun show(context: Context, config: OverlayConfig, payload: Any? = null) {
        Log.d(TAG, "show: id=${config.id}, type=${config.type}, payloadPresent=${payload != null}")
        val now = System.currentTimeMillis()
        if (now < startBackoffUntilMs) {
            Log.w(TAG, "show() backoff active; deferring overlay id=${config.id} for ${startBackoffUntilMs - now}ms")
            enqueuePendingShow(context.applicationContext, config, payload)
            return
        }
        if (!isContentRegistered(config.type)) {
            enqueuePendingShow(context.applicationContext, config, payload)
            return
        }
        _activeOverlays.update { current ->
            current + (config.id to ActiveOverlay(config, payload))
        }
        val started = startService(context)
        if (!started) {
            Log.w(TAG, "OverlayService not started; rolling back overlay id=${config.id}")
            _activeOverlays.update { current -> current - config.id }
            recordStartFailure()
        } else {
            resetStartFailures()
            Log.i(TAG, "OverlayService start requested for id=${config.id}")
        }
    }

    private fun enqueuePendingShow(context: Context, config: OverlayConfig, payload: Any?) {
        val list = pendingShows.getOrPut(config.type) { mutableListOf() }
        list.removeAll { it.config.id == config.id }
        if (list.size >= MAX_PENDING_PER_TYPE) {
            Log.w(TAG, "Pending show queue full for type '${config.type}'; dropping oldest")
            list.removeAt(0)
        }
        list.add(PendingShow(context, config, payload))
        Log.w(TAG, "show() deferred: no content registered for type '${config.type}'")
    }

    private fun flushPendingShows(type: String) {
        val pending = pendingShows.remove(type) ?: return
        val now = System.currentTimeMillis()
        val valid = pending.filter { now - it.queuedAt <= PENDING_SHOW_TTL_MS }
        val dropped = pending.size - valid.size
        if (dropped > 0) {
            Log.w(TAG, "Dropping $dropped expired pending show(s) for type '$type'")
        }
        if (valid.isNotEmpty()) {
            Log.i(TAG, "Flushing ${valid.size} pending show request(s) for type '$type'")
        }
        valid.forEach { request ->
            show(request.context, request.config, request.payload)
        }
    }

    fun hide(id: String) {
        Log.d(TAG, "hide: id=$id")
        _activeOverlays.update { current ->
            current - id
        }
    }

    fun isOverlayActive(id: String): Boolean = _activeOverlays.value.containsKey(id)

    private fun startService(context: Context): Boolean {
        val intent = Intent(context, OverlayService::class.java)
        Log.d(TAG, "startService: requesting startForegroundService")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.e(TAG, "Foreground service start not allowed (possibly background launch); will keep overlay state rolled back", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService failed", e)
            false
        }
    }

    fun reportOverlayError(source: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, "Overlay error from $source: $message", throwable)
        } else {
            Log.e(TAG, "Overlay error from $source: $message")
        }
        _overlayHealth.update {
            it.copy(
                isDegraded = true,
                message = message,
                source = source,
                lastErrorAt = System.currentTimeMillis()
            )
        }
    }

    fun clearOverlayError() {
        _overlayHealth.update { OverlayHealth() }
    }

    private fun recordStartFailure() {
        startFailureCount += 1
        val backoffMs = (1_000L shl (startFailureCount - 1)).coerceAtMost(START_BACKOFF_MAX_MS)
        startBackoffUntilMs = System.currentTimeMillis() + backoffMs
        Log.w(TAG, "Overlay start backoff set for ${backoffMs}ms (failures=$startFailureCount)")
    }

    private fun resetStartFailures() {
        startFailureCount = 0
        startBackoffUntilMs = 0L
    }
}
