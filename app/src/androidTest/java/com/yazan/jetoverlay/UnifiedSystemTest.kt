package com.yazan.jetoverlay

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.yazan.jetoverlay.data.AppDatabase
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.domain.MessageProcessor
import com.yazan.jetoverlay.service.callscreening.CallScreeningService
import com.yazan.jetoverlay.service.callscreening.CallScreeningService.ScreeningState
import com.yazan.jetoverlay.service.notification.NotificationMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unified System Test Suite (Phase 06, 07, 08).
 * 
 * This comprehensive suite verifies the integration of the three major subsystems:
 * 1. Phase 06: LLM-based Message Processing ("The Brain")
 * 2. Phase 07: Call Screening Service ("The Gatekeeper")
 * 3. Phase 08: Notification Zero & Unified Inbox ("The Hub")
 */
@RunWith(AndroidJUnit4::class)
class UnifiedSystemTest : BaseAndroidTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val unifiedPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.READ_CALL_LOG
    )

    private lateinit var repository: MessageRepository
    private lateinit var processor: MessageProcessor
    private var callScreeningService: CallScreeningService? = null

    @Before
    override fun setUp() {
        super.setUp()
        val db = AppDatabase.getDatabase(context)
        repository = MessageRepository(db.messageDao())
        
        // Initialize Phase 06 Processor (now uses real LiteRTLlmService by default)
        processor = MessageProcessor(repository)
        processor.start()

        // Initialize Phase 07 Service
        CallScreeningService.start(context)
        
        // Wait for service to start (poll for instance)
        var waitAttempts = 0
        while (CallScreeningService.getInstance() == null && waitAttempts < 20) {
            runBlocking { delay(100) }
            waitAttempts++
        }
        
        callScreeningService = CallScreeningService.getInstance()
    }

    @After
    override fun tearDown() {
        processor.stop()
        CallScreeningService.stop(context)
        super.tearDown()
    }

    /**
     * The "Mega Scenario":
     * 1. A notification arrives from WhatsApp (Phase 08).
     * 2. The Brain processes it, categorizes it, and generates replies (Phase 06).
     * 3. An urgent call comes in while processing, triggering Call Screening (Phase 07).
     */
    @Test
    fun testUnifiedSystemScenario() = runBlocking {
        // --- Part 1: Notification Zero (Phase 08) ---
        // Simulate incoming WhatsApp message
        val sender = "Mom"
        val content = "Call me urgently!"
        val msgId = repository.ingestNotification(
            packageName = NotificationMapper.PKG_WHATSAPP,
            sender = sender,
            content = content,
            contextTag = "personal" // Phase 08 context tagging
        )

        assertTrue("Message should be ingested", msgId > 0)

        // --- Part 2: The Brain (Phase 06) ---
        // Wait for the Processor to pick it up and enrich it
        // We expect:
        // - Status -> PROCESSED
        // - Bucket -> URGENT (based on content "urgently")
        // - Generated Responses -> Not empty
        
        var attempts = 0
        var processed = false
        while (!processed && attempts < 300) {
            val messages = repository.allMessages.first()
            val msg = messages.find { it.id == msgId }
            
            if (msg != null && msg.status == "PROCESSED") {
                processed = true
                
                // Verify Phase 06 Logic
                assertEquals("URGENT", msg.bucket)
                assertNotNull("Veiled content should be generated", msg.veiledContent)
                assertTrue("Smart replies should be generated", msg.generatedResponses.isNotEmpty())
            } else {
                delay(100)
                attempts++
            }
        }
        assertTrue("Message processing timed out", processed)

        // --- Part 3: Call Screening (Phase 07) ---
        // Now simulate an incoming call interruption
        assertNotNull("CallScreeningService must be running", callScreeningService)
        
        callScreeningService!!.simulateIncomingCall(
            phoneNumber = "+1-555-0199",
            displayName = "Unknown Caller",
            isContact = false
        )

        // Verify state transition to IncomingCall
        val state = callScreeningService!!.screeningState.first { it is ScreeningState.IncomingCall }
        assertTrue("Should detect incoming call", state is ScreeningState.IncomingCall)

        // Start Screening
        callScreeningService!!.startScreening()
        
        // Verify state transition to Screening
        val screeningState = callScreeningService!!.screeningState.first { it is ScreeningState.Screening }
        assertTrue("Should be screening call", screeningState is ScreeningState.Screening)

        // Simulate Caller Speech
        callScreeningService!!.simulateCallerSpeech("I have an important package for you.")
        delay(500)

        // Stop Screening
        callScreeningService!!.stopScreening()
        
        // Verify Transcript
        val decisionState = callScreeningService!!.screeningState.first { it is ScreeningState.AwaitingDecision }
        assertTrue(
            "Transcript should contain 'package'",
            (decisionState as ScreeningState.AwaitingDecision).finalTranscript.contains("package")
        )
    }
}