package com.yazan.jetoverlay.ui.inbox

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.ui.swipe.MagneticSwipeableItem
import com.yazan.jetoverlay.ui.swipe.rememberMagneticSwipeController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SentInboxScreen(
    repository: MessageRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val allMessages by repository.allMessages.collectAsState(initial = emptyList())
    val sentMessages = remember(allMessages) {
        allMessages
            .filter { it.status == "SENT" }
            .sortedByDescending { it.timestamp }
    }
    var query by rememberSaveable { mutableStateOf("") }

    val filteredMessages = remember(sentMessages, query) {
        if (query.isBlank()) {
            sentMessages
        } else {
            val needle = query.trim().lowercase(Locale.getDefault())
            sentMessages.filter { message ->
                message.senderName.lowercase(Locale.getDefault()).contains(needle) ||
                    message.packageName.lowercase(Locale.getDefault()).contains(needle) ||
                    message.originalContent.lowercase(Locale.getDefault()).contains(needle) ||
                    (message.selectedResponse ?: message.generatedResponses.firstOrNull().orEmpty())
                        .lowercase(Locale.getDefault())
                        .contains(needle)
            }
        }
    }

    var selectedMessageId by rememberSaveable { mutableStateOf<Long?>(null) }

    val scope = rememberCoroutineScope()
    val swipeController = rememberMagneticSwipeController(
        items = filteredMessages,
        keySelector = { it.id.toString() },
        onDismiss = { key ->
            val id = key.toString().toLongOrNull()
            if (id != null) {
                scope.launch { repository.dismiss(id) }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sent Inbox")
                        Text(
                            "${filteredMessages.size} sent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isWide = maxWidth >= 840.dp

            LaunchedEffect(isWide, filteredMessages) {
                if (isWide && selectedMessageId == null && filteredMessages.isNotEmpty()) {
                    selectedMessageId = filteredMessages.first().id
                }
                if (selectedMessageId != null && filteredMessages.none { it.id == selectedMessageId }) {
                    selectedMessageId = if (isWide) filteredMessages.firstOrNull()?.id else null
                }
            }

            val selectedMessage = remember(filteredMessages, selectedMessageId) {
                filteredMessages.firstOrNull { it.id == selectedMessageId }
            }

            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    SentInboxList(
                        query = query,
                        onQueryChange = { query = it },
                        messages = filteredMessages,
                        selectedMessageId = selectedMessageId,
                        onMessageClick = { selectedMessageId = it },
                        swipeController = swipeController,
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                            .padding(end = 16.dp)
                    ) {
                        if (selectedMessage != null) {
                            SentMessageDetail(
                                message = selectedMessage,
                                appLabel = getAppLabel(context, selectedMessage.packageName)
                            )
                        } else {
                            EmptyDetailState()
                        }
                    }
                }
            } else {
                if (selectedMessage == null) {
                    SentInboxList(
                        query = query,
                        onQueryChange = { query = it },
                        messages = filteredMessages,
                        selectedMessageId = selectedMessageId,
                        onMessageClick = { selectedMessageId = it },
                        swipeController = swipeController,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SentMessageDetailInline(
                        message = selectedMessage,
                        appLabel = getAppLabel(context, selectedMessage.packageName),
                        onBack = { selectedMessageId = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun SentInboxSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        placeholder = { Text("Search by sender, app, or reply…") },
        singleLine = true
    )
}

@Composable
private fun SentInboxList(
    query: String,
    onQueryChange: (String) -> Unit,
    messages: List<Message>,
    selectedMessageId: Long?,
    onMessageClick: (Long) -> Unit,
    swipeController: com.yazan.jetoverlay.ui.swipe.MagneticSwipeController,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
    ) {
        SentInboxSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
        if (messages.isEmpty()) {
            EmptySentState()
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = messages, key = { it.id }) { message ->
                    MagneticSwipeableItem(
                        key = message.id.toString(),
                        controller = swipeController,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        SentMessageListItem(
                            message = message,
                            appLabel = getAppLabel(LocalContext.current, message.packageName),
                            isSelected = selectedMessageId == message.id,
                            onClick = { onMessageClick(message.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SentMessageListItem(
    message: Message,
    appLabel: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderAvatar(
                    name = message.senderName,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$appLabel · ${formatTimestamp(message.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = message.originalContent,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp)
            )
            val sent = message.selectedResponse
                ?: message.generatedResponses.firstOrNull()
                ?: ""
            if (sent.isNotBlank()) {
                Text(
                    text = sent,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SentMessageDetail(
    message: Message,
    appLabel: String,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val sentReply = message.selectedResponse
        ?: message.generatedResponses.firstOrNull()
        ?: ""

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderAvatar(
                    name = message.senderName,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$appLabel · ${formatTimestamp(message.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    if (sentReply.isNotBlank()) {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(sentReply))
                    }
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy reply")
                }
            }

            DetailSection(
                title = "Incoming",
                body = message.originalContent
            )
            if (sentReply.isNotBlank()) {
                DetailSection(
                    title = "Sent reply",
                    body = sentReply
                )
            }
            DetailSection(
                title = "Status",
                body = message.status
            )
        }
    }
}

@Composable
private fun SentMessageDetailInline(
    message: Message,
    appLabel: String,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Sent message",
                style = MaterialTheme.typography.titleMedium
            )
        }
        SentMessageDetail(
            message = message,
            appLabel = appLabel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    body: String,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = body.ifBlank { "—" },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SenderAvatar(
    name: String,
    modifier: Modifier = Modifier,
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun EmptySentState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No sent messages yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyDetailState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Select a message to see details.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getAppLabel(context: android.content.Context, packageName: String): String {
    return try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(appInfo).toString()
    } catch (e: Exception) {
        packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val relative = DateUtils.getRelativeTimeSpanString(
        timestamp,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
    val date = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    return "$relative · $date"
}
