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
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.api.OverlaySdk
import com.yazan.jetoverlay.domain.LiteRTLlmService
import com.yazan.jetoverlay.domain.LlmService
import com.yazan.jetoverlay.domain.MessageBucket
import com.yazan.jetoverlay.domain.ResponseSender
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

private const val TAG = "AgentOverlay"
private const val AUTO_REPLY_COUNTDOWN_SECONDS = 5

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
    val llmService = remember { LiteRTLlmService() as LlmService }
    val overlayHealth by OverlaySdk.overlayHealth.collectAsStateWithLifecycle()
    val autoReplyJob = remember { mutableStateOf<Job?>(null) }
    val countdownJob = remember { mutableStateOf<Job?>(null) }
    val pendingCountdownMessageId = remember { mutableStateOf<Long?>(null) }
    val automationEnabled = remember { SettingsManager.isAutomationEnabled(context) }
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }
    fun hapticTick() {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(15)
            }
        }
    }

    fun shouldSuggestEdit(message: Message): Boolean {
        val content = message.originalContent.lowercase()
        return content.contains("?") ||
            content.contains("urgent") ||
            content.contains("asap") ||
            content.contains("please") ||
            content.contains("can you") ||
            content.contains("need you")
    }

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
                val now = System.currentTimeMillis()
                messages.filter {
                    it.status != "SENT" &&
                        it.status != "DISMISSED" &&
                        (it.snoozedUntil == 0L || it.snoozedUntil <= now)
                }
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
    val uiState = remember { OverlayUiState(createIdleMessage()) }

    LaunchedEffect(overlayHealth.isDegraded, overlayHealth.message) {
        if (overlayHealth.isDegraded) {
            uiState.setSafeMode(overlayHealth.message)
        } else {
            uiState.clearSafeMode()
        }
    }

    LaunchedEffect(uiState.userInteracted, uiState.message.id) {
        if (uiState.userInteracted && uiState.message.id != 0L) {
            repository.markUserInteracted(uiState.message.id)
        }
    }

    fun cancelAutoReply() {
        autoReplyJob.value?.cancel()
        autoReplyJob.value = null
        uiState.clearCountdown()
    }
    fun cancelCountdown() {
        countdownJob.value?.cancel()
        countdownJob.value = null
        uiState.clearCountdown()
    }
    suspend fun autoReplyIfNeeded(msg: Message) {
        if (uiState.isSafeMode) {
            Logger.w(TAG, "Auto-reply suppressed: overlay in safe mode")
            return
        }
        val responseText = msg.generatedResponses.firstOrNull()
            ?: msg.veiledContent
            ?: "Received."
        val result = responseSender.sendResponse(msg, responseText)
        if (result is ResponseSender.SendResult.Success) {
            repository.markAsSent(msg.id)
        }
        uiState.clearGlow()
        uiState.isExpanded = false
        uiState.clearCountdown()
    }
    fun startCountdown(message: Message) {
        if (!automationEnabled) return
        if (uiState.isSafeMode) return
        cancelAutoReply()
        cancelCountdown()
        pendingCountdownMessageId.value = null
        autoReplyJob.value = scope.launch {
            hapticTick()
            for (remaining in AUTO_REPLY_COUNTDOWN_SECONDS downTo 1) {
                uiState.setCountdown(remaining)
                hapticTick()
                delay(1000)
                if (uiState.userInteracted || message.id != uiState.message.id || uiState.isExpanded || uiState.isEditing) {
                    uiState.clearCountdown()
                    return@launch
                }
            }
            uiState.clearCountdown()
            if (!uiState.userInteracted && !uiState.isExpanded && !uiState.isEditing) {
                autoReplyIfNeeded(message)
            }
        }
    }

    // Wire up callbacks to the real logic (idempotent assignments)
    uiState.onSendResponse = { text ->
        scope.launch {
            uiState.markInteracted()
            cancelAutoReply()
            cancelCountdown()
            val msgId = uiState.message.id
            if (msgId != 0L) {
                Log.i(TAG, "Sending response for message: $msgId")
                val result = responseSender.sendResponse(uiState.message, text)
                if (result is ResponseSender.SendResult.Success) {
                    repository.markAsSent(msgId)
                } else {
                    Log.e(
                        TAG,
                        "Failed to send response, keeping message pending: ${(result as ResponseSender.SendResult.Error).message}"
                    )
                }
                uiState.clearGlow()
                uiState.resetResponseSelection()
                uiState.isExpanded = false
            }
        }
    }

    uiState.onDismissMessage = {
        scope.launch {
            uiState.markInteracted()
            cancelAutoReply()
            cancelCountdown()
            val msgId = uiState.message.id
            if (msgId != 0L) {
                responseSender.markMessageAsRead(uiState.message)
                llmService.closeSession(uiState.message)
                val snoozeUntil = System.currentTimeMillis() + (20 * 60 * 1000L)
                repository.snoozeMessage(msgId, snoozeUntil)
                uiState.isExpanded = false
                uiState.clearGlow()
            }
        }
    }

    uiState.onNavigateToNextMessage = {
        Log.d(TAG, "Navigating to next message")
    }

    uiState.onNavigateToPreviousMessage = {
        Log.d(TAG, "Navigating to previous message")
    }

    uiState.onRegenerateResponses = {
        val msg = uiState.message
        if (msg.id != 0L) {
            scope.launch {
                if (uiState.isSafeMode) {
                    Logger.w(TAG, "Regeneration suppressed: overlay in safe mode")
                    return@launch
                }
                uiState.markInteracted()
                cancelAutoReply()
                cancelCountdown()
                uiState.startRegenerating()
                Log.i(TAG, "Regenerating responses for ${msg.id}")
                val bucket = MessageBucket.fromString(msg.bucket)
                val regenerated = try {
                    withTimeoutOrNull(2500L) {
                        llmService.generateResponses(msg, bucket)
                    } ?: emptyList()
                } catch (e: Exception) {
                    Log.e(TAG, "LLM regeneration failed, falling back", e)
                    emptyList()
                }.ifEmpty {
                    listOf(
                        "Got it, I'll follow up soon.",
                        "Received your message, replying shortly.",
                        "Thanks for reaching out—I'll respond in a bit."
                    )
                }
                repository.updateMessageState(
                    id = msg.id,
                    status = "GENERATED",
                    generatedResponses = regenerated
                )
                uiState.selectResponse(regenerated.indices.firstOrNull())       
                uiState.isEditing = false
                uiState.finishRegenerating()
            }
        }
    }

    // Track last surfaced message for glow/processing signals
    val lastMessageId = remember { mutableStateOf<Long?>(null) }
    val lastStatus = remember { mutableStateOf<String?>(null) }

    // --- Focus Management for IME ---
    // Toggle focusability when expanded or editing so the IME can appear
    LaunchedEffect(uiState.isExpanded, uiState.isEditing) {
        val config = com.yazan.jetoverlay.api.OverlayConfig(
            id = "agent_bubble",
            type = "overlay_1",
            initialX = 0,
            initialY = 120,
            isFocusable = uiState.isExpanded || uiState.isEditing
        )
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
        uiState.updatePendingCount(pendingMessages.size)
        uiState.updatePendingCounts(pendingCounts)
        if (targetMessage.status == "IDLE") {
            uiState.isRevealed = false
            uiState.resetResponseSelection()
            uiState.clearGlow()
            uiState.setIdle()
        } else if (targetMessage.generatedResponses.isNotEmpty()) {
            uiState.selectResponse(0)
            uiState.editedResponse = uiState.selectedResponse.orEmpty()
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

    // Sync derived counts for badges/attention
    LaunchedEffect(pendingCounts) {
        uiState.updatePendingCounts(pendingCounts)
    }
    LaunchedEffect(pendingMessages.size) {
        uiState.updatePendingCount(pendingMessages.size)
    }

    // Manage glow + processing indicators based on message lifecycle
    LaunchedEffect(targetMessage.id) {
        if (targetMessage.id == 0L) {
            uiState.clearGlow()
            uiState.setIdle()
            uiState.isExpanded = false
            uiState.resetInteraction()
            cancelAutoReply()
            cancelCountdown()
            return@LaunchedEffect
        }
        if (targetMessage.id != lastMessageId.value) {
            uiState.triggerGlow()
            if (automationEnabled && shouldSuggestEdit(targetMessage)) {
                hapticTick()
            }
            uiState.isExpanded = true
            if (targetMessage.status == "RECEIVED" || targetMessage.status == "PROCESSING") {
                uiState.setProcessing()
            }
            uiState.resetInteraction()
            cancelAutoReply()
            cancelCountdown()
            lastMessageId.value = targetMessage.id
        }
    }
    LaunchedEffect(targetMessage.status) {
        if (targetMessage.id == 0L) {
            uiState.clearGlow()
            uiState.setIdle()
            cancelAutoReply()
            cancelCountdown()
            return@LaunchedEffect
        }
        when (targetMessage.status) {
            "RECEIVED" -> uiState.setProcessing()
            "GENERATED", "VEILED", "PROCESSED" -> {
                uiState.setProcessingComplete()
                // Only start countdown once per status transition and only when not interacting
                if (!uiState.userInteracted && targetMessage.id != 0L && lastStatus.value != targetMessage.status) {
                    if (uiState.isExpanded || uiState.isEditing) {
                        pendingCountdownMessageId.value = targetMessage.id
                    } else {
                        startCountdown(targetMessage)
                    }
                }
            }
            "SENT", "DISMISSED", "IDLE" -> {
                uiState.clearGlow()
                uiState.setIdle()
                cancelAutoReply()
                cancelCountdown()
            }
        }
        lastStatus.value = targetMessage.status
    }

    // Cancel auto-reply when the user expands or interacts
    LaunchedEffect(uiState.isExpanded, uiState.userInteracted) {
        if (uiState.userInteracted || uiState.isExpanded) {
            cancelAutoReply()
        }
    }

    // If a countdown was deferred while the sheet/editor was open, start it when idle again
    LaunchedEffect(uiState.isExpanded, uiState.isEditing, uiState.message.id) {
        val pendingId = pendingCountdownMessageId.value
        if (!uiState.isExpanded && !uiState.isEditing && pendingId != null && pendingId == uiState.message.id) {
            startCountdown(uiState.message)
        }
    }
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
