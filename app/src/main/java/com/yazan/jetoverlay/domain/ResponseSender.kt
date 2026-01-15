package com.yazan.jetoverlay.domain

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.service.notification.NotificationListenerService
import androidx.core.app.RemoteInput
import com.yazan.jetoverlay.data.Message
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
        private const val FORCE_TEST_EMAIL_FORWARD = false
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
        message: Message,
        responseText: String,
        markAsRead: Boolean = true
    ): SendResult {
        Logger.d(COMPONENT, "Sending response for message ${message.id}")

        // Validate input
        if (responseText.isBlank()) {
            return SendResult.Error("Response text cannot be empty")
        }

        // Testing mode: redirect to test email and skip replying to original recipient.
        if (FORCE_TEST_EMAIL_FORWARD) {
            return forwardToTestMailbox(message.id, responseText)
        }

        val handler = ChannelRegistry.resolve(context, this, message)
            ?: return SendResult.Error("No reply handler found for message ${message.id}")
        val result = handler.send(message, responseText, markAsRead)
        if (result is SendResult.Success && markAsRead && handler !is NotificationReplyHandler) {
            markMessageAsRead(message)
        }
        return result
    }

    /**
     * Legacy entry point used by tests; resolves message id via ReplyAction cache only.
     */
    fun sendResponse(
        messageId: Long,
        responseText: String,
        markAsRead: Boolean = true
    ): SendResult {
        val message = Message(
            id = messageId,
            packageName = "",
            senderName = "",
            originalContent = "",
            veiledContent = null,
            generatedResponses = emptyList(),
            selectedResponse = null,
            status = "UNKNOWN",
            bucket = "UNKNOWN",
            timestamp = System.currentTimeMillis(),
            contextTag = null
        )
        return sendResponse(message, responseText, markAsRead)
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
            val mailto = "mailto:$TEST_FORWARD_EMAIL?subject=${Uri.encode("JetOverlay test message #$messageId")}&body=${Uri.encode(responseText)}"
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse(mailto)
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
                Logger.d(COMPONENT, "Marked message $messageId as read via notification action")
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
            SendResult.Success
        }
    }

    fun markMessageAsRead(message: Message): SendResult {
        return MarkAsReadService.markAsRead(context, message)
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

class NotificationReplyHandler(
    private val context: Context,
    private val sender: ResponseSender
) : ReplyHandler {
    override fun canHandle(message: Message): Boolean {
        return ReplyActionCache.hasReplyAction(message.id)
    }

    override fun send(message: Message, responseText: String, markAsRead: Boolean): ResponseSender.SendResult {
        val replyAction = ReplyActionCache.get(message.id)
            ?: return ResponseSender.SendResult.Error("No reply action found for message ${message.id}")

        val remoteInputs = replyAction.remoteInputs
        if (remoteInputs.isNullOrEmpty()) {
            return ResponseSender.SendResult.Error("No RemoteInput found in reply action")
        }

        val remoteInput = remoteInputs[0]
        val resultKey = remoteInput.resultKey

        return try {
            val replyIntent = buildReplyIntent(resultKey, responseText)
            replyAction.actionIntent.send(context, 0, replyIntent)
            Logger.d("NotificationReplyHandler", "Reply sent successfully for message ${message.id}")

            if (markAsRead) {
                sender.markMessageAsRead(message)
            }
            val notifKey = ReplyActionCache.getNotificationKey(message.id)
            ReplyActionCache.remove(message.id)
            if (notifKey != null) {
                val intent = Intent("com.yazan.jetoverlay.ACTION_CANCEL_NOTIFICATION").apply {
                    putExtra("key", notifKey)
                    `package` = context.packageName
                }
                context.sendBroadcast(intent)
            }

            ResponseSender.SendResult.Success
        } catch (e: PendingIntent.CanceledException) {
            Logger.e("NotificationReplyHandler", "Reply action was cancelled", e)
            ResponseSender.SendResult.Error("Reply action was cancelled: ${e.message}")
        } catch (e: Exception) {
            Logger.e("NotificationReplyHandler", "Failed to send response", e)
            ResponseSender.SendResult.Error("Failed to send response: ${e.message}")
        }
    }

    private fun buildReplyIntent(resultKey: String, responseText: String): Intent {
        val intent = Intent()
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
}

class EmailReplyHandler(
    private val context: Context
) : ReplyHandler {
    override fun canHandle(message: Message): Boolean {
        val pkg = message.packageName.lowercase()
        val tag = message.contextTag?.lowercase()
        return tag == "email" || pkg.contains("gmail") || pkg.contains("outlook") || pkg == "email"
    }

    override fun send(message: Message, responseText: String, markAsRead: Boolean): ResponseSender.SendResult {
        Logger.i("EmailReplyHandler", "Email handler stubbed; message ${message.id} would be sent via Email API.")
        // Real email sending not implemented; rely on test forward mode or future Gmail API.
        return ResponseSender.SendResult.Success
    }
}

class SlackReplyHandler : ReplyHandler {
    override fun canHandle(message: Message): Boolean {
        val pkg = message.packageName.lowercase()
        val tag = message.contextTag?.lowercase()
        return tag == "slack" || pkg.contains("slack")
    }

    override fun send(message: Message, responseText: String, markAsRead: Boolean): ResponseSender.SendResult {
        Logger.i("SlackReplyHandler", "Slack handler stubbed; message ${message.id} would be sent via Slack API.")
        return ResponseSender.SendResult.Success
    }
}
