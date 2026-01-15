package com.yazan.jetoverlay.data

import android.app.Notification
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache for Notification Actions (PendingIntents).
 * Since these cannot be persisted in Room, we hold them here ensuring
 * the app can reply as long as it remains alive.
 *
 * Stores both Reply actions (with RemoteInput) and Mark-as-Read actions.
 */
object ReplyActionCache {
    private const val MAX_CACHE_SIZE = 200
    private const val MAX_AGE_MS = 60 * 60 * 1000L

    private data class CachedAction(
        val action: Notification.Action,
        val savedAt: Long = System.currentTimeMillis()
    )

    private val replyActions = ConcurrentHashMap<Long, CachedAction>()
    private val markAsReadActions = ConcurrentHashMap<Long, CachedAction>()
    private val notificationKeys = ConcurrentHashMap<Long, String>()

    /**
     * Saves a reply action for a message.
     */
    fun save(messageId: Long, action: Notification.Action) {
        replyActions[messageId] = CachedAction(action)
        pruneIfNeeded()
    }

    /**
     * Saves a mark-as-read action for a message.
     */
    fun saveMarkAsRead(messageId: Long, action: Notification.Action) {
        markAsReadActions[messageId] = CachedAction(action)
        pruneIfNeeded()
    }

    /**
     * Saves the notification key for a message (used for cancellation).
     */
    fun saveNotificationKey(messageId: Long, key: String) {
        notificationKeys[messageId] = key
    }

    /**
     * Gets the reply action for a message.
     */
    fun get(messageId: Long): Notification.Action? {
        return replyActions[messageId]?.takeIf { !isExpired(it) }?.action
            ?: run {
                replyActions.remove(messageId)
                null
            }
    }

    /**
     * Gets the mark-as-read action for a message.
     */
    fun getMarkAsRead(messageId: Long): Notification.Action? {
        return markAsReadActions[messageId]?.takeIf { !isExpired(it) }?.action
            ?: run {
                markAsReadActions.remove(messageId)
                null
            }
    }

    /**
     * Gets the notification key for a message.
     */
    fun getNotificationKey(messageId: Long): String? {
        return notificationKeys[messageId]
    }

    /**
     * Checks if a reply action exists for a message.
     */
    fun hasReplyAction(messageId: Long): Boolean {
        return get(messageId) != null
    }

    /**
     * Removes all cached actions for a message.
     */
    fun remove(messageId: Long) {
        replyActions.remove(messageId)
        markAsReadActions.remove(messageId)
        notificationKeys.remove(messageId)
    }

    /**
     * Clears all cached actions.
     */
    fun clear() {
        replyActions.clear()
        markAsReadActions.clear()
        notificationKeys.clear()
    }

    /**
     * Gets the count of cached reply actions.
     */
    fun replyActionCount(): Int = replyActions.size

    /**
     * Gets all message IDs with cached reply actions.
     */
    fun getAllMessageIds(): Set<Long> = replyActions.keys.toSet()

    private fun isExpired(action: CachedAction): Boolean {
        return System.currentTimeMillis() - action.savedAt > MAX_AGE_MS
    }

    private fun pruneIfNeeded() {
        val now = System.currentTimeMillis()
        replyActions.entries.removeIf { now - it.value.savedAt > MAX_AGE_MS }
        markAsReadActions.entries.removeIf { now - it.value.savedAt > MAX_AGE_MS }

        val totalSize = replyActions.size + markAsReadActions.size
        if (totalSize <= MAX_CACHE_SIZE) return

        val toRemove = (replyActions.entries + markAsReadActions.entries)
            .sortedBy { it.value.savedAt }
            .take(totalSize - MAX_CACHE_SIZE)
            .map { it.key }

        toRemove.forEach { id ->
            replyActions.remove(id)
            markAsReadActions.remove(id)
            notificationKeys.remove(id)
        }
    }
}
