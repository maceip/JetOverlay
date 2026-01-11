package com.yazan.jetoverlay.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository

/**
 * Top-level Composable for the Agent Overlay.
 *
 * Responsibilities:
 * 1. Collects data from the Repository.
 * 2. Determines the active Message state (Active vs Idle).
 * 3. Hoists the state into OverlayUiState.
 * 4. Renders the FloatingBubble.
 */
@Composable
fun AgentOverlay(
    repository: MessageRepository,
    modifier: Modifier = Modifier
) {
    // Collect all messages from the DB
    // Optimization: In a real app, query only "active" messages to avoid list diffing cost.
    val messages by repository.allMessages.collectAsState(initial = emptyList())

    // Logic to find the most relevant message to show
    val activeMessage = remember(messages) {
        messages.lastOrNull { it.status != "SENT" && it.status != "DISMISSED" }
    }

    // Define the Idle state message
    val idleMessage = remember {
        Message(
            packageName = "",
            senderName = "System",
            originalContent = "No new notifications. The agent is listening.",
            status = "IDLE",
            veiledContent = "Agent Active"
        )
    }

    // Determine target message
    val targetMessage = activeMessage ?: idleMessage

    // Create the UI State holder
    val uiState = remember(targetMessage.id) { 
        OverlayUiState(targetMessage) 
    }

    // Effect: Update state if the message content changes underneath (e.g. status update)
    LaunchedEffect(targetMessage) {
        uiState.updateMessage(targetMessage)
    }

    FloatingBubble(
        modifier = modifier,
        uiState = uiState
    )
}
