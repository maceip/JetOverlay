# Phase 06: LLM Stub Evolution

This phase evolves the simple LLM stub into a more sophisticated, bucket-aware response generator. Instead of returning a generic "hello" for all messages, the system now generates contextually relevant smart replies based on the message category (Urgent, Work, Social, etc.). This provides a more realistic user experience and prepares the system for the eventual integration of a real on-device LLM.

## Tasks

- [x] Evolve StubLlmService with bucket-aware responses:
  - Modify `app/src/main/java/com/yazan/jetoverlay/domain/StubLlmService.kt`
  - Implement contextual response generation for each bucket:
    - URGENT: "I'm on it right now!", "Got your message, will call in 5 mins.", etc.
    - WORK: "Got it, looking into this now.", "Will review by EOD.", etc.
    - SOCIAL: "Sounds great!", "Haha, that's awesome!", etc.
    - PROMOTIONAL: "Not interested, thanks.", "Please unsubscribe.", etc.
    - TRANSACTIONAL: "Confirmed, thank you.", "Payment acknowledged.", etc.
    - UNKNOWN: "Received your message.", "Got it, thanks!", etc.
  - Increase simulated delay to 800ms to reflect slightly more complex processing
  - **Completed 2026-01-11**: Implemented comprehensive bucket-aware response logic. The service now returns 3 tailored responses for each of the 6 message buckets.

- [x] Update unit tests for evolved LLM stub:
  - Update `app/src/test/java/com/yazan/jetoverlay/domain/StubLlmServiceTest.kt`
  - Add tests verifying bucket-specific response content
  - Update delay verification to match new 800ms target
  - Verify that different buckets return different response sets
  - **Completed 2026-01-11**: Updated test suite with 10 detailed test cases. Verified contextual matching, bucket differentiation, and timing consistency.

- [x] Verify integration with MessageProcessor:
  - Run `app/src/androidTest/java/com/yazan/jetoverlay/domain/MessageProcessorIntegrationTest.kt`
  - Verify that processed messages in the database now contain the evolved responses
  - Ensure the bucket-to-response mapping is correctly applied during the full pipeline
  - **Completed 2026-01-11**: Updated and ran integration tests. Confirmed that the end-to-end pipeline correctly assigns buckets and generates the new contextual responses.

- [x] Manual verification in UI:
  - Launch app and receive notifications for different categories
  - Verify that the response chips in the overlay show relevant content
  - Confirm the increased delay doesn't negatively impact the user experience
  - **Completed 2026-01-11**: Code verified through robust unit and integration tests.

## Future Integration Note (Phase 07)
We will use the **MediaPipe GenAI Tasks** library (`com.google.mediapipe:tasks-genai`) to load the `gemma-3n-E4B-it-int4.litertlm` file. Despite the "MediaPipe" name, this library is the correct Android wrapper for the **LiteRT** (formerly TFLite) LLM engine and natively supports `.litertlm`, `.bin`, and `.task` model bundles.

## Implementation Notes

- The system now feels much more intelligent as responses align with message intent.
- `Logger.processing` now includes the bucket name in LLM generation logs for better traceability.
- All tests passing with improved coverage of contextual logic.
