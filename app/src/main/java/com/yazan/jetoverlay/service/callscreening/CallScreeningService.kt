package com.yazan.jetoverlay.service.callscreening

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telecom.Call
import com.yazan.jetoverlay.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Service that orchestrates call screening functionality.
 *
 * This service coordinates:
 * 1. Detecting incoming calls
 * 2. Capturing caller's voice via AudioCaptureStub
 * 3. Converting speech to text via SpeechToTextStub
 * 4. Presenting transcription to user for decision
 * 5. Handling user's response (answer, reject, reply)
 *
 * Flow:
 * Incoming Call -> Audio Capture -> STT -> Transcription Display -> User Decision
 */
class CallScreeningService : Service() {

    companion object {
        private const val COMPONENT = "CallScreeningService"
        private const val CHANNEL_ID = "call_screening_channel"
        private const val CHANNEL_NAME = "Call Screening"
        private const val NOTIFICATION_ID = 301

        private var instance: CallScreeningService? = null

        fun getInstance(): CallScreeningService? = instance

        /**
         * Starts the call screening service.
         */
        fun start(context: Context) {
            val intent = Intent(context, CallScreeningService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the call screening service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, CallScreeningService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var audioCapture: AudioCaptureStub
    private lateinit var speechToText: SpeechToTextStub
    private lateinit var ttsPromptPlayer: TtsPromptPlayer
    private val promptGenerator = CallScreeningPromptGenerator()
    private var lastPartialTranscript: String? = null

    // Current screening state
    private val _screeningState = MutableStateFlow<ScreeningState>(ScreeningState.Idle)
    val screeningState: StateFlow<ScreeningState> = _screeningState.asStateFlow()

    // Events for UI to observe
    private val _screeningEvents = MutableSharedFlow<ScreeningEvent>(extraBufferCapacity = 16)
    val screeningEvents: SharedFlow<ScreeningEvent> = _screeningEvents.asSharedFlow()

    // Current call info
    private val _currentCall = MutableStateFlow<CallInfo?>(null)
    val currentCall: StateFlow<CallInfo?> = _currentCall.asStateFlow()

    /**
     * State of the call screening process.
     */
    sealed class ScreeningState {
        object Idle : ScreeningState()
        data class IncomingCall(val callInfo: CallInfo) : ScreeningState()
        data class Screening(val callInfo: CallInfo, val transcript: String) : ScreeningState()
        data class AwaitingDecision(val callInfo: CallInfo, val finalTranscript: String) : ScreeningState()
        object Answered : ScreeningState()
        object Rejected : ScreeningState()
        data class Error(val message: String) : ScreeningState()
    }

    /**
     * Events emitted during call screening.
     */
    sealed class ScreeningEvent {
        data class CallReceived(val callInfo: CallInfo) : ScreeningEvent()
        data class TranscriptUpdated(val transcript: String, val isFinal: Boolean) : ScreeningEvent()
        data class CallerFinishedSpeaking(val finalTranscript: String) : ScreeningEvent()
        data class DecisionMade(val decision: UserDecision) : ScreeningEvent()
        data class Error(val message: String) : ScreeningEvent()
    }

    /**
     * User's decision on how to handle the screened call.
     */
    enum class UserDecision {
        ANSWER,           // Accept the call
        REJECT,           // Decline the call
        REJECT_WITH_SMS,  // Decline and send SMS
        SEND_TO_VOICEMAIL // Let it go to voicemail
    }

    /**
     * Information about an incoming call.
     */
    data class CallInfo(
        val phoneNumber: String,
        val displayName: String?,
        val isContact: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    override fun onCreate() {
        super.onCreate()
        Logger.lifecycle(COMPONENT, "onCreate")

        instance = this
        audioCapture = AudioCaptureStub(this)
        speechToText = SpeechToTextStub(this)
        ttsPromptPlayer = TtsPromptPlayer(this)

        startForegroundNotification()
        setupAudioPipeline()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.lifecycle(COMPONENT, "onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Logger.lifecycle(COMPONENT, "onDestroy")

        instance = null
        serviceScope.cancel()
    }

    /**
     * Sets up the audio capture -> STT pipeline.
     */
    private fun setupAudioPipeline() {
        serviceScope.launch {
            // Initialize STT
            speechToText.initialize()

            // Observe audio chunks and process through STT
            audioCapture.audioChunks.collect { chunk ->
                speechToText.processAudioChunk(chunk)
            }
        }

        serviceScope.launch {
            // Observe transcription results and update state
            speechToText.transcriptions.collect { result ->
                val currentState = _screeningState.value
                if (currentState is ScreeningState.Screening) {
                    val updatedTranscript = if (result.isFinal) {
                        lastPartialTranscript = null
                        "${currentState.transcript} ${result.text}".trim()
                    } else {
                        lastPartialTranscript = result.text
                        "${currentState.transcript} ${result.text}".trim()
                    }

                    _screeningState.value = ScreeningState.Screening(
                        callInfo = currentState.callInfo,
                        transcript = updatedTranscript
                    )

                    _screeningEvents.emit(
                        ScreeningEvent.TranscriptUpdated(
                            transcript = updatedTranscript,
                            isFinal = result.isFinal
                        )
                    )
                }
            }
        }
    }

    /**
     * Called when an incoming call is detected.
     * STUB: In real implementation, this would be triggered by TelecomManager/CallScreeningService.
     *
     * @param phoneNumber The caller's phone number
     * @param displayName Contact name if available
     * @param isContact Whether the caller is in contacts
     */
    suspend fun onIncomingCall(phoneNumber: String, displayName: String?, isContact: Boolean) {
        Logger.d(COMPONENT, "Incoming call from: $phoneNumber (contact: $isContact)")

        val callInfo = CallInfo(
            phoneNumber = phoneNumber,
            displayName = displayName,
            isContact = isContact
        )

        _currentCall.value = callInfo
        _screeningState.value = ScreeningState.IncomingCall(callInfo)
        _screeningEvents.emit(ScreeningEvent.CallReceived(callInfo))
    }

    /**
     * Plays the greeting prompt and then starts listening for caller response.
     */
    suspend fun startGreetingAndListen(assistantName: String = "Agent") {
        val prompt = promptGenerator.buildPrompt(assistantName)
        Logger.d(COMPONENT, "Playing greeting prompt: $prompt")
        ttsPromptPlayer.speak(prompt)
        startScreening()
    }

    /**
     * Starts screening the current incoming call.
     * Begins audio capture and STT processing.
     */
    suspend fun startScreening() {
        val callInfo = _currentCall.value ?: run {
            Logger.w(COMPONENT, "No current call to screen")
            return
        }

        Logger.d(COMPONENT, "Starting call screening for: ${callInfo.phoneNumber}")

        speechToText.resetSession()

        if (audioCapture.startCapture()) {
            _screeningState.value = ScreeningState.Screening(callInfo, "")
        } else {
            _screeningState.value = ScreeningState.Error("Failed to start audio capture")
            _screeningEvents.emit(ScreeningEvent.Error("Failed to start audio capture"))
        }
    }

    /**
     * Stops screening and presents the final transcript for user decision.
     */
    suspend fun stopScreening() {
        val currentState = _screeningState.value
        if (currentState !is ScreeningState.Screening) {
            Logger.w(COMPONENT, "Not currently screening")
            return
        }

        Logger.d(COMPONENT, "Stopping call screening")

        audioCapture.stopCapture()
        val finalTranscript = speechToText.finalizeSession()

        _screeningState.value = ScreeningState.AwaitingDecision(
            callInfo = currentState.callInfo,
            finalTranscript = finalTranscript
        )

        _screeningEvents.emit(ScreeningEvent.CallerFinishedSpeaking(finalTranscript))
    }

    /**
     * Handles the user's decision on the screened call.
     */
    suspend fun handleUserDecision(decision: UserDecision) {
        Logger.d(COMPONENT, "User decision: $decision")

        when (decision) {
            UserDecision.ANSWER -> {
                _screeningState.value = ScreeningState.Answered
                // STUB: In real implementation, would accept the call via TelecomManager
            }
            UserDecision.REJECT -> {
                _screeningState.value = ScreeningState.Rejected
                // STUB: In real implementation, would reject the call
            }
            UserDecision.REJECT_WITH_SMS -> {
                _screeningState.value = ScreeningState.Rejected
                // STUB: In real implementation, would reject and send SMS
            }
            UserDecision.SEND_TO_VOICEMAIL -> {
                _screeningState.value = ScreeningState.Rejected
                // STUB: In real implementation, would let it ring to voicemail
            }
        }

        _screeningEvents.emit(ScreeningEvent.DecisionMade(decision))
        resetState()
    }

    /**
     * Resets the screening state for the next call.
     */
    private fun resetState() {
        _currentCall.value = null
        serviceScope.launch {
            // Small delay before returning to idle
            kotlinx.coroutines.delay(500)
            _screeningState.value = ScreeningState.Idle
        }
    }

    /**
     * STUB: Simulates an incoming call for testing.
     */
    suspend fun simulateIncomingCall(
        phoneNumber: String = "+1-555-123-4567",
        displayName: String? = "Test Caller",
        isContact: Boolean = false
    ) {
        Logger.d(COMPONENT, "Simulating incoming call (STUB)")
        onIncomingCall(phoneNumber, displayName, isContact)
    }

    /**
     * STUB: Simulates caller speaking for testing.
     */
    suspend fun simulateCallerSpeech(text: String) {
        Logger.d(COMPONENT, "Simulating caller speech: \"$text\" (STUB)")
        speechToText.injectTranscription(text, confidence = 0.92f, isFinal = true)
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Call screening service"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Screening Active")
            .setContentText("Monitoring incoming calls")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Logger.d(COMPONENT, "Foreground notification started")
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Failed to start foreground notification", e)
        }
    }
}
