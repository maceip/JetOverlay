package com.yazan.jetoverlay.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yazan.jetoverlay.data.Message

@Stable
class OverlayUiState(
    initialMessage: Message
) {
    var message by mutableStateOf(initialMessage)
    var isRevealed by mutableStateOf(false)
    var isExpanded by mutableStateOf(false)

    // Derived states
    val displayContent: String
        get() = if (isRevealed) message.originalContent else (message.veiledContent ?: "New Message")

    val showActions: Boolean
        get() = isRevealed && isExpanded

    fun toggleReveal() {
        isRevealed = !isRevealed
        if (isRevealed) isExpanded = true // Auto expand on reveal
    }

    fun updateMessage(newMessage: Message) {
        message = newMessage
    }
}
