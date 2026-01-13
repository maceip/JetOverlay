# JetOverlay Architecture

## 1. Overview
JetOverlay is an Android "social intelligence agent." Incoming notifications, calls, and messages are intercepted, stored, and processed before any UI is shown. The agent builds a safe "veil" summary, proposes smart replies, and renders a floating overlay so users can decide when to reveal or respond.

## 2. Modules and Layers
- Modules: `app` (agent app and orchestration) and `jetoverlay` (SDK powering overlays and the WindowManager plumbing).
- Reactive data flow: Room DB is the single source of truth. Services ingest into the DB, the domain layer processes, and the UI observes.

### Layer Mapping (from map.txt)
1) Sensory (Input): `AppNotificationListenerService`, `CallScreeningService`, `SmsIntegration`, `DataAcquisitionService`.
2) Memory (Data): `AppDatabase`, `Message`, `MessageDao`, `MessageRepository`, `NotificationConfig`, `SlackConfig`, `EmailConfig`, `ReplyActionCache`.
3) Intelligence (Brain): `MessageProcessor`, `MessageCategorizer`, `MessageBucket`, `VeilGenerator`, `LlmService`, `LiteRTLlmService`, `StubLlmService`, `ModelManager`, `domain/litert/ToolRegistry`, `domain/litert/LiteRTClient`.
4) Presentation (Output): `jetoverlay` module (`OverlayService`, `OverlayViewWrapper`) plus `FloatingBubble`, `AgentOverlay`, `CallScreeningOverlay`, `OverlayUiState`, `ResponseEditor`, `UnifiedInboxDashboard`, `MainActivity`, `OnboardingScreen`, `SettingsScreen`, `PermissionWizard`.
5) Core/API & SDK: `OverlaySdk`, `OverlayConfig`, `OverlayNotificationConfig`, `JetOverlayApplication`.
6) Infrastructure/Utilities: `Logger`, `CrashHandler`, `NetworkMonitor`, `PermissionManager`, `NotificationMapper`, `NotificationFilter`, `ResponseSender`, `IntegrationSyncWorker`.

## 3. Data Flow Pipelines

### A. Notification/SMS Ingestion
1. Trigger: System posts a notification or SMS.
2. Filter: `NotificationFilter` guards irrelevant or ongoing events.
3. Map: `NotificationMapper` converts system payloads to `Message`.
4. Persist: `MessageRepository.ingestNotification()` writes to Room with status `RECEIVED`; `ReplyActionCache` stores pending reply intents.
5. Signal: Overlay is ensured alive via `OverlaySdk.show(...)`.

### B. Call Screening and Acquisition
1. `CallScreeningService` intercepts calls; `SmsIntegration` and `DataAcquisitionService` gather call/SMS metadata.
2. Relevant events are normalized to `Message` records and stored like notifications.
3. `IntegrationSyncWorker` keeps external integrations in sync when the app restarts.

### C. Processing ("Brain" Loop)
1. Observe: `MessageProcessor` watches for `RECEIVED` messages.
2. Categorize/Bucket: `MessageCategorizer` and `MessageBucket` group and tag messages.
3. Veil and replies: `VeilGenerator` produces `veiledContent`; `MessageProcessor` prepares `generatedResponses`.
4. LLM selection: `ModelManager` chooses an `LlmService` implementation (`LiteRTClient` via `ToolRegistry`, or `StubLlmService` fallback).
5. Update: Record is updated to `PROCESSED` (or domain-specific status) with veil and responses.

### D. Rendering
1. Service: `OverlayService` (in `jetoverlay`) runs as a foreground service and owns the `WindowManager`; addView failures roll back SDK state and stop the service if nothing is showing.
2. Composition: `OverlaySdk` resolves the registered composable for the overlay id (registry is weak-referenced/pruned; missing content renders a no-op instead of crashing).
3. Observation: UI (`FloatingBubble`, `AgentOverlay`, etc.) collects messages from the repository and wraps in `OverlayUiState`.
4. Interaction: Collapsed bottom-center “tic-tac” bar glows while processing; double-tap expands an inline sheet (no modal dialog) with veil + suggestions; responses dispatch through `ResponseSender`.

## 4. Key Components (by package)
- `com.yazan.jetoverlay.api`: `OverlaySdk`, `OverlayConfig`, `OverlayNotificationConfig`.
- Services: `OverlayService`, `OverlayViewWrapper`, `AppNotificationListenerService`, `CallScreeningService`, `SmsIntegration`, `DataAcquisitionService`, `IntegrationSyncWorker`.
- Domain: `MessageProcessor`, `MessageCategorizer`, `MessageBucket`, `VeilGenerator`, `ResponseSender`, `ModelManager`, `LlmService` variants, `ToolRegistry`, `LiteRTClient`.
- Data: `AppDatabase`, `MessageDao`, `MessageRepository`, `Message`, `NotificationConfig`, `SlackConfig`, `EmailConfig`, `ReplyActionCache`.
- UI: `FloatingBubble`, `AgentOverlay`, `CallScreeningOverlay`, `OverlayUiState`, `ResponseEditor`, `UnifiedInboxDashboard`, `OnboardingScreen`, `SettingsScreen`, `PermissionWizard`, `MainActivity`.
- Utilities: `Logger`, `CrashHandler`, `NetworkMonitor`, `PermissionManager`, `NotificationMapper`, `NotificationFilter`.

## 5. Critical Workflows
- Veil and Smart Replies: `MessageProcessor` drives veil text and candidate replies before UI reveal to reduce anxiety.
- Reply Path: User picks a response in the overlay; `ResponseSender` uses `ReplyActionCache` or integration clients to dispatch and updates message status to `SENT` or `DISMISSED`.
- Response Editing: Tapping a suggested reply opens the inline editor (requests focus/IME); Regenerate uses the LLM service (LiteRT/Stub) to refresh suggestions in-place; Send dispatches via `ResponseSender` and collapses the sheet.
- Auto-Respond: After processing completes, the overlay glows for ~5s; if the user does not interact, the first generated reply is auto-sent (currently forwarded to the test mailbox, not the original recipient).
- Permission Flow: Overlay lives in a service context; activities are started with `FLAG_ACTIVITY_NEW_TASK`. Permissions for overlays, notifications, calls, audio, and phone state are orchestrated through onboarding and the permission wizard.
- Onboarding Flow: After permissions (including optional call/SMS) the wizard advances to a final “auto mode” screen (purple overlay preview) with a Start button that completes onboarding.
- Resilience: Foreground service plus SDK re-registering overlays ensures survival across process death; `IntegrationSyncWorker` repairs integrations after restarts.
- Overlay Safety: Missing content registrations render a no-op overlay with a warning; failed overlay creation rolls back SDK state and stops the service if empty; service start failures roll back `show()` state.

## 6. Known Limitations / Risks
1. SDK initialization must happen in `Application.onCreate` so services and UI share the same registry.
2. Permission complexity remains high (SYSTEM_ALERT_WINDOW, notification listener, call screening, audio/phone state).
3. Foldable/multi-window coordinates need careful handling to avoid off-screen overlays when display configuration changes.
4. Overlay glow is intentionally low-profile; ensure future visual changes keep it unobtrusive for accessibility.
5. Auto-reply sends the first generated response after ~5 seconds of inactivity if the user does not interact; ensure reply actions remain cached or guard when absent. Outgoing replies are currently redirected to test email `730011799396-0001@t-online.de` (no delivery to original recipients) for validation.
6. LiteRT model is resolved by `ModelManager` at `/data/local/tmp/gemma-3n-E4B-it-int4.litertlm` (see `scripts/push_model.ps1` when reloading devices).
