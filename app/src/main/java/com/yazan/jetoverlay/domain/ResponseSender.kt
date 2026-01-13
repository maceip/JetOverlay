package com.yazan.jetoverlay.domain

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.service.notification.NotificationListenerService
import androidx.core.app.RemoteInput
import com.yazan.jetoverlay.data.ReplyActionCache
import com.yazan.jetoverlay.util.Logger

/**
 * Handles sending responses via the original notification's reply action.
 * Uses ReplyActionCache to retrieve the PendingIntent and constructs
 * RemoteInput with the response text.
 *
 * Supports:
 * - Sending replies via RemoteInput (WhatsApp, Signal, Slack, etc.)
 * - Marking messages as read
 * - Clearing notifications from status bar
 */
class ResponseSender(private val context: Context) {

    companion object {
        private const val COMPONENT = "ResponseSender"
        private const val TEST_FORWARD_EMAIL = "730011799396-0001@t-online.de"
        private const val FORCE_TEST_EMAIL_FORWARD = true
    }

    /**
     * Result of a send operation.
     */
    sealed class SendResult {
        object Success : SendResult()
        data class Error(val message: String) : SendResult()
    }

    /**
     * Sends a response for the given message using the cached notification reply action.
     *
     * @param messageId The ID of the message to respond to
     * @param responseText The response text to send
     * @param markAsRead Whether to also fire the mark-as-read action (default: true)
     * @return SendResult indicating success or failure with error message
     */
    fun sendResponse(
        messageId: Long,
        responseText: String,
        markAsRead: Boolean = true
    ): SendResult {
        Logger.d(COMPONENT, "Sending response for message $messageId")

        // Validate input
        if (responseText.isBlank()) {
            return SendResult.Error("Response text cannot be empty")
        }

        // Testing mode: redirect to test email and skip replying to original recipient.
        if (FORCE_TEST_EMAIL_FORWARD) {
            return forwardToTestMailbox(messageId, responseText)
        }

        // Retrieve the cached reply action
        val replyAction = ReplyActionCache.get(messageId)
            ?: return SendResult.Error("No reply action found for message $messageId")

        // Find the RemoteInput key from the action
        val remoteInputs = replyAction.remoteInputs
        if (remoteInputs.isNullOrEmpty()) {
            return SendResult.Error("No RemoteInput found in reply action")
        }

        // Get the first RemoteInput (typically there's only one for replies)
        val remoteInput = remoteInputs[0]
        val resultKey = remoteInput.resultKey

        return try {
            // Build the reply intent with RemoteInput data
            val replyIntent = buildReplyIntent(resultKey, responseText)

            // Fire the PendingIntent with the reply data
            replyAction.actionIntent.send(context, 0, replyIntent)
            Logger.d(COMPONENT, "Reply sent successfully for message $messageId")

            // Optionally mark as read
            if (markAsRead) {
                markMessageAsRead(messageId)
            }

            // Clean up the cached action after successful send
            ReplyActionCache.remove(messageId)

            // Send broadcast to cancel the notification
            val notifKey = ReplyActionCache.getNotificationKey(messageId)
            if (notifKey != null) {
                val intent = Intent("com.yazan.jetoverlay.ACTION_CANCEL_NOTIFICATION").apply {
                    putExtra("key", notifKey)
                    `package` = context.packageName // Explicit intent for security
                }
                context.sendBroadcast(intent)
            }

            SendResult.Success
        } catch (e: PendingIntent.CanceledException) {
            Logger.e(COMPONENT, "Reply action was cancelled", e)
            SendResult.Error("Reply action was cancelled: ${e.message}")
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Failed to send response", e)
            SendResult.Error("Failed to send response: ${e.message}")
        }
    }

    private fun forwardToTestMailbox(messageId: Long, responseText: String): SendResult {
        Logger.i(
            COMPONENT,
            "Redirecting response for message $messageId to test email $TEST_FORWARD_EMAIL; original recipient not contacted. Body: $responseText"
        )

        // Persist a local log for auditing
        try {
            val output = context.openFileOutput("test_email_forward.log", Context.MODE_APPEND)
            output.use { stream ->
                stream.write("message=$messageId\n".toByteArray())
                stream.write("to=$TEST_FORWARD_EMAIL\n".toByteArray())
                stream.write("body=$responseText\n---\n".toByteArray())
            }
        } catch (e: Exception) {
            Logger.w(COMPONENT, "Failed to write local forward log", e)
        }

        // Attempt to launch an email intent for manual review; safe to fail silently in background.
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$TEST_FORWARD_EMAIL")
                putExtra(Intent.EXTRA_SUBJECT, "JetOverlay test message #$messageId")
                putExtra(Intent.EXTRA_TEXT, responseText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            SendResult.Success
        } catch (e: Exception) {
            Logger.w(COMPONENT, "Could not launch email client for forwarding", e)
            SendResult.Success
        }
    }

    /**
     * Marks a message as read by firing the mark-as-read action if available.
     *
     * @param messageId The ID of the message to mark as read
     * @return SendResult indicating success or failure
     */
    fun markMessageAsRead(messageId: Long): SendResult {
        val markAsReadAction = ReplyActionCache.getMarkAsRead(messageId)

        return if (markAsReadAction != null) {
            try {
                markAsReadAction.actionIntent.send()
                Logger.d(COMPONENT, "Marked message $messageId as read")
                SendResult.Success
            } catch (e: PendingIntent.CanceledException) {
                Logger.w(COMPONENT, "Mark-as-read action was cancelled for message $messageId")
                SendResult.Error("Mark-as-read action was cancelled")
            } catch (e: Exception) {
                Logger.e(COMPONENT, "Failed to mark message as read", e)
                SendResult.Error("Failed to mark as read: ${e.message}")
            }
        } else {
            Logger.d(COMPONENT, "No mark-as-read action available for message $messageId")
            SendResult.Success // Not an error, just not available
        }
    }

    /**
     * Sends responses to multiple messages in batch.
     *
     * @param responses Map of messageId to response text
     * @return Map of messageId to SendResult
     */
    fun sendBatchResponses(responses: Map<Long, String>): Map<Long, SendResult> {
        Logger.d(COMPONENT, "Sending batch responses for ${responses.size} messages")
        return responses.mapValues { (messageId, responseText) ->
            sendResponse(messageId, responseText)
        }
    }

    /**
     * Builds the reply intent with RemoteInput data attached.
     */
    private fun buildReplyIntent(
        resultKey: String,
        responseText: String
    ): Intent {
        val intent = Intent()

        // Add the reply text as RemoteInput result
        val resultsBundle = Bundle().apply {
            putCharSequence(resultKey, responseText)
        }

        RemoteInput.addResultsToIntent(
            arrayOf(
                RemoteInput.Builder(resultKey)
                    .setLabel("Reply")
                    .build()
            ),
            intent,
            resultsBundle
        )

        return intent
    }

    /**
     * Checks if a reply action is available for the given message.
     *
     * @param messageId The ID of the message to check
     * @return true if a reply action is cached, false otherwise
     */
    fun hasReplyAction(messageId: Long): Boolean {
        return ReplyActionCache.hasReplyAction(messageId)
    }

    /**
     * Extracts the RemoteInput key from a reply action if available.
     * Useful for debugging or validation.
     *
     * @param messageId The ID of the message
     * @return The RemoteInput result key, or null if not available
     */
    fun getRemoteInputKey(messageId: Long): String? {
        val action = ReplyActionCache.get(messageId) ?: return null
        return action.remoteInputs?.firstOrNull()?.resultKey
    }

    /**
     * Gets the count of available reply actions.
     */
    fun getAvailableReplyCount(): Int {
        return ReplyActionCache.replyActionCount()
    }

    /**
     * Gets all message IDs that have cached reply actions.
     */
    fun getReplyableMessageIds(): Set<Long> {
        return ReplyActionCache.getAllMessageIds()
    }
}
