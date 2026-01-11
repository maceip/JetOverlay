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
}
