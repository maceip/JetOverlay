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
    private val replyActions = ConcurrentHashMap<Long, Notification.Action>()
    private val markAsReadActions = ConcurrentHashMap<Long, Notification.Action>()
    private val notificationKeys = ConcurrentHashMap<Long, String>()

    /**
     * Saves a reply action for a message.
     */
    fun save(messageId: Long, action: Notification.Action) {
        replyActions[messageId] = action
    }

    /**
     * Saves a mark-as-read action for a message.
     */
    fun saveMarkAsRead(messageId: Long, action: Notification.Action) {
        markAsReadActions[messageId] = action
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
        return replyActions[messageId]
    }

    /**
     * Gets the mark-as-read action for a message.
     */
    fun getMarkAsRead(messageId: Long): Notification.Action? {
        return markAsReadActions[messageId]
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
        return replyActions.containsKey(messageId)
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
}
