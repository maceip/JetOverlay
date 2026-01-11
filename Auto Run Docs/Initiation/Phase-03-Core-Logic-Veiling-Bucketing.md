# Phase 03: Core Logic - Veiling, Bucketing & LLM Stub

This phase implements the intelligence layer that processes incoming messages. Messages are categorized into buckets (urgent, social, promotional, etc.), veiled content is generated to hide emotionally charged text, and AI-generated responses are created. The LLM integration is stubbed to return "hello" for all messages as the real implementation will be copied from google-ai-edge/gallery later. By the end of this phase, all messages will flow through the complete processing pipeline: receive -> veil -> bucket -> generate responses.

## Tasks

- [x] Create message bucketing/categorization system:
  - Create `app/src/main/java/com/yazan/jetoverlay/domain/MessageBucket.kt`:
    - Enum class with buckets: URGENT, WORK, SOCIAL, PROMOTIONAL, TRANSACTIONAL, UNKNOWN
    - Each bucket has display name and color for UI
  - Create `app/src/main/java/com/yazan/jetoverlay/domain/MessageCategorizer.kt`:
    - Class with `categorize(message: Message): MessageBucket` method
    - Use simple heuristics for now:
      - URGENT: keywords like "urgent", "emergency", "asap", sender in priority contacts
      - WORK: from Slack, Notion, GitHub, or work email domains
      - SOCIAL: from messaging apps (WhatsApp, Telegram, etc.)
      - PROMOTIONAL: from known marketing senders or keywords
      - TRANSACTIONAL: OTPs, receipts, shipping updates
      - UNKNOWN: default fallback
  - Update `Message.kt` entity to add `bucket: String` field (Room migration needed)
  - **Completed:** Created MessageBucket enum with 6 buckets, each with displayName and color. Created MessageCategorizer with heuristic-based categorization. Added `bucket: String = "UNKNOWN"` field to Message entity. Created 51 unit tests in MessageCategorizerTest.kt and MessageBucketTest.kt - all passing.

- [x] Implement Room database migration for new fields:
  - Update `app/src/main/java/com/yazan/jetoverlay/data/AppDatabase.kt`:
    - Increment database version to 2
    - Add Migration(1, 2) that adds `bucket` column with default "UNKNOWN"
  - Test migration works correctly with existing data
  - Ensure the migration is added to Room builder
  - **Completed:** Updated AppDatabase to version 2 with MIGRATION_1_2 that adds `bucket` column via ALTER TABLE. Created comprehensive migration tests in AppDatabaseMigrationTest.kt (5 tests) verifying: single record migration, multiple records migration, Room compatibility after migration, empty database migration, and custom bucket values for new messages.

- [x] Enhance veiling logic in MessageProcessor:
  - Read existing `app/src/main/java/com/yazan/jetoverlay/domain/MessageProcessor.kt`
  - Create `app/src/main/java/com/yazan/jetoverlay/domain/VeilGenerator.kt`:
    - Class with `generateVeil(message: Message, bucket: MessageBucket): String` method
    - Generate contextual veils based on bucket:
      - URGENT: "Priority message from [sender]"
      - WORK: "Work notification from [app/sender]"
      - SOCIAL: "New message from [sender]"
      - PROMOTIONAL: "Promotional content"
      - TRANSACTIONAL: "Account notification"
      - UNKNOWN: "New notification"
    - Never reveal actual message content in the veil
  - Update MessageProcessor to use VeilGenerator and MessageCategorizer
  - **Completed:** Created VeilGenerator class with bucket-based veil generation, sender sanitization (removes XSS-prone characters), and app name extraction for work notifications. Updated MessageProcessor to inject and use MessageCategorizer and VeilGenerator. Added bucket parameter to MessageRepository.updateMessageState(). Created 28 unit tests in VeilGeneratorTest.kt verifying all bucket types, content leak prevention, and edge cases - all passing.

- [x] Create stubbed LLM service for response generation:
  - Create `app/src/main/java/com/yazan/jetoverlay/domain/LlmService.kt`:
    - Interface defining: `suspend fun generateResponses(message: Message, bucket: MessageBucket): List<String>`
  - Create `app/src/main/java/com/yazan/jetoverlay/domain/StubLlmService.kt`:
    - Implementation that always returns: `listOf("hello", "Got it!", "Thanks!")`
    - Add artificial delay of 500ms to simulate LLM processing
    - Log "StubLlmService: Returning mock responses for message ${message.id}"
  - Wire StubLlmService into MessageProcessor for response generation
  - **Completed:** Created LlmService interface with `suspend fun generateResponses(message: Message, bucket: MessageBucket): List<String>`. Created StubLlmService implementation with 500ms delay and logging. Updated MessageProcessor to inject and use LlmService (defaults to StubLlmService). Added testOptions.unitTests.isReturnDefaultValues=true to build.gradle.kts to enable Android mocking in unit tests. Created 19 unit tests in StubLlmServiceTest.kt verifying: expected responses, consistency across buckets, delay application, interface compliance, and edge cases - all passing.

- [x] Update MessageProcessor to use new components:
  - Modify `app/src/main/java/com/yazan/jetoverlay/domain/MessageProcessor.kt`:
    - Inject MessageCategorizer, VeilGenerator, and LlmService (via constructor)
    - Update processing flow:
      1. Categorize message -> set bucket
      2. Generate veil -> set veiledContent
      3. Generate responses -> set generatedResponses
      4. Update status to PROCESSED
  - Ensure proper error handling if any step fails
  - Add logging for each processing step
  - **Completed:** MessageProcessor already had all components properly integrated during the previous task (VeilGenerator and MessageProcessor enhancement). Constructor injects MessageCategorizer, VeilGenerator, and LlmService with sensible defaults. Processing flow follows the exact 4-step pattern: categorize → veil → generate responses → update state. Error handling with try-catch keeps messages in RECEIVED state for retry on failure. Comprehensive logging via android.util.Log.d() tracks each processing step.

- [x] Update MessageRepository with new operations:
  - Add method `applyBucket(messageId: Long, bucket: String)` to MessageRepository
  - Add method `getMessagesByBucket(bucket: String): Flow<List<Message>>` for filtering
  - Update MessageDao with corresponding queries
  - Ensure all repository methods properly emit Flow updates
  - **Completed:** Added `getMessagesByBucket(bucket: String): Flow<List<Message>>` query to MessageDao for filtering messages by bucket category. Added `updateBucket(id: Long, bucket: String)` query to MessageDao for direct bucket updates. Added `applyBucket(messageId: Long, bucket: String)` method to MessageRepository. Added `getMessagesByBucket(bucket: String)` method to MessageRepository. Created MessageRepositoryTest.kt with 16 unit tests covering all bucket types, applyBucket operations, Flow emission updates, and edge cases - all passing.

- [x] Write unit tests for categorization and veiling:
  - Create `app/src/test/java/com/yazan/jetoverlay/domain/MessageCategorizerTest.kt`:
    - Test each bucket classification with sample messages
    - Test edge cases and unknown senders
  - Create `app/src/test/java/com/yazan/jetoverlay/domain/VeilGeneratorTest.kt`:
    - Test veil generation for each bucket type
    - Verify original content is never leaked in veil
  - Create `app/src/test/java/com/yazan/jetoverlay/domain/StubLlmServiceTest.kt`:
    - Test that stub always returns expected responses
    - Test that delay is applied
  - **Completed:** Verified all unit tests exist and pass. MessageCategorizerTest.kt has 28 tests covering all bucket classifications (URGENT, WORK, SOCIAL, PROMOTIONAL, TRANSACTIONAL, UNKNOWN), priority handling, case-insensitive matching, and edge cases. VeilGeneratorTest.kt has 28 tests verifying veil generation for each bucket type, sender sanitization (XSS prevention), content leak prevention (emails, passwords, phone numbers, OTPs never exposed), and app name extraction. StubLlmServiceTest.kt has 19 tests confirming expected responses ("hello", "Got it!", "Thanks!"), delay application (~500ms), consistent responses across all buckets, and edge cases. All 75 tests pass successfully.

- [ ] Write integration tests for complete processing pipeline:
  - Create `app/src/androidTest/java/com/yazan/jetoverlay/domain/MessageProcessorIntegrationTest.kt`:
    - Insert a RECEIVED message into database
    - Verify MessageProcessor picks it up and processes it
    - Verify bucket is assigned correctly
    - Verify veiledContent is generated
    - Verify generatedResponses are populated
    - Verify status transitions to PROCESSED

- [ ] Run all tests and verify processing pipeline:
  - Execute `./gradlew test` for unit tests
  - Execute `./gradlew connectedAndroidTest` for integration tests
  - Manually verify on emulator:
    - Send notification -> verify bucket assignment in logs
    - Verify veiled content appears in overlay
    - Verify "hello" responses appear in UI
