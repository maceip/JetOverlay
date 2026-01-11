package com.yazan.jetoverlay.ui

import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.domain.MessageBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OverlayUiStateTest {

    private lateinit var testMessage: Message
    private lateinit var uiState: OverlayUiState

    @Before
    fun setup() {
        testMessage = Message(
            id = 1L,
            packageName = "com.test.app",
            senderName = "Test Sender",
            originalContent = "Original content",
            veiledContent = "Veiled content",
            bucket = "WORK"
        )
        uiState = OverlayUiState(testMessage)
    }

    // Processing state tests

    @Test
    fun `initial processing state is IDLE`() {
        assertEquals(ProcessingState.IDLE, uiState.processingState)
    }

    @Test
    fun `isProcessing returns false when IDLE`() {
        uiState.setIdle()
        assertFalse(uiState.isProcessing)
    }

    @Test
    fun `isProcessing returns true when PROCESSING`() {
        uiState.setProcessing()
        assertTrue(uiState.isProcessing)
    }

    @Test
    fun `isProcessing returns false when COMPLETE`() {
        uiState.setProcessingComplete()
        assertFalse(uiState.isProcessing)
    }

    @Test
    fun `isProcessingComplete returns true when COMPLETE`() {
        uiState.setProcessingComplete()
        assertTrue(uiState.isProcessingComplete)
    }

    @Test
    fun `isProcessingComplete returns false when IDLE`() {
        uiState.setIdle()
        assertFalse(uiState.isProcessingComplete)
    }

    @Test
    fun `isProcessingComplete returns false when PROCESSING`() {
        uiState.setProcessing()
        assertFalse(uiState.isProcessingComplete)
    }

    @Test
    fun `setProcessing transitions state to PROCESSING`() {
        uiState.setProcessing()
        assertEquals(ProcessingState.PROCESSING, uiState.processingState)
    }

    @Test
    fun `setProcessingComplete transitions state to COMPLETE`() {
        uiState.setProcessingComplete()
        assertEquals(ProcessingState.COMPLETE, uiState.processingState)
    }

    @Test
    fun `setIdle transitions state to IDLE`() {
        uiState.setProcessing()
        uiState.setIdle()
        assertEquals(ProcessingState.IDLE, uiState.processingState)
    }

    // Pending count tests

    @Test
    fun `initial pending count is zero`() {
        assertEquals(0, uiState.pendingMessageCount)
    }

    @Test
    fun `updatePendingCount sets correct count`() {
        uiState.updatePendingCount(5)
        assertEquals(5, uiState.pendingMessageCount)
    }

    @Test
    fun `updatePendingCount can set large counts`() {
        uiState.updatePendingCount(100)
        assertEquals(100, uiState.pendingMessageCount)
    }

    @Test
    fun `updatePendingCount can reset to zero`() {
        uiState.updatePendingCount(10)
        uiState.updatePendingCount(0)
        assertEquals(0, uiState.pendingMessageCount)
    }

    // Bucket color coding tests

    @Test
    fun `currentBucket returns correct bucket from message`() {
        assertEquals(MessageBucket.WORK, uiState.currentBucket)
    }

    @Test
    fun `currentBucket returns URGENT for urgent messages`() {
        val urgentMessage = testMessage.copy(bucket = "URGENT")
        uiState.updateMessage(urgentMessage)
        assertEquals(MessageBucket.URGENT, uiState.currentBucket)
    }

    @Test
    fun `currentBucket returns SOCIAL for social messages`() {
        val socialMessage = testMessage.copy(bucket = "SOCIAL")
        uiState.updateMessage(socialMessage)
        assertEquals(MessageBucket.SOCIAL, uiState.currentBucket)
    }

    @Test
    fun `currentBucket returns PROMOTIONAL for promotional messages`() {
        val promoMessage = testMessage.copy(bucket = "PROMOTIONAL")
        uiState.updateMessage(promoMessage)
        assertEquals(MessageBucket.PROMOTIONAL, uiState.currentBucket)
    }

    @Test
    fun `currentBucket returns UNKNOWN for unknown bucket string`() {
        val unknownMessage = testMessage.copy(bucket = "INVALID_BUCKET")
        uiState.updateMessage(unknownMessage)
        assertEquals(MessageBucket.UNKNOWN, uiState.currentBucket)
    }

    // Existing functionality tests (ensure no regressions)

    @Test
    fun `initial isRevealed is false`() {
        assertFalse(uiState.isRevealed)
    }

    @Test
    fun `initial isExpanded is false`() {
        assertFalse(uiState.isExpanded)
    }

    @Test
    fun `displayContent shows veiled content when not revealed`() {
        assertEquals("Veiled content", uiState.displayContent)
    }

    @Test
    fun `displayContent shows original content when revealed`() {
        uiState.toggleReveal()
        assertEquals("Original content", uiState.displayContent)
    }

    @Test
    fun `toggleReveal sets isRevealed to true`() {
        uiState.toggleReveal()
        assertTrue(uiState.isRevealed)
    }

    @Test
    fun `toggleReveal also expands the view`() {
        uiState.toggleReveal()
        assertTrue(uiState.isExpanded)
    }

    @Test
    fun `showActions is false when not revealed`() {
        uiState.isExpanded = true
        assertFalse(uiState.showActions)
    }

    @Test
    fun `showActions is true when revealed and expanded`() {
        uiState.isRevealed = true
        uiState.isExpanded = true
        assertTrue(uiState.showActions)
    }

    @Test
    fun `updateMessage updates the message`() {
        val newMessage = testMessage.copy(senderName = "New Sender")
        uiState.updateMessage(newMessage)
        assertEquals("New Sender", uiState.message.senderName)
    }

    // Bucket filtering tests

    @Test
    fun `initial selectedBucket is null`() {
        assertEquals(null, uiState.selectedBucket)
    }

    @Test
    fun `selectBucket sets selectedBucket correctly`() {
        uiState.selectBucket(MessageBucket.URGENT)
        assertEquals(MessageBucket.URGENT, uiState.selectedBucket)
    }

    @Test
    fun `selectBucket can be set to null to show all`() {
        uiState.selectBucket(MessageBucket.WORK)
        uiState.selectBucket(null)
        assertEquals(null, uiState.selectedBucket)
    }

    @Test
    fun `initial pendingCounts is empty map`() {
        assertEquals(emptyMap<MessageBucket, Int>(), uiState.pendingCounts)
    }

    @Test
    fun `updatePendingCounts sets pendingCounts correctly`() {
        val counts = mapOf(
            MessageBucket.URGENT to 2,
            MessageBucket.WORK to 5,
            MessageBucket.SOCIAL to 3
        )
        uiState.updatePendingCounts(counts)
        assertEquals(counts, uiState.pendingCounts)
    }

    @Test
    fun `updatePendingCounts updates total pendingMessageCount`() {
        val counts = mapOf(
            MessageBucket.URGENT to 2,
            MessageBucket.WORK to 5,
            MessageBucket.SOCIAL to 3
        )
        uiState.updatePendingCounts(counts)
        assertEquals(10, uiState.pendingMessageCount)
    }

    @Test
    fun `bucketsWithPendingMessages returns empty when no pending messages`() {
        assertEquals(emptyList<MessageBucket>(), uiState.bucketsWithPendingMessages)
    }

    @Test
    fun `bucketsWithPendingMessages returns only buckets with count greater than zero`() {
        val counts = mapOf(
            MessageBucket.URGENT to 2,
            MessageBucket.WORK to 0,
            MessageBucket.SOCIAL to 3
        )
        uiState.updatePendingCounts(counts)
        val result = uiState.bucketsWithPendingMessages
        assertEquals(2, result.size)
        assertTrue(result.contains(MessageBucket.URGENT))
        assertTrue(result.contains(MessageBucket.SOCIAL))
        assertFalse(result.contains(MessageBucket.WORK))
    }

    @Test
    fun `bucketsWithPendingMessages returns buckets sorted by ordinal`() {
        val counts = mapOf(
            MessageBucket.SOCIAL to 3,
            MessageBucket.URGENT to 2,
            MessageBucket.PROMOTIONAL to 1
        )
        uiState.updatePendingCounts(counts)
        val result = uiState.bucketsWithPendingMessages
        // URGENT (ordinal 0) should come before SOCIAL (ordinal 2) should come before PROMOTIONAL (ordinal 3)
        assertEquals(MessageBucket.URGENT, result[0])
        assertEquals(MessageBucket.SOCIAL, result[1])
        assertEquals(MessageBucket.PROMOTIONAL, result[2])
    }

    @Test
    fun `selectBucket changes between different buckets`() {
        uiState.selectBucket(MessageBucket.URGENT)
        assertEquals(MessageBucket.URGENT, uiState.selectedBucket)

        uiState.selectBucket(MessageBucket.SOCIAL)
        assertEquals(MessageBucket.SOCIAL, uiState.selectedBucket)

        uiState.selectBucket(MessageBucket.WORK)
        assertEquals(MessageBucket.WORK, uiState.selectedBucket)
    }

    @Test
    fun `updatePendingCounts with empty map sets zero pending count`() {
        uiState.updatePendingCount(10) // First set a count
        uiState.updatePendingCounts(emptyMap())
        assertEquals(0, uiState.pendingMessageCount)
    }

    @Test
    fun `updatePendingCounts overwrites previous counts`() {
        val firstCounts = mapOf(MessageBucket.URGENT to 5)
        val secondCounts = mapOf(MessageBucket.WORK to 3)

        uiState.updatePendingCounts(firstCounts)
        assertEquals(5, uiState.pendingMessageCount)

        uiState.updatePendingCounts(secondCounts)
        assertEquals(3, uiState.pendingMessageCount)
        assertEquals(secondCounts, uiState.pendingCounts)
    }

    // Response selection tests

    @Test
    fun `initial selectedResponseIndex is null`() {
        assertEquals(null, uiState.selectedResponseIndex)
    }

    @Test
    fun `selectResponse sets selectedResponseIndex correctly`() {
        uiState.selectResponse(0)
        assertEquals(0, uiState.selectedResponseIndex)
    }

    @Test
    fun `selectResponse with null clears selection`() {
        uiState.selectResponse(0)
        uiState.selectResponse(null)
        assertEquals(null, uiState.selectedResponseIndex)
    }

    @Test
    fun `selectResponse exits editing mode`() {
        uiState.startEditing()
        uiState.selectResponse(0)
        assertFalse(uiState.isEditing)
        assertEquals("", uiState.editedResponse)
    }

    @Test
    fun `selectedResponse returns response at selectedIndex`() {
        val messageWithResponses = testMessage.copy(
            generatedResponses = listOf("hello", "Got it!", "Thanks!")
        )
        uiState.updateMessage(messageWithResponses)
        uiState.selectResponse(1)
        assertEquals("Got it!", uiState.selectedResponse)
    }

    @Test
    fun `selectedResponse returns null when no selection`() {
        assertEquals(null, uiState.selectedResponse)
    }

    @Test
    fun `selectedResponse returns editedResponse when editing with content`() {
        uiState.isEditing = true
        uiState.editedResponse = "Custom response"
        assertEquals("Custom response", uiState.selectedResponse)
    }

    @Test
    fun `hasSelectedResponse is false when no response selected`() {
        assertFalse(uiState.hasSelectedResponse)
    }

    @Test
    fun `hasSelectedResponse is true when response is selected`() {
        val messageWithResponses = testMessage.copy(
            generatedResponses = listOf("hello", "Got it!")
        )
        uiState.updateMessage(messageWithResponses)
        uiState.selectResponse(0)
        assertTrue(uiState.hasSelectedResponse)
    }

    @Test
    fun `hasSelectedResponse is true when editing with content`() {
        uiState.isEditing = true
        uiState.editedResponse = "Custom"
        assertTrue(uiState.hasSelectedResponse)
    }

    // Editing mode tests

    @Test
    fun `initial isEditing is false`() {
        assertFalse(uiState.isEditing)
    }

    @Test
    fun `initial editedResponse is empty`() {
        assertEquals("", uiState.editedResponse)
    }

    @Test
    fun `startEditing sets isEditing to true`() {
        uiState.startEditing()
        assertTrue(uiState.isEditing)
    }

    @Test
    fun `startEditing pre-populates with selected response`() {
        val messageWithResponses = testMessage.copy(
            generatedResponses = listOf("hello", "Got it!")
        )
        uiState.updateMessage(messageWithResponses)
        uiState.selectResponse(1)
        uiState.startEditing()
        assertEquals("Got it!", uiState.editedResponse)
    }

    @Test
    fun `startEditing with no selection leaves editedResponse empty`() {
        uiState.startEditing()
        assertEquals("", uiState.editedResponse)
    }

    @Test
    fun `cancelEditing sets isEditing to false`() {
        uiState.startEditing()
        uiState.cancelEditing()
        assertFalse(uiState.isEditing)
    }

    @Test
    fun `cancelEditing clears editedResponse`() {
        uiState.startEditing()
        uiState.updateEditedResponse("Some text")
        uiState.cancelEditing()
        assertEquals("", uiState.editedResponse)
    }

    @Test
    fun `updateEditedResponse updates editedResponse`() {
        uiState.updateEditedResponse("New response text")
        assertEquals("New response text", uiState.editedResponse)
    }

    @Test
    fun `useEditedResponse clears selectedResponseIndex`() {
        uiState.selectResponse(0)
        uiState.startEditing()
        uiState.updateEditedResponse("Edited text")
        uiState.useEditedResponse()
        assertEquals(null, uiState.selectedResponseIndex)
    }

    @Test
    fun `useEditedResponse sets isEditing to false`() {
        uiState.startEditing()
        uiState.useEditedResponse()
        assertFalse(uiState.isEditing)
    }

    @Test
    fun `useEditedResponse sets useEditedResponseFlag to true`() {
        uiState.startEditing()
        uiState.updateEditedResponse("Some text")
        uiState.useEditedResponse()
        assertTrue(uiState.useEditedResponseFlag)
    }

    @Test
    fun `initial useEditedResponseFlag is false`() {
        assertFalse(uiState.useEditedResponseFlag)
    }

    @Test
    fun `selectResponse clears useEditedResponseFlag`() {
        uiState.startEditing()
        uiState.updateEditedResponse("Text")
        uiState.useEditedResponse()
        assertTrue(uiState.useEditedResponseFlag)

        uiState.selectResponse(0)
        assertFalse(uiState.useEditedResponseFlag)
    }

    @Test
    fun `resetResponseSelection clears all response state`() {
        uiState.selectResponse(0)
        uiState.startEditing()
        uiState.updateEditedResponse("Some text")
        uiState.useEditedResponse()
        uiState.resetResponseSelection()

        assertEquals(null, uiState.selectedResponseIndex)
        assertFalse(uiState.isEditing)
        assertEquals("", uiState.editedResponse)
        assertFalse(uiState.useEditedResponseFlag)
    }

    // Callback tests

    @Test
    fun `regenerateResponses invokes callback`() {
        var callbackInvoked = false
        uiState.onRegenerateResponses = { callbackInvoked = true }
        uiState.regenerateResponses()
        assertTrue(callbackInvoked)
    }

    @Test
    fun `regenerateResponses does nothing when callback is null`() {
        uiState.onRegenerateResponses = null
        uiState.regenerateResponses() // Should not throw
    }

    @Test
    fun `sendSelectedResponse invokes callback with response`() {
        var sentResponse: String? = null
        val messageWithResponses = testMessage.copy(
            generatedResponses = listOf("hello", "Got it!")
        )
        uiState.updateMessage(messageWithResponses)
        uiState.selectResponse(1)
        uiState.onSendResponse = { response -> sentResponse = response }
        uiState.sendSelectedResponse()
        assertEquals("Got it!", sentResponse)
    }

    @Test
    fun `sendSelectedResponse does nothing when no response selected`() {
        var callbackInvoked = false
        uiState.onSendResponse = { callbackInvoked = true }
        uiState.sendSelectedResponse()
        assertFalse(callbackInvoked)
    }

    @Test
    fun `sendSelectedResponse sends edited response when editing`() {
        var sentResponse: String? = null
        uiState.isEditing = true
        uiState.editedResponse = "Custom edited response"
        uiState.onSendResponse = { response -> sentResponse = response }
        uiState.sendSelectedResponse()
        assertEquals("Custom edited response", sentResponse)
    }

    // Edge case tests

    @Test
    fun `selectedResponse returns null for out of bounds index`() {
        val messageWithResponses = testMessage.copy(
            generatedResponses = listOf("hello")
        )
        uiState.updateMessage(messageWithResponses)
        uiState.selectedResponseIndex = 5 // Out of bounds
        assertEquals(null, uiState.selectedResponse)
    }

    @Test
    fun `editing with blank response returns null for selectedResponse`() {
        uiState.isEditing = true
        uiState.editedResponse = "   " // Blank
        assertEquals(null, uiState.selectedResponse)
    }

    @Test
    fun `editing takes priority over selected index for selectedResponse`() {
        val messageWithResponses = testMessage.copy(
            generatedResponses = listOf("hello", "Got it!")
        )
        uiState.updateMessage(messageWithResponses)
        uiState.selectResponse(0)
        uiState.isEditing = true
        uiState.editedResponse = "Edited text"
        assertEquals("Edited text", uiState.selectedResponse)
    }

    // ResponseEditor integration tests

    @Test
    fun `complete editing workflow from selection to use`() {
        val messageWithResponses = testMessage.copy(
            generatedResponses = listOf("hello", "Got it!", "Thanks!")
        )
        uiState.updateMessage(messageWithResponses)

        // Step 1: Select a response
        uiState.selectResponse(1)
        assertEquals("Got it!", uiState.selectedResponse)

        // Step 2: Start editing (should pre-populate with selected response)
        uiState.startEditing()
        assertTrue(uiState.isEditing)
        assertEquals("Got it!", uiState.editedResponse)

        // Step 3: Modify the response
        uiState.updateEditedResponse("Got it, I'll handle this!")
        assertEquals("Got it, I'll handle this!", uiState.editedResponse)
        assertEquals("Got it, I'll handle this!", uiState.selectedResponse)

        // Step 4: Use the edited response
        uiState.useEditedResponse()
        assertFalse(uiState.isEditing)
        assertTrue(uiState.useEditedResponseFlag) // Flag should be set
        assertEquals(null, uiState.selectedResponseIndex)
        // editedResponse is preserved and selectedResponse returns it via useEditedResponseFlag
        assertEquals("Got it, I'll handle this!", uiState.editedResponse)
        assertEquals("Got it, I'll handle this!", uiState.selectedResponse)
    }

    @Test
    fun `cancel editing returns to previous state`() {
        val messageWithResponses = testMessage.copy(
            generatedResponses = listOf("hello", "Got it!")
        )
        uiState.updateMessage(messageWithResponses)

        // Select a response and start editing
        uiState.selectResponse(0)
        uiState.startEditing()
        uiState.updateEditedResponse("Modified text")

        // Cancel should clear editing state
        uiState.cancelEditing()
        assertFalse(uiState.isEditing)
        assertEquals("", uiState.editedResponse)

        // Original selection should still be available if we want it
        // (Note: selectResponse clears editing, but cancelEditing just resets editing state)
    }

    @Test
    fun `editing from scratch with no prior selection`() {
        uiState.startEditing()
        assertTrue(uiState.isEditing)
        assertEquals("", uiState.editedResponse)

        uiState.updateEditedResponse("Custom from scratch")
        assertEquals("Custom from scratch", uiState.selectedResponse)
        assertTrue(uiState.hasSelectedResponse)
    }

    @Test
    fun `hasSelectedResponse is false during editing with empty text`() {
        uiState.startEditing()
        uiState.updateEditedResponse("")
        assertFalse(uiState.hasSelectedResponse)
    }

    @Test
    fun `useEditedResponse preserves editedResponse content`() {
        uiState.startEditing()
        uiState.updateEditedResponse("My custom response")
        uiState.useEditedResponse()

        // After useEditedResponse, the editedResponse content should still be accessible
        // via the useEditedResponseFlag
        assertEquals("My custom response", uiState.editedResponse)
        assertEquals("My custom response", uiState.selectedResponse)
        assertTrue(uiState.hasSelectedResponse)
    }

    @Test
    fun `selecting response after editing clears edit state`() {
        val messageWithResponses = testMessage.copy(
            generatedResponses = listOf("hello", "Got it!")
        )
        uiState.updateMessage(messageWithResponses)

        // Start editing with custom text
        uiState.startEditing()
        uiState.updateEditedResponse("Custom text")

        // Selecting a pre-generated response should exit editing mode
        uiState.selectResponse(0)
        assertFalse(uiState.isEditing)
        assertEquals("", uiState.editedResponse)
        assertEquals("hello", uiState.selectedResponse)
    }

    @Test
    fun `send callback receives edited response content`() {
        var sentResponse: String? = null
        uiState.onSendResponse = { response -> sentResponse = response }

        uiState.startEditing()
        uiState.updateEditedResponse("Edited and ready to send")
        uiState.sendSelectedResponse()

        assertEquals("Edited and ready to send", sentResponse)
    }

    @Test
    fun `editing workflow with useEditedResponse then send`() {
        var sentResponse: String? = null
        uiState.onSendResponse = { response -> sentResponse = response }

        // Complete workflow: edit, use, then send
        uiState.startEditing()
        uiState.updateEditedResponse("Final response text")
        uiState.useEditedResponse()
        uiState.sendSelectedResponse()

        // After useEditedResponse, selectedResponse still returns editedResponse
        // because selectedResponseIndex is null and editedResponse is preserved
        assertEquals("Final response text", sentResponse)
    }

    // Dismiss message callback tests

    @Test
    fun `initial onDismissMessage callback is null`() {
        assertEquals(null, uiState.onDismissMessage)
    }

    @Test
    fun `dismissMessage invokes callback`() {
        var callbackInvoked = false
        uiState.onDismissMessage = { callbackInvoked = true }
        uiState.dismissMessage()
        assertTrue(callbackInvoked)
    }

    @Test
    fun `dismissMessage does nothing when callback is null`() {
        uiState.onDismissMessage = null
        uiState.dismissMessage() // Should not throw
    }

    @Test
    fun `onDismissMessage can be set and invoked multiple times`() {
        var invokeCount = 0
        uiState.onDismissMessage = { invokeCount++ }
        uiState.dismissMessage()
        uiState.dismissMessage()
        uiState.dismissMessage()
        assertEquals(3, invokeCount)
    }

    @Test
    fun `onDismissMessage callback can be changed`() {
        var firstCallbackInvoked = false
        var secondCallbackInvoked = false

        uiState.onDismissMessage = { firstCallbackInvoked = true }
        uiState.dismissMessage()
        assertTrue(firstCallbackInvoked)
        assertFalse(secondCallbackInvoked)

        uiState.onDismissMessage = { secondCallbackInvoked = true }
        uiState.dismissMessage()
        assertTrue(secondCallbackInvoked)
    }

    @Test
    fun `dismissMessage works independently of other callbacks`() {
        var dismissCalled = false
        var sendCalled = false
        var regenerateCalled = false

        uiState.onDismissMessage = { dismissCalled = true }
        uiState.onSendResponse = { sendCalled = true }
        uiState.onRegenerateResponses = { regenerateCalled = true }

        uiState.dismissMessage()

        assertTrue(dismissCalled)
        assertFalse(sendCalled)
        assertFalse(regenerateCalled)
    }

    // Navigation state tests

    @Test
    fun `initial hasNextMessage is false`() {
        assertFalse(uiState.hasNextMessage)
    }

    @Test
    fun `initial hasPreviousMessage is false`() {
        assertFalse(uiState.hasPreviousMessage)
    }

    @Test
    fun `updateNavigationState sets hasNextMessage correctly`() {
        uiState.updateNavigationState(hasNext = true, hasPrevious = false)
        assertTrue(uiState.hasNextMessage)
        assertFalse(uiState.hasPreviousMessage)
    }

    @Test
    fun `updateNavigationState sets hasPreviousMessage correctly`() {
        uiState.updateNavigationState(hasNext = false, hasPrevious = true)
        assertFalse(uiState.hasNextMessage)
        assertTrue(uiState.hasPreviousMessage)
    }

    @Test
    fun `updateNavigationState sets both values correctly`() {
        uiState.updateNavigationState(hasNext = true, hasPrevious = true)
        assertTrue(uiState.hasNextMessage)
        assertTrue(uiState.hasPreviousMessage)
    }

    @Test
    fun `updateNavigationState can reset both to false`() {
        uiState.updateNavigationState(hasNext = true, hasPrevious = true)
        uiState.updateNavigationState(hasNext = false, hasPrevious = false)
        assertFalse(uiState.hasNextMessage)
        assertFalse(uiState.hasPreviousMessage)
    }

    // Navigation callback tests

    @Test
    fun `initial onNavigateToNextMessage callback is null`() {
        assertEquals(null, uiState.onNavigateToNextMessage)
    }

    @Test
    fun `initial onNavigateToPreviousMessage callback is null`() {
        assertEquals(null, uiState.onNavigateToPreviousMessage)
    }

    @Test
    fun `navigateToNextMessage invokes callback when hasNextMessage is true`() {
        var callbackInvoked = false
        uiState.onNavigateToNextMessage = { callbackInvoked = true }
        uiState.updateNavigationState(hasNext = true, hasPrevious = false)
        uiState.navigateToNextMessage()
        assertTrue(callbackInvoked)
    }

    @Test
    fun `navigateToNextMessage does not invoke callback when hasNextMessage is false`() {
        var callbackInvoked = false
        uiState.onNavigateToNextMessage = { callbackInvoked = true }
        uiState.updateNavigationState(hasNext = false, hasPrevious = false)
        uiState.navigateToNextMessage()
        assertFalse(callbackInvoked)
    }

    @Test
    fun `navigateToPreviousMessage invokes callback when hasPreviousMessage is true`() {
        var callbackInvoked = false
        uiState.onNavigateToPreviousMessage = { callbackInvoked = true }
        uiState.updateNavigationState(hasNext = false, hasPrevious = true)
        uiState.navigateToPreviousMessage()
        assertTrue(callbackInvoked)
    }

    @Test
    fun `navigateToPreviousMessage does not invoke callback when hasPreviousMessage is false`() {
        var callbackInvoked = false
        uiState.onNavigateToPreviousMessage = { callbackInvoked = true }
        uiState.updateNavigationState(hasNext = false, hasPrevious = false)
        uiState.navigateToPreviousMessage()
        assertFalse(callbackInvoked)
    }

    @Test
    fun `navigateToNextMessage does nothing when callback is null`() {
        uiState.onNavigateToNextMessage = null
        uiState.updateNavigationState(hasNext = true, hasPrevious = false)
        uiState.navigateToNextMessage() // Should not throw
    }

    @Test
    fun `navigateToPreviousMessage does nothing when callback is null`() {
        uiState.onNavigateToPreviousMessage = null
        uiState.updateNavigationState(hasNext = false, hasPrevious = true)
        uiState.navigateToPreviousMessage() // Should not throw
    }

    @Test
    fun `navigation callbacks can be invoked multiple times`() {
        var nextCount = 0
        var prevCount = 0
        uiState.onNavigateToNextMessage = { nextCount++ }
        uiState.onNavigateToPreviousMessage = { prevCount++ }
        uiState.updateNavigationState(hasNext = true, hasPrevious = true)

        uiState.navigateToNextMessage()
        uiState.navigateToNextMessage()
        uiState.navigateToPreviousMessage()

        assertEquals(2, nextCount)
        assertEquals(1, prevCount)
    }

    @Test
    fun `navigation callbacks work independently of other callbacks`() {
        var nextCalled = false
        var prevCalled = false
        var dismissCalled = false
        var sendCalled = false

        uiState.onNavigateToNextMessage = { nextCalled = true }
        uiState.onNavigateToPreviousMessage = { prevCalled = true }
        uiState.onDismissMessage = { dismissCalled = true }
        uiState.onSendResponse = { sendCalled = true }

        uiState.updateNavigationState(hasNext = true, hasPrevious = false)
        uiState.navigateToNextMessage()

        assertTrue(nextCalled)
        assertFalse(prevCalled)
        assertFalse(dismissCalled)
        assertFalse(sendCalled)
    }

    // Crash resilience tests

    @Test
    fun `selectedResponse handles negative index gracefully`() {
        val messageWithResponses = testMessage.copy(
            generatedResponses = listOf("hello", "Got it!")
        )
        uiState.updateMessage(messageWithResponses)
        uiState.selectedResponseIndex = -1 // Invalid negative index
        assertEquals(null, uiState.selectedResponse)
    }

    @Test
    fun `selectedResponse handles empty responses list gracefully`() {
        val messageWithNoResponses = testMessage.copy(generatedResponses = emptyList())
        uiState.updateMessage(messageWithNoResponses)
        uiState.selectResponse(0) // Try to select when list is empty
        assertEquals(null, uiState.selectedResponse)
    }

    @Test
    fun `selectResponse handles null index without crash`() {
        uiState.selectResponse(null)
        assertEquals(null, uiState.selectedResponseIndex)
    }

    @Test
    fun `callbacks handle exceptions gracefully - regenerate`() {
        uiState.onRegenerateResponses = { throw RuntimeException("Test exception") }
        // Should not throw, error is logged
        uiState.regenerateResponses()
    }

    @Test
    fun `callbacks handle exceptions gracefully - send`() {
        val messageWithResponses = testMessage.copy(
            generatedResponses = listOf("hello")
        )
        uiState.updateMessage(messageWithResponses)
        uiState.selectResponse(0)
        uiState.onSendResponse = { throw RuntimeException("Test exception") }
        // Should not throw, error is logged
        uiState.sendSelectedResponse()
    }

    @Test
    fun `callbacks handle exceptions gracefully - dismiss`() {
        uiState.onDismissMessage = { throw RuntimeException("Test exception") }
        // Should not throw, error is logged
        uiState.dismissMessage()
    }

    @Test
    fun `callbacks handle exceptions gracefully - navigateNext`() {
        uiState.updateNavigationState(hasNext = true, hasPrevious = false)
        uiState.onNavigateToNextMessage = { throw RuntimeException("Test exception") }
        // Should not throw, error is logged
        uiState.navigateToNextMessage()
    }

    @Test
    fun `callbacks handle exceptions gracefully - navigatePrevious`() {
        uiState.updateNavigationState(hasNext = false, hasPrevious = true)
        uiState.onNavigateToPreviousMessage = { throw RuntimeException("Test exception") }
        // Should not throw, error is logged
        uiState.navigateToPreviousMessage()
    }

    @Test
    fun `rapid state changes do not cause crashes`() {
        // Simulate rapid state changes
        repeat(100) {
            uiState.selectResponse(it % 3)
            uiState.startEditing()
            uiState.updateEditedResponse("Text $it")
            uiState.cancelEditing()
            uiState.toggleReveal()
        }
        // If we get here without exception, the test passes
    }

    @Test
    fun `updatePendingCounts with very large counts does not crash`() {
        val largeCounts = mapOf(
            MessageBucket.URGENT to 1000000,
            MessageBucket.WORK to 500000
        )
        uiState.updatePendingCounts(largeCounts)
        // Should not crash
        assertEquals(1500000, uiState.pendingMessageCount)
    }

    @Test
    fun `resetResponseSelection called multiple times does not crash`() {
        repeat(10) {
            uiState.resetResponseSelection()
        }
        assertEquals(null, uiState.selectedResponseIndex)
    }

    @Test
    fun `updateMessage with same message does not crash`() {
        repeat(10) {
            uiState.updateMessage(testMessage)
        }
        assertEquals(testMessage, uiState.message)
    }

    @Test
    fun `startEditing then useEditedResponse without text is safe`() {
        uiState.startEditing()
        uiState.useEditedResponse() // Empty edited response
        assertFalse(uiState.isEditing)
        assertTrue(uiState.useEditedResponseFlag)
    }
}
