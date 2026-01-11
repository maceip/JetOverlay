package com.yazan.jetoverlay.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.domain.MessageBucket

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

    // Filter pending messages (not SENT or DISMISSED)
    val pendingMessages by remember {
        derivedStateOf {
            messages.filter { it.status != "SENT" && it.status != "DISMISSED" }
        }
    }

    // Compute pending counts per bucket
    val pendingCounts by remember {
        derivedStateOf {
            pendingMessages
                .groupBy { MessageBucket.fromString(it.bucket) }
                .mapValues { it.value.size }
        }
    }

    // Create a stable UI State holder (not dependent on specific message)
    val uiState = remember {
        OverlayUiState(createIdleMessage())
    }

    // Filter messages by selected bucket (if any)
    val filteredMessages by remember(pendingMessages) {
        derivedStateOf {
            val bucket = uiState.selectedBucket
            if (bucket == null) {
                pendingMessages
            } else {
                pendingMessages.filter { MessageBucket.fromString(it.bucket) == bucket }
            }
        }
    }

    // Get the active message to display
    val activeMessage by remember {
        derivedStateOf {
            filteredMessages.lastOrNull()
        }
    }

    // Determine target message
    val targetMessage = activeMessage ?: createIdleMessage()

    // Effect: Update pending counts
    LaunchedEffect(pendingCounts) {
        uiState.updatePendingCounts(pendingCounts)
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

/**
 * Creates an idle state message when no pending messages exist.
 */
private fun createIdleMessage(): Message {
    return Message(
        packageName = "",
        senderName = "System",
        originalContent = "No new notifications. The agent is listening.",
        status = "IDLE",
        veiledContent = "Agent Active"
    )
}
