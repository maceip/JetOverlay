# ssh-jet: Notification Veiling & Intelligent Response System

## Project Overview

ssh-jet (JetOverlay) is an Android overlay application that intercepts notifications, hides emotionally-charged content from the user through "veiling," categorizes messages into buckets, generates AI responses, and offers human-in-the-loop approval before sending. The core philosophy is **reducing anxiety** by protecting users from immediate exposure to potentially stressful messages.

## Phase Summary

| Phase | Title | Purpose |
|-------|-------|---------|
| 01 | Architecture Review & E2E Testing Harness | Audit existing code for Android 16 compatibility, document fragility points, set up emulator-based testing infrastructure |
| 02 | Data Acquisition Layer | Enhance notification interception, add SMS/Email/OAuth integrations, create unified data pipeline |
| 03 | Core Logic - Veiling, Bucketing & LLM Stub | Implement message categorization, veil generation, and stubbed LLM responses |
| 04 | UI Overlay Polish & Human-in-the-Loop | Complete overlay UX with status indicators, response editing, send/dismiss flows |
| 05 | Integration Polish & Production Readiness | Permission onboarding, settings, battery optimization, first-run experience |
| 06 | LLM Stub Evolution | full integration with on-device llm framework LiteRT-LM |
| 07 | Screening Calls | create an android  service that picks up all calls and can respond with a human  voice  using on device llm|
| 08 | Notification Zero  | email, slack, linear, text message, signal, instagram, whatsapp, any android app all working through our system |

## Key Constraints

- **All work must be tested E2E via emulator before commit** - No guesswork commits
- **LLM is stubbed** - Returns "hello" for all messages; real implementation from google-ai-edge/gallery later
- **No security/encryption needed** - Veiling is psychological protection, not data security
- **Phone call screening deprioritized** - Stub exists but won't be implemented in these phases
- **Rock-solid UX** - Overlay must never crash, never block user interaction

## Existing Architecture

The project uses a **Reactive Architecture** with Room as Single Source of Truth:

```
Sensory Layer → Memory Layer → Intelligence Layer → Presentation Layer
(Notification/   (Room DB,      (MessageProcessor,  (FloatingBubble,
 SMS/OAuth)      Repository)    Categorizer, LLM)   AgentOverlay)
```

Key components already exist:
- `OverlaySdk` - Singleton managing overlay lifecycle via WindowManager
- `OverlayService` - Foreground service rendering Compose UI as system overlay
- `AppNotificationListenerService` - Captures system notifications
- `MessageProcessor` - Processes messages (veiling, response generation)
- `FloatingBubble` - Main Compose UI component

## Documentation Structure

All architecture audits and documentation use structured markdown with:
- YAML front matter (type, tags, related)
- Wiki-link cross-references (`[[Document-Name]]`)
- Organized under `docs/` folder

## Execution Notes

- Each phase should be completed in order
- Phase 1 must pass all tests before proceeding
- Use the emulator for all E2E testing
- Run `./gradlew connectedAndroidTest` to verify each phase
