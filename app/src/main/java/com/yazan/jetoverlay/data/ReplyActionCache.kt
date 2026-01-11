package com.yazan.jetoverlay.data

import android.app.Notification
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache for Notification Actions (PendingIntents).
 * Since these cannot be persisted in Room, we hold them here ensuring
 * the app can reply as long as it remains alive.
 */
object ReplyActionCache {
    private val cache = ConcurrentHashMap<Long, Notification.Action>()

    fun save(messageId: Long, action: Notification.Action) {
        cache[messageId] = action
    }

    fun get(messageId: Long): Notification.Action? {
        return cache[messageId]
    }

    fun remove(messageId: Long) {
        cache.remove(messageId)
    }

    fun clear() {
        cache.clear()
    }
}
