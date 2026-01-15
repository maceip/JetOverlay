package com.yazan.jetoverlay.ui

import android.util.Log
import com.yazan.jetoverlay.util.Logger
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures        
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import com.skydoves.flexible.bottomsheet.material3.FlexibleBottomSheet
import com.skydoves.flexible.core.FlexibleSheetSize
import com.skydoves.flexible.core.rememberFlexibleBottomSheetState
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.domain.MessageBucket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "FloatingBubble"
private const val FAST_ANIM_1 = 80
private const val FAST_ANIM_2 = 120
private const val FAST_ANIM_3 = 160

@Composable
fun FloatingBubble(
    modifier: Modifier = Modifier,
    uiState: OverlayUiState
) {
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(uiState.countdownSeconds) {
        if (uiState.countdownSeconds in 1..5) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)      
        }
    }

    LaunchedEffect(uiState.message.id) {
        if (uiState.message.id == 0L) {
            uiState.isExpanded = false
        }
    }

    // Always-on collapsed "tic tac" overlay that only responds to double taps
    Box(modifier = modifier) {
        CollapsedStatusOverlay(
            showKnightRider = uiState.shouldGlow || uiState.isProcessing,
            onDoubleTap = {
                uiState.markInteracted()
                uiState.clearGlow()
                val hasActiveWork = uiState.isProcessing ||
                        uiState.shouldGlow ||
                        uiState.message.status !in listOf("IDLE", "SENT", "DISMISSED")
                if (hasActiveWork) {
                    Logger.d("FloatingBubble", "Double-tap while active -> expanding inline sheet")
                    uiState.isExpanded = true
                } else {
                    // Launch full-screen settings/control panel when idle
                    Logger.d("FloatingBubble", "Double-tap while idle -> opening MainActivity")
                    val intent = android.content.Intent(
                        context,
                        com.yazan.jetoverlay.MainActivity::class.java
                    ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                    context.startActivity(intent)
                }
            }
        )

        InlineDetailSheet(
            visible = uiState.isExpanded,
            uiState = uiState,
            onDismiss = {
                uiState.markInteracted()
                uiState.clearGlow()
                uiState.dismissMessage()
                uiState.isExpanded = false
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CollapsedStatusOverlay(
    showKnightRider: Boolean,
    onDoubleTap: () -> Unit
) {
    val stripeOffset by rememberInfiniteTransition(label = "kitt").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (showKnightRider) 500 else 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stripeOffset"
    )
    val baseColor = Color(0xFF0D0D0D)
    val activeColor = Color(0xFFD40000) // bright red
    val isLight = !isSystemInDarkTheme()
    val strokeColor = if (isLight) Color(0xFF3B185F) else Color.White

    Box(
        modifier = Modifier
            .width(64.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(baseColor)
            .border(1.dp, strokeColor, RoundedCornerShape(9.dp))
            .graphicsLayer { alpha = if (showKnightRider) 0.95f else 0.8f }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onTap = { /* ignore single taps for pass-through intent */ }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (showKnightRider) {
                val gradient = Brush.linearGradient(
                    colors = listOf(baseColor, activeColor.copy(alpha = 0.9f), baseColor),
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end = androidx.compose.ui.geometry.Offset(x = stripeOffset * 400f, y = 0f)
                )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(gradient, alpha = 0.7f)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun InlineDetailSheet(
    visible: Boolean,
    uiState: OverlayUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberFlexibleBottomSheetState(
        isModal = false,
        skipSlightlyExpanded = true,
        skipIntermediatelyExpanded = true,
        flexibleSheetSize = FlexibleSheetSize(
            fullyExpanded = 0.9f,
            intermediatelyExpanded = 0.6f,
            slightlyExpanded = 0.3f
        )
    )

    LaunchedEffect(uiState.isEditing) {
        if (uiState.isEditing) {
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    if (visible) {
        LaunchedEffect(visible) { uiState.isExpanded = true }
        FlexibleBottomSheet(
            onDismissRequest = {
                uiState.isExpanded = false
                onDismiss()
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Transparent,
            dragHandle = null,
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isSafeMode) {
                    SafeModeBanner(uiState.safeModeMessage)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIconBadge(packageName = uiState.message.packageName)
                    Column {
                        Text("Incoming message", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            uiState.message.senderName.ifBlank { uiState.message.packageName.ifBlank { "System" } },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close sheet")
                    }
                }

                Text(
                    text = uiState.message.veiledContent ?: "Message veiled",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                if (uiState.message.generatedResponses.isNotEmpty()) {
                    Text(
                        text = "Suggestions",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.message.generatedResponses.forEachIndexed { index, suggestion ->
                            AssistChip(
                                onClick = { uiState.selectResponse(index) },
                                label = { Text(suggestion.take(60)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (uiState.selectedResponseIndex == index) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            )
                        }
                    }
                }

                if (uiState.isEditing) {
                    OutlinedTextField(
                        value = uiState.editedResponse,
                        onValueChange = { uiState.updateEditedResponse(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text("Edit response") },
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                uiState.useEditedResponse()
                                uiState.sendSelectedResponse()
                                keyboardController?.hide()
                                onDismiss()
                            }
                        )
                    )
                } else {
                    val responsePreview = uiState.selectedResponse ?: uiState.message.generatedResponses.firstOrNull() ?: "No generated response yet."
                    Text(
                        text = "Suggested response:\n$responsePreview",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.countdownSeconds > 0) {
                    Text(
                        text = "Auto-send in ${uiState.countdownSeconds}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { uiState.startEditing() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Edit") }
                    OutlinedButton(
                        onClick = { uiState.regenerateResponses() },
                        enabled = !uiState.isRegenerating,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.isRegenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Regenerate")
                        }
                    }
                    Button(
                        onClick = {
                            if (uiState.isEditing) {
                                uiState.useEditedResponse()
                            }
                            uiState.sendSelectedResponse()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.hasSelectedResponse
                    ) { Text("Send") }
                }
            }
        }
    }
}

@Composable
private fun SafeModeBanner(message: String?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Safe mode",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message ?: "The overlay hit a recoverable error. Actions are limited.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun AppIconBadge(packageName: String) {
    val context = LocalContext.current
    val label = remember(packageName) { packageName.takeLast(2).uppercase() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ExpandedMessageView(
    uiState: OverlayUiState,
    onCollapse: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Horizontal swipe offset for dismiss gesture
    val horizontalOffsetX = remember { Animatable(0f) }
    // Vertical swipe offset for navigation gesture
    val verticalOffsetY = remember { Animatable(0f) }

    // Threshold for triggering swipe action (in dp converted to px)
    val dismissThreshold = remember(density) { with(density) { 100.dp.toPx() } }
    val navigationThreshold = remember(density) { with(density) { 60.dp.toPx() } }

    // Alpha based on horizontal swipe for visual feedback - cached with derivedStateOf
    val dismissAlpha by remember {
        derivedStateOf { 1f - (abs(horizontalOffsetX.value) / (dismissThreshold * 1.5f)).coerceIn(0f, 0.5f) }
    }

    // Cache navigation state with derivedStateOf to prevent unnecessary recompositions
    val hasNext by remember { derivedStateOf { uiState.hasNextMessage } }
    val hasPrevious by remember { derivedStateOf { uiState.hasPreviousMessage } }

    Card(
        modifier = Modifier
            .offset { IntOffset(horizontalOffsetX.value.roundToInt(), verticalOffsetY.value.roundToInt()) }
            .graphicsLayer { alpha = dismissAlpha }
            .width(320.dp)
            .wrapContentHeight()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            try {
                                if (horizontalOffsetX.value < -dismissThreshold) {
                                    // Swipe left past threshold - dismiss
                                    horizontalOffsetX.animateTo(-size.width.toFloat())
                                    uiState.dismissMessage()
                                } else {
                                    // Snap back
                                    horizontalOffsetX.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in horizontal drag end", e)
                                // Try to snap back on error
                                try { horizontalOffsetX.snapTo(0f) } catch (_: Exception) {}
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            try {
                                horizontalOffsetX.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in horizontal drag cancel", e)
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        scope.launch {
                            try {
                                // Only allow left swipe (negative values) for dismiss
                                val newOffset = horizontalOffsetX.value + dragAmount
                                horizontalOffsetX.snapTo(newOffset.coerceAtMost(0f))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in horizontal drag", e)
                            }
                        }
                    }
                )
            }
            .pointerInput(hasNext, hasPrevious) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            try {
                                when {
                                    // Swipe up past threshold - go to next message
                                    verticalOffsetY.value < -navigationThreshold && uiState.hasNextMessage -> {
                                        verticalOffsetY.animateTo(-size.height.toFloat() / 2)
                                        uiState.navigateToNextMessage()
                                        verticalOffsetY.snapTo(0f)
                                    }
                                    // Swipe down past threshold - go to previous message
                                    verticalOffsetY.value > navigationThreshold && uiState.hasPreviousMessage -> {
                                        verticalOffsetY.animateTo(size.height.toFloat() / 2)
                                        uiState.navigateToPreviousMessage()
                                        verticalOffsetY.snapTo(0f)
                                    }
                                    else -> {
                                        // Snap back
                                        verticalOffsetY.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in vertical drag end", e)
                                try { verticalOffsetY.snapTo(0f) } catch (_: Exception) {}
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            try {
                                verticalOffsetY.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in vertical drag cancel", e)
                            }
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        scope.launch {
                            try {
                                // Allow swipe up if there's a next message, swipe down if there's a previous
                                val newOffset = verticalOffsetY.value + dragAmount
                                val constrainedOffset = when {
                                    newOffset < 0 && uiState.hasNextMessage -> newOffset.coerceAtLeast(-navigationThreshold * 1.5f)
                                    newOffset > 0 && uiState.hasPreviousMessage -> newOffset.coerceAtMost(navigationThreshold * 1.5f)
                                    else -> 0f
                                }
                                verticalOffsetY.snapTo(constrainedOffset)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in vertical drag", e)
                            }
                        }
                    }
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with sender name, dismiss (X) button, and collapse button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.message.senderName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // Dismiss button (X) - dismisses message without responding
                IconButton(
                    onClick = { uiState.dismissMessage() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss message",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Collapse button (minimize)
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Bucket Filter Tabs (only show if there are pending messages in multiple buckets)
            val bucketsWithMessages = uiState.bucketsWithPendingMessages
            if (bucketsWithMessages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                BucketFilterRow(
                    bucketsWithMessages = bucketsWithMessages,
                    pendingCounts = uiState.pendingCounts,
                    selectedBucket = uiState.selectedBucket,
                    onBucketSelected = { bucket -> uiState.selectBucket(bucket) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Veiled/Unveiled Content with smooth transition (tappable to reveal)
            AnimatedContent(
                targetState = uiState.message.id,
                transitionSpec = {
                    fadeIn(animationSpec = tween(FAST_ANIM_3)) + slideInVertically { it / 6 } togetherWith
                    fadeOut(animationSpec = tween(FAST_ANIM_3)) + slideOutVertically { -it / 6 }
                },
                label = "MessageContent"
            ) { _ ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { uiState.toggleReveal() }
                        .padding(12.dp)
                ) {
                    Column {
                        // "Tap to reveal" hint when veiled
                        if (!uiState.isRevealed) {
                            Text(
                                text = "Tap to reveal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(
                            text = uiState.displayContent,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.isRevealed) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Response chips and actions (only show when revealed and expanded)
            if (uiState.showActions && uiState.message.generatedResponses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                // Show either ResponseEditor (when editing) or ResponseChips (when not editing)
                AnimatedContent(
                    targetState = uiState.isEditing,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(FAST_ANIM_2)) togetherWith
                        fadeOut(animationSpec = tween(FAST_ANIM_2))
                    },
                    label = "ResponseEditorToggle"
                ) { isEditing ->
                    if (isEditing) {
                        // Show ResponseEditor when in editing mode
                        ResponseEditor(
                            currentText = uiState.editedResponse,
                            onTextChanged = { text -> uiState.updateEditedResponse(text) },
                            onCancel = { uiState.cancelEditing() },
                            onUseThis = { uiState.useEditedResponse() }
                        )
                    } else {
                        // Show response chips and action buttons when not editing
                        Column {
                            // Horizontal scrollable row of response chips
                            ResponseChipsRow(
                                responses = uiState.message.generatedResponses,
                                selectedIndex = uiState.selectedResponseIndex,
                                onResponseSelected = { index -> uiState.selectResponse(index) }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons row: Edit, Regenerate, Send
                            ActionButtonsRow(
                                hasSelectedResponse = uiState.hasSelectedResponse,
                                onEditClick = { uiState.startEditing() },
                                onRegenerateClick = { uiState.regenerateResponses() },
                                onSendClick = { uiState.sendSelectedResponse() },
                                isRegenerating = uiState.isRegenerating
                            )
                        }
                    }
                }
            }

            // Navigation indicators showing swipe hints
            if (uiState.hasNextMessage || uiState.hasPreviousMessage) {
                Spacer(modifier = Modifier.height(8.dp))
                SwipeNavigationIndicator(
                    hasNext = uiState.hasNextMessage,
                    hasPrevious = uiState.hasPreviousMessage
                )
            }
        }
    }
}

/**
 * Visual indicator showing available swipe directions for message navigation.
 */
@Composable
fun SwipeNavigationIndicator(
    hasNext: Boolean,
    hasPrevious: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasPrevious) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Swipe down for previous",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = when {
                hasNext && hasPrevious -> "Swipe up/down to navigate"
                hasNext -> "Swipe up for next"
                hasPrevious -> "Swipe down for previous"
                else -> ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        if (hasNext) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Swipe up for next",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Horizontal scrollable row of response chips.
 * Each chip is tappable to select that response.
 */
@Composable
fun ResponseChipsRow(
    responses: List<String>,
    selectedIndex: Int?,
    onResponseSelected: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Column {
        Text(
            text = "Suggested responses:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            responses.forEachIndexed { index, response ->
                ResponseChip(
                    response = response,
                    isSelected = selectedIndex == index,
                    onClick = { onResponseSelected(index) }
                )
            }
        }
    }
}

/**
 * Individual response chip with selection highlight.
 */
@Composable
fun ResponseChip(
    response: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent
    val borderColor = if (isSelected) primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val textColor = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface

    // Animate selection state
    val animatedBorderWidth by animateFloatAsState(
        targetValue = if (isSelected) 2f else 1f,
        animationSpec = tween(FAST_ANIM_1),
        label = "chipBorder"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(animatedBorderWidth.dp, borderColor, RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Show checkmark when selected
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = primaryColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = response,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

/**
 * Row of action buttons: Edit, Regenerate, Send.
 */
@Composable
fun ActionButtonsRow(
    hasSelectedResponse: Boolean,
    onEditClick: () -> Unit,
    onRegenerateClick: () -> Unit,
    onSendClick: () -> Unit,
    isRegenerating: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Edit button
        OutlinedButton(
            onClick = onEditClick,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Edit", style = MaterialTheme.typography.labelMedium)
        }

        // Regenerate button
        OutlinedButton(
            onClick = onRegenerateClick,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (isRegenerating) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Regenerating…", style = MaterialTheme.typography.labelSmall)
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Regenerate", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Send button (enabled only when response is selected)
        Button(
            onClick = onSendClick,
            enabled = hasSelectedResponse,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Send", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Horizontal row of bucket filter chips with badge counts.
 * Shows "All" chip plus a chip for each bucket that has pending messages.
 */
@Composable
fun BucketFilterRow(
    bucketsWithMessages: List<MessageBucket>,
    pendingCounts: Map<MessageBucket, Int>,
    selectedBucket: MessageBucket?,
    onBucketSelected: (MessageBucket?) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All" chip
        BucketChip(
            label = "All",
            count = pendingCounts.values.sum(),
            color = Color(0xFF6200EE), // Default purple
            isSelected = selectedBucket == null,
            onClick = { onBucketSelected(null) }
        )

        // Bucket-specific chips
        bucketsWithMessages.forEach { bucket ->
            val count = pendingCounts[bucket] ?: 0
            BucketChip(
                label = bucket.displayName,
                count = count,
                color = Color(bucket.color),
                isSelected = selectedBucket == bucket,
                onClick = { onBucketSelected(bucket) }
            )
        }
    }
}

/**
 * Individual bucket filter chip with count badge.
 */
@Composable
fun BucketChip(
    label: String,
    count: Int,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent
    val borderColor = if (isSelected) color else color.copy(alpha = 0.5f)
    val textColor = if (isSelected) color else MaterialTheme.colorScheme.onSurface

    // Animate selection state
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.7f,
        animationSpec = tween(150),
        label = "chipAlpha"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor.copy(alpha = animatedAlpha), RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = animatedAlpha),
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )

            // Count badge
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = if (isSelected) 0.8f else 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (count > 99) "99+" else count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = if (count > 99) 7.sp else 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
