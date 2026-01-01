package com.yazan.jetoverlay.api

import android.content.Context
import android.content.Intent
import com.yazan.jetoverlay.service.OverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object OverlaySdk {

    private var contentFactory: OverlayContentFactory? = null

    internal var notificationConfig: OverlayNotificationConfig = OverlayNotificationConfig()

    // Internal state of active overlays
    private val _activeOverlays = MutableStateFlow<Map<String, ActiveOverlay>>(emptyMap())
    val activeOverlays = _activeOverlays.asStateFlow()

    data class ActiveOverlay(
        val config: OverlayConfig,
        val payload: Any?
    )

    /**
     * Initialize the SDK.
     * @param notificationConfig Optional custom notification settings.
     * @param factory The factory that renders your overlay content.
     */
    fun initialize(
        notificationConfig: OverlayNotificationConfig = OverlayNotificationConfig(),
        factory: OverlayContentFactory
    ) {
        this.notificationConfig = notificationConfig
        this.contentFactory = factory
    }

    internal fun getContentFactory(): OverlayContentFactory {
        return contentFactory ?: throw IllegalStateException("OverlaySdk.initialize() must be called first")
    }

    fun show(context: Context, config: OverlayConfig, payload: Any? = null) {
        _activeOverlays.update { current ->
            current + (config.id to ActiveOverlay(config, payload))
        }
        startService(context)
    }

    fun hide(id: String) {
        _activeOverlays.update { current ->
            current - id
        }
    }

    fun isOverlayActive(id: String): Boolean = _activeOverlays.value.containsKey(id)

    private fun startService(context: Context) {
        val intent = Intent(context, OverlayService::class.java)
        context.startForegroundService(intent)
    }
}