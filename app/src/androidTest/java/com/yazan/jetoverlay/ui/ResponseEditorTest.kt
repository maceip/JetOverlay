package com.yazan.jetoverlay.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for ResponseEditor component.
 * Tests text input, cancel functionality, and "Use This" callback behavior.
 */
@RunWith(AndroidJUnit4::class)
class ResponseEditorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    //region Text Input Tests

    @Test
    fun responseEditor_displaysEditLabel() {
        composeTestRule.setContent {
            ResponseEditor(
                currentText = "",
                onTextChanged = {},
                onCancel = {},
                onUseThis = {}
            )
        }

        composeTestRule
            .onNodeWithText("Edit response:")
            .assertIsDisplayed()
    }

    @Test
    fun responseEditor_displaysPlaceholder() {
        composeTestRule.setContent {
            ResponseEditor(
                currentText = "",
                onTextChanged = {},
                onCancel = {},
                onUseThis = {}
            )
        }

        composeTestRule
            .onNodeWithText("Type your response...")
            .assertIsDisplayed()
    }

    @Test
    fun responseEditor_displaysPrePopulatedText() {
        composeTestRule.setContent {
            ResponseEditor(
                currentText = "Pre-filled response",
                onTextChanged = {},
                onCancel = {},
                onUseThis = {}
            )
        }

        composeTestRule
            .onNodeWithText("Pre-filled response")
            .assertIsDisplayed()
    }

    @Test
    fun responseEditor_textInputWorks() {
        var text by mutableStateOf("")

        composeTestRule.setContent {
            ResponseEditor(
                currentText = text,
                onTextChanged = { text = it },
                onCancel = {},
                onUseThis = {}
            )
        }

        composeTestRule
            .onNodeWithText("Type your response...")
            .performTextInput("Hello world")

        composeTestRule.waitForIdle()
        assertEquals("Hello world", text)
    }

    @Test
    fun responseEditor_textReplacementWorks() {
        var text by mutableStateOf("Original text")

        composeTestRule.setContent {
            ResponseEditor(
                currentText = text,
                onTextChanged = { text = it },
                onCancel = {},
                onUseThis = {}
            )
        }

        composeTestRule
            .onNodeWithText("Original text")
            .performTextReplacement("New text")

        composeTestRule.waitForIdle()
        assertEquals("New text", text)
    }

    @Test
    fun responseEditor_textClearanceWorks() {
        var text by mutableStateOf("Some text")

        composeTestRule.setContent {
            ResponseEditor(
                currentText = text,
                onTextChanged = { text = it },
                onCancel = {},
                onUseThis = {}
            )
        }

        composeTestRule
            .onNodeWithText("Some text")
            .performTextClearance()

        composeTestRule.waitForIdle()
        assertEquals("", text)
    }

    //endregion

    //region Cancel Button Tests

    @Test
    fun cancelButton_isDisplayed() {
        composeTestRule.setContent {
            ResponseEditor(
                currentText = "",
                onTextChanged = {},
                onCancel = {},
                onUseThis = {}
            )
        }

        composeTestRule
            .onNodeWithText("Cancel")
            .assertIsDisplayed()
    }

    @Test
    fun cancelButton_triggersCallback() {
        var cancelCalled = false

        composeTestRule.setContent {
            ResponseEditor(
                currentText = "Some text",
                onTextChanged = {},
                onCancel = { cancelCalled = true },
                onUseThis = {}
            )
        }

        composeTestRule
            .onNodeWithText("Cancel")
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue(cancelCalled)
    }

    @Test
    fun cancelButton_worksWithEmptyText() {
        var cancelCalled = false

        composeTestRule.setContent {
            ResponseEditor(
                currentText = "",
                onTextChanged = {},
                onCancel = { cancelCalled = true },
                onUseThis = {}
            )
        }

        composeTestRule
            .onNodeWithText("Cancel")
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue(cancelCalled)
    }

    //endregion

    //region Use This Button Tests

    @Test
    fun useThisButton_isDisplayed() {
        composeTestRule.setContent {
            ResponseEditor(
                currentText = "",
                onTextChanged = {},
                onCancel = {},
                onUseThis = {}
            )
        }

        composeTestRule
            .onNodeWithText("Use This")
            .assertIsDisplayed()
    }

    @Test
    fun useThisButton_disabledWhenEmpty() {
        composeTestRule.setContent {
            ResponseEditor(
                currentText = "",
                onTextChanged = {},
                onCancel = {},
                onUseThis = {}
            )
        }

        composeTestRule
            .onNodeWithText("Use This")
            .assertIsNotEnabled()
    }

    @Test
    fun useThisButton_disabledWhenBlank() {
        composeTestRule.setContent {
            ResponseEditor(
                currentText = "   ",
                onTextChanged = {},
                onCancel = {},
                onUseThis = {}
            )
        }

        composeTestRule
            .onNodeWithText("Use This")
            .assertIsNotEnabled()
    }

    @Test
    fun useThisButton_enabledWithText() {
        composeTestRule.setContent {
            ResponseEditor(
                currentText = "Valid text",
                onTextChanged = {},
                onCancel = {},
                onUseThis = {}
            )
        }

        composeTestRule
            .onNodeWithText("Use This")
            .assertIsEnabled()
    }

    @Test
    fun useThisButton_triggersCallback() {
        var useThisCalled = false

        composeTestRule.setContent {
            ResponseEditor(
                currentText = "My response",
                onTextChanged = {},
                onCancel = {},
                onUseThis = { useThisCalled = true }
            )
        }

        composeTestRule
            .onNodeWithText("Use This")
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue(useThisCalled)
    }

    //endregion

    //region Animated Response Editor Tests

    @Test
    fun animatedResponseEditor_visibleWhenTrue() {
        composeTestRule.setContent {
            AnimatedResponseEditor(
                isVisible = true,
                currentText = "Text",
                onTextChanged = {},
                onCancel = {},
                onUseThis = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Edit response:")
            .assertIsDisplayed()
    }

    @Test
    fun animatedResponseEditor_hiddenWhenFalse() {
        composeTestRule.setContent {
            AnimatedResponseEditor(
                isVisible = false,
                currentText = "Text",
                onTextChanged = {},
                onCancel = {},
                onUseThis = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Edit response:")
            .assertDoesNotExist()
    }

    @Test
    fun animatedResponseEditor_transitionsVisibility() {
        var isVisible by mutableStateOf(false)

        composeTestRule.setContent {
            AnimatedResponseEditor(
                isVisible = isVisible,
                currentText = "Transition test",
                onTextChanged = {},
                onCancel = {},
                onUseThis = {}
            )
        }

        // Initially hidden
        composeTestRule
            .onNodeWithText("Transition test")
            .assertDoesNotExist()

        // Make visible
        isVisible = true
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Transition test")
            .assertIsDisplayed()

        // Hide again
        isVisible = false
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Transition test")
            .assertDoesNotExist()
    }

    //endregion

    //region Integration with OverlayUiState Tests

    @Test
    fun integration_editingStateTriggersEditor() {
        val message = com.yazan.jetoverlay.data.Message(
            id = 1L,
            packageName = "com.test",
            senderName = "Sender",
            originalContent = "Original",
            generatedResponses = listOf("Response A")
        )
        val uiState = OverlayUiState(message)
        uiState.isExpanded = true
        uiState.isRevealed = true
        uiState.startEditing()

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Edit response:")
            .assertIsDisplayed()
    }

    @Test
    fun integration_cancelEditingReturnsToChips() {
        val message = com.yazan.jetoverlay.data.Message(
            id = 1L,
            packageName = "com.test",
            senderName = "Sender",
            originalContent = "Original",
            generatedResponses = listOf("Response A")
        )
        val uiState = OverlayUiState(message)
        uiState.isExpanded = true
        uiState.isRevealed = true
        uiState.startEditing()

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Cancel")
            .performClick()

        composeTestRule.waitForIdle()
        // Response chips should be visible again
        composeTestRule
            .onNodeWithText("Suggested responses:")
            .assertIsDisplayed()
    }

    @Test
    fun integration_useThisPreservesEditedResponse() {
        val message = com.yazan.jetoverlay.data.Message(
            id = 1L,
            packageName = "com.test",
            senderName = "Sender",
            originalContent = "Original",
            generatedResponses = listOf("Response A")
        )
        val uiState = OverlayUiState(message)
        uiState.isExpanded = true
        uiState.isRevealed = true
        uiState.startEditing()
        uiState.updateEditedResponse("Custom response")

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Use This")
            .performClick()

        composeTestRule.waitForIdle()
        assertEquals("Custom response", uiState.selectedResponse)
    }

    @Test
    fun integration_editPrePopulatesSelectedResponse() {
        val message = com.yazan.jetoverlay.data.Message(
            id = 1L,
            packageName = "com.test",
            senderName = "Sender",
            originalContent = "Original",
            generatedResponses = listOf("Response A", "Response B")
        )
        val uiState = OverlayUiState(message)
        uiState.isExpanded = true
        uiState.isRevealed = true
        uiState.selectResponse(1) // Select "Response B"
        uiState.startEditing()

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        // The editor should be pre-populated with "Response B"
        composeTestRule
            .onNodeWithText("Response B")
            .assertIsDisplayed()
    }

    //endregion
}
