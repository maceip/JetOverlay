package com.yazan.jetoverlay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.service.notification.NotificationMapper
import kotlinx.coroutines.flow.Flow

/**
 * Unified Inbox Dashboard - Full-screen view of all messages grouped by context.
 *
 * Features:
 * - Group messages by context (Work, Personal, Social, Email, Other)
 * - Filter by context
 * - Batch actions: "Veil All", "Auto-Reply All"
 * - Select multiple messages for batch operations
 * - Shows reply status and pending actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedInboxDashboard(
    messagesFlow: Flow<List<Message>>,
    onVeilAll: (List<Long>) -> Unit,
    onAutoReplyAll: (List<Long>) -> Unit,
    onMessageClick: (Message) -> Unit,
    onDismissMessage: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by messagesFlow.collectAsState(initial = emptyList())

    // Filter only pending messages (not SENT or DISMISSED and not snoozed)
    val pendingMessages = remember(messages) {
        val now = System.currentTimeMillis()
        messages.filter {
            it.status != "SENT" &&
                it.status != "DISMISSED" &&
                (it.snoozedUntil == 0L || it.snoozedUntil <= now)
        }
    }

    // Group messages by context
    val messagesByContext = remember(pendingMessages) {
        pendingMessages.groupBy { message ->
            message.contextTag?.let { tag ->
                when (tag.lowercase()) {
                    "personal" -> MessageContext.PERSONAL
                    "work" -> MessageContext.WORK
                    "social" -> MessageContext.SOCIAL
                    "email" -> MessageContext.EMAIL
                    else -> MessageContext.OTHER
                }
            } ?: MessageContext.OTHER
        }
    }

    // Selected context filter
    var selectedContext by remember { mutableStateOf<MessageContext?>(null) }

    // Selected messages for batch operations
    val selectedMessages = remember { mutableStateMapOf<Long, Boolean>() }

    // Batch operation state
    var isBatchProcessing by remember { mutableStateOf(false) }
    var showBatchMenu by remember { mutableStateOf(false) }

    // Filtered messages based on selected context
    val filteredMessages = remember(pendingMessages, selectedContext) {
        if (selectedContext == null) {
            pendingMessages
        } else {
            messagesByContext[selectedContext] ?: emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Unified Inbox")
                        Text(
                            "${pendingMessages.size} pending messages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    // Select all button
                    if (filteredMessages.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val allSelected = filteredMessages.all { selectedMessages[it.id] == true }
                                filteredMessages.forEach { msg ->
                                    selectedMessages[msg.id] = !allSelected
                                }
                            }
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                    }

                    // Batch actions menu
                    Box {
                        IconButton(onClick = { showBatchMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Batch Actions")
                        }
                        DropdownMenu(
                            expanded = showBatchMenu,
                            onDismissRequest = { showBatchMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Veil All Selected") },
                                onClick = {
                                    showBatchMenu = false
                                    val ids = selectedMessages.filter { it.value }.keys.toList()
                                    if (ids.isNotEmpty()) {
                                        isBatchProcessing = true
                                        onVeilAll(ids)
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.VisibilityOff, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Auto-Reply All Selected") },
                                onClick = {
                                    showBatchMenu = false
                                    val ids = selectedMessages.filter { it.value }.keys.toList()
                                    if (ids.isNotEmpty()) {
                                        isBatchProcessing = true
                                        onAutoReplyAll(ids)
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Reply, contentDescription = null)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Context filter chips
            ContextFilterRow(
                contextCounts = messagesByContext.mapValues { it.value.size },
                selectedContext = selectedContext,
                onContextSelected = { context ->
                    selectedContext = if (selectedContext == context) null else context
                }
            )

            // Batch action bar (shown when items selected)
            AnimatedVisibility(
                visible = selectedMessages.any { it.value },
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                BatchActionBar(
                    selectedCount = selectedMessages.count { it.value },
                    isProcessing = isBatchProcessing,
                    onVeilAll = {
                        val ids = selectedMessages.filter { it.value }.keys.toList()
                        isBatchProcessing = true
                        onVeilAll(ids)
                    },
                    onAutoReplyAll = {
                        val ids = selectedMessages.filter { it.value }.keys.toList()
                        isBatchProcessing = true
                        onAutoReplyAll(ids)
                    },
                    onClearSelection = {
                        selectedMessages.clear()
                    }
                )
            }

            // Messages list grouped by context
            if (filteredMessages.isEmpty()) {
                EmptyInboxView(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // If no context filter, show grouped
                    if (selectedContext == null) {
                        MessageContext.values().forEach { context ->
                            val contextMessages = messagesByContext[context] ?: emptyList()
                            if (contextMessages.isNotEmpty()) {
                                item(key = "header_$context") {
                                    ContextHeader(
                                        context = context,
                                        count = contextMessages.size
                                    )
                                }
                                items(
                                    items = contextMessages,
                                    key = { it.id }
                                ) { message ->
                                    MessageCard(
                                        message = message,
                                        isSelected = selectedMessages[message.id] == true,
                                        onSelectionChanged = { selected ->
                                            selectedMessages[message.id] = selected
                                        },
                                        onClick = { onMessageClick(message) },
                                        onDismiss = { onDismissMessage(message.id) }
                                    )
                                }
                                item(key = "spacer_$context") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    } else {
                        // Filtered view - flat list
                        items(
                            items = filteredMessages,
                            key = { it.id }
                        ) { message ->
                            MessageCard(
                                message = message,
                                isSelected = selectedMessages[message.id] == true,
                                onSelectionChanged = { selected ->
                                    selectedMessages[message.id] = selected
                                },
                                onClick = { onMessageClick(message) },
                                onDismiss = { onDismissMessage(message.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextFilterRow(
    contextCounts: Map<MessageContext, Int>,
    selectedContext: MessageContext?,
    onContextSelected: (MessageContext) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(MessageContext.values().toList()) { context ->
            val count = contextCounts[context] ?: 0
            ContextFilterChip(
                context = context,
                count = count,
                isSelected = selectedContext == context,
                onClick = { onContextSelected(context) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextFilterChip(
    context: MessageContext,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(context.displayName)
                if (count > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Badge(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.primary
                    ) {
                        Text("$count")
                    }
                }
            }
        },
        leadingIcon = {
            Icon(
                imageVector = context.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = context.backgroundColor,
            selectedContainerColor = context.color
        )
    )
}

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    isProcessing: Boolean,
    onVeilAll: () -> Unit,
    onAutoReplyAll: () -> Unit,
    onClearSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                "$selectedCount selected",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onVeilAll,
                enabled = !isProcessing,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Veil All", style = MaterialTheme.typography.labelMedium)
            }

            Button(
                onClick = onAutoReplyAll,
                enabled = !isProcessing,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Auto-Reply", style = MaterialTheme.typography.labelMedium)
            }

            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = "Clear selection")
            }
        }
    }
}

@Composable
private fun ContextHeader(
    context: MessageContext,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = context.icon,
            contentDescription = null,
            tint = context.color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = context.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = context.color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "($count)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MessageCard(
    message: Message,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Checkbox for selection
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChanged,
                modifier = Modifier.padding(end = 8.dp)
            )

            // App icon placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.senderName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Message content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Status badge
                    StatusBadge(status = message.status)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Show veiled content if available, otherwise original
                Text(
                    text = message.veiledContent ?: message.originalContent,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Package name (app source)
                Text(
                    text = getAppDisplayName(message.packageName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Dismiss button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (color, icon) = when (status) {
        "RECEIVED" -> MaterialTheme.colorScheme.tertiary to Icons.Default.Email
        "VEILED" -> MaterialTheme.colorScheme.secondary to Icons.Default.VisibilityOff
        "GENERATED" -> MaterialTheme.colorScheme.primary to Icons.Default.Reply
        "QUEUED" -> Color(0xFFFF9800) to Icons.Default.CheckCircle
        "SENT" -> Color(0xFF4CAF50) to Icons.Default.Check
        else -> MaterialTheme.colorScheme.outline to Icons.Default.MoreVert
    }

    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = status.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun EmptyInboxView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Inbox Zero!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "All messages have been handled",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Gets a human-readable app name from package name.
 */
private fun getAppDisplayName(packageName: String): String {
    return when (packageName) {
        NotificationMapper.PKG_WHATSAPP -> "WhatsApp"
        NotificationMapper.PKG_SIGNAL -> "Signal"
        NotificationMapper.PKG_TELEGRAM -> "Telegram"
        NotificationMapper.PKG_MESSENGER -> "Messenger"
        NotificationMapper.PKG_SLACK -> "Slack"
        NotificationMapper.PKG_TEAMS -> "Teams"
        NotificationMapper.PKG_INSTAGRAM -> "Instagram"
        NotificationMapper.PKG_TWITTER -> "Twitter/X"
        NotificationMapper.PKG_GMAIL -> "Gmail"
        NotificationMapper.PKG_OUTLOOK -> "Outlook"
        else -> packageName.substringAfterLast('.')
    }
}

/**
 * Context categories for message grouping.
 */
enum class MessageContext(
    val displayName: String,
    val icon: ImageVector,
    val color: Color,
    val backgroundColor: Color
) {
    PERSONAL(
        displayName = "Personal",
        icon = Icons.Default.Person,
        color = Color(0xFF4CAF50),
        backgroundColor = Color(0xFFE8F5E9)
    ),
    WORK(
        displayName = "Work",
        icon = Icons.Default.Business,
        color = Color(0xFF2196F3),
        backgroundColor = Color(0xFFE3F2FD)
    ),
    SOCIAL(
        displayName = "Social",
        icon = Icons.Default.Person,
        color = Color(0xFF9C27B0),
        backgroundColor = Color(0xFFF3E5F5)
    ),
    EMAIL(
        displayName = "Email",
        icon = Icons.Default.Email,
        color = Color(0xFFFF5722),
        backgroundColor = Color(0xFFFBE9E7)
    ),
    OTHER(
        displayName = "Other",
        icon = Icons.Default.MoreVert,
        color = Color(0xFF607D8B),
        backgroundColor = Color(0xFFECEFF1)
    )
}
