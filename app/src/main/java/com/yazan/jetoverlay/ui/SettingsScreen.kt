package com.yazan.jetoverlay.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yazan.jetoverlay.util.Logger

/**
 * Settings manager for persisting user preferences.
 */
object SettingsManager {
    private const val PREFS_NAME = "jetoverlay_settings"

    // Integration toggles
    private const val KEY_SLACK_ENABLED = "integration_slack_enabled"
    private const val KEY_EMAIL_ENABLED = "integration_email_enabled"
    private const val KEY_NOTION_ENABLED = "integration_notion_enabled"
    private const val KEY_GITHUB_ENABLED = "integration_github_enabled"

    // Notification settings
    private const val KEY_CANCEL_NOTIFICATIONS = "notification_cancel"
    private const val KEY_VEIL_ENABLED = "veil_enabled"

    // Bubble settings
    private const val KEY_BUBBLE_POSITION_X = "bubble_position_x"
    private const val KEY_BUBBLE_POSITION_Y = "bubble_position_y"
    private const val KEY_REMEMBER_POSITION = "bubble_remember_position"

    // Integration toggle getters/setters
    fun isSlackEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SLACK_ENABLED, true)

    fun setSlackEnabled(context: Context, enabled: Boolean) {
        Logger.d("SettingsManager", "Slack enabled: $enabled")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SLACK_ENABLED, enabled).apply()
    }

    fun isEmailEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_EMAIL_ENABLED, true)

    fun setEmailEnabled(context: Context, enabled: Boolean) {
        Logger.d("SettingsManager", "Email enabled: $enabled")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_EMAIL_ENABLED, enabled).apply()
    }

    fun isNotionEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTION_ENABLED, true)

    fun setNotionEnabled(context: Context, enabled: Boolean) {
        Logger.d("SettingsManager", "Notion enabled: $enabled")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTION_ENABLED, enabled).apply()
    }

    fun isGitHubEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GITHUB_ENABLED, true)

    fun setGitHubEnabled(context: Context, enabled: Boolean) {
        Logger.d("SettingsManager", "GitHub enabled: $enabled")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_GITHUB_ENABLED, enabled).apply()
    }

    // Notification settings
    fun isCancelNotificationsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CANCEL_NOTIFICATIONS, true)

    fun setCancelNotificationsEnabled(context: Context, enabled: Boolean) {
        Logger.d("SettingsManager", "Cancel notifications: $enabled")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CANCEL_NOTIFICATIONS, enabled).apply()
    }

    fun isVeilEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VEIL_ENABLED, true)

    fun setVeilEnabled(context: Context, enabled: Boolean) {
        Logger.d("SettingsManager", "Veil enabled: $enabled")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VEIL_ENABLED, enabled).apply()
    }

    // Bubble position
    fun isRememberPositionEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMEMBER_POSITION, true)

    fun setRememberPositionEnabled(context: Context, enabled: Boolean) {
        Logger.d("SettingsManager", "Remember position: $enabled")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_REMEMBER_POSITION, enabled).apply()
    }

    fun saveBubblePosition(context: Context, x: Int, y: Int) {
        if (isRememberPositionEnabled(context)) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_BUBBLE_POSITION_X, x)
                .putInt(KEY_BUBBLE_POSITION_Y, y)
                .apply()
        }
    }

    fun getBubblePosition(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Pair(
            prefs.getInt(KEY_BUBBLE_POSITION_X, 100),
            prefs.getInt(KEY_BUBBLE_POSITION_Y, 300)
        )
    }
}

/**
 * Settings screen for user preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Integration states
    var slackEnabled by remember { mutableStateOf(SettingsManager.isSlackEnabled(context)) }
    var emailEnabled by remember { mutableStateOf(SettingsManager.isEmailEnabled(context)) }
    var notionEnabled by remember { mutableStateOf(SettingsManager.isNotionEnabled(context)) }
    var githubEnabled by remember { mutableStateOf(SettingsManager.isGitHubEnabled(context)) }

    // Notification states
    var cancelNotifications by remember { mutableStateOf(SettingsManager.isCancelNotificationsEnabled(context)) }
    var veilEnabled by remember { mutableStateOf(SettingsManager.isVeilEnabled(context)) }

    // Bubble states
    var rememberPosition by remember { mutableStateOf(SettingsManager.isRememberPositionEnabled(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Integrations Section
            SettingsSection(title = "Integrations") {
                SettingsToggleItem(
                    title = "Slack",
                    description = "Receive Slack messages through JetOverlay",
                    icon = Icons.Default.Email,
                    checked = slackEnabled,
                    onCheckedChange = {
                        slackEnabled = it
                        SettingsManager.setSlackEnabled(context, it)
                    }
                )
                HorizontalDivider()
                SettingsToggleItem(
                    title = "Email",
                    description = "Receive email notifications through JetOverlay",
                    icon = Icons.Default.Email,
                    checked = emailEnabled,
                    onCheckedChange = {
                        emailEnabled = it
                        SettingsManager.setEmailEnabled(context, it)
                    }
                )
                HorizontalDivider()
                SettingsToggleItem(
                    title = "Notion",
                    description = "Receive Notion notifications through JetOverlay",
                    icon = Icons.Default.Email,
                    checked = notionEnabled,
                    onCheckedChange = {
                        notionEnabled = it
                        SettingsManager.setNotionEnabled(context, it)
                    }
                )
                HorizontalDivider()
                SettingsToggleItem(
                    title = "GitHub",
                    description = "Receive GitHub notifications through JetOverlay",
                    icon = Icons.Default.Email,
                    checked = githubEnabled,
                    onCheckedChange = {
                        githubEnabled = it
                        SettingsManager.setGitHubEnabled(context, it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notifications Section
            SettingsSection(title = "Notifications") {
                SettingsToggleItem(
                    title = "Enable Veiling",
                    description = "Hide message content behind The Veil until you're ready",
                    icon = Icons.Default.Notifications,
                    checked = veilEnabled,
                    onCheckedChange = {
                        veilEnabled = it
                        SettingsManager.setVeilEnabled(context, it)
                    }
                )
                HorizontalDivider()
                SettingsToggleItem(
                    title = "Hide Notifications",
                    description = "Cancel original notifications when veiling (recommended)",
                    icon = Icons.Default.Notifications,
                    checked = cancelNotifications,
                    onCheckedChange = {
                        cancelNotifications = it
                        SettingsManager.setCancelNotificationsEnabled(context, it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bubble Section
            SettingsSection(title = "Floating Bubble") {
                SettingsToggleItem(
                    title = "Remember Position",
                    description = "Save bubble position between sessions",
                    icon = Icons.Default.Settings,
                    checked = rememberPosition,
                    onCheckedChange = {
                        rememberPosition = it
                        SettingsManager.setRememberPositionEnabled(context, it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Advanced Section
            SettingsSection(title = "Advanced") {
                SettingsClickableItem(
                    title = "Re-run Onboarding",
                    description = "See the welcome tour again",
                    icon = Icons.Default.Refresh,
                    onClick = {
                        OnboardingManager.resetOnboarding(context)
                        onNavigateBack()
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp)
            .testTag("settings_toggle_${title.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsClickableItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag("settings_item_${title.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
