package com.yazan.jetoverlay.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yazan.jetoverlay.api.OverlaySdk
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.domain.MessageBucket

@Composable
fun FloatingBubble(
    modifier: Modifier = Modifier,
    uiState: OverlayUiState
) {
    // Determine state: Are we expanded for a message?
    // We expand if we have a NEW message that hasn't been dismissed/sent.
    // For this prototype, we'll toggle based on the uiState "expanded" flag.
    
    // We use a Box to hold the content, applying the drag modifier passed from SDK
    Box(modifier = modifier) {
        AnimatedContent(
            targetState = uiState.isExpanded,
            transitionSpec = {
                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(initialScale = 0.8f) togetherWith
                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                scaleOut(targetScale = 0.8f)
            },
            label = "BubbleState"
        ) { isExpanded ->
            if (isExpanded) {
                ExpandedMessageView(
                    uiState = uiState,
                    onCollapse = { uiState.isExpanded = false }
                )
            } else {
                CollapsedBubbleView(
                    onClick = { uiState.isExpanded = true },
                    isProcessing = uiState.isProcessing,
                    isProcessingComplete = uiState.isProcessingComplete,
                    pendingCount = uiState.pendingMessageCount,
                    bucket = uiState.currentBucket
                )
            }
        }
    }
}

@Composable
fun CollapsedBubbleView(
    onClick: () -> Unit,
    isProcessing: Boolean = false,
    isProcessingComplete: Boolean = false,
    pendingCount: Int = 0,
    bucket: MessageBucket = MessageBucket.UNKNOWN
) {
    // Get the bucket color for border accent
    val bucketColor = Color(bucket.color)

    Box(
        modifier = Modifier.size(68.dp), // Slightly larger to accommodate border
        contentAlignment = Alignment.Center
    ) {
        // Main bubble with bucket-colored border
        Box(
            modifier = Modifier
                .size(60.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .border(
                    width = 3.dp,
                    color = bucketColor,
                    shape = CircleShape
                )
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF6200EE), Color(0xFF3700B3))
                    )
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Show different content based on processing state
            when {
                isProcessing -> {
                    // Show spinner when processing
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
                isProcessingComplete -> {
                    // Show checkmark when complete
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Processing Complete",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                else -> {
                    // Default chat bubble icon
                    Icon(
                        imageVector = Icons.Default.ChatBubble,
                        contentDescription = "Open Chat",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Pending message count badge (top-right)
        if (pendingCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935)), // Red badge
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (pendingCount > 99) "99+" else pendingCount.toString(),
                    color = Color.White,
                    fontSize = if (pendingCount > 99) 8.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ExpandedMessageView(
    uiState: OverlayUiState,
    onCollapse: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(320.dp) // Slightly wider for action buttons
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.message.senderName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onCollapse, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, "Close")
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
                    fadeIn(animationSpec = tween(200)) + slideInVertically { it / 4 } togetherWith
                    fadeOut(animationSpec = tween(200)) + slideOutVertically { -it / 4 }
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
                        fadeIn(animationSpec = tween(150)) togetherWith
                        fadeOut(animationSpec = tween(150))
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
                                onSendClick = { uiState.sendSelectedResponse() }
                            )
                        }
                    }
                }
            }
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
        animationSpec = tween(150),
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
    onSendClick: () -> Unit
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
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Regenerate", style = MaterialTheme.typography.labelSmall)
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
