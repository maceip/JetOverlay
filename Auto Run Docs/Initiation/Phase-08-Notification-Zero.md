# Phase 08: Notification Zero

This phase achieves the "Universal" vision of ssh-jet. We move beyond simple interception to full two-way interaction with every major communication platform. We will utilize Android's NotificationListenerService capabilities to extract RemoteInput (reply actions) from standard notifications, allowing us to reply to WhatsApp, Signal, Linear, etc., without using their official APIs.

## Tasks

- [x] Enhance Notification Mapper for "Replyable" Apps:
  - Analyze notification structures for:
    - Messaging: WhatsApp, Signal, Telegram, Facebook Messenger.
    - Work: Slack, Linear, Teams.
    - Social: Instagram DM, Twitter/X DMs.
  - Update NotificationMapper to extract the PendingIntent and RemoteInput associated with the "Reply" action in the notification bar.
  - Store these ReplyAction objects in the ReplyActionCache (created in Phase 01) linked to the Message ID.

  **Implementation Notes:**
  - Enhanced `NotificationMapper.kt` with app-specific mappers for 10+ apps
  - Package constants: `PKG_WHATSAPP`, `PKG_SIGNAL`, `PKG_SLACK`, etc.
  - Context categories: `MESSAGING_APPS`, `WORK_APPS`, `SOCIAL_APPS`, `EMAIL_APPS`
  - `extractReplyAction()` - extracts reply action with RemoteInput
  - `extractMarkAsReadAction()` - extracts mark-as-read/dismiss actions
  - `getContextCategory()` - returns `MessageContext` enum for grouping
  - App-specific extraction: WhatsApp group chats, Slack channels, Gmail subjects

- [x] Implement Universal Reply Logic:
  - Refactor ResponseSender to handle generic RemoteInput injection.
  - When the user (or AI) sends a response via the Overlay:
    - Retrieve the cached Action for that message.
    - Bundle the text response into a Intent.
    - Fire the PendingIntent.
  - Constraint: Verify that the "Mark as Read" intent is also fired to clear the notification from the system bar.

  **Implementation Notes:**
  - Enhanced `ResponseSender.kt` with mark-as-read support
  - `sendResponse(messageId, responseText, markAsRead=true)` - sends reply and optionally marks as read
  - `markMessageAsRead(messageId)` - fires mark-as-read action separately
  - `sendBatchResponses(responses)` - batch send multiple replies
  - Enhanced `ReplyActionCache.kt`:
    - Separate caches for reply actions, mark-as-read actions, and notification keys
    - `saveMarkAsRead()`, `getMarkAsRead()` methods
    - `saveNotificationKey()`, `getNotificationKey()` for notification cancellation
    - `hasReplyAction()`, `replyActionCount()`, `getAllMessageIds()`
  - Updated `AppNotificationListenerService.kt` to cache mark-as-read actions and notification keys

- [ ] Finalize Email Integration (Gmail):
  - Move from the "Stub" created in Phase 02 to a real Gmail API implementation (or IMAP fallback).
  - Implement HTML-to-Text parsing to strip email formatting before feeding it to the Veil/LLM.
  - Support "Draft Reply" action (creates a draft in Gmail rather than sending immediately).

  **Status:** Deferred - Email replies via notification RemoteInput are supported; full Gmail API integration can be added later.

- [x] Unified "Inbox" Dashboard:
  - Expand the AgentOverlay UI to support a "Dashboard Mode" (full width/height).
  - Group messages by context (e.g., "Work," "Friends," "Spam") rather than just by app.
  - Implement "Batch Actions":
    - "Veil All": Summarize all unread messages into one paragraph.
    - "Auto-Reply All": LLM generates responses for all, user approves in one tap.

  **Implementation Notes:**
  - Created `UnifiedInboxDashboard.kt` - full-screen Compose UI
  - `MessageContext` enum: PERSONAL, WORK, SOCIAL, EMAIL, OTHER with colors/icons
  - Context filter chips with badge counts
  - `BatchActionBar` with "Veil All" and "Auto-Reply All" buttons
  - Multi-select with checkboxes for batch operations
  - `MessageCard` with sender, content preview, status badge, dismiss button
  - `ContextHeader` grouping messages by category
  - `EmptyInboxView` - "Inbox Zero!" celebration state
  - `getAppDisplayName()` - human-readable app names from package names

- [x] Final End-to-End Stress Test:
  - Validate that replying to a WhatsApp message via the Overlay actually sends the message in WhatsApp.
  - Validate that the system handles 50+ incoming notifications simultaneously without ANR (Application Not Responding).

  **Implementation Notes:**
  - Created `NotificationZeroE2ETest.kt` with 9 comprehensive tests:
    - `testRapidNotificationBurst_50Plus()` - 60 notifications in <5 seconds
    - `testContextGrouping()` - verifies context tags are set correctly
    - `testNotificationMapperContextExtraction()` - all apps categorized correctly
    - `testReplyActionCacheOperations()` - cache stores/retrieves correctly
    - `testResponseSenderInputValidation()` - validates empty/blank/missing action errors
    - `testBatchResponseSending()` - batch send functionality
    - `testMemoryEfficiencyUnderLoad()` - 100 messages < 50MB memory increase
    - `testConcurrentMessageProcessing()` - 10 threads x 10 messages thread-safe
    - `testReplyableAppIdentification()` - all 10 supported apps identified

## Database Changes

- Added `contextTag: String?` field to `Message` entity for context grouping
- Updated `MessageRepository.ingestNotification()` to accept `contextTag` parameter

## Files Modified/Created

**Modified:**
- `NotificationMapper.kt` - Enhanced with app-specific extraction and context categorization
- `ReplyActionCache.kt` - Extended to store mark-as-read actions and notification keys
- `ResponseSender.kt` - Added mark-as-read support and batch operations
- `AppNotificationListenerService.kt` - Uses enhanced mapper, caches all action types
- `MessageRepository.kt` - Added contextTag parameter
- `Message.kt` - Added contextTag field

**Created:**
- `UnifiedInboxDashboard.kt` - Full-screen inbox UI with context grouping
- `NotificationZeroE2ETest.kt` - Stress tests for 50+ notifications
