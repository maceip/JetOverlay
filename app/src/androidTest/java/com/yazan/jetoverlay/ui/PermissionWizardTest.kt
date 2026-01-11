package com.yazan.jetoverlay.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yazan.jetoverlay.util.PermissionManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for PermissionWizard component.
 * Tests the permission wizard flow, step indicators, and user interactions.
 */
@RunWith(AndroidJUnit4::class)
class PermissionWizardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var permissionManager: PermissionManager

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        permissionManager = PermissionManager(context)
        // Clear any persisted state
        context.getSharedPreferences("jetoverlay_permissions", 0).edit().clear().apply()
    }

    @Test
    fun permissionWizard_showsSetupRequiredHeader() {
        composeTestRule.setContent {
            PermissionWizard(
                permissionManager = permissionManager,
                onAllPermissionsGranted = {},
                onSkipOptional = {}
            )
        }

        composeTestRule
            .onNodeWithText("Setup Required")
            .assertIsDisplayed()
    }

    @Test
    fun permissionWizard_showsProgressIndicator() {
        composeTestRule.setContent {
            PermissionWizard(
                permissionManager = permissionManager,
                onAllPermissionsGranted = {},
                onSkipOptional = {}
            )
        }

        composeTestRule
            .onNodeWithTag("progress_indicator")
            .assertIsDisplayed()
    }

    @Test
    fun permissionWizard_showsFirstPermissionCard() {
        composeTestRule.setContent {
            PermissionWizard(
                permissionManager = permissionManager,
                onAllPermissionsGranted = {},
                onSkipOptional = {}
            )
        }

        // The first required permission should be displayed
        composeTestRule
            .onNodeWithTag("permission_card_overlay")
            .assertIsDisplayed()
    }

    @Test
    fun permissionRequestCard_displaysGrantButton() {
        composeTestRule.setContent {
            PermissionRequestCard(
                permission = PermissionManager.RequiredPermission.OVERLAY,
                isGranted = false,
                deniedCount = 0,
                isOptional = false,
                onRequestPermission = {},
                onOpenSettings = {},
                onSkip = null
            )
        }

        composeTestRule
            .onNodeWithTag("grant_permission_button")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun permissionRequestCard_showsPermissionTitle() {
        composeTestRule.setContent {
            PermissionRequestCard(
                permission = PermissionManager.RequiredPermission.OVERLAY,
                isGranted = false,
                deniedCount = 0,
                isOptional = false,
                onRequestPermission = {},
                onOpenSettings = {},
                onSkip = null
            )
        }

        composeTestRule
            .onNodeWithText("Display Over Other Apps")
            .assertIsDisplayed()
    }

    @Test
    fun permissionRequestCard_showsPermissionDescription() {
        composeTestRule.setContent {
            PermissionRequestCard(
                permission = PermissionManager.RequiredPermission.OVERLAY,
                isGranted = false,
                deniedCount = 0,
                isOptional = false,
                onRequestPermission = {},
                onOpenSettings = {},
                onSkip = null
            )
        }

        composeTestRule
            .onNodeWithText(PermissionManager.RequiredPermission.OVERLAY.description)
            .assertIsDisplayed()
    }

    @Test
    fun permissionRequestCard_grantedState_showsCheckmark() {
        composeTestRule.setContent {
            PermissionRequestCard(
                permission = PermissionManager.RequiredPermission.NOTIFICATION_POST,
                isGranted = true,
                deniedCount = 0,
                isOptional = false,
                onRequestPermission = {},
                onOpenSettings = {},
                onSkip = null
            )
        }

        composeTestRule
            .onNodeWithText("Permission Granted")
            .assertIsDisplayed()
    }

    @Test
    fun permissionRequestCard_optional_showsOptionalLabel() {
        composeTestRule.setContent {
            PermissionRequestCard(
                permission = PermissionManager.RequiredPermission.RECORD_AUDIO,
                isGranted = false,
                deniedCount = 0,
                isOptional = true,
                onRequestPermission = {},
                onOpenSettings = {},
                onSkip = {}
            )
        }

        composeTestRule
            .onNodeWithText("(Optional)")
            .assertIsDisplayed()
    }

    @Test
    fun permissionRequestCard_optional_showsSkipButton() {
        composeTestRule.setContent {
            PermissionRequestCard(
                permission = PermissionManager.RequiredPermission.RECORD_AUDIO,
                isGranted = false,
                deniedCount = 0,
                isOptional = true,
                onRequestPermission = {},
                onOpenSettings = {},
                onSkip = {}
            )
        }

        composeTestRule
            .onNodeWithTag("skip_permission_button")
            .assertIsDisplayed()
    }

    @Test
    fun permissionRequestCard_clicksGrantButton() {
        var clicked = false

        composeTestRule.setContent {
            PermissionRequestCard(
                permission = PermissionManager.RequiredPermission.OVERLAY,
                isGranted = false,
                deniedCount = 0,
                isOptional = false,
                onRequestPermission = { clicked = true },
                onOpenSettings = {},
                onSkip = null
            )
        }

        composeTestRule
            .onNodeWithTag("grant_permission_button")
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue(clicked)
    }

    @Test
    fun permissionRequestCard_skipButtonClick() {
        var skipped = false

        composeTestRule.setContent {
            PermissionRequestCard(
                permission = PermissionManager.RequiredPermission.RECORD_AUDIO,
                isGranted = false,
                deniedCount = 0,
                isOptional = true,
                onRequestPermission = {},
                onOpenSettings = {},
                onSkip = { skipped = true }
            )
        }

        composeTestRule
            .onNodeWithTag("skip_permission_button")
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue(skipped)
    }

    @Test
    fun permissionRequestCard_deniedOnce_showsRationale() {
        composeTestRule.setContent {
            PermissionRequestCard(
                permission = PermissionManager.RequiredPermission.OVERLAY,
                isGranted = false,
                deniedCount = 1,
                isOptional = false,
                onRequestPermission = {},
                onOpenSettings = {},
                onSkip = null
            )
        }

        // Should show rationale text
        composeTestRule
            .onNodeWithText("This permission is required", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun permissionRequestCard_deniedTwice_showsOpenSettings() {
        composeTestRule.setContent {
            PermissionRequestCard(
                permission = PermissionManager.RequiredPermission.OVERLAY,
                isGranted = false,
                deniedCount = 2,
                isOptional = false,
                onRequestPermission = {},
                onOpenSettings = {},
                onSkip = null
            )
        }

        composeTestRule
            .onNodeWithText("Open Settings")
            .assertIsDisplayed()
    }

    @Test
    fun permissionStatusRow_displaysTitle() {
        composeTestRule.setContent {
            PermissionStatusRow(
                permission = PermissionManager.RequiredPermission.SMS,
                isGranted = false,
                onClick = {}
            )
        }

        composeTestRule
            .onNodeWithText("SMS Access")
            .assertIsDisplayed()
    }

    @Test
    fun permissionStatusRow_granted_showsGrantedText() {
        composeTestRule.setContent {
            PermissionStatusRow(
                permission = PermissionManager.RequiredPermission.RECORD_AUDIO,
                isGranted = true,
                onClick = {}
            )
        }

        composeTestRule
            .onNodeWithText("Granted")
            .assertIsDisplayed()
    }

    @Test
    fun permissionStatusRow_notGranted_showsNotGrantedText() {
        composeTestRule.setContent {
            PermissionStatusRow(
                permission = PermissionManager.RequiredPermission.RECORD_AUDIO,
                isGranted = false,
                onClick = {}
            )
        }

        composeTestRule
            .onNodeWithText("Not granted")
            .assertIsDisplayed()
    }

    @Test
    fun permissionStatusRow_clickTriggersCallback() {
        var clicked = false

        composeTestRule.setContent {
            PermissionStatusRow(
                permission = PermissionManager.RequiredPermission.PHONE,
                isGranted = false,
                onClick = { clicked = true }
            )
        }

        composeTestRule
            .onNodeWithText("Phone Access")
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue(clicked)
    }

    @Test
    fun stepDot_granted_showsCheck() {
        composeTestRule.setContent {
            PermissionRequestCard(
                permission = PermissionManager.RequiredPermission.OVERLAY,
                isGranted = true,
                deniedCount = 0,
                isOptional = false,
                onRequestPermission = {},
                onOpenSettings = {},
                onSkip = null
            )
        }

        // When granted, card background should change and show "Permission Granted"
        composeTestRule
            .onNodeWithText("Permission Granted")
            .assertIsDisplayed()
    }
}
