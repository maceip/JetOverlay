package com.yazan.jetoverlay.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.yazan.jetoverlay.util.PermissionManager
import com.yazan.jetoverlay.util.PermissionManager.RequiredPermission
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yazan.jetoverlay.util.Logger

/**
 * Step-by-step permission wizard for onboarding users.
 * Shows clear explanations and guides through each permission request.
 */
@Composable
fun PermissionWizard(
    permissionManager: PermissionManager,
    onAllPermissionsGranted: () -> Unit,
    onSkipOptional: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Track current step (0-based index into permission list)
    var currentStepIndex by remember { mutableIntStateOf(0) }

    // Get all permissions to request (required first)
    val allPermissions = remember { RequiredPermission.allRequired() + RequiredPermission.allOptional() }
    val requiredCount = RequiredPermission.allRequired().size
    val optionalStartIndex = requiredCount
    val lastIndex = allPermissions.lastIndex

    // Track which optional permissions have been explicitly handled (granted or skipped)
    var handledOptionalIndices by remember { mutableStateOf(setOf<Int>()) }
    var optionalFinished by remember { mutableStateOf(false) }

    // Track permission statuses
    var permissionStatuses by remember {
        mutableStateOf(allPermissions.map { permissionManager.isPermissionGranted(it) })
    }

    // Track if we're in the optional section
    val isInOptionalSection = currentStepIndex >= requiredCount

    // Calculate progress
    val grantedCount = permissionStatuses.count { it }
    val progress by animateFloatAsState(
        targetValue = if (requiredCount == 0) 1f else grantedCount.toFloat() / requiredCount.toFloat(),
        animationSpec = tween(300),
        label = "progress"
    )

    // Check if all required permissions are granted
    val allRequiredGranted = permissionStatuses.take(requiredCount).all { it }

    // Launcher for runtime permissions
    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            val allGranted = results.values.all { it }
            if (!allGranted) {
                val currentPermission = allPermissions[currentStepIndex]
                permissionManager.recordPermissionDenied(currentPermission)
            }
            // Refresh status
            permissionStatuses = allPermissions.map { permissionManager.isPermissionGranted(it) }
        }
    )

    // Launcher for special permission intents
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            // Status will be refreshed on resume
        }
    )

    // Re-check permissions when resuming
    LifecycleResumeEffect(Unit) {
        permissionStatuses = allPermissions.map { permissionManager.isPermissionGranted(it) }
        permissionManager.updateAllPermissionStatuses()
        onPauseOrDispose { }
    }

    // Auto-advance required permissions; optional steps move forward only, never backward.
    LaunchedEffect(permissionStatuses, currentStepIndex) {
        if (optionalFinished) return@LaunchedEffect

        if (currentStepIndex < requiredCount && permissionStatuses[currentStepIndex]) {
            val nextMissingRequired = permissionStatuses.take(requiredCount).indexOfFirst { !it }
            if (nextMissingRequired != -1) {
                currentStepIndex = nextMissingRequired
            } else {
                onAllPermissionsGranted()
            }
        } else if (currentStepIndex >= optionalStartIndex && currentStepIndex <= lastIndex) {
            if (permissionStatuses[currentStepIndex]) {
                handledOptionalIndices = handledOptionalIndices + currentStepIndex
                val nextPending = (optionalStartIndex..lastIndex)
                    .firstOrNull { it !in handledOptionalIndices && !permissionStatuses[it] }

                if (nextPending != null) {
                    currentStepIndex = nextPending
                } else {
                    optionalFinished = true
                    onSkipOptional()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = if (isInOptionalSection) "Optional Permissions" else "Setup Required",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isInOptionalSection) {
                "Enable additional features (you can skip these)"
            } else {
                "Grant these permissions to use JetOverlay"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Progress indicator for required permissions
        if (!isInOptionalSection) {
            ProgressSection(
                progress = progress,
                grantedCount = permissionStatuses.take(requiredCount).count { it },
                totalCount = requiredCount
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Permission steps indicator - UPDATED: Shows continuous progress
        PermissionStepsIndicator(
            permissions = allPermissions,
            statuses = permissionStatuses,
            currentIndex = currentStepIndex
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Current permission card
        if (currentStepIndex < allPermissions.size) {
            val currentPermission = allPermissions[currentStepIndex]
            val isGranted = permissionStatuses[currentStepIndex]
            val deniedCount = permissionManager.getDeniedCount(currentPermission)

            AnimatedContent(
                targetState = currentStepIndex,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                },
                label = "permission_card"
            ) { stepIndex ->
                if (stepIndex < allPermissions.size) {
                    val permission = allPermissions[stepIndex]

                    PermissionRequestCard(
                        permission = permission,
                        isGranted = permissionStatuses[stepIndex],
                        deniedCount = permissionManager.getDeniedCount(permission),
                        isOptional = stepIndex >= requiredCount,
                        onRequestPermission = {
                            Logger.d("PermissionWizard", "Requesting permission: ${permission.title}")
                            if (permissionManager.requiresSettingsNavigation(permission)) {
                                when (permission) {
                                    RequiredPermission.OVERLAY -> {
                                        settingsLauncher.launch(permissionManager.getOverlayPermissionIntent())
                                    }
                                    RequiredPermission.NOTIFICATION_LISTENER -> {
                                        settingsLauncher.launch(permissionManager.getNotificationListenerSettingsIntent())
                                    }
                                    RequiredPermission.CALL_SCREENING -> {
                                        Logger.d("PermissionWizard", "Getting Call Screening intent")
                                        val intent = permissionManager.getCallScreeningRoleIntent()
                                        if (intent != null) {
                                            Logger.d("PermissionWizard", "Launching Call Screening intent: $intent")
                                            try {
                                                settingsLauncher.launch(intent)
                                            } catch (e: Exception) {
                                                Logger.e("PermissionWizard", "Failed to launch role intent", e)
                                                settingsLauncher.launch(permissionManager.getAppSettingsIntent())
                                            }
                                        } else {
                                            Logger.d("PermissionWizard", "Call Screening intent is null, using fallback")
                                            settingsLauncher.launch(permissionManager.getAppSettingsIntent())
                                        }
                                    }
                                    else -> {
                                        settingsLauncher.launch(permissionManager.getAppSettingsIntent())
                                    }
                                }
                            } else {
                                val runtimePermissions = permissionManager.getRuntimePermissions(permission)
                                if (runtimePermissions.isNotEmpty()) {
                                    runtimePermissionLauncher.launch(runtimePermissions)
                                }
                            }
                        },
                        onOpenSettings = {
                            settingsLauncher.launch(permissionManager.getAppSettingsIntent())
                        },
                        onSkip = if (stepIndex >= requiredCount) {
                            {
                                // Skip logic: Advance to next step regardless of grant status
                                handledOptionalIndices = handledOptionalIndices + stepIndex
                                val nextPending = (optionalStartIndex..lastIndex)
                                    .firstOrNull { it !in handledOptionalIndices && !permissionStatuses[it] }

                                if (nextPending != null) {
                                    currentStepIndex = nextPending
                                } else {
                                    optionalFinished = true
                                    onSkipOptional()
                                }
                            }
                        } else null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Manual Navigation Buttons (to prevent getting stuck)
        if (isInOptionalSection) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val nextPending = (optionalStartIndex..lastIndex)
                    .firstOrNull { it !in handledOptionalIndices && !permissionStatuses[it] }

                if (nextPending != null && nextPending <= lastIndex) {
                    TextButton(onClick = { currentStepIndex = nextPending }) {
                        Text("Next")
                    }
                } else {
                     Button(onClick = {
                         optionalFinished = true
                         onSkipOptional()
                     }) {
                        Text("Finish")
                    }
                }
            }
        }
    }
}

@Composable
fun FinalCompletionScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Purple Overlay Graphic
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFF6200EE), Color(0xFF3700B3))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "You're All Set!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "The app will now go into auto mode.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "The overlay will appear automatically when you receive messages.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Start", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ProgressSection(
    progress: Float,
    grantedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .testTag("progress_indicator"),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "$grantedCount of $totalCount required permissions granted",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionStepsIndicator(
    permissions: List<RequiredPermission>,
    statuses: List<Boolean>,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        permissions.forEachIndexed { index, permission ->
            val isGranted = statuses.getOrNull(index) ?: false
            val isCurrent = index == currentIndex

            PermissionStepDot(
                isGranted = isGranted,
                isCurrent = isCurrent,
                icon = getPermissionIcon(permission)
            )

            if (index < permissions.size - 1) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(
                            if (isGranted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

@Composable
private fun PermissionStepDot(
    isGranted: Boolean,
    isCurrent: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isGranted -> MaterialTheme.colorScheme.primary
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val iconTint = when {
        isGranted -> MaterialTheme.colorScheme.onPrimary
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .size(if (isCurrent) 48.dp else 36.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .testTag("step_dot_$isGranted"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.Check else icon,
            contentDescription = if (isGranted) "Granted" else "Pending",
            tint = iconTint,
            modifier = Modifier.size(if (isCurrent) 24.dp else 18.dp)
        )
    }
}

@Composable
fun PermissionRequestCard(
    permission: RequiredPermission,
    isGranted: Boolean,
    deniedCount: Int,
    isOptional: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val showRationale = deniedCount >= 1
    val showOpenSettings = deniedCount >= 2

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("permission_card_${permission.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else getPermissionIcon(permission),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = if (isGranted) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = permission.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isGranted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            if (isOptional) {
                Text(
                    text = "(Optional)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = permission.description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isGranted) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )

            // Rationale for denied permissions
            AnimatedVisibility(visible = showRationale && !isGranted) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "This permission is required for ${permission.title.lowercase()}. " +
                                    "The app cannot function properly without it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action buttons
            if (isGranted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Permission Granted",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = if (showOpenSettings) onOpenSettings else onRequestPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_permission_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (showOpenSettings) Icons.Default.Settings else Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showOpenSettings) "Open Settings" else "Grant Permission"
                        )
                    }

                    if (onSkip != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.testTag("skip_permission_button")
                        ) {
                            Text("Skip for now")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Get an appropriate icon for each permission type.
 */
private fun getPermissionIcon(permission: RequiredPermission): ImageVector {
    return when (permission) {
        RequiredPermission.OVERLAY -> Icons.Rounded.Add
        RequiredPermission.NOTIFICATION_POST -> Icons.Default.Notifications
        RequiredPermission.NOTIFICATION_LISTENER -> Icons.Default.Notifications
        RequiredPermission.RECORD_AUDIO -> Icons.Default.Lock
        RequiredPermission.PHONE -> Icons.Default.Call
        RequiredPermission.CALL_SCREENING -> Icons.Default.Call
        RequiredPermission.SMS -> Icons.Default.Email
    }
}

/**
 * Compact permission status row for settings screen.
 */
@Composable
fun PermissionStatusRow(
    permission: RequiredPermission,
    isGranted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = getPermissionIcon(permission),
            contentDescription = null,
            tint = if (isGranted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = permission.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isGranted) "Granted" else "Not granted",
                style = MaterialTheme.typography.bodySmall,
                color = if (isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }

        Icon(
            imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Settings,
            contentDescription = if (isGranted) "Granted" else "Tap to grant",
            tint = if (isGranted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
    }
}
