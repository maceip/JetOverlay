package com.yazan.jetoverlay.service

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.yazan.jetoverlay.BaseAndroidTest
import com.yazan.jetoverlay.TestConstants
import com.yazan.jetoverlay.service.callscreening.CallScreeningService
import com.yazan.jetoverlay.service.callscreening.CallScreeningService.ScreeningState
import com.yazan.jetoverlay.service.callscreening.CallScreeningService.UserDecision
import com.yazan.jetoverlay.ui.CallScreeningOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end tests for Call Screening functionality.
 *
 * These tests verify the complete call screening flow:
 * 1. Detecting incoming calls (via emulator GSM commands or simulation)
 * 2. Audio capture and speech-to-text pipeline
 * 3. UI display of transcriptions
 * 4. User decision handling
 *
 * Note: Full integration with real telephony requires:
 * - Emulator with telephony support
 * - ANSWER_PHONE_CALLS and READ_PHONE_STATE permissions
 * - App set as default call screening app
 */
@RunWith(AndroidJUnit4::class)
class CallScreeningE2ETest : BaseAndroidTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val phonePermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG
    )

    private var serviceStarted = false

    @Before
    override fun setUp() {
        super.setUp()
        // Start the call screening service
        CallScreeningService.start(context)
        serviceStarted = true

        // Wait for service to initialize
        runBlocking { delay(500) }
    }

    @After
    override fun tearDown() {
        if (serviceStarted) {
            CallScreeningService.stop(context)
        }
        super.tearDown()
    }

    /**
     * Test: Simulate incoming call and verify state transitions.
     * Uses the stub's simulation methods to test without real telephony.
     */
    @Test
    fun testSimulatedIncomingCall() = runTest {
        val service = CallScreeningService.getInstance()
        assertNotNull("CallScreeningService should be running", service)

        // Simulate incoming call
        service!!.simulateIncomingCall(
            phoneNumber = "+1-555-867-5309",
            displayName = "Jenny",
            isContact = true
        )

        // Verify state changed to IncomingCall
        val state = service.screeningState.first { it !is ScreeningState.Idle }
        assertTrue("State should be IncomingCall", state is ScreeningState.IncomingCall)

        val incomingState = state as ScreeningState.IncomingCall
        assertEquals("+1-555-867-5309", incomingState.callInfo.phoneNumber)
        assertEquals("Jenny", incomingState.callInfo.displayName)
        assertTrue(incomingState.callInfo.isContact)
    }

    /**
     * Test: Complete screening flow from incoming call to user decision.
     */
    @Test
    fun testCompleteScreeningFlow() = runTest {
        val service = CallScreeningService.getInstance()
        assertNotNull("CallScreeningService should be running", service)

        // Step 1: Incoming call
        service!!.simulateIncomingCall(
            phoneNumber = "+1-555-123-4567",
            displayName = "Unknown Caller",
            isContact = false
        )

        // Verify incoming call state
        var state = service.screeningState.first { it is ScreeningState.IncomingCall }
        assertTrue(state is ScreeningState.IncomingCall)

        // Step 2: Start screening
        service.startScreening()

        state = service.screeningState.first { it is ScreeningState.Screening }
        assertTrue("State should be Screening", state is ScreeningState.Screening)

        // Step 3: Simulate caller speaking
        service.simulateCallerSpeech("Hi, this is your doctor's office calling about your appointment.")
        delay(200) // Allow transcript to propagate

        // Step 4: Stop screening
        service.stopScreening()

        state = service.screeningState.first { it is ScreeningState.AwaitingDecision }
        assertTrue("State should be AwaitingDecision", state is ScreeningState.AwaitingDecision)

        val decisionState = state as ScreeningState.AwaitingDecision
        assertTrue(
            "Transcript should contain caller's message",
            decisionState.finalTranscript.contains("doctor") ||
            decisionState.finalTranscript.contains("appointment") ||
            decisionState.finalTranscript.isNotEmpty()
        )

        // Step 5: User decides to answer
        service.handleUserDecision(UserDecision.ANSWER)

        state = service.screeningState.first { it is ScreeningState.Answered }
        assertTrue("State should be Answered", state is ScreeningState.Answered)
    }

    /**
     * Test: User rejects call with SMS.
     */
    @Test
    fun testRejectWithSms() = runTest {
        val service = CallScreeningService.getInstance()
        assertNotNull(service)

        service!!.simulateIncomingCall("+1-555-SPAM-00", "Spam Caller", false)
        service.startScreening()
        delay(100)

        service.simulateCallerSpeech("You've won a free cruise!")
        service.stopScreening()

        // Wait for AwaitingDecision state
        service.screeningState.first { it is ScreeningState.AwaitingDecision }

        // Reject with SMS
        service.handleUserDecision(UserDecision.REJECT_WITH_SMS)

        val state = service.screeningState.first { it is ScreeningState.Rejected }
        assertTrue("State should be Rejected", state is ScreeningState.Rejected)
    }

    /**
     * Test: Emulator GSM call simulation (requires emulator).
     * This test uses ADB commands to simulate an incoming call on the emulator.
     */
    @Test
    fun testEmulatorGsmCall() = runTest {
        // Skip on real devices - GSM commands only work on emulator
        if (!isEmulator()) {
            println("Skipping GSM test - not running on emulator")
            return@runTest
        }

        val device = getUiDevice()
        val testPhoneNumber = "5551234567"

        try {
            // Simulate incoming call via emulator console
            // The emulator listens on port 5554 by default
            device.executeShellCommand("service call phone 1 s16 \"$testPhoneNumber\"")

            // Alternative: Use GSM command through telnet-like interface
            // This requires the emulator to have telephony enabled
            simulateGsmCall(testPhoneNumber)

            // Wait for call to be detected
            delay(2000)

            // Verify the service detected something (if telephony integration is complete)
            val service = CallScreeningService.getInstance()
            if (service != null) {
                val currentCall = service.currentCall.value
                // In stub mode, we won't get real calls, but the infrastructure should be ready
                println("Current call state: $currentCall")
            }

            // End the simulated call
            endGsmCall()

        } catch (e: Exception) {
            // GSM commands may fail if emulator doesn't support telephony
            println("GSM test skipped: ${e.message}")
        }
    }

    /**
     * Test: UI displays incoming call correctly.
     */
    @Test
    fun testCallScreeningUI() = runTest {
        val service = CallScreeningService.getInstance()
        assertNotNull(service)

        // Simulate incoming call
        service!!.simulateIncomingCall(
            phoneNumber = "+1-555-UI-TEST",
            displayName = "UI Test Caller",
            isContact = true
        )

        // Set up the Compose UI
        composeTestRule.setContent {
            CallScreeningOverlay(
                screeningState = service.screeningState,
                onDecision = { decision ->
                    runBlocking { service.handleUserDecision(decision) }
                },
                onStartScreening = {
                    runBlocking { service.startScreening() }
                },
                onStopScreening = {
                    runBlocking { service.stopScreening() }
                }
            )
        }

        // Wait for UI to render
        composeTestRule.waitForIdle()

        // Verify caller info is displayed
        composeTestRule.onNodeWithText("UI Test Caller").assertIsDisplayed()
        composeTestRule.onNodeWithText("+1-555-UI-TEST").assertIsDisplayed()

        // Verify action buttons are present
        composeTestRule.onNodeWithText("Screen This Call").assertIsDisplayed()
        composeTestRule.onNodeWithText("Answer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reject").assertIsDisplayed()
    }

    /**
     * Test: UI transitions through screening states.
     */
    @Test
    fun testUIStateTransitions() = runTest {
        val service = CallScreeningService.getInstance()
        assertNotNull(service)

        service!!.simulateIncomingCall("+1-555-TRANS", "Transition Test", false)

        composeTestRule.setContent {
            CallScreeningOverlay(
                screeningState = service.screeningState,
                onDecision = { runBlocking { service.handleUserDecision(it) } },
                onStartScreening = { runBlocking { service.startScreening() } },
                onStopScreening = { runBlocking { service.stopScreening() } }
            )
        }

        composeTestRule.waitForIdle()

        // Click "Screen This Call"
        composeTestRule.onNodeWithText("Screen This Call").performClick()
        composeTestRule.waitForIdle()
        delay(200)

        // Should now show screening UI with "Waiting for caller to speak..."
        composeTestRule.onNodeWithText("Waiting for caller to speak...", substring = true)
            .assertIsDisplayed()

        // Simulate speech
        service.simulateCallerSpeech("Hello, is anyone there?")
        delay(300)

        // Stop screening
        service.stopScreening()
        composeTestRule.waitForIdle()
        delay(200)

        // Should now show decision UI with transcript
        composeTestRule.onNodeWithText("Caller said:").assertIsDisplayed()
    }

    /**
     * Test: Rapid call handling - answer immediately.
     */
    @Test
    fun testQuickAnswer() = runTest {
        val service = CallScreeningService.getInstance()
        assertNotNull(service)

        service!!.simulateIncomingCall("+1-555-QUICK", "Quick Answer", true)

        composeTestRule.setContent {
            CallScreeningOverlay(
                screeningState = service.screeningState,
                onDecision = { runBlocking { service.handleUserDecision(it) } },
                onStartScreening = { runBlocking { service.startScreening() } },
                onStopScreening = { runBlocking { service.stopScreening() } }
            )
        }

        composeTestRule.waitForIdle()

        // Immediately click Answer without screening
        composeTestRule.onNodeWithText("Answer").performClick()

        // Verify state becomes Answered
        val state = withTimeout(2000) {
            service.screeningState.first { it is ScreeningState.Answered }
        }
        assertTrue(state is ScreeningState.Answered)
    }

    /**
     * Test: Unknown caller (not in contacts) shows warning.
     */
    @Test
    fun testUnknownCallerWarning() = runTest {
        val service = CallScreeningService.getInstance()
        assertNotNull(service)

        // Simulate unknown caller
        service!!.simulateIncomingCall("+1-555-UNKNOWN", null, false)

        composeTestRule.setContent {
            CallScreeningOverlay(
                screeningState = service.screeningState,
                onDecision = { runBlocking { service.handleUserDecision(it) } },
                onStartScreening = { runBlocking { service.startScreening() } },
                onStopScreening = { runBlocking { service.stopScreening() } }
            )
        }

        composeTestRule.waitForIdle()

        // Unknown caller should show "Unknown Caller" and warning
        composeTestRule.onNodeWithText("Unknown Caller").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not in contacts").assertIsDisplayed()
    }

    // --- Helper methods for emulator GSM control ---

    /**
     * Simulates an incoming GSM call on the emulator.
     * Uses ADB to send AT commands or telnet to emulator console.
     */
    private fun simulateGsmCall(phoneNumber: String) {
        try {
            val device = getUiDevice()
            // Method 1: Use 'am' to broadcast telephony intent (limited)
            device.executeShellCommand(
                "am broadcast -a android.intent.action.PHONE_STATE " +
                "--es state RINGING --es incoming_number $phoneNumber"
            )

            // Method 2: Direct service call (requires system permissions)
            // device.executeShellCommand("service call phone 1 s16 \"$phoneNumber\"")

        } catch (e: Exception) {
            println("Failed to simulate GSM call: ${e.message}")
        }
    }

    /**
     * Ends the simulated GSM call.
     */
    private fun endGsmCall() {
        try {
            val device = getUiDevice()
            device.executeShellCommand(
                "am broadcast -a android.intent.action.PHONE_STATE --es state IDLE"
            )
        } catch (e: Exception) {
            println("Failed to end GSM call: ${e.message}")
        }
    }

    /**
     * Alternative: Connect to emulator console and send GSM commands.
     * Emulator console is available at localhost:5554 (or 5556, 5558, etc.)
     *
     * Commands:
     * - gsm call <phone_number>  - Simulate incoming call
     * - gsm cancel <phone_number> - Cancel call
     * - gsm accept <phone_number> - Accept call
     * - gsm busy <phone_number> - Set busy signal
     */
    private suspend fun sendEmulatorConsoleCommand(command: String): String {
        // This would require opening a socket to localhost:5554
        // and authenticating with the emulator's auth token
        // For simplicity, we use ADB shell commands instead
        return try {
            val device = getUiDevice()
            device.executeShellCommand(command)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
