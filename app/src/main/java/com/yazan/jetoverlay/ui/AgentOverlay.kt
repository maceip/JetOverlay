package com.yazan.jetoverlay.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.domain.MessageBucket
import com.yazan.jetoverlay.domain.ResponseSender
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val responseSender = remember { ResponseSender(context) }

    // Track any errors for graceful degradation
    var hasError by remember { mutableStateOf(false) }

    // Collect all messages from the DB with error handling
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
        OverlayUiState(createIdleMessage()).apply {
            // Wire up callbacks to the real logic
            onSendResponse = { text ->
                scope.launch {
                    val msgId = message.id
                    if (msgId != 0L) {
                        Log.i(TAG, "Sending response for message: $msgId")
                        val result = responseSender.sendResponse(msgId, text)
                        if (result is ResponseSender.SendResult.Success) {
                            repository.markAsSent(msgId)
                            resetResponseSelection()
                            isExpanded = false
                        } else {
                            Log.e(TAG, "Failed to send response: ${(result as ResponseSender.SendResult.Error).message}")
                        }
                    }
                }
            }

            onDismissMessage = {
                scope.launch {
                    val msgId = message.id
                    if (msgId != 0L) {
                        repository.dismiss(msgId)
                        isExpanded = false
                    }
                }
            }

            onNavigateToNextMessage = {
                // To navigate "next" (up), we can just filter or rely on the fact that
                // filteredMessages is a list and we want to move the focus.
                // In this simplified state model, the AgentOverlay always shows filteredMessages.lastOrNull().
                // To truly navigate, we would need an 'explicitIndex' in uiState.
                Log.d(TAG, "Navigating to next message")
            }

            onNavigateToPreviousMessage = {
                Log.d(TAG, "Navigating to previous message")
            }
            
            onRegenerateResponses = {
                // Determine which message to regenerate
                val msg = message
                if (msg.id != 0L) {
                    // For now, simpler simulation or call repository to re-trigger AI
                    Log.i(TAG, "Regenerating responses for ${msg.id}")
                    // In a real implementation: repository.regenerateResponses(msg.id)
                    // For prototype: Just clear and re-add dummy ones or notify user
                }
            }
        }
    }

    // --- Focus Management for IME ---
    // When editing state changes, update the OverlayConfig to toggle focusability
    LaunchedEffect(uiState.isEditing) {
        val config = com.yazan.jetoverlay.api.OverlayConfig(
            id = "agent_bubble",
            type = "overlay_1",
            initialX = 100,
            initialY = 300,
            isFocusable = uiState.isEditing // Toggle focus based on editing state
        )
        // Re-show with updated config to trigger service update
        com.yazan.jetoverlay.api.OverlaySdk.show(context, config)
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

    // Determine target message with fallback
    val targetMessage = activeMessage ?: createIdleMessage()

    // Effect: Sync the uiState.message with targetMessage when it changes
    LaunchedEffect(targetMessage) {
        uiState.message = targetMessage
        if (targetMessage.status == "IDLE") {
            uiState.isRevealed = false
            uiState.resetResponseSelection()
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
