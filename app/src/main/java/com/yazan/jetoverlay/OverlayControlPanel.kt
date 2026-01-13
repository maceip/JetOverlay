package com.yazan.jetoverlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yazan.jetoverlay.api.OverlayConfig
import com.yazan.jetoverlay.api.OverlaySdk
import com.yazan.jetoverlay.ui.OnboardingManager
import com.yazan.jetoverlay.ui.OnboardingScreen
import com.yazan.jetoverlay.ui.PermissionWizard
import com.yazan.jetoverlay.util.Logger
import com.yazan.jetoverlay.util.PermissionManager

// --- The Control Panel UI ---

/**
 * UI state for the control panel.
 */
private enum class ControlPanelState {
    ONBOARDING,      // First-run onboarding experience
    PERMISSION_SETUP, // Permission wizard (if user skipped onboarding somehow)
    MAIN_PANEL       // Main control panel
}

@Composable
fun OverlayControlPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Initialize PermissionManager
    val permissionManager = remember { PermissionManager(context) }

    // Observe Active Overlays from SDK
    val activeOverlays by OverlaySdk.activeOverlays.collectAsStateWithLifecycle()

    // Check first-run and onboarding status
    val isFirstLaunch = remember { OnboardingManager.isFirstLaunch(context) }
    val onboardingComplete = remember { mutableStateOf(OnboardingManager.isOnboardingComplete(context)) }

    // Track if all required permissions are granted
    var allRequiredPermissionsGranted by remember {
        mutableStateOf(permissionManager.areAllRequiredPermissionsGranted())
    }

    // Track if user has completed the wizard (either granted all or skipped optional)
    var wizardCompleted by remember { mutableStateOf(allRequiredPermissionsGranted) }

    // Determine current state
    val currentState = remember(onboardingComplete.value, allRequiredPermissionsGranted, wizardCompleted) {
        when {
            !onboardingComplete.value -> ControlPanelState.ONBOARDING
            !allRequiredPermissionsGranted || !wizardCompleted -> ControlPanelState.PERMISSION_SETUP
            else -> ControlPanelState.MAIN_PANEL
        }
    }

    // --- AUTO-REFRESH LOGIC (LifecycleResumeEffect) ---
    LifecycleResumeEffect(Unit) {
        // Re-check all permissions on resume (user may have changed in Settings)
        allRequiredPermissionsGranted = permissionManager.areAllRequiredPermissionsGranted()
        onboardingComplete.value = OnboardingManager.isOnboardingComplete(context)
        permissionManager.updateAllPermissionStatuses()
        onPauseOrDispose { }
    }

    // --- AUTO-SHOW ON LAUNCH ---
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(currentState) {
        if (currentState == ControlPanelState.MAIN_PANEL) {
            // Start the DataAcquisitionService when the UI is visible.
            // This is safe because the app is in the foreground.
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                try {
                    JetOverlayApplication.instance.startDataAcquisitionService()
                } catch (e: Exception) {
                    Logger.e("OverlayControlPanel", "Failed to start DataAcquisitionService", e)
                }
            }
            
            if (!OverlaySdk.isOverlayActive("agent_bubble")) {
                Logger.uiState("OverlayControlPanel", "Auto-showing agent bubble")
                OverlaySdk.show(
                    context = context,
                    config = OverlayConfig(
                        id = "agent_bubble",
                        type = "overlay_1",
                        initialX = 100,
                        initialY = 300
                    )
                )
            }
        }
    }

    // Main content with animated transition between states (Full Screen)
    AnimatedContent(
        targetState = currentState,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "control_panel_content",
        modifier = modifier.fillMaxSize()
    ) { state ->
        when (state) {
            ControlPanelState.ONBOARDING -> {
                OnboardingScreen(
                    permissionManager = permissionManager,
                    onOnboardingComplete = {
                        onboardingComplete.value = true
                        allRequiredPermissionsGranted = permissionManager.areAllRequiredPermissionsGranted()
                        wizardCompleted = allRequiredPermissionsGranted
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            ControlPanelState.PERMISSION_SETUP -> {
                PermissionWizard(
                    permissionManager = permissionManager,
                    onAllPermissionsGranted = {
                        allRequiredPermissionsGranted = true
                        wizardCompleted = true
                    },
                    onSkipOptional = {
                        wizardCompleted = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            ControlPanelState.MAIN_PANEL -> {
                MainControlPanel(
                    permissionManager = permissionManager,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Main control panel shown after all required permissions are granted.
 */
@Composable
private fun MainControlPanel(
    permissionManager: PermissionManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Overlay Agent",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Background Intelligence Service",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Active Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .testTag("status_card"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Agent is Listening",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Waiting for new messages...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Permission Status Summary (optional permissions)
        val optionalPermissions = PermissionManager.RequiredPermission.allOptional()
        val optionalStatuses = optionalPermissions.map { permissionManager.isPermissionGranted(it) }
        val grantedOptionalCount = optionalStatuses.count { it }

        if (grantedOptionalCount < optionalPermissions.size) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // Could navigate to permissions screen
                    }
                    .testTag("optional_permissions_card"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Optional Permissions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "$grantedOptionalCount of ${optionalPermissions.size} granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Integrations Section
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Integrations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                androidx.compose.material3.Button(
                    onClick = {
                        com.yazan.jetoverlay.service.integration.SlackIntegration.startOAuth(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect Slack")
                }
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        com.yazan.jetoverlay.service.integration.EmailIntegration.startOAuth(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Email")
                }
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        com.yazan.jetoverlay.service.integration.NotionIntegration.startOAuth(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect Notion")
                }
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        com.yazan.jetoverlay.service.integration.GitHubIntegration.startOAuth(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect GitHub")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Debug Button
        androidx.compose.material3.Button(
            onClick = {
                // Manually trigger the agent bubble for testing
                if (!OverlaySdk.isOverlayActive("agent_bubble")) {
                    OverlaySdk.show(
                        context = context,
                        config = OverlayConfig(
                            id = "agent_bubble",
                            type = "overlay_1",
                            initialX = 100,
                            initialY = 300
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test: Spawn Agent Bubble")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PermissionWarningCard(
    title: String,
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Add,
    onRequest: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().clickable { onRequest() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun OverlayShapeContent(
    modifier: Modifier,
    id: String,
    color: Color
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200)
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(100.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(color)
            .clickable {
                OverlaySdk.hide(id)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DRAG ME",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tap to Close",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}