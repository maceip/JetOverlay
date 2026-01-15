# JetOverlay Architecture

## 1. Overview
JetOverlay is an Android “social intelligence agent.” Incoming notifications, calls, and messages are intercepted, normalized, veiled, and replied to with minimal user friction. The app owns ingestion, processing, and integrations; the `jetoverlay` module provides the floating overlay SDK and window plumbing. Automation is opt-in via a “Auto Reply All” toggle; the default experience is user-in-the-loop with a fast bottom sheet.

## 2. Modules and Layers (map.txt)
- Modules: `app` (agent + orchestration) and `jetoverlay` (overlay SDK/service).
- Reactive flow: data ingests into Room; the domain layer processes; UI observes flows.
- Layer mapping:
  1) Sensory/Input: `AppNotificationListenerService`, `CallScreeningService`, `SmsIntegration`, `DataAcquisitionService`.
  2) Memory/Data: `AppDatabase`, `Message`, `MessageDao`, `MessageRepository`, `NotificationConfig`, `SlackConfig`, `EmailConfig`, `ReplyActionCache`.
  3) Intelligence/Brain: `MessageProcessor`, `MessageCategorizer`, `MessageBucket`, `VeilGenerator`, `LlmService`, `LiteRTLlmService`, `ModelManager`, `domain/litert/ToolRegistry`, `domain/litert/LiteRTClient`.
  4) Presentation/Output: SDK (`OverlayService`, `OverlayViewWrapper`) plus app UI (`FloatingBubble`, `AgentOverlay`, `CallScreeningOverlay`, `OverlayUiState`, `ResponseEditor`, `UnifiedInboxDashboard`, `MainActivity`, `OnboardingScreen`, `SettingsScreen`, `PermissionWizard`).
  5) Core/API & SDK: `OverlaySdk`, `OverlayConfig`, `OverlayNotificationConfig`, `JetOverlayApplication`.
  6) Infrastructure/Utilities: `Logger`, `CrashHandler`, `NetworkMonitor`, `PermissionManager`, `NotificationMapper`, `NotificationFilter`, `ResponseSender`, `IntegrationSyncWorker`.

## 3. Overlay Lifecycle Hardening
- Registry safety: `OverlaySdk` keeps strong registrations until explicitly unregistered (no GC drops). Missing content renders a no-op overlay with a warning; `isContentRegistered` and `unregisterContent` allow probing/cleanup.
- Service start safety: `OverlaySdk.startService` always attempts to start the foreground service and catches `ForegroundServiceStartNotAllowedException` on Android 12+; failed starts roll back pending `show()` calls.
- Add/remove safety: `OverlayService.addOverlay` rolls back `_activeOverlays` and stops the service if `addView` throws (e.g., missing overlay permission). Failures are logged to aid recovery by SDK callers.
- Cleanup order: `OverlayService.onDestroy` now removes overlays first, then cancels its scope so cleanup jobs are not cancelled early.
- Failure visibility: window attach errors, missing registry entries, and service start failures are logged; callers can check `OverlaySdk.isOverlayActive` to avoid restart loops.
- Registry hygiene: repeated registrations are pruned; callers can unregister to avoid leaking stale lambdas.

## 4. UI and Interaction Model
- Default overlay: a bottom-center “tic-tac” pill with a 1 px border (purple in light mode, white in dark mode). All taps pass through unless double-tapped.
- Processing state: when a new message is being handled, the pill animates with a bright red Knight Rider beam; a 5s auto-send countdown ticks with haptics when automation is enabled.
- Bottom sheet behavior:
  - On new messages: expands an inline bottom sheet showing source app icon, veiled content, generated response, and actions (`Edit`, `Regenerate`, `Send`).
  - Dismiss/close: snoozes the message for 20 minutes and moves it to the back of the queue; reappears after expiry.
  - Idle (not processing): double-tap launches `MainActivity` (full control/settings).
- Actions: `Regenerate` calls the LLM service and shows a spinner; `Send` dispatches via `ResponseSender` and collapses; `Edit` focuses the input (IME visible).
- Presentation pipeline: `OverlayUiState` wraps repository data; `FloatingBubble` observes state and toggles focusability when the sheet/input is active so IME can appear.

## 5. Permission and Onboarding Flow
- Wizard runs required permissions first, then optional (call screening, SMS, mic). Steps show a spinner/disabled state while requesting. If a call-screening role intent is unavailable, the wizard toasts and allows skipping.
- “Optional permissions” entry point from the control panel launches the wizard directly at optional steps. Skipping optional completes the wizard without looping back.
- Onboarding completes with a final auto-mode screen that previews the purple overlay and a Start button that ends setup and returns to the control panel.

## 6. Data and Processing Pipelines
### Notification/SMS ingestion
1) Trigger: system notification or SMS.
2) Fast silence: `NotificationSilencer` drops OTP/system noise before ingestion.
3) Filter/map: `NotificationFilter` guards noise; `NotificationMapper` normalizes to `Message` (including `threadKey`).
4) Persist: `MessageRepository.ingestNotification()` stores with status `RECEIVED`; `ReplyActionCache` keeps reply intents.
5) Cancel: notifications are cancelled if the user has enabled canceling and the config allows it.
6) Signal UI: `OverlaySdk.show(...)` ensures the service and overlay are alive.

### Call screening/acquisition
`CallScreeningService`, `SmsIntegration`, and `DataAcquisitionService` normalize call/SMS metadata to `Message` and store like notifications; `IntegrationSyncWorker` repairs state after restarts.

### Processing (“Brain” loop)
`MessageProcessor` watches `RECEIVED` across all ingest sources (started in `JetOverlayApplication`), buckets via `MessageCategorizer/MessageBucket`, veils via `VeilGenerator`, generates replies via `LlmService` (LiteRT with fallback), and updates records to `PROCESSED`. LLM generation is bounded by a short timeout; fallback replies are used if inference is slow or unavailable.

### Reply/auto-respond
- Default: auto-reply is OFF. Users must opt in via “Auto Reply All”.
- If enabled: a 5s window starts from message receipt; if the user doesn’t interact, the first generated reply is sent.
- Failures: send failures are queued with backoff (`RETRY` status) and retried later.
- Mark-as-read: best-effort; uses cached notification actions when available, otherwise integration handlers.
- `ResponseSender` uses cached reply actions or integration clients; email path is stubbed and will be replaced by provider APIs.

## 7. Integrations and OAuth
- Slack: `SLACK_CLIENT_ID/SECRET` are read from `gradle.properties`/env (not committed). Redirect URI uses the custom scheme `jetoverlay://slack/oauth`; the hosted helper page `https://maceip.github.io/id/slack-oauth.html` must forward to that URI.
- GitHub: `MainActivity` handles `jetoverlay://github-callback`; ensure the GitHub Pages redirect stays in sync.
- Email/Notion/other providers: configs live in `NotificationConfig`, `EmailConfig`, `SlackConfig`; buttons in the control panel surface connection status and SVG icons.

## 8. Known Limitations / Operational Notes
1) SDK initialization must occur in `Application.onCreate` so services and UI share the registry.
2) Overlay coordinates need re-evaluation on configuration changes (foldables, density changes) to avoid off-screen placements.
3) Auto-reply depends on cached reply intents and integration availability; guard when unavailable.
4) LiteRT model path: `/data/local/tmp/gemma-3n-E4B-int-int4.litertlm` (see `scripts/push_model.ps1` when loading to a device).
5) GitHub Pages redirect caching has caused stale Slack redirects; if CDN caching lingers, publish under a new filename and update the app URL.
6) Foreground service start still depends on Android policy; background launches on 12+ may still be denied without a visible activity.

## 9. Simplification Opportunities
- Consolidate `NotificationFilter` + `NotificationSilencer` into a single ruleset with explicit allow/deny lists.
- Replace stringly-typed `status` with an enum-backed column to reduce state errors.
- Centralize per-app config in a single settings module instead of mixing UI and data concerns.
- Move retry/snooze timing constants into a single policy object to avoid divergence.
- Add a single “thread key resolver” abstraction to avoid duplicating mapping logic across integrations.
