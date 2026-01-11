# Phase 01: Architecture Review & E2E Testing Harness

This phase establishes the foundation for confident development by auditing the existing architecture for Android 16 compatibility and lifecycle durability, then setting up a complete E2E testing harness using the Android emulator. By the end of this phase, you will have a documented architecture review, identified fragility points, and a working test infrastructure that can verify the overlay lifecycle, notification handling, and core data flows. This ensures no guesswork commits and gives the team confidence that the existing system is sound before building new features.

## Tasks

- [x] Audit overlay lifecycle and Android 16 compatibility:
  - Read `jetoverlay/src/main/java/com/yazan/jetoverlay/service/OverlayService.kt` thoroughly
  - Read `jetoverlay/src/main/java/com/yazan/jetoverlay/internal/OverlayViewWrapper.kt` thoroughly
  - Analyze WindowManager lifecycle handling (attach/detach, configuration changes)
  - Check for proper handling of `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on Android 14+
  - Verify lifecycle callbacks align with Android 16 behavior (ON_RESUME, ON_DESTROY)
  - Review drag gesture handling for potential ANR scenarios
  - Document findings in `docs/architecture/overlay-lifecycle-audit.md` with front matter:
    - type: analysis, tags: [architecture, android-16, overlay, lifecycle]
  - List any fragility points or recommended fixes
  - **Completed 2026-01-11**: Created comprehensive audit document identifying 6 fragility points including race condition in onDestroy, silent exception swallowing, and potential memory leak in registry. Android 16 compatibility verified - FOREGROUND_SERVICE_TYPE_SPECIAL_USE handling is correct.

- [x] Audit Hilt/DI setup and service architecture:
  - Read `app/src/main/java/com/yazan/jetoverlay/JetOverlayApplication.kt`
  - Read `app/src/main/java/com/yazan/jetoverlay/service/AppNotificationListenerService.kt`
  - Analyze current dependency injection approach (singleton pattern vs Hilt)
  - Identify testability concerns with current DI approach
  - Review service initialization order and potential race conditions
  - Check for memory leaks in service-to-repository bindings
  - Document findings in `docs/architecture/di-service-audit.md` with front matter:
    - type: analysis, tags: [architecture, dependency-injection, services]
  - Include recommendations for improving testability
  - **Completed 2026-01-11**: Created comprehensive DI/service architecture audit identifying 6 issues including: inability to inject mock repository (HIGH), MessageProcessor not cancellable (MEDIUM), Application initialization race condition (HIGH), multiple MessageProcessor instances (MEDIUM), ReplyActionCache accumulation (LOW-MEDIUM). Manual singleton pattern works but creates significant barriers to unit testing.

- [x] Audit data layer and Room database implementation:
  - Read `app/src/main/java/com/yazan/jetoverlay/data/AppDatabase.kt`
  - Read `app/src/main/java/com/yazan/jetoverlay/data/Message.kt`
  - Read `app/src/main/java/com/yazan/jetoverlay/data/MessageDao.kt`
  - Read `app/src/main/java/com/yazan/jetoverlay/data/MessageRepository.kt`
  - Verify Room schema is correct and type converters handle edge cases
  - Check Flow emissions for potential UI thread issues
  - Review ReplyActionCache for memory leak potential
  - Document findings in `docs/architecture/data-layer-audit.md` with front matter:
    - type: analysis, tags: [architecture, room, data-layer]
  - **Completed 2026-01-11**: Created comprehensive data layer audit identifying 13 issues: malformed JSON handling in TypeConverters (MEDIUM), unbounded ReplyActionCache growth (MEDIUM), silent failures in repository operations (MEDIUM), no migration strategy, status field stringly-typed, Gson instance recreation, no pagination. Flow emissions verified thread-safe via Room's automatic IO dispatcher and Compose's collectAsState. Room database correctly configured for v1.

- [x] Audit MessageProcessor (Brain) and notification handling:
  - Read `app/src/main/java/com/yazan/jetoverlay/domain/MessageProcessor.kt`
  - Read `app/src/main/java/com/yazan/jetoverlay/service/notification/NotificationFilter.kt`
  - Read `app/src/main/java/com/yazan/jetoverlay/service/notification/NotificationMapper.kt`
  - Analyze coroutine scope management and cancellation handling
  - Check for proper error handling in message processing flow
  - Verify SupervisorJob usage and exception propagation
  - Document findings in `docs/architecture/brain-audit.md` with front matter:
    - type: analysis, tags: [architecture, message-processing, coroutines]
  - **Completed 2026-01-11**: Created comprehensive audit identifying 11 issues: no coroutine cancellation in MessageProcessor (HIGH), duplicate processing risk due to Flow re-emission (HIGH), silent exception handling with SupervisorJob (MEDIUM), service scope not cancelled (MEDIUM), missing EXTRA_BIG_TEXT handling in NotificationMapper (MEDIUM). SupervisorJob usage is correct but incomplete - needs CoroutineExceptionHandler. NotificationFilter is intentionally permissive for debugging. Recommended adding stop() method to MessageProcessor and cancelling scopes in onDestroy().

- [x] Create architecture summary with fragility matrix:
  - Create `docs/architecture/fragility-matrix.md` consolidating all audit findings
  - Include front matter: type: report, tags: [architecture, summary, fragility]
  - Create a risk matrix table with columns: Component, Risk Level (Low/Medium/High), Issue, Recommendation
  - Link to individual audit documents using `[[document-name]]` wiki-links
  - Prioritize issues that could cause crashes or ANRs
  - Include a "Quick Wins" section for easy fixes
  - **Completed 2026-01-11**: Created comprehensive fragility matrix consolidating 37 issues from all 4 audit documents. Categorized by severity (4 HIGH, 13 MEDIUM, 20 LOW), component risk levels, and issue types. Included 8 Quick Wins that can be implemented in under an hour combined. Most critical findings: coroutine lifecycle management (MessageProcessor/services), duplicate processing risk, and testability barriers from singleton pattern.

- [x] Set up Android emulator for E2E testing:
  - Check if Android SDK and emulator are available on the system via command line tools
  - If not present, document the manual steps needed in `docs/testing/emulator-setup.md`
  - Create or identify an AVD configuration targeting API 34+ (Android 14+)
  - Verify the emulator can start and run the application
  - Document the emulator configuration and launch commands
  - Include front matter: type: reference, tags: [testing, emulator, setup]
  - **Completed 2026-01-11**: Verified Android SDK installed at `%LOCALAPPDATA%\Android\Sdk` with emulator, platforms (API 34-36), and system images. Existing AVD `Medium_Phone_API_36.1` (x86_64, Google Play) is configured and ready. Created comprehensive `docs/testing/emulator-setup.md` with launch commands, troubleshooting guide, and CI integration notes. Note: ANDROID_HOME environment variable is not set - documented as recommendation.

- [x] Create E2E test infrastructure for the app module:
  - Create `app/src/androidTest/java/com/yazan/jetoverlay/BaseAndroidTest.kt`:
    - Base test class with common setup/teardown
    - ActivityScenario utilities for MainActivity
    - Helper methods for granting overlay permissions in tests
  - Create `app/src/androidTest/java/com/yazan/jetoverlay/TestUtils.kt`:
    - Utility functions for waiting on UI state changes
    - Helper for simulating notification posts (if possible via instrumentation)
    - Timeout constants and retry logic
  - Update `app/build.gradle.kts` if needed to add test dependencies (MockK, Turbine for Flow testing)
  - **Completed 2026-01-11**: Created comprehensive E2E test infrastructure including:
    - `BaseAndroidTest.kt`: Base test class with ActivityScenario utilities, overlay permission helpers (via ADB shell), wait condition helpers, UiDevice access, and emulator detection
    - `TestConstants.kt`: Timeout constants, test message data, and configuration values
    - `TestUtils.kt`: In-memory Room database factory, Flow condition waiting, overlay show/hide helpers, test notification posting, retry with backoff, and `runBlockingTest` extension
    - Updated `libs.versions.toml` with MockK (1.13.13), Turbine (1.2.0), Coroutines Test (1.9.0), Room Testing (2.8.4), UI Automator (2.3.0), and Test Core/Runner/Rules
    - Updated `app/build.gradle.kts` with all test dependencies
    - Build verified successful with `./gradlew :app:compileDebugAndroidTestKotlin`

- [x] Create Room database tests:
  - Create `app/src/androidTest/java/com/yazan/jetoverlay/data/MessageDaoTest.kt`:
    - Test insert and retrieve messages
    - Test Flow emissions on data changes
    - Test getAllMessages ordering (timestamp DESC)
    - Test update and delete operations
  - Use in-memory Room database for test isolation
  - Verify type converters work correctly for List<String> serialization
  - **Completed 2026-01-11**: Created comprehensive MessageDaoTest with 19 test cases covering:
    - Insert/retrieve operations (single message, multiple messages, by ID, non-existent ID)
    - Flow emissions (initial empty, on insert, on update, on deleteAll) using Turbine
    - Ordering verification (timestamp DESC)
    - Update operations (modifying fields, preserving unmodified fields)
    - Delete operations (deleteAll, empty table handling)
    - Type converters for List<String> (empty list, single item, multiple items, special characters including Unicode/emoji, updates)
    - REPLACE conflict strategy behavior for insert with existing ID
    - Uses in-memory Room database for test isolation, Turbine for Flow testing
    - Fixed META-INF packaging conflicts in build.gradle.kts by excluding LICENSE.md and LICENSE-notice.md for JUnit Jupiter dependencies

- [ ] Create overlay lifecycle integration tests:
  - Create `app/src/androidTest/java/com/yazan/jetoverlay/service/OverlayServiceTest.kt`:
    - Test service starts correctly as foreground service
    - Test overlay appears when OverlaySdk.show() is called
    - Test overlay disappears when OverlaySdk.hide() is called
    - Test service survives configuration changes
  - These tests validate the core overlay mechanism works end-to-end

- [ ] Run all tests and verify green state:
  - Execute `./gradlew connectedAndroidTest` on the emulator
  - Capture test results and any failures
  - Fix any immediate test infrastructure issues
  - Document test execution process in `docs/testing/running-tests.md` with front matter:
    - type: reference, tags: [testing, ci, commands]
  - Confirm all tests pass before proceeding to Phase 2
