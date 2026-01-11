package com.yazan.jetoverlay.domain

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.RemoteInput
import com.yazan.jetoverlay.data.ReplyActionCache

/**
 * Handles sending responses via the original notification's reply action.
 * Uses ReplyActionCache to retrieve the PendingIntent and constructs
 * RemoteInput with the response text.
 */
class ResponseSender(private val context: Context) {

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
     * @return SendResult indicating success or failure with error message
     */
    fun sendResponse(messageId: Long, responseText: String): SendResult {
        // Validate input
        if (responseText.isBlank()) {
            return SendResult.Error("Response text cannot be empty")
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
            val replyIntent = buildReplyIntent(replyAction.actionIntent, resultKey, responseText)

            // Fire the PendingIntent with the reply data
            replyAction.actionIntent.send(context, 0, replyIntent)

            // Clean up the cached action after successful send
            ReplyActionCache.remove(messageId)

            SendResult.Success
        } catch (e: PendingIntent.CanceledException) {
            SendResult.Error("Reply action was cancelled: ${e.message}")
        } catch (e: Exception) {
            SendResult.Error("Failed to send response: ${e.message}")
        }
    }

    /**
     * Builds the reply intent with RemoteInput data attached.
     */
    private fun buildReplyIntent(
        actionIntent: PendingIntent,
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
        return ReplyActionCache.get(messageId) != null
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
}
