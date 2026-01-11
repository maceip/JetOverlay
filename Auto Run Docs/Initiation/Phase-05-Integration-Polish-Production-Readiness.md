# Phase 05: Integration Polish & Production Readiness

This phase ties all components together into a cohesive, production-ready application. Focus areas include improving the permission onboarding flow, adding proper error handling and logging throughout, ensuring battery efficiency, and creating a smooth first-run experience. By the end of this phase, the app will be ready for real-world testing with a polished user experience, comprehensive error handling, and sustainable resource usage.

## Tasks

- [x] Enhance permission onboarding flow:
  - Read existing `app/src/main/java/com/yazan/jetoverlay/OverlayControlPanel.kt`
  - Create step-by-step permission wizard:
    - Show clear explanation for each permission before requesting
    - Guide user through Settings screens for special permissions
    - Show checkmarks for completed permissions
    - Only enable "Start" button when all required permissions granted
  - Add permission status persistence:
    - Remember which permissions have been granted across app restarts
    - Re-check permissions on app resume in case user revoked in Settings
  - Handle permission denial gracefully:
    - Show explanation why permission is needed
    - Offer to open app Settings for manual grant

  **Implementation Notes:**
  - Created `PermissionManager.kt` utility class for centralized permission handling
  - Created `PermissionWizard.kt` composable with step-by-step UI and animated transitions
  - Integrated wizard into `OverlayControlPanel.kt` with AnimatedContent transitions
  - Permission status persisted in SharedPreferences with denial count tracking
  - Shows rationale after first denial, opens Settings after second denial
  - Unit tests in `util/PermissionManagerTest.kt`, UI tests in `ui/PermissionWizardTest.kt`

- [ ] Implement proper logging and error reporting:
  - Create `app/src/main/java/com/yazan/jetoverlay/util/Logger.kt`:
    - Wrapper around Android Log with consistent tagging
    - Methods: `d()`, `i()`, `w()`, `e()` with tag prefix "JetOverlay"
    - Include timestamp and component name in log messages
  - Add logging throughout the app:
    - Service lifecycle events (start, stop, bind)
    - Message processing steps (receive, veil, bucket, respond)
    - UI state changes (expand, reveal, send)
    - Errors with stack traces
  - Create crash handler:
    - Set uncaught exception handler in Application.onCreate()
    - Log crash details before app terminates

- [ ] Optimize battery and resource usage:
  - Review all polling intervals:
    - DataAcquisitionService: ensure efficient scheduling
    - Slack/OAuth integrations: use WorkManager for background polling instead of foreground loops
  - Implement doze mode compatibility:
    - Use `setExactAndAllowWhileIdle()` for critical alarms only
    - Allow non-urgent polls to be batched by system
  - Review coroutine scopes:
    - Ensure all scopes are properly cancelled when services stop
    - Use `viewModelScope` or `lifecycleScope` where appropriate
  - Add `JobScheduler` or `WorkManager` for periodic sync tasks

- [ ] Create first-run experience:
  - Create `app/src/main/java/com/yazan/jetoverlay/ui/OnboardingScreen.kt`:
    - Welcome screen explaining the app concept (The Veil)
    - Visual demonstration of how veiling works
    - Permission request flow integrated
  - Track first-run state in SharedPreferences
  - Show onboarding only on first launch
  - Allow user to re-access onboarding from settings

- [ ] Add settings screen for user preferences:
  - Create `app/src/main/java/com/yazan/jetoverlay/ui/SettingsScreen.kt`:
    - Toggle for each integration (enable/disable Slack, Email, etc.)
    - Toggle for notification cancellation (hide vs pass-through)
    - Bubble position preference (remember last position)
    - Theme preference (if applicable)
  - Store settings in SharedPreferences
  - Apply settings reactively throughout the app

- [ ] Implement graceful degradation and offline handling:
  - Handle network unavailable:
    - OAuth integrations should retry with backoff
    - Show "Offline" indicator if cloud features unavailable
    - Local notification processing should continue regardless
  - Handle service killed by system:
    - Use `START_STICKY` for critical services
    - Restore state from database on restart
    - Re-register notification listener if needed
  - Handle low memory situations:
    - Implement `onTrimMemory()` in Application
    - Clear non-essential caches when memory is low

- [ ] Write E2E test scenarios for production readiness:
  - Create `app/src/androidTest/java/com/yazan/jetoverlay/E2EProductionTest.kt`:
    - Test complete flow from notification to sent response
    - Test app survives process death and restart
    - Test app handles rapid notification bursts
    - Test app handles configuration changes (rotation)
  - Test battery impact:
    - Run app for extended period on emulator
    - Monitor battery stats via `dumpsys batterystats`
    - Ensure no wake locks held longer than necessary

- [ ] Final integration verification:
  - Run complete test suite: `./gradlew test connectedAndroidTest`
  - Perform manual smoke test of all features:
    - Fresh install -> onboarding -> permissions
    - Receive notification -> veil -> respond -> send
    - Multiple message buckets working
    - OAuth integrations connecting (or showing proper stubs)
    - Settings changes apply correctly
    - App survives backgrounding and foregrounding
  - Document any remaining issues in `docs/issues/known-issues.md` with front matter:
    - type: report, tags: [issues, bugs, backlog]
  - Create release checklist in `docs/release/release-checklist.md`
