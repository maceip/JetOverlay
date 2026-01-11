# JetOverlay Architecture

## 1. Overview

JetOverlay is a pervasive "Social Intelligence Agent" for Android. It acts as a layer between the user and their incoming digital noise (notifications, calls).

**Core Philosophy: The Veil**
The system is designed to reduce anxiety. Incoming messages are not immediately shown. Instead, they are "Veiled" — summarized or hidden behind a generic "New Message" indicator — until the user explicitly chooses to "Unveil" them. The agent also proactively generates AI responses ("Smart Replies") to allow for quick, low-friction interactions.

## 2. System Architecture

The application follows a **Reactive Architecture** where the Database (`Room`) acts as the Single Source of Truth (SSoT). Services ingest data into the DB, and the UI observes the DB. There is no direct coupling between the Notification Listener and the UI.

### High-Level Layers

1.  **Sensory Layer (Input)**
    - Listens to System Events (Notifications, Phone Calls).
    - Run by `AppNotificationListenerService` and `CallScreeningService`.
2.  **Memory Layer (Data)**
    - Persists events as `Message` entities.
    - Managed by `MessageRepository` and `Room`.
3.  **Intelligence Layer (Brain)**
    - Processes raw messages asynchronously.
    - Generates "Veils" and "Smart Replies".
    - Managed by `MessageProcessor`.
4.  **Presentation Layer (Output)**
    - Renders the "Floating Bubble" overlay.
    - Managed by `OverlayService` and Jetpack Compose.

---

## 3. Data Flow Pipelines

### A. Notification Ingestion Pipeline

1.  **Trigger**: Android posts a notification.
2.  **Filter**: `AppNotificationListenerService` captures it.
3.  **Validate**: `MessageNotificationFilter` checks if it's relevant (ignores ongoing events).
4.  **Map**: `NotificationMapper` converts `StatusBarNotification` -> `Message` object.
5.  **Persist**: `MessageRepository.ingestNotification()` saves it to DB with status `RECEIVED`.
6.  **Action Cache**: `ReplyActionCache` stores the `PendingIntent` for replying later (in-memory).
7.  **Signal**: Services trigger `OverlaySdk.show("agent_bubble")` to ensure the UI service is running.

### B. The "Brain" Loop (MessageProcessor)

1.  **Observe**: `MessageProcessor` watches the DB for messages with status `RECEIVED`.
2.  **Process**:
    - Simulates "Thinking" (Network/LLM latency).
    - Generates `veiledContent` (e.g., "Message from Yazan").
    - Generates `generatedResponses` (e.g., "On my way!", "Can't talk").
3.  **Update**: Updates the DB record:
    - `status` -> `PROCESSED` (or `VEILED`)
    - Sets `veiledContent` and `generatedResponses`.

### C. The Rendering Loop (Overlay)

1.  **Launch**: `OverlayService` starts (FOREGROUND_SERVICE_TYPE_SPECIAL_USE).
2.  **Composition**: `OverlaySdk` retrieves the registered Composable (`overlay_1`).
3.  **Observation**: The Composable collects `repository.allMessages`.
4.  **Select**: Finds the _latest_ active message (`status != SENT && status != DISMISSED`).
5.  **State**: `OverlayUiState` wraps this message.
6.  **Render**: `FloatingBubble` draws the UI.
    - **Collapsed**: Shows a small bubble.
    - **Expanded**: Shows the `veiledContent` initially.
    - **Revealed**: User taps veil -> Shows `originalContent`.

---

## 4. Key Components & Responsibilities

### Core/API (`com.yazan.jetoverlay.api`)

- **`OverlaySdk`**: Singleton entry point. Manages the Service lifecycle and the "Registry" of overlay content types.
- **`OverlayConfig`**: Data class defining initial position, type, and ID of an overlay.

### Service Layer (`com.yazan.jetoverlay.service`)

- **`OverlayService`**: The heavy lifter. A bound Service that holds the `WindowManager` reference. It synchronizes the `ActiveOverlay` state from the SDK with actual Android Views (`OverlayViewWrapper`).
- **`AppNotificationListenerService`**: Extends Android's `NotificationListenerService`. The "Eyes" of the agent.
- **`integration.CallScreeningIntegration`**: Extends `CallScreeningService`. Intercepts calls, filters them, and potentially answers/records them.
- **`integration.CallRecordingService`**: Captures audio from the microphone during intercepted calls and saves it as an audio file (ingested as a message).

### Data Layer (`com.yazan.jetoverlay.data`)

- **`Message`**: The core entity.
  - `originalContent`: The raw notification text.
  - `veiledContent`: The safe, anxiety-free summary.
  - `status`: State machine (`RECEIVED` -> `PROCESSED` -> `QUEUED` -> `SENT` / `DISMISSED`).
- **`MessageRepository`**: Abstraction over Room DAO.

### UI Layer (`com.yazan.jetoverlay.ui`)

- **`FloatingBubble`**: The main composable. Handles the expansion animation and the "Veil" reveal interaction.
- **`OverlayUiState`**: Hoists the UI state (isExpanded, isRevealed) to separate it from the raw Data object.

---

## 5. Critical Workflows

### The "Veil" Mechanism

The veil is applied in `MessageProcessor.processMessage`. Even if the UI shows the bubble immediately upon ingestion, the `OverlayUiState` will fallback to "New Message" until the Processor populates the specific `veiledContent`.

### Activity vs. Context

Because the Overlay lives in a `Service` context, it does **not** have access to `Activity`-level features (like `startActivityForResult` directly). It must start Activities with `FLAG_ACTIVITY_NEW_TASK`. This impacts Permission flows, which are handled in `OverlayControlPanel` (an Activity UI).

---

## 6. Known Limitations / TODOs

1.  **Process Separation**: `OverlaySdk` singleton state must be initialized in `Application.onCreate` to ensure availability across Service and UI processes.
2.  **Permission Complexity**: Requires `SYSTEM_ALERT_WINDOW`, `BIND_NOTIFICATION_LISTENER`, `RECORD_AUDIO`, `ANSWER_PHONE_CALLS`, `READ_PHONE_STATE`.
3.  **Foldable Support**: `WindowManager` coordinates need careful handling on devices like Pixel Fold to avoid rendering in the "hinge" gap or off-screen when the display configuration changes (Open vs Closed).
