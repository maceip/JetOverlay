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
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.Modifier
import com.yazan.jetoverlay.api.OverlaySdk
import com.yazan.jetoverlay.internal.OverlayViewWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class OverlayService : Service() {

    private val tag = "OverlayService"

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val activeViews = mutableMapOf<String, OverlayViewWrapper>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "Service onCreate; starting foreground and observing overlays")
        startForegroundNotification()
        observeOverlays()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun observeOverlays() {
        OverlaySdk.activeOverlays.onEach { overlayMap ->
            Log.i(tag, "Active overlay state changed: requested=${overlayMap.keys}")
            try {
                synchronizeViews(overlayMap)
            } catch (t: Throwable) {
                Log.e(tag, "Overlay synchronization crashed; keeping service alive", t)
                OverlaySdk.reportOverlayError("OverlayService", "Synchronization failed", t)
            }
        }.launchIn(serviceScope)
    }

    private fun synchronizeViews(requestedOverlays: Map<String, OverlaySdk.ActiveOverlay>) {
        val currentIds = activeViews.keys.toSet()
        val requestedIds = requestedOverlays.keys

        // 1. Remove
        (currentIds - requestedIds).forEach { removeOverlay(it) }

        // 2. Add
        (requestedIds - currentIds).forEach { id ->
            requestedOverlays[id]?.let { overlayData ->
                addOverlay(id, overlayData)
            }
        }

        // 3. Update existing
        (requestedIds intersect currentIds).forEach { id ->
            val existingView = activeViews[id]
            val newData = requestedOverlays[id]
            if (existingView != null && newData != null) {
                updateOverlay(id, existingView, newData)
            }
        }
    }

    private fun updateOverlay(
        id: String,
        viewWrapper: OverlayViewWrapper,
        data: OverlaySdk.ActiveOverlay
    ) {
        // Check if layout params need update (e.g. focusable flag)
        val params = viewWrapper.layoutParams as? WindowManager.LayoutParams ?: return
        
        val currentFlags = params.flags
        val shouldBeFocusable = data.config.isFocusable

        // Calculate expected flags based on focusability
        // FLAG_NOT_FOCUSABLE = 8
        val isCurrentlyNotFocusable = (currentFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0

        var needsUpdate = false

        if (shouldBeFocusable && isCurrentlyNotFocusable) {
            // Remove NOT_FOCUSABLE
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            needsUpdate = true
            Log.i(tag, "Updating overlay focusability: id=$id -> focusable")
        } else if (!shouldBeFocusable && !isCurrentlyNotFocusable) {
            // Add NOT_FOCUSABLE
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
            needsUpdate = true
            Log.i(tag, "Updating overlay focusability: id=$id -> not focusable")
        }

        // Ensure position is synced if changed (though drag handles this mostly)
        // Only update if external config changed significantly, to avoid fighting the drag loop
        // For now, we only care about focus flags for the IME fix.

        if (needsUpdate && viewWrapper.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(viewWrapper, params)
            } catch (e: Exception) {
                // Ignore detached
            }
        }
    }

    private fun addOverlay(id: String, data: OverlaySdk.ActiveOverlay) {
        if (activeViews.containsKey(id)) return

        val viewWrapper = OverlayViewWrapper(this)

        var layoutFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (data.config.isFocusable) {
            layoutFlags = layoutFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            layoutFlags = layoutFlags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            layoutFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            // Keep y at 0 so it sits exactly on the bottom inset; avoid accidental off-screen offsets.
            y = 0
        }

        viewWrapper.setContent {
            OverlaySdk.getContentFactory().Content(
                modifier = Modifier,
                id = id,
                payload = data.payload
            )
        }

        try {
            windowManager.addView(viewWrapper, params)
            viewWrapper.onAttachToWindowCustom()
            activeViews[id] = viewWrapper
            Log.i(tag, "Overlay added: id=$id, type=${data.config.type}, pos=(${params.x}, ${params.y}), flags=${params.flags}, focusable=${data.config.isFocusable}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to add overlay view for id=$id", e)
            OverlaySdk.reportOverlayError("OverlayService", "Failed to add overlay view for id=$id", e)
            // Roll back the active overlay state to avoid a stuck FGS with no view
            OverlaySdk.hide(id)
            if (activeViews.isEmpty()) {
                stopSelf()
            }
        }
    }

    private fun removeOverlay(id: String) {
        activeViews.remove(id)?.let { view ->
            try {
                view.onDetachFromWindowCustom()
                windowManager.removeView(view)
                Log.i(tag, "Overlay removed: id=$id")
            } catch (e: Exception) {
                // View might already be detached, ignore.
                e.printStackTrace()
            }
        }

        if (activeViews.isEmpty()) {
            Log.i(tag, "No active overlays; stopping service")
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(tag, "Service onDestroy; removing overlays and cancelling scope")
        activeViews.keys.toList().forEach { id ->
            removeOverlay(id)
        }
        serviceScope.cancel()
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
