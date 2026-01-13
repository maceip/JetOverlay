package com.yazan.jetoverlay.ui

import android.content.Context
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yazan.jetoverlay.util.Logger
import com.yazan.jetoverlay.util.PermissionManager

/**
 * Manager for tracking first-run state and onboarding completion.
 */
object OnboardingManager {
    private const val PREFS_NAME = "jetoverlay_onboarding"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    private const val KEY_FIRST_LAUNCH = "first_launch"

    /**
     * Check if this is the first launch of the app.
     */
    fun isFirstLaunch(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    /**
     * Check if onboarding has been completed.
     */
    fun isOnboardingComplete(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }

    /**
     * Mark onboarding as complete.
     */
    fun setOnboardingComplete(context: Context, complete: Boolean = true) {
        Logger.i("OnboardingManager", "Setting onboarding complete: $complete")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, complete)
            .putBoolean(KEY_FIRST_LAUNCH, false)
            .apply()
    }

    /**
     * Reset onboarding state (for settings "Re-run Onboarding" option).
     */
    fun resetOnboarding(context: Context) {
        Logger.i("OnboardingManager", "Resetting onboarding state")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, false)
            .apply()
    }
}

/**
 * Onboarding screen steps.
 */
private enum class OnboardingStep {
    WELCOME,
    VEIL_EXPLANATION,
    HOW_IT_WORKS,
    PERMISSIONS
}

/**
 * First-run onboarding screen that explains the app concept and guides through permissions.
 */
@Composable
fun OnboardingScreen(
    permissionManager: PermissionManager,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }
    val steps = OnboardingStep.entries

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
            },
            label = "onboarding_step",
            modifier = Modifier.fillMaxSize()
        ) { step ->
            when (steps[step]) {
                OnboardingStep.WELCOME -> WelcomeStep(
                    onNext = { currentStep++ }
                )
                OnboardingStep.VEIL_EXPLANATION -> VeilExplanationStep(
                    onNext = { currentStep++ },
                    onBack = { currentStep-- }
                )
                OnboardingStep.HOW_IT_WORKS -> HowItWorksStep(
                    onNext = { currentStep++ },
                    onBack = { currentStep-- }
                )
                OnboardingStep.PERMISSIONS -> PermissionWizard(
                    permissionManager = permissionManager,
                    onAllPermissionsGranted = {
                        OnboardingManager.setOnboardingComplete(context)
                        onOnboardingComplete()
                    },
                    onSkipOptional = {
                        OnboardingManager.setOnboardingComplete(context)
                        onOnboardingComplete()
                    }
                )
            }
        }

        // Step indicators at the top
        if (currentStep < steps.size - 1) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                StepIndicators(
                    currentStep = currentStep,
                    totalSteps = steps.size - 1, // Don't count permissions step
                    modifier = Modifier.padding(top = 48.dp)
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Icon/Logo placeholder
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Welcome to JetOverlay",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your personal message guardian",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "JetOverlay helps you manage incoming messages with less anxiety by gently hiding potentially stressful content until you're ready to see it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("welcome_next_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Get Started", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun VeilExplanationStep(
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "The Veil",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your shield against message anxiety",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Veil demonstration cards
        VeilDemoCard(
            title = "Incoming Message",
            originalContent = "Hey, we need to talk about what happened yesterday. I'm really upset...",
            veiledContent = "Someone has sent you a message",
            isVeiled = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        VeilDemoCard(
            title = "When You're Ready",
            originalContent = "Hey, we need to talk about what happened yesterday. I'm really upset...",
            veiledContent = null,
            isVeiled = false
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "The Veil hides the actual content of messages, showing you a gentle summary instead. When you're ready, simply tap to reveal the full message.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        NavigationButtons(
            onBack = onBack,
            onNext = onNext,
            nextText = "Continue"
        )
    }
}

@Composable
private fun VeilDemoCard(
    title: String,
    originalContent: String,
    veiledContent: String?,
    isVeiled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isVeiled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isVeiled) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = if (isVeiled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isVeiled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isVeiled && veiledContent != null) veiledContent else originalContent,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isVeiled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun HowItWorksStep(
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "How It Works",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Step 1
        HowItWorksItem(
            number = 1,
            icon = Icons.Default.Notifications,
            title = "Message Arrives",
            description = "When a new notification arrives, JetOverlay intercepts it before you see the stressful content."
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Step 2
        HowItWorksItem(
            number = 2,
            icon = Icons.Default.Lock,
            title = "Content is Veiled",
            description = "The message content is hidden behind The Veil, showing you only a gentle summary."
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Step 3
        HowItWorksItem(
            number = 3,
            icon = Icons.Rounded.Visibility,
            title = "Reveal When Ready",
            description = "Tap the floating bubble to reveal messages on your own terms, when you're ready."
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Step 4
        HowItWorksItem(
            number = 4,
            icon = Icons.Default.Check,
            title = "Respond Mindfully",
            description = "Use AI-suggested responses or write your own reply without feeling pressured."
        )

        Spacer(modifier = Modifier.weight(1f))

        NavigationButtons(
            onBack = onBack,
            onNext = onNext,
            nextText = "Set Up Permissions"
        )
    }
}

@Composable
private fun HowItWorksItem(
    number: Int,
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Number circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NavigationButtons(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .testTag("onboarding_back_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Back")
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .testTag("onboarding_next_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(nextText)
        }
    }
}

@Composable
private fun StepIndicators(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val isPast = index < currentStep

            Box(
                modifier = Modifier
                    .size(if (isActive) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isActive -> MaterialTheme.colorScheme.primary
                            isPast -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )
        }
    }
}
