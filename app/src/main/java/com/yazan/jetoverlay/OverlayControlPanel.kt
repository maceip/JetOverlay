package com.yazan.jetoverlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yazan.jetoverlay.api.OverlayConfig
import com.yazan.jetoverlay.api.OverlaySdk

// --- The Control Panel UI ---

@Composable
fun OverlayControlPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Observe Active Overlays from SDK
    val activeOverlays by OverlaySdk.activeOverlays.collectAsStateWithLifecycle()

    // 1. Check Overlay Permission
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    // 2. Check Notification Permission (Android 13+)
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Implicitly granted below API 33
            }
        )
    }

    // Launcher for Notification Permission
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
        }
    )

    // --- AUTO-REFRESH LOGIC (LifecycleResumeEffect) ---
    LifecycleResumeEffect(Unit) {
        val newOverlayPermission = Settings.canDrawOverlays(context)
        if (newOverlayPermission != hasOverlayPermission) {
            hasOverlayPermission = newOverlayPermission
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val newNotifPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (newNotifPermission != hasNotificationPermission) {
                hasNotificationPermission = newNotifPermission
            }
        }

        onPauseOrDispose { }
    }

    // --- AUTO-SHOW ON LAUNCH ---
    // If we have permissions, just show the agent bubble immediately so the user sees something.
    LaunchedEffect(hasOverlayPermission, hasNotificationPermission) {
         if (hasOverlayPermission && hasNotificationPermission) {
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
         }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
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

        when {
            // Priority 1: Overlay Permission
            !hasOverlayPermission -> {
                PermissionWarningCard(
                    title = "Overlay Permission Required",
                    text = "Tap to enable 'Display over other apps'",
                    icon = Icons.Rounded.Add
                ) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri()
                    )
                    context.startActivity(intent)
                }
            }

            // Priority 3: Runtime Permissions (Audio/Phone)
            !hasNotificationPermission -> {
                PermissionWarningCard(
                    title = "Notifications Required",
                    text = "Tap to enable notifications for the active service",
                    icon = Icons.Default.Notifications
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            // Priority 3: Notification Listener Access (Read/Reply)
            !Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty().contains(context.packageName) -> {
                PermissionWarningCard(
                    title = "Read/Reply Access Required",
                    text = "Tap to grant Notification Access for intelligence",
                    icon = Icons.Default.Notifications
                ) {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                }
            }

            // Priority 4: Runtime Permissions (Audio & Phone)
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED -> {
                
                val multiplePermissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { 
                        // Refresh will handle
                    }
                )

                PermissionWarningCard(
                    title = "Permissions Required",
                    text = "Allow Audio & Phone access for Agent features",
                    icon = Icons.Default.Call
                ) {
                    multiplePermissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.ANSWER_PHONE_CALLS,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_CONTACTS
                        )
                    )
                }
            }

            // Priority 3: Call Screening Role (Android 10+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !context.getSystemService(android.app.role.RoleManager::class.java).isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) -> {
                val roleLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                    onResult = {
                        // Refresh UI will handle state change
                        android.util.Log.d("OverlayControlPanel", "Role request result: $it")
                    }
                )

                PermissionWarningCard(
                    title = "Call Screening Required",
                    text = "Tap to set as Default Call Screening App",
                    icon = Icons.Default.Call
                ) {
                   try {
                       android.util.Log.d("OverlayControlPanel", "Requesting Call Screening Role")
                       val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                       val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING)
                       roleLauncher.launch(intent)
                   } catch (e: Exception) {
                       android.util.Log.e("OverlayControlPanel", "Failed to request role", e)
                       // Fallback to settings
                       val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                       context.startActivity(intent)
                   }
                }
            }

            // Priority 5: SMS Permissions
            context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED -> {
                val smsPermissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = {
                        // Refresh will handle
                    }
                )

                PermissionWarningCard(
                    title = "SMS Permissions Required",
                    text = "Allow SMS access to intercept text messages",
                    icon = Icons.Default.Email
                ) {
                    smsPermissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                        )
                    )
                }
            }

            // Priority 6: Active Status
            else -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().height(200.dp),
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
            }
        }
    }
}
// Removed OverlayOptionCard


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

// --- The Content Rendered inside the Overlay ---

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
        modifier = modifier // <--- SDK Modifier applied here
            .scale(scale)
            .size(100.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(color)
            .clickable {
                // Close on tap logic
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