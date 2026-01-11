# Phase 04: UI Overlay Polish & Human-in-the-Loop Approval

This phase completes the user-facing experience by enhancing the floating bubble overlay with visual status indicators, bucket-based styling, and a complete human-in-the-loop approval flow. Users will be able to see what's happening at a glance, review veiled messages, see AI-generated responses, edit them if needed, request new responses, or approve and send. The overlay must never crash, never block user interaction, and always provide a clear path forward. By the end of this phase, the complete notification veiling and response workflow will be functional end-to-end.

## Tasks

- [x] Add visual status indicators to FloatingBubble:
  - Read existing `app/src/main/java/com/yazan/jetoverlay/ui/FloatingBubble.kt`
  - Add processing state indicator:
    - Small animated spinner when message is being processed
    - Checkmark icon when processing complete
    - Use `CircularProgressIndicator` with small size (16dp)
  - Add message count badge on collapsed bubble:
    - Show number of pending messages (status != SENT && status != DISMISSED)
    - Red badge with white text, positioned top-right of bubble
  - Add bucket color coding:
    - Tint the bubble border/accent based on bucket color from MessageBucket
    - URGENT: red, WORK: blue, SOCIAL: green, PROMOTIONAL: gray

  **Completed:** Added `ProcessingState` enum and visual indicators to `FloatingBubble.kt`:
  - Processing spinner (16dp `CircularProgressIndicator`) shown when `isProcessing` is true
  - Checkmark icon when processing is complete
  - Red badge with white text positioned top-right showing pending message count (handles 99+ overflow)
  - Bucket-colored 3dp border around bubble using `MessageBucket.color`
  - Updated `OverlayUiState.kt` with `processingState`, `pendingMessageCount`, and `currentBucket` properties
  - Created `OverlayUiStateTest.kt` with 29 unit tests covering all new functionality

- [x] Implement bucket-based message queue UI:
  - Update `app/src/main/java/com/yazan/jetoverlay/ui/AgentOverlay.kt`:
    - Show tabs or chips for each bucket with pending messages
    - Allow user to filter by bucket
    - Show message count per bucket
  - Update `app/src/main/java/com/yazan/jetoverlay/ui/OverlayUiState.kt`:
    - Add `selectedBucket: MessageBucket?` for filtering
    - Add `pendingCounts: Map<MessageBucket, Int>` for badge counts
  - Implement smooth transition when switching between buckets

  **Completed:** Implemented full bucket-based message queue UI:
  - Added `selectedBucket: MessageBucket?` and `pendingCounts: Map<MessageBucket, Int>` to `OverlayUiState.kt`
  - Added `selectBucket()`, `updatePendingCounts()`, and `bucketsWithPendingMessages` property
  - Updated `AgentOverlay.kt` to compute pending counts per bucket using `derivedStateOf`
  - Added message filtering by selected bucket with `LaunchedEffect` to update counts
  - Created `BucketFilterRow` composable with horizontally scrollable bucket chips
  - Created `BucketChip` composable with count badges, bucket-colored styling, and selection animation
  - Added smooth `AnimatedContent` transitions when switching between messages
  - Added 13 new unit tests for bucket filtering functionality (total 42 tests now)
  - All tests pass successfully

- [x] Enhance expanded card with response actions:
  - Update FloatingBubble expanded state to show:
    - Veiled content at top (tappable to reveal)
    - Horizontal scrollable row of generated response chips
    - Each chip is tappable to select that response
    - Selected response highlighted with different background
  - Add "Edit" button that opens text input for custom response
  - Add "Regenerate" button to request new AI responses (calls StubLlmService again)
  - Add "Send" button that queues the selected/edited response

  **Completed:** Enhanced `ExpandedMessageView` in `FloatingBubble.kt` with full response actions:
  - Added "Tap to reveal" hint for veiled content with tappable reveal
  - Created `ResponseChipsRow` composable with horizontal scrollable response chips
  - Created `ResponseChip` composable with selection animation (checkmark, highlighted border, primary color)
  - Created `ActionButtonsRow` composable with Edit, Regenerate, and Send buttons
  - Send button is disabled until a response is selected
  - Updated `OverlayUiState.kt` with:
    - `selectedResponseIndex`, `isEditing`, `editedResponse` state properties
    - `selectedResponse` derived property handling both chip selection and custom edits
    - `hasSelectedResponse` property for button enable/disable logic
    - Methods: `selectResponse()`, `startEditing()`, `cancelEditing()`, `updateEditedResponse()`, `useEditedResponse()`, `regenerateResponses()`, `sendSelectedResponse()`, `resetResponseSelection()`
    - Callback hooks: `onRegenerateResponses` and `onSendResponse`
  - Added 26 new unit tests for response selection, editing, and callback functionality (total 68 tests now)
  - All tests pass successfully

- [ ] Implement response editing flow:
  - Create `app/src/main/java/com/yazan/jetoverlay/ui/ResponseEditor.kt`:
    - Composable with TextField for editing response
    - Pre-populated with selected response or empty for custom
    - "Cancel" and "Use This" buttons
    - Keyboard should appear automatically
  - Integrate into FloatingBubble:
    - Tapping "Edit" shows ResponseEditor in place of response chips
    - "Use This" sets the edited text as selectedResponse

- [ ] Implement send/dismiss flow:
  - Create `app/src/main/java/com/yazan/jetoverlay/domain/ResponseSender.kt`:
    - Class that handles sending responses via the original notification's reply action
    - Uses ReplyActionCache to retrieve the PendingIntent
    - Constructs RemoteInput with the response text
    - Fires the PendingIntent with the RemoteInput
  - Update MessageRepository:
    - `queueForSending()` sets selectedResponse and status=QUEUED
    - `markAsSent()` sets status=SENT after successful send
    - `dismiss()` sets status=DISMISSED without sending
  - Add "Dismiss" button (X) to card header that dismisses without responding

- [ ] Add swipe gestures for quick actions:
  - Implement swipe-to-dismiss on expanded card:
    - Swipe left to dismiss message
    - Use `SwipeToDismissBox` or custom `detectHorizontalDragGestures`
  - Implement swipe between messages:
    - Swipe up/down to navigate to next/previous pending message
    - Show subtle animation indicating more messages available

- [ ] Ensure crash resilience and smooth UX:
  - Wrap all UI operations in try-catch to prevent crashes
  - Add `remember { derivedStateOf }` for expensive computations
  - Use `LaunchedEffect` with proper keys for side effects
  - Add graceful fallback UI if data is unavailable:
    - "No pending messages" state
    - "Processing..." state while waiting for responses
  - Test rapid tapping, rotation, and overlay toggle to ensure stability
  - Verify no ANR when processing many messages

- [ ] Write Compose UI tests for overlay components:
  - Create `app/src/androidTest/java/com/yazan/jetoverlay/ui/FloatingBubbleTest.kt`:
    - Test collapsed state renders correctly
    - Test expansion animation
    - Test veil reveal interaction
    - Test response chip selection
    - Test send button triggers callback
  - Create `app/src/androidTest/java/com/yazan/jetoverlay/ui/ResponseEditorTest.kt`:
    - Test text input works
    - Test cancel returns to previous state
    - Test "Use This" calls callback with edited text

- [ ] Run full E2E test of complete workflow:
  - Execute all tests: `./gradlew connectedAndroidTest`
  - Manual E2E test on emulator:
    1. Receive notification -> verify it's hidden from user
    2. Verify bubble appears with processing indicator
    3. Verify veiled content appears after processing
    4. Tap to expand -> verify response chips appear
    5. Select a response -> verify highlight
    6. Tap "Edit" -> verify editor appears
    7. Modify response -> tap "Use This"
    8. Tap "Send" -> verify message status changes to SENT
    9. Verify bubble shows next pending message or idle state
  - Document any issues and fixes
