package com.yazan.jetoverlay.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
            .width(300.dp) // Fixed width for message view
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

            Spacer(modifier = Modifier.height(8.dp))

            // Veiled/Unveiled Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { uiState.toggleReveal() }
                    .padding(12.dp)
            ) {
                Text(
                    text = uiState.displayContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.isRevealed) MaterialTheme.colorScheme.onSurface 
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Actions (Chips)
            if (uiState.showActions) {
                Spacer(modifier = Modifier.height(16.dp))
                uiState.message.generatedResponses.forEach { response ->
                    SuggestionChip(
                        onClick = { 
                            // TODO: Send Action
                        },
                        label = { Text(response) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
