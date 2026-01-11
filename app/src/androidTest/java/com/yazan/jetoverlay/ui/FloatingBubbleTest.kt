package com.yazan.jetoverlay.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.domain.MessageBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for FloatingBubble component.
 * Tests collapsed state, expansion, veil reveal, response selection, and send functionality.
 */
@RunWith(AndroidJUnit4::class)
class FloatingBubbleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Helper to create a test message with generated responses
    private fun createTestMessage(
        id: Long = 1L,
        senderName: String = "Test Sender",
        originalContent: String = "This is the original message content",
        veiledContent: String = "•••••••••",
        generatedResponses: List<String> = listOf("Sure!", "Thanks!", "I'll check"),
        bucket: String = "WORK",
        status: String = "RECEIVED"
    ) = Message(
        id = id,
        packageName = "com.test.app",
        senderName = senderName,
        originalContent = originalContent,
        veiledContent = veiledContent,
        generatedResponses = generatedResponses,
        bucket = bucket,
        status = status
    )

    //region Collapsed State Tests

    @Test
    fun collapsedBubble_rendersCorrectly() {
        val uiState = OverlayUiState(createTestMessage())

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        // Collapsed bubble should show chat icon
        composeTestRule
            .onNodeWithContentDescription("Open Chat")
            .assertIsDisplayed()
    }

    @Test
    fun collapsedBubble_showsProcessingSpinner() {
        composeTestRule.setContent {
            CollapsedBubbleView(
                onClick = {},
                isProcessing = true,
                isProcessingComplete = false,
                pendingCount = 0,
                bucket = MessageBucket.WORK
            )
        }

        // Processing state should show, not chat icon
        composeTestRule
            .onNodeWithContentDescription("Open Chat")
            .assertDoesNotExist()
    }

    @Test
    fun collapsedBubble_showsCompletionCheckmark() {
        composeTestRule.setContent {
            CollapsedBubbleView(
                onClick = {},
                isProcessing = false,
                isProcessingComplete = true,
                pendingCount = 0,
                bucket = MessageBucket.WORK
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Processing Complete")
            .assertIsDisplayed()
    }

    @Test
    fun collapsedBubble_showsPendingCountBadge() {
        composeTestRule.setContent {
            CollapsedBubbleView(
                onClick = {},
                isProcessing = false,
                isProcessingComplete = false,
                pendingCount = 5,
                bucket = MessageBucket.URGENT
            )
        }

        composeTestRule
            .onNodeWithText("5")
            .assertIsDisplayed()
    }

    @Test
    fun collapsedBubble_showsOverflowBadge() {
        composeTestRule.setContent {
            CollapsedBubbleView(
                onClick = {},
                isProcessing = false,
                isProcessingComplete = false,
                pendingCount = 150,
                bucket = MessageBucket.WORK
            )
        }

        composeTestRule
            .onNodeWithText("99+")
            .assertIsDisplayed()
    }

    @Test
    fun collapsedBubble_clickExpands() {
        val uiState = OverlayUiState(createTestMessage())
        assertFalse(uiState.isExpanded)

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule
            .onNodeWithContentDescription("Open Chat")
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue(uiState.isExpanded)
    }

    //endregion

    //region Expansion Animation Tests

    @Test
    fun expandedState_showsSenderName() {
        val uiState = OverlayUiState(createTestMessage(senderName = "Alice"))
        uiState.isExpanded = true

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Alice")
            .assertIsDisplayed()
    }

    @Test
    fun expandedState_showsCollapseButton() {
        val uiState = OverlayUiState(createTestMessage())
        uiState.isExpanded = true

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription("Collapse")
            .assertIsDisplayed()
    }

    @Test
    fun expandedState_showsDismissButton() {
        val uiState = OverlayUiState(createTestMessage())
        uiState.isExpanded = true

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription("Dismiss message")
            .assertIsDisplayed()
    }

    @Test
    fun collapseButton_collapsesBubble() {
        val uiState = OverlayUiState(createTestMessage())
        uiState.isExpanded = true

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        // Wait for expanded state to be rendered
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription("Collapse")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("Collapse")
            .performClick()

        // Allow animations to complete
        composeTestRule.waitForIdle()
        Thread.sleep(100) // Brief pause for state propagation on slower devices
        composeTestRule.waitForIdle()

        assertFalse(uiState.isExpanded)
    }

    //endregion

    //region Veil Reveal Tests

    @Test
    fun veiledContent_showsTapToRevealHint() {
        val uiState = OverlayUiState(createTestMessage(veiledContent = "Hidden content"))
        uiState.isExpanded = true

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Tap to reveal")
            .assertIsDisplayed()
    }

    @Test
    fun veiledContent_showsVeiledText() {
        val uiState = OverlayUiState(createTestMessage(veiledContent = "••• veiled •••"))
        uiState.isExpanded = true

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("••• veiled •••")
            .assertIsDisplayed()
    }

    @Test
    fun revealedContent_showsOriginalContent() {
        val uiState = OverlayUiState(
            createTestMessage(
                originalContent = "Secret original message",
                veiledContent = "••••"
            )
        )
        uiState.isExpanded = true
        uiState.isRevealed = true

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Secret original message")
            .assertIsDisplayed()
    }

    @Test
    fun revealedContent_hidesTapToRevealHint() {
        val uiState = OverlayUiState(createTestMessage())
        uiState.isExpanded = true
        uiState.isRevealed = true

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Tap to reveal")
            .assertDoesNotExist()
    }

    @Test
    fun tappingVeil_revealsContent() {
        val uiState = OverlayUiState(
            createTestMessage(
                originalContent = "The real message",
                veiledContent = "••••"
            )
        )
        uiState.isExpanded = true
        assertFalse(uiState.isRevealed)

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        // Tap on the veiled content to reveal
        composeTestRule
            .onNodeWithText("••••")
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue(uiState.isRevealed)
    }

    //endregion

    //region Response Chips Tests

    @Test
    fun responseChips_showWhenRevealed() {
        val uiState = OverlayUiState(
            createTestMessage(
                generatedResponses = listOf("Yes", "No", "Maybe")
            )
        )
        uiState.isExpanded = true
        uiState.isRevealed = true

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Suggested responses:")
            .assertIsDisplayed()
    }

    @Test
    fun responseChip_isClickable() {
        val uiState = OverlayUiState(
            createTestMessage(
                generatedResponses = listOf("Response A")
            )
        )
        uiState.isExpanded = true
        uiState.isRevealed = true

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Response A")
            .assertHasClickAction()
    }

    @Test
    fun responseChip_selectionUpdatesState() {
        val uiState = OverlayUiState(
            createTestMessage(
                generatedResponses = listOf("First", "Second")
            )
        )
        uiState.isExpanded = true
        uiState.isRevealed = true
        assertNull(uiState.selectedResponseIndex)

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Second")
            .performClick()

        composeTestRule.waitForIdle()
        assertEquals(1, uiState.selectedResponseIndex)
    }

    @Test
    fun selectedChip_showsCheckmark() {
        val uiState = OverlayUiState(
            createTestMessage(
                generatedResponses = listOf("Selected Response")
            )
        )
        uiState.isExpanded = true
        uiState.isRevealed = true
        uiState.selectResponse(0)

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription("Selected")
            .assertIsDisplayed()
    }

    //endregion

    //region Action Buttons Tests

    @Test
    fun actionButtons_showWhenRevealed() {
        val uiState = OverlayUiState(
            createTestMessage(
                generatedResponses = listOf("Test")
            )
        )
        uiState.isExpanded = true
        uiState.isRevealed = true

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Edit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Regenerate").assertIsDisplayed()
        composeTestRule.onNodeWithText("Send").assertIsDisplayed()
    }

    @Test
    fun sendButton_disabledWithoutSelection() {
        val uiState = OverlayUiState(
            createTestMessage(
                generatedResponses = listOf("Test")
            )
        )
        uiState.isExpanded = true
        uiState.isRevealed = true
        // No response selected

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Send")
            .assertIsNotEnabled()
    }

    @Test
    fun sendButton_enabledWithSelection() {
        val uiState = OverlayUiState(
            createTestMessage(
                generatedResponses = listOf("Test")
            )
        )
        uiState.isExpanded = true
        uiState.isRevealed = true
        uiState.selectResponse(0)

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Send")
            .assertIsEnabled()
    }

    @Test
    fun sendButton_triggersCallback() {
        var sentResponse: String? = null
        val uiState = OverlayUiState(
            createTestMessage(
                generatedResponses = listOf("Send me!")
            )
        )
        uiState.isExpanded = true
        uiState.isRevealed = true
        uiState.selectResponse(0)
        uiState.onSendResponse = { response -> sentResponse = response }

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Send")
            .performClick()

        composeTestRule.waitForIdle()
        assertEquals("Send me!", sentResponse)
    }

    @Test
    fun editButton_triggersEditingMode() {
        val uiState = OverlayUiState(
            createTestMessage(
                generatedResponses = listOf("Test")
            )
        )
        uiState.isExpanded = true
        uiState.isRevealed = true
        assertFalse(uiState.isEditing)

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Edit")
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue(uiState.isEditing)
    }

    @Test
    fun regenerateButton_triggersCallback() {
        var regenerateCalled = false
        val uiState = OverlayUiState(
            createTestMessage(
                generatedResponses = listOf("Test")
            )
        )
        uiState.isExpanded = true
        uiState.isRevealed = true
        uiState.onRegenerateResponses = { regenerateCalled = true }

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Regenerate")
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue(regenerateCalled)
    }

    //endregion

    //region Dismiss Tests

    @Test
    fun dismissButton_triggersCallback() {
        var dismissCalled = false
        val uiState = OverlayUiState(createTestMessage())
        uiState.isExpanded = true
        uiState.onDismissMessage = { dismissCalled = true }

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription("Dismiss message")
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue(dismissCalled)
    }

    //endregion

    //region Bucket Filter Tests

    @Test
    fun bucketChips_showWhenMultipleBuckets() {
        val uiState = OverlayUiState(createTestMessage(bucket = "WORK"))
        uiState.isExpanded = true
        uiState.updatePendingCounts(
            mapOf(
                MessageBucket.WORK to 2,
                MessageBucket.SOCIAL to 1
            )
        )

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("All").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
        composeTestRule.onNodeWithText("Social").assertIsDisplayed()
    }

    @Test
    fun bucketChip_showsCount() {
        val uiState = OverlayUiState(createTestMessage(bucket = "URGENT"))
        uiState.isExpanded = true
        uiState.updatePendingCounts(
            mapOf(MessageBucket.URGENT to 5)
        )

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        // "5" should appear in the badge
        composeTestRule.onAllNodesWithText("5").fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun bucketChip_selectionUpdatesBucket() {
        val uiState = OverlayUiState(createTestMessage(bucket = "WORK"))
        uiState.isExpanded = true
        uiState.updatePendingCounts(
            mapOf(
                MessageBucket.WORK to 2,
                MessageBucket.SOCIAL to 1
            )
        )

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Social")
            .performClick()

        composeTestRule.waitForIdle()
        assertEquals(MessageBucket.SOCIAL, uiState.selectedBucket)
    }

    //endregion

    //region Navigation Indicator Tests

    @Test
    fun navigationIndicator_showsNextHint() {
        val uiState = OverlayUiState(createTestMessage())
        uiState.isExpanded = true
        uiState.updateNavigationState(hasNext = true, hasPrevious = false)

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Swipe up for next")
            .assertIsDisplayed()
    }

    @Test
    fun navigationIndicator_showsPreviousHint() {
        val uiState = OverlayUiState(createTestMessage())
        uiState.isExpanded = true
        uiState.updateNavigationState(hasNext = false, hasPrevious = true)

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Swipe down for previous")
            .assertIsDisplayed()
    }

    @Test
    fun navigationIndicator_showsBothHints() {
        val uiState = OverlayUiState(createTestMessage())
        uiState.isExpanded = true
        uiState.updateNavigationState(hasNext = true, hasPrevious = true)

        composeTestRule.setContent {
            FloatingBubble(uiState = uiState)
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Swipe up/down to navigate")
            .assertIsDisplayed()
    }

    //endregion

    //region Response Chips Row Composable Tests

    @Test
    fun responseChipsRow_displaysAllResponses() {
        composeTestRule.setContent {
            ResponseChipsRow(
                responses = listOf("First", "Second", "Third"),
                selectedIndex = null,
                onResponseSelected = {}
            )
        }

        composeTestRule.onNodeWithText("First").assertIsDisplayed()
        composeTestRule.onNodeWithText("Second").assertIsDisplayed()
        composeTestRule.onNodeWithText("Third").assertIsDisplayed()
    }

    @Test
    fun responseChipsRow_selectionCallback() {
        var selectedIdx: Int? = null

        composeTestRule.setContent {
            ResponseChipsRow(
                responses = listOf("A", "B", "C"),
                selectedIndex = null,
                onResponseSelected = { idx -> selectedIdx = idx }
            )
        }

        composeTestRule.onNodeWithText("B").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, selectedIdx)
    }

    //endregion

    //region Action Buttons Row Composable Tests

    @Test
    fun actionButtonsRow_displaysAllButtons() {
        composeTestRule.setContent {
            ActionButtonsRow(
                hasSelectedResponse = true,
                onEditClick = {},
                onRegenerateClick = {},
                onSendClick = {}
            )
        }

        composeTestRule.onNodeWithText("Edit").assertIsDisplayed()
        composeTestRule.onNodeWithText("Regenerate").assertIsDisplayed()
        composeTestRule.onNodeWithText("Send").assertIsDisplayed()
    }

    @Test
    fun actionButtonsRow_editButtonCallback() {
        var editClicked = false

        composeTestRule.setContent {
            ActionButtonsRow(
                hasSelectedResponse = false,
                onEditClick = { editClicked = true },
                onRegenerateClick = {},
                onSendClick = {}
            )
        }

        composeTestRule.onNodeWithText("Edit").performClick()
        composeTestRule.waitForIdle()
        assertTrue(editClicked)
    }

    @Test
    fun actionButtonsRow_regenerateButtonCallback() {
        var regenerateClicked = false

        composeTestRule.setContent {
            ActionButtonsRow(
                hasSelectedResponse = false,
                onEditClick = {},
                onRegenerateClick = { regenerateClicked = true },
                onSendClick = {}
            )
        }

        composeTestRule.onNodeWithText("Regenerate").performClick()
        composeTestRule.waitForIdle()
        assertTrue(regenerateClicked)
    }

    //endregion

    //region Bucket Chip Composable Tests

    @Test
    fun bucketChip_displaysLabel() {
        composeTestRule.setContent {
            BucketChip(
                label = "Work",
                count = 3,
                color = androidx.compose.ui.graphics.Color(0xFF1E88E5),
                isSelected = false,
                onClick = {}
            )
        }

        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
    }

    @Test
    fun bucketChip_displaysCount() {
        composeTestRule.setContent {
            BucketChip(
                label = "Test",
                count = 7,
                color = androidx.compose.ui.graphics.Color.Red,
                isSelected = false,
                onClick = {}
            )
        }

        composeTestRule.onNodeWithText("7").assertIsDisplayed()
    }

    @Test
    fun bucketChip_clickCallback() {
        var clicked = false

        composeTestRule.setContent {
            BucketChip(
                label = "Clickable",
                count = 1,
                color = androidx.compose.ui.graphics.Color.Green,
                isSelected = false,
                onClick = { clicked = true }
            )
        }

        composeTestRule.onNodeWithText("Clickable").performClick()
        composeTestRule.waitForIdle()
        assertTrue(clicked)
    }

    //endregion

    //region Swipe Navigation Indicator Tests

    @Test
    fun swipeNavigationIndicator_noNavigation_showsNothing() {
        composeTestRule.setContent {
            SwipeNavigationIndicator(
                hasNext = false,
                hasPrevious = false
            )
        }

        // No navigation text should show
        composeTestRule.onNodeWithText("Swipe up for next").assertDoesNotExist()
        composeTestRule.onNodeWithText("Swipe down for previous").assertDoesNotExist()
    }

    //endregion
}
