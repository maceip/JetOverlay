package com.yazan.jetoverlay.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.domain.MessageBucket
import kotlinx.coroutines.flow.catch

private const val TAG = "AgentOverlay"

/**
 * Top-level Composable for the Agent Overlay.
 *
 * Responsibilities:
 * 1. Collects data from the Repository (with error handling).
 * 2. Determines the active Message state (Active vs Idle).
 * 3. Hoists the state into OverlayUiState.
 * 4. Renders the FloatingBubble with graceful fallbacks.
 */
@Composable
fun AgentOverlay(
    repository: MessageRepository,
    modifier: Modifier = Modifier
) {
    // Track any errors for graceful degradation
    var hasError by remember { mutableStateOf(false) }

    // Collect all messages from the DB with error handling
    // Optimization: In a real app, query only "active" messages to avoid list diffing cost.
    val messages by repository.allMessages
        .catch { e ->
            Log.e(TAG, "Error collecting messages from repository", e)
            hasError = true
            emit(emptyList())
        }
        .collectAsState(initial = emptyList())

    // Filter pending messages (not SENT or DISMISSED) - cached with derivedStateOf
    val pendingMessages by remember(messages) {
        derivedStateOf {
            try {
                messages.filter { it.status != "SENT" && it.status != "DISMISSED" }
            } catch (e: Exception) {
                Log.e(TAG, "Error filtering pending messages", e)
                emptyList()
            }
        }
    }

    // Compute pending counts per bucket - cached with derivedStateOf
    val pendingCounts by remember(pendingMessages) {
        derivedStateOf {
            try {
                pendingMessages
                    .groupBy { MessageBucket.fromString(it.bucket) }
                    .mapValues { it.value.size }
            } catch (e: Exception) {
                Log.e(TAG, "Error computing pending counts", e)
                emptyMap()
            }
        }
    }

    // Create a stable UI State holder (not dependent on specific message)
    val uiState = remember {
        OverlayUiState(createIdleMessage())
    }

    // Filter messages by selected bucket (if any) - cached with derivedStateOf
    val filteredMessages by remember(pendingMessages, uiState.selectedBucket) {
        derivedStateOf {
            try {
                val bucket = uiState.selectedBucket
                if (bucket == null) {
                    pendingMessages
                } else {
                    pendingMessages.filter { MessageBucket.fromString(it.bucket) == bucket }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error filtering messages by bucket", e)
                pendingMessages
            }
        }
    }

    // Get the active message to display - cached with derivedStateOf
    val activeMessage by remember(filteredMessages) {
        derivedStateOf {
            filteredMessages.lastOrNull()
        }
    }

    // Compute navigation state - cached with derivedStateOf
    val navigationState by remember(filteredMessages, activeMessage) {
        derivedStateOf {
            try {
                val currentIndex = if (activeMessage != null) {
                    filteredMessages.indexOfLast { it.id == activeMessage!!.id }
                } else -1
                val hasNext = currentIndex < filteredMessages.size - 1 && currentIndex >= 0
                val hasPrevious = currentIndex > 0
                Pair(hasNext, hasPrevious)
            } catch (e: Exception) {
                Log.e(TAG, "Error computing navigation state", e)
                Pair(false, false)
            }
        }
    }

    // Determine target message with fallback
    val targetMessage = activeMessage ?: createIdleMessage()

    // Effect: Update pending counts (with error handling)
    LaunchedEffect(pendingCounts) {
        try {
            uiState.updatePendingCounts(pendingCounts)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating pending counts", e)
        }
    }

    // Effect: Update state if the message content changes underneath (e.g. status update)
    LaunchedEffect(targetMessage.id, targetMessage.status) {
        try {
            uiState.updateMessage(targetMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating message", e)
        }
    }

    // Effect: Update navigation state
    LaunchedEffect(navigationState) {
        try {
            val (hasNext, hasPrevious) = navigationState
            uiState.updateNavigationState(hasNext, hasPrevious)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating navigation state", e)
        }
    }

    // Render the bubble (gracefully handles errors internally)
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
