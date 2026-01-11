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
}
