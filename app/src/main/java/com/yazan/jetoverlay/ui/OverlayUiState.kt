package com.yazan.jetoverlay.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.domain.MessageBucket

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

    // Current bucket of the message for color coding
    val currentBucket: MessageBucket
        get() = MessageBucket.fromString(message.bucket)

    // Selected bucket for filtering (null = show all)
    var selectedBucket: MessageBucket? by mutableStateOf(null)

    // Pending counts per bucket for badge display
    var pendingCounts: Map<MessageBucket, Int> by mutableStateOf(emptyMap())

    // Response selection state
    var selectedResponseIndex: Int? by mutableStateOf(null)

    // Editing mode state
    var isEditing by mutableStateOf(false)

    // Custom edited response text (when editing)
    var editedResponse by mutableStateOf("")

    // Callbacks for actions (set by the overlay controller)
    var onRegenerateResponses: (() -> Unit)? = null
    var onSendResponse: ((String) -> Unit)? = null

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

    // Get the currently selected response text
    val selectedResponse: String?
        get() = when {
            isEditing && editedResponse.isNotBlank() -> editedResponse
            selectedResponseIndex != null && selectedResponseIndex!! < message.generatedResponses.size ->
                message.generatedResponses[selectedResponseIndex!!]
            else -> null
        }

    // Check if a valid response is selected or edited
    val hasSelectedResponse: Boolean
        get() = selectedResponse != null

    fun selectResponse(index: Int?) {
        selectedResponseIndex = index
        // Exit editing mode when selecting a pre-generated response
        if (index != null) {
            isEditing = false
            editedResponse = ""
        }
    }

    fun startEditing() {
        isEditing = true
        // Pre-populate with selected response if available
        editedResponse = selectedResponse ?: ""
    }

    fun cancelEditing() {
        isEditing = false
        editedResponse = ""
    }

    fun updateEditedResponse(text: String) {
        editedResponse = text
    }

    fun useEditedResponse() {
        // Clear selected index to use edited response instead
        selectedResponseIndex = null
        isEditing = false
    }

    fun regenerateResponses() {
        onRegenerateResponses?.invoke()
    }

    fun sendSelectedResponse() {
        selectedResponse?.let { response ->
            onSendResponse?.invoke(response)
        }
    }

    fun resetResponseSelection() {
        selectedResponseIndex = null
        isEditing = false
        editedResponse = ""
    }
}
