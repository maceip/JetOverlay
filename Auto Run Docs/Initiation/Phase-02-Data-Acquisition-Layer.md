# Phase 02: Data Acquisition Layer

This phase builds the unified data ingestion pipeline that captures messages from multiple sources: Android notifications (primary), SMS, email, and OAuth integrations (Slack, Notion, GitHub). Each source will feed into the existing `MessageRepository` using a consistent `Message` entity format. The notification listener already exists but will be enhanced to properly veil notifications from users. By the end of this phase, the app will be able to intercept and store messages from all configured sources, ready for processing by the Brain layer.

## Tasks

- [x] Enhance NotificationListenerService to prevent user from seeing notifications:
  - Read existing `app/src/main/java/com/yazan/jetoverlay/service/AppNotificationListenerService.kt`
  - Implement `cancelNotification(sbn.key)` after processing to hide the notification from the user
  - Add configuration option to control which apps should have notifications cancelled vs passed through
  - Create `app/src/main/java/com/yazan/jetoverlay/data/NotificationConfig.kt`:
    - Data class with `packageName: String`, `shouldVeil: Boolean`, `shouldCancel: Boolean`
    - Default to veiling and cancelling all notifications
  - Test that notifications are properly intercepted and hidden on the emulator
  - **Completed:** Created `NotificationConfig.kt` with `NotificationConfigManager` for per-app configuration. Enhanced `AppNotificationListenerService.kt` to check config and call `cancelNotification(sbn.key)` after processing. Added unit tests in `NotificationConfigTest.kt`. System apps (android, systemui, downloads) are protected from cancellation.

- [x] Create SMS ingestion service:
  - Create `app/src/main/java/com/yazan/jetoverlay/service/integration/SmsIntegration.kt`:
    - BroadcastReceiver for `android.provider.Telephony.SMS_RECEIVED`
    - Extract sender number and message body from PDU
    - Map to `Message` entity with packageName="sms", senderName=phone number
    - Ingest via `MessageRepository.ingestNotification()`
  - Update `AndroidManifest.xml` to register the receiver with proper intent filter
  - Add `RECEIVE_SMS` and `READ_SMS` permissions to manifest
  - Handle the permission request flow in `OverlayControlPanel.kt`
  - **Completed:** Created `SmsIntegration.kt` as a BroadcastReceiver that handles `SMS_RECEIVED_ACTION` intents, extracts sender and message body using `Telephony.Sms.Intents.getMessagesFromIntent()`, supports multi-part SMS concatenation, and ingests messages via `MessageRepository` with `packageName="sms"`. Added SMS permissions (`RECEIVE_SMS`, `READ_SMS`) and receiver registration in `AndroidManifest.xml`. Added SMS permission request flow in `OverlayControlPanel.kt` after Call Screening role check. Created unit tests in `SmsIntegrationTest.kt` and instrumented tests in `service/SmsIntegrationTest.kt`.

- [x] Create email ingestion foundation (Gmail API stub):
  - Create `app/src/main/java/com/yazan/jetoverlay/service/integration/EmailIntegration.kt`:
    - Object/singleton matching the SlackIntegration pattern
    - Stub methods: `startOAuth(context)`, `handleOAuthCallback(code)`, `pollForEmails()`
    - For now, `pollForEmails()` returns mock email messages every 30 seconds
    - Log "Email integration not yet implemented - returning mock data"
  - Create `app/src/main/java/com/yazan/jetoverlay/data/EmailConfig.kt`:
    - Store OAuth tokens (access_token, refresh_token, expiry)
    - Persist to SharedPreferences (no encryption needed per requirements)
  - Add placeholder UI button in `OverlayControlPanel.kt` for "Connect Email"
  - **Completed:** Created `EmailIntegration.kt` as an object/singleton with full OAuth flow stub (`startOAuth`, `handleOAuthCallback`), mock polling with 30-second interval, and message ingestion via `MessageRepository` with `packageName="email"`. Created `EmailConfig.kt` for persisting OAuth tokens (access_token, refresh_token, expiry_time) to SharedPreferences with token validation and expiry checking (5-minute buffer). Added "Connect Email" button in `OverlayControlPanel.kt` in the Integrations section. Created unit tests in `EmailIntegrationTest.kt` (7 tests for MockEmail data class and constants) and instrumented tests in `EmailConfigTest.kt` (14 tests for token storage/retrieval). All tests pass.

- [x] Enhance Slack integration with proper error handling:
  - Read existing `app/src/main/java/com/yazan/jetoverlay/service/integration/SlackIntegration.kt`
  - Add exponential backoff on API failures (start at 5s, max 60s)
  - Add proper error logging for OAuth failures
  - Store last successful poll timestamp to avoid duplicate messages
  - Create `app/src/main/java/com/yazan/jetoverlay/data/SlackConfig.kt`:
    - Persist workspace info and last poll timestamp
  - Reduce polling frequency to 15 seconds for production use
  - **Completed:** Created `SlackConfig.kt` with full configuration persistence including OAuth tokens (access_token, bot_token), workspace info (workspace_id, workspace_name, user_id), last poll timestamp for duplicate detection, and exponential backoff state (current_backoff, last_error_timestamp). Enhanced `SlackIntegration.kt` with exponential backoff (5s initial, 60s max, 2x multiplier), comprehensive OAuth error logging (error codes, descriptions, full response bodies), last poll timestamp integration using Slack API format, and 15-second polling interval (via `SlackConfig.DEFAULT_POLL_INTERVAL_MS`). Created unit tests in `SlackConfigTest.kt` (7 tests for constants and backoff math) and `SlackIntegrationTest.kt` (2 tests for constants and singleton). Created instrumented tests in `data/SlackConfigTest.kt` (26 tests covering token storage, workspace info, poll timestamps, exponential backoff, and debug info). All tests pass.

- [x] Create Notion integration stub:
  - Create `app/src/main/java/com/yazan/jetoverlay/service/integration/NotionIntegration.kt`:
    - Object/singleton matching SlackIntegration pattern
    - Stub methods: `startOAuth(context)`, `handleOAuthCallback(code)`, `pollForNotifications()`
    - `pollForNotifications()` returns mock Notion mention notifications
    - Log "Notion integration not yet implemented - returning mock data"
  - Add placeholder UI button in `OverlayControlPanel.kt` for "Connect Notion"
  - **Completed:** Created `NotionIntegration.kt` as an object/singleton with full OAuth flow stub (`startOAuth`, `handleOAuthCallback`), mock polling with 30-second interval, and message ingestion via `MessageRepository` with `packageName="notion"`. Implemented `NotionNotificationType` enum with MENTION, COMMENT, PAGE_UPDATE, and DATABASE_UPDATE types. Created `MockNotionNotification` data class with id, type, pageTitle, author, and content fields. Added "Connect Notion" button in `OverlayControlPanel.kt` in the Integrations section. Created unit tests in `NotionIntegrationTest.kt` (10 tests for data class operations, singleton verification, and enum operations). All tests pass.

- [x] Create GitHub integration stub:
  - Create `app/src/main/java/com/yazan/jetoverlay/service/integration/GitHubIntegration.kt`:
    - Object/singleton matching SlackIntegration pattern
    - Stub methods: `startOAuth(context)`, `handleOAuthCallback(code)`, `pollForNotifications()`
    - `pollForNotifications()` returns mock GitHub notification messages (PR reviews, mentions)
    - Log "GitHub integration not yet implemented - returning mock data"
  - Add placeholder UI button in `OverlayControlPanel.kt` for "Connect GitHub"
  - **Completed:** Created `GitHubIntegration.kt` as an object/singleton with full OAuth flow stub (`startOAuth`, `handleOAuthCallback`), mock polling with 30-second interval, and message ingestion via `MessageRepository` with `packageName="github"`. Implemented `GitHubNotificationType` enum with PR_REVIEW, PR_COMMENT, ISSUE_COMMENT, MENTION, ASSIGN, CI_FAILURE, and RELEASE types. Created `MockGitHubNotification` data class with id, type, repository, author, title, and content fields. Added "Connect GitHub" button in `OverlayControlPanel.kt` in the Integrations section. Created unit tests in `GitHubIntegrationTest.kt` (12 tests for data class operations, singleton verification, and enum operations). All tests pass.

- [ ] Create unified DataAcquisitionService:
  - Create `app/src/main/java/com/yazan/jetoverlay/service/DataAcquisitionService.kt`:
    - Foreground service that coordinates all data sources
    - Manages polling intervals for each integration
    - Provides single point of control for starting/stopping data collection
    - Uses coroutine scope with SupervisorJob for isolation
  - Update `AndroidManifest.xml` to register the service
  - Start this service from `JetOverlayApplication.onCreate()` when permissions are granted

- [ ] Write integration tests for data acquisition:
  - Create `app/src/androidTest/java/com/yazan/jetoverlay/service/NotificationListenerTest.kt`:
    - Test that mock notifications are properly ingested into the database
    - Test that notification cancellation works (may require shell commands)
    - Test that filtered notifications are ignored
  - Create `app/src/androidTest/java/com/yazan/jetoverlay/service/SmsIntegrationTest.kt`:
    - Test SMS broadcast receiver processes messages correctly
    - Test SMS messages appear in MessageRepository

- [ ] Run all tests and verify data acquisition works end-to-end:
  - Execute `./gradlew connectedAndroidTest`
  - Manually test on emulator:
    - Send a test notification and verify it's intercepted and hidden
    - Verify message appears in the overlay bubble
  - Document any issues found and fixes applied
