package com.yazan.jetoverlay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.yazan.jetoverlay.api.OverlaySdk
import com.yazan.jetoverlay.internal.OverlayViewWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class OverlayService : Service() {

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val activeViews = mutableMapOf<String, OverlayViewWrapper>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        observeOverlays()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun observeOverlays() {
        OverlaySdk.activeOverlays.onEach { overlayMap ->
            synchronizeViews(overlayMap)
        }.launchIn(serviceScope)
    }

    private fun synchronizeViews(requestedOverlays: Map<String, OverlaySdk.ActiveOverlay>) {
        val currentIds = activeViews.keys.toSet()
        val requestedIds = requestedOverlays.keys

        (currentIds - requestedIds).forEach { removeOverlay(it) }

        (requestedIds - currentIds).forEach { id ->
            requestedOverlays[id]?.let { overlayData ->
                addOverlay(id, overlayData)
            }
        }
    }

    private fun addOverlay(id: String, data: OverlaySdk.ActiveOverlay) {
        if (activeViews.containsKey(id)) return

        val viewWrapper = OverlayViewWrapper(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = data.config.initialX
            y = data.config.initialY
        }

        viewWrapper.setContent {
            // Drag Modifier
            val dragModifier = Modifier.pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    params.x += dragAmount.x.toInt()
                    params.y += dragAmount.y.toInt()
                    if (viewWrapper.isAttachedToWindow) {
                        windowManager.updateViewLayout(viewWrapper, params)
                    }
                }
            }

            OverlaySdk.getContentFactory().Content(
                modifier = dragModifier,
                id = id,
                payload = data.payload
            )
        }

        try {
            windowManager.addView(viewWrapper, params)
            viewWrapper.onAttachToWindowCustom()
            activeViews[id] = viewWrapper
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlay(id: String) {
        activeViews.remove(id)?.let { view ->
            try {
                view.onDetachFromWindowCustom()
                windowManager.removeView(view)
            } catch (e: Exception) {
                // View might already be detached, ignore.
                e.printStackTrace()
            }
        }

        if (activeViews.isEmpty()) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        activeViews.keys.toList().forEach { id ->
            removeOverlay(id)
        }
    }

    private fun startForegroundNotification() {
        val config = OverlaySdk.notificationConfig

        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            config.channelId,
            config.channelName,
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        val smallIcon = config.iconResId ?: android.R.drawable.ic_dialog_info

        val notification = Notification.Builder(this, config.channelId)
            .setContentTitle(config.title)
            .setContentText(config.message)
            .setSmallIcon(smallIcon)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(101, notification)
                }
            } else {
                startForeground(101, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}