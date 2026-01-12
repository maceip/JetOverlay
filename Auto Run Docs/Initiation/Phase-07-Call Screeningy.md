# Phase 07: Call Screening

This phase adds call screening functionality to JetOverlay. When an incoming call arrives, the app can capture the caller's voice message, convert it to text, and present a transcription to the user so they can make an informed decision about whether to answer, reject, or respond via SMS.

## Tasks

- [x] Create audio capture stub for incoming call audio:
  - Create `app/src/main/java/com/yazan/jetoverlay/service/callscreening/AudioCaptureStub.kt`:
    - Stub implementation with same interface as real AudioRecord
    - Flow-based audio chunk emission for real-time processing
    - CaptureState sealed class for tracking capture lifecycle
    - Test injection method for simulating audio input

  **Implementation Notes:**
  - Created `AudioCaptureStub.kt` with 16kHz mono PCM audio config (standard for STT)
  - Exposes `audioChunks: Flow<AudioChunk>` for downstream processing
  - `CaptureState` sealed class: Idle, Starting, Capturing, Stopping, Error
  - `injectTestAudioChunk()` method for testing without real audio hardware
  - Buffer size calculated from AudioRecord.getMinBufferSize()

- [x] Create speech-to-text stub for voice processing:
  - Create `app/src/main/java/com/yazan/jetoverlay/service/callscreening/SpeechToTextStub.kt`:
    - Interface compatible with real STT services (Whisper, Google, etc.)
    - Configurable language, punctuation, word timestamps
    - Session-based transcript accumulation
    - Flow-based transcription results

  **Implementation Notes:**
  - Created `SpeechToTextStub.kt` with `TranscriptionResult` data class
  - `SttConfig` data class for language, punctuation, profanity filter settings
  - Exposes `transcriptions: Flow<TranscriptionResult>` for UI updates
  - `currentTranscript: StateFlow<String>` for accumulated session text
  - `injectTranscription()` for testing without real STT service
  - `finalizeSession()` returns complete transcript and resets state

- [x] Create call screening service infrastructure:
  - Create `app/src/main/java/com/yazan/jetoverlay/service/callscreening/CallScreeningService.kt`:
    - Foreground service for call screening orchestration
    - Coordinates audio capture -> STT pipeline
    - State machine for screening flow
    - Event flow for UI observation

  **Implementation Notes:**
  - Created `CallScreeningService.kt` extending Android Service
  - `ScreeningState` sealed class: Idle, IncomingCall, Screening, AwaitingDecision, Answered, Rejected, Error
  - `ScreeningEvent` sealed class for UI events: CallReceived, TranscriptUpdated, CallerFinishedSpeaking, UserDecision
  - `UserDecision` enum: ANSWER, REJECT, REJECT_WITH_SMS, SEND_TO_VOICEMAIL
  - `CallInfo` data class with phoneNumber, displayName, isContact, timestamp
  - Pipeline: audioChunks flow -> speechToText.processAudioChunk() -> transcriptions flow -> UI
  - `simulateIncomingCall()` and `simulateCallerSpeech()` for testing

- [x] Create call screening UI components:
  - Create `app/src/main/java/com/yazan/jetoverlay/ui/CallScreeningOverlay.kt`:
    - Compose UI for incoming call overlay
    - Real-time transcription display
    - Action buttons for user decisions
    - Animated state transitions

  **Implementation Notes:**
  - Created `CallScreeningOverlay.kt` with Material3 components
  - `IncomingCallCard` - shows caller info with Screen/Answer/Reject buttons
  - `ScreeningCard` - shows live transcript with listening indicator
  - `DecisionCard` - shows final transcript with 4 action buttons (Answer, Reject, SMS, Voicemail)
  - `StatusCard` - brief confirmation of action taken
  - `ErrorCard` - displays error state
  - AnimatedContent for smooth state transitions
  - Caller avatar with contact/unknown differentiation

- [x] Create E2E tests for call screening:
  - Create `app/src/androidTest/java/com/yazan/jetoverlay/service/CallScreeningE2ETest.kt`:
    - Test simulated incoming call state transitions
    - Test complete screening flow (call -> screen -> transcript -> decision)
    - Test UI displays caller info correctly
    - Test UI state transitions through screening
    - Test quick answer without screening
    - Test unknown caller warning display
    - Test emulator GSM call simulation (when available)

  **Implementation Notes:**
  - Created `CallScreeningE2ETest.kt` with 8 comprehensive tests
  - `testSimulatedIncomingCall()` - verifies state transitions on incoming call
  - `testCompleteScreeningFlow()` - full flow from call to user decision
  - `testRejectWithSms()` - tests reject with SMS option
  - `testEmulatorGsmCall()` - uses ADB to simulate real GSM calls on emulator
  - `testCallScreeningUI()` - verifies Compose UI displays caller info
  - `testUIStateTransitions()` - tests UI flow through screening states
  - `testQuickAnswer()` - tests immediate answer without screening
  - `testUnknownCallerWarning()` - verifies unknown caller UI warning
  - Helper methods for emulator GSM control via ADB shell commands

## Future Work (Real Implementation)

When replacing stubs with real implementations:

1. **AudioCaptureStub -> Real Audio Capture:**
   - Requires RECORD_AUDIO permission
   - Use AudioRecord with VOICE_CALL audio source (requires system permission)
   - Or integrate with Android's CallScreeningService API (Android 10+)
   - Consider legal requirements for call recording notification

2. **SpeechToTextStub -> Real STT:**
   - Option A: Android SpeechRecognizer (on-device, free, privacy-friendly)
   - Option B: Google Cloud Speech-to-Text (streaming, high accuracy)
   - Option C: OpenAI Whisper API (best accuracy, requires network)
   - Option D: Local Whisper model (privacy-friendly, requires significant resources)

3. **CallScreeningService Integration:**
   - Extend android.telecom.CallScreeningService for proper call interception
   - Requires BIND_SCREENING_SERVICE permission
   - User must set app as default call screening app
   - Handle ANSWER_PHONE_CALLS permission for call control

## Testing

To test the call screening flow without real calls:

```kotlin
// Get service instance
val service = CallScreeningService.getInstance()

// Simulate incoming call
service?.simulateIncomingCall(
    phoneNumber = "+1-555-123-4567",
    displayName = "Test Caller",
    isContact = false
)

// Start screening
service?.startScreening()

// Simulate caller speaking
service?.simulateCallerSpeech("Hi, this is John from the doctor's office calling to confirm your appointment tomorrow at 3pm.")

// Stop screening to see decision UI
service?.stopScreening()
```
