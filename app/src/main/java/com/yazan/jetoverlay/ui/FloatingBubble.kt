package com.yazan.jetoverlay.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
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
import androidx.compose.ui.unit.dp
import com.yazan.jetoverlay.api.OverlaySdk
import com.yazan.jetoverlay.data.Message

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
                    onClick = { uiState.isExpanded = true }
                )
            }
        }
    }
}

@Composable
fun CollapsedBubbleView(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp) // Much smaller as requested
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF6200EE), Color(0xFF3700B3))
                )
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubble,
            contentDescription = "Open Chat",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
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
