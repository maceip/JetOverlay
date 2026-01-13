package com.yazan.jetoverlay.ui

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.domain.MessageBucket

private const val TAG = "OverlayUiState"

/**
 * Represents the processing state of the current message.
 */
enum class ProcessingState {
    IDLE,       // No message being processed
    PROCESSING, // Message is being processed (categorization, veiling, response generation)
    COMPLETE    // Processing is complete
}

@Stable
class OverlayUiState(
    initialMessage: Message
) {
    var message by mutableStateOf(initialMessage)
    var isRevealed by mutableStateOf(false)
    var isExpanded by mutableStateOf(false)

    // Processing state for the current message
    var processingState by mutableStateOf(ProcessingState.IDLE)

    // Count of pending messages (not SENT or DISMISSED)
    var pendingMessageCount by mutableStateOf(0)

    // Navigation state - indicates if there are more messages above/below
    var hasNextMessage by mutableStateOf(false)
    var hasPreviousMessage by mutableStateOf(false)

    // Current bucket of the message for color coding
    val currentBucket: MessageBucket
        get() = MessageBucket.fromString(message.bucket)

    // Selected bucket for filtering (null = show all)
    var selectedBucket: MessageBucket? by mutableStateOf(null)

    // Pending counts per bucket for badge display
    var pendingCounts: Map<MessageBucket, Int> by mutableStateOf(emptyMap())

    // Attention glow for newly arrived/processing messages
    var shouldGlow by mutableStateOf(false)
        private set

    // User interaction flag to suppress auto-reply
    var userInteracted by mutableStateOf(false)
        private set

    // Response selection state
    var selectedResponseIndex: Int? by mutableStateOf(null)

    // Editing mode state
    var isEditing by mutableStateOf(false)

    // Custom edited response text (when editing)
    var editedResponse by mutableStateOf("")

    // Callbacks for actions (set by the overlay controller)
    var onRegenerateResponses: (() -> Unit)? = null
    var onSendResponse: ((String) -> Unit)? = null
    var onDismissMessage: (() -> Unit)? = null
    var onNavigateToNextMessage: (() -> Unit)? = null
    var onNavigateToPreviousMessage: (() -> Unit)? = null

    // Derived states
    val displayContent: String
        get() = if (isRevealed) message.originalContent else (message.veiledContent ?: "New Message")

    val showActions: Boolean
        get() = isRevealed && isExpanded

    // Whether to show the processing indicator
    val isProcessing: Boolean
        get() = processingState == ProcessingState.PROCESSING

    // Whether processing is complete (show checkmark briefly)
    val isProcessingComplete: Boolean
        get() = processingState == ProcessingState.COMPLETE

    fun toggleReveal() {
        isRevealed = !isRevealed
        if (isRevealed) isExpanded = true // Auto expand on reveal
    }

    fun updateMessage(newMessage: Message) {
        message = newMessage
    }

    fun setProcessing() {
        processingState = ProcessingState.PROCESSING
    }

    fun setProcessingComplete() {
        processingState = ProcessingState.COMPLETE
    }

    fun setIdle() {
        processingState = ProcessingState.IDLE
    }

    fun updatePendingCount(count: Int) {
        pendingMessageCount = count
    }

    fun triggerGlow() {
        shouldGlow = true
    }

    fun clearGlow() {
        shouldGlow = false
    }

    fun markInteracted() {
        userInteracted = true
    }

    fun resetInteraction() {
        userInteracted = false
    }

    fun selectBucket(bucket: MessageBucket?) {
        selectedBucket = bucket
    }

    fun updatePendingCounts(counts: Map<MessageBucket, Int>) {
        pendingCounts = counts
        // Update total pending count
        pendingMessageCount = counts.values.sum()
    }

    // Get buckets that have pending messages, sorted by priority
    val bucketsWithPendingMessages: List<MessageBucket>
        get() = pendingCounts.filter { it.value > 0 }
            .keys
            .sortedBy { it.ordinal }

    // Flag indicating if edited response should be used (set by useEditedResponse)
    var useEditedResponseFlag by mutableStateOf(false)
        private set

    // Get the currently selected response text (with crash resilience)
    val selectedResponse: String?
        get() = try {
            when {
                // When actively editing, use current edit text
                isEditing && editedResponse.isNotBlank() -> editedResponse
                // When user clicked "Use This", use the preserved edited response
                useEditedResponseFlag && editedResponse.isNotBlank() -> editedResponse
                // Otherwise use selected index from response chips (with bounds checking)
                selectedResponseIndex != null -> {
                    val index = selectedResponseIndex!!
                    val responses = message.generatedResponses
                    if (index >= 0 && index < responses.size) responses[index] else null
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting selectedResponse", e)
            null
        }

    // Check if a valid response is selected or edited
    val hasSelectedResponse: Boolean
        get() = selectedResponse != null

    fun selectResponse(index: Int?) {
        try {
            selectedResponseIndex = index
            // Exit editing mode when selecting a pre-generated response
            if (index != null) {
                isEditing = false
                editedResponse = ""
                useEditedResponseFlag = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in selectResponse", e)
        }
    }

    fun startEditing() {
        try {
            isEditing = true
            // Pre-populate with selected response if available
            editedResponse = selectedResponse ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error in startEditing", e)
        }
    }

    fun cancelEditing() {
        try {
            isEditing = false
            editedResponse = ""
        } catch (e: Exception) {
            Log.e(TAG, "Error in cancelEditing", e)
        }
    }

    fun updateEditedResponse(text: String) {
        try {
            editedResponse = text
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateEditedResponse", e)
        }
    }

    fun useEditedResponse() {
        try {
            // Clear selected index to use edited response instead
            selectedResponseIndex = null
            isEditing = false
            useEditedResponseFlag = true
        } catch (e: Exception) {
            Log.e(TAG, "Error in useEditedResponse", e)
        }
    }

    fun regenerateResponses() {
        try {
            onRegenerateResponses?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Error in regenerateResponses", e)
        }
    }

    fun sendSelectedResponse() {
        try {
            selectedResponse?.let { response ->
                onSendResponse?.invoke(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendSelectedResponse", e)
        }
    }

    fun dismissMessage() {
        try {
            onDismissMessage?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Error in dismissMessage", e)
        }
    }

    fun resetResponseSelection() {
        try {
            selectedResponseIndex = null
            isEditing = false
            editedResponse = ""
            useEditedResponseFlag = false
        } catch (e: Exception) {
            Log.e(TAG, "Error in resetResponseSelection", e)
        }
    }

    fun updateNavigationState(hasNext: Boolean, hasPrevious: Boolean) {
        try {
            hasNextMessage = hasNext
            hasPreviousMessage = hasPrevious
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateNavigationState", e)
        }
    }

    fun navigateToNextMessage() {
        try {
            if (hasNextMessage) {
                onNavigateToNextMessage?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in navigateToNextMessage", e)
        }
    }

    fun navigateToPreviousMessage() {
        try {
            if (hasPreviousMessage) {
                onNavigateToPreviousMessage?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in navigateToPreviousMessage", e)
        }
    }
}
