---
type: report
title: Architecture Fragility Matrix - Consolidated Audit Summary
created: 2026-01-11
tags:
  - architecture
  - summary
  - fragility
related:
  - "[[overlay-lifecycle-audit]]"
  - "[[di-service-audit]]"
  - "[[data-layer-audit]]"
  - "[[brain-audit]]"
---

# Architecture Fragility Matrix

## Executive Summary

This report consolidates findings from four architecture audits conducted on the JetOverlay application. A total of **37 issues** were identified across all components, with **4 HIGH severity**, **13 MEDIUM severity**, and **20 LOW severity** issues. The most critical concerns relate to coroutine lifecycle management, potential race conditions, and testability barriers.

### Audit Coverage

| Audit Document | Focus Area | Issues Found |
|----------------|------------|--------------|
| [[overlay-lifecycle-audit]] | Overlay service, WindowManager, Android 16 compatibility | 6 issues |
| [[di-service-audit]] | Dependency injection, service architecture, testability | 6 issues |
| [[data-layer-audit]] | Room database, repository, ReplyActionCache | 13 issues |
| [[brain-audit]] | MessageProcessor, notification filter/mapper | 11 issues |

---

## Risk Matrix

### HIGH Severity (Crashes, ANRs, Data Loss)

| Component | Issue | Description | Recommendation | Audit Source |
|-----------|-------|-------------|----------------|--------------|
| MessageProcessor | No coroutine cancellation | Scope never cancelled; orphaned coroutines continue after service death | Add `stop()` method with `scope.cancel()` | [[brain-audit]] |
| MessageProcessor | Duplicate processing risk | Flow re-emission causes same message to be processed multiple times | Add in-memory tracking or immediate status transition | [[brain-audit]] |
| AppNotificationListenerService | Application initialization race | Service may start before `JetOverlayApplication.onCreate()` completes | Add defensive null check for `instance` | [[di-service-audit]] |
| DI Architecture | Cannot inject mock repository | Direct singleton access prevents unit testing | Extract interface or use constructor injection | [[di-service-audit]] |

### MEDIUM Severity (Reliability, Memory, Debuggability)

| Component | Issue | Description | Recommendation | Audit Source |
|-----------|-------|-------------|----------------|--------------|
| OverlayService | Race condition in onDestroy | Scope cancelled before overlays removed | Remove overlays first, then cancel scope | [[overlay-lifecycle-audit]] |
| OverlaySdk | Memory leak in registry | Registered content factories never removed | Add `unregisterContent()` method | [[overlay-lifecycle-audit]] |
| OverlayService | Silent exception swallowing | `addView` failures silently caught with `printStackTrace()` | Expose error callbacks to SDK | [[overlay-lifecycle-audit]] |
| MessageProcessor | Silent exception handling | SupervisorJob swallows exceptions without logging | Add CoroutineExceptionHandler | [[brain-audit]] |
| AppNotificationListenerService | Service scope not cancelled | Coroutine scope never cancelled in `onDestroy()` | Cancel scope in `onDestroy()` | [[brain-audit]] |
| AppNotificationListenerService | Multiple MessageProcessor instances | Service recreation creates duplicate processors | Cancel old processor on recreation | [[di-service-audit]] |
| ReplyActionCache | Unbounded cache growth | Entries accumulate without cleanup after replies | Call `remove()` after successful reply | [[data-layer-audit]] |
| MessageRepository | Silent failures in updates | Methods return nothing when message not found | Return Boolean/Result indicator | [[data-layer-audit]] |
| TypeConverters | Malformed JSON handling | `Gson.fromJson()` can throw on malformed input | Add try-catch wrapper | [[data-layer-audit]] |
| NotificationMapper | Missing EXTRA_BIG_TEXT | Multi-line notification content not captured | Try EXTRA_BIG_TEXT before EXTRA_TEXT | [[brain-audit]] |
| AppDatabase | No fallback strategy | Migration failure causes crash | Add fallback or explicit migrations | [[data-layer-audit]] |
| AppNotificationListenerService | Nested coroutine launch | Awkward dispatcher switching pattern | Use `withContext(Dispatchers.Main)` | [[brain-audit]] |
| ReplyActionCache | PendingIntent retention | Stale PendingIntents held indefinitely | Add TTL-based expiration | [[data-layer-audit]] |

### LOW Severity (Code Quality, Performance, Future-Proofing)

| Component | Issue | Description | Recommendation | Audit Source |
|-----------|-------|-------------|----------------|--------------|
| OverlayService | Unsynchronized params mutation | Drag gesture mutates params without synchronization | Use AtomicInteger or document main-thread constraint | [[overlay-lifecycle-audit]] |
| OverlayService | No overlay bounds checking | Overlays can be dragged off-screen | Add optional bounds constraints to OverlayConfig | [[overlay-lifecycle-audit]] |
| OverlayViewWrapper | ViewModelStore not restored | ViewModel state lost on service recreation | Document expected behavior | [[overlay-lifecycle-audit]] |
| AppDatabase | No migration strategy | Schema export disabled | Enable `exportSchema = true` | [[data-layer-audit]] |
| Message | Status field stringly-typed | No compile-time validation of status values | Consider enum with TypeConverter | [[data-layer-audit]] |
| Message | Missing packageName index | May need index for future filtering | Add index when filtering implemented | [[data-layer-audit]] |
| TypeConverters | Gson instance per conversion | Minor allocation overhead | Reuse Gson instance | [[data-layer-audit]] |
| MessageDao | No pagination support | Loads all messages into memory | Add paged query for scale | [[data-layer-audit]] |
| MessageRepository | No transaction wrapper | Read-modify-write not atomic | Consider @Transaction annotation | [[data-layer-audit]] |
| MessageRepository | ifEmpty semantics | Logic could be clearer | Replace with explicit if/else | [[data-layer-audit]] |
| ReplyActionCache | No LRU eviction | Cache grows unbounded | Add size limit with LRU | [[data-layer-audit]] |
| NotificationFilter | Overly permissive filter | Accepts all non-ongoing notifications | Intentional for debug; add package filtering for production | [[brain-audit]] |
| NotificationFilter | Verbose debug logging | Debug logs in production code | Guard with `BuildConfig.DEBUG` | [[brain-audit]] |
| NotificationMapper | No MessagingStyle support | Missing Android 11+ structured message data | Future enhancement | [[brain-audit]] |
| NotificationMapper | Null safety in extras | `sbn.notification.extras` not null-checked | Add safe navigation | [[brain-audit]] |
| OverlaySdk | No reset method for testing | Internal state persists across tests | Add `@VisibleForTesting reset()` | [[di-service-audit]] |
| ReplyActionCache | Object singleton testability | Global state affects test isolation | Extract interface or add reset method | [[di-service-audit]] |
| OverlaySdk | Object singleton testability | Cannot inject test doubles | Add reset method or interface | [[di-service-audit]] |
| AppDatabase | Room database lazy init | First query has I/O latency | Consider warm-up query if critical | [[di-service-audit]] |
| MessageProcessor | Hard-coded responses | Placeholder for future LLM integration | Design for pluggable strategy | [[brain-audit]] |

---

## Component Risk Summary

| Component | HIGH | MEDIUM | LOW | Total | Risk Level |
|-----------|------|--------|-----|-------|------------|
| MessageProcessor | 2 | 1 | 1 | 4 | **CRITICAL** |
| AppNotificationListenerService | 1 | 3 | 0 | 4 | **HIGH** |
| ReplyActionCache | 0 | 2 | 2 | 4 | MEDIUM |
| OverlayService | 0 | 2 | 2 | 4 | MEDIUM |
| OverlaySdk | 0 | 1 | 2 | 3 | MEDIUM |
| MessageRepository | 0 | 1 | 2 | 3 | MEDIUM |
| AppDatabase | 0 | 1 | 1 | 2 | LOW |
| TypeConverters | 0 | 1 | 1 | 2 | LOW |
| NotificationMapper | 0 | 1 | 2 | 3 | LOW |
| NotificationFilter | 0 | 0 | 2 | 2 | LOW |
| Message (entity) | 0 | 0 | 2 | 2 | LOW |
| MessageDao | 0 | 0 | 1 | 1 | LOW |
| OverlayViewWrapper | 0 | 0 | 1 | 1 | LOW |
| DI Architecture | 1 | 0 | 0 | 1 | **HIGH** |

---

## Issues by Type

### Memory/Lifecycle Issues (9)
- MessageProcessor coroutine not cancelled (HIGH)
- Service scope not cancelled (MEDIUM)
- Multiple MessageProcessor instances (MEDIUM)
- ReplyActionCache unbounded growth (MEDIUM)
- OverlaySdk registry leak (MEDIUM)
- PendingIntent retention (MEDIUM)
- ViewModelStore not restored (LOW)
- No LRU eviction (LOW)
- Gson instance creation (LOW)

### Race Conditions (4)
- Duplicate message processing (HIGH)
- Application initialization race (HIGH)
- onDestroy scope/overlay ordering (MEDIUM)
- Read-modify-write not atomic (LOW)

### Error Handling (4)
- Silent exception in MessageProcessor (MEDIUM)
- Silent exception in OverlayService (MEDIUM)
- Silent failures in repository (MEDIUM)
- Malformed JSON not caught (MEDIUM)

### Testability (4)
- Cannot inject mock repository (HIGH)
- OverlaySdk singleton (LOW)
- ReplyActionCache singleton (LOW)
- Missing reset methods (LOW)

### Functionality Gaps (5)
- Missing EXTRA_BIG_TEXT (MEDIUM)
- No migration strategy (MEDIUM)
- No MessagingStyle support (LOW)
- No overlay bounds (LOW)
- No pagination (LOW)

### Code Quality (5)
- Nested coroutine launch (MEDIUM)
- Verbose debug logging (LOW)
- ifEmpty semantics (LOW)
- Unsynchronized params (LOW)
- Status stringly-typed (LOW)

---

## Quick Wins

These fixes are low-effort but high-impact improvements that can be made immediately:

### 1. Add stop() to MessageProcessor
**Impact**: Prevents memory leak and orphaned coroutines
**Effort**: ~5 minutes
```kotlin
fun stop() {
    scope.cancel()
}
```

### 2. Cancel scopes in onDestroy
**Impact**: Proper resource cleanup
**Effort**: ~5 minutes
```kotlin
// AppNotificationListenerService.kt
override fun onDestroy() {
    scope.cancel()
    processor.stop()
    ReplyActionCache.clear()
    super.onDestroy()
}
```

### 3. Fix onDestroy order in OverlayService
**Impact**: Prevents race condition
**Effort**: ~2 minutes
```kotlin
override fun onDestroy() {
    super.onDestroy()
    activeViews.keys.toList().forEach { removeOverlay(it) }
    serviceScope.cancel()  // Cancel AFTER cleanup
}
```

### 4. Add try-catch to TypeConverter
**Impact**: Prevents crashes on malformed JSON
**Effort**: ~5 minutes
```kotlin
@TypeConverter
fun toStringList(value: String): List<String> {
    return try {
        gson.fromJson(value, listType) ?: emptyList()
    } catch (e: JsonSyntaxException) {
        emptyList()
    }
}
```

### 5. Remove ReplyActionCache entry after reply
**Impact**: Prevents memory accumulation
**Effort**: ~2 minutes
```kotlin
// After sending reply
ReplyActionCache.remove(messageId)
```

### 6. Add EXTRA_BIG_TEXT support
**Impact**: Captures full message content
**Effort**: ~5 minutes
```kotlin
val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
    ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    ?: return null
```

### 7. Add unregisterContent to OverlaySdk
**Impact**: Prevents registry leak
**Effort**: ~2 minutes
```kotlin
fun unregisterContent(type: String) {
    registry.remove(type)
}
```

### 8. Guard debug logs
**Impact**: Performance in production
**Effort**: ~5 minutes
```kotlin
if (BuildConfig.DEBUG) {
    Log.d("JetOverlayDebug", "...")
}
```

---

## Recommended Fix Order

### Phase 1: Critical Fixes (Before any new feature work)

1. Add `stop()` to MessageProcessor and call it in service `onDestroy()`
2. Cancel `scope` in `AppNotificationListenerService.onDestroy()`
3. Fix `onDestroy()` ordering in `OverlayService`
4. Add duplicate processing prevention to `MessageProcessor`

### Phase 2: Reliability Improvements

5. Add `CoroutineExceptionHandler` to MessageProcessor
6. Add try-catch to TypeConverter
7. Add `unregisterContent()` to OverlaySdk
8. Add `ReplyActionCache.remove()` after successful replies

### Phase 3: Testability Preparation

9. Add `@VisibleForTesting reset()` methods to singletons
10. Consider interface extraction for Repository
11. Add defensive Application initialization check

### Phase 4: Feature Completeness

12. Add EXTRA_BIG_TEXT support in NotificationMapper
13. Add error callbacks to OverlaySdk
14. Enable schema export for AppDatabase

### Phase 5: Future Hardening

15. Add pagination to MessageDao
16. Add TTL to ReplyActionCache
17. Consider Hilt migration for full testability

---

## Android 16 Compatibility Status

**Overall Status**: COMPLIANT

| Requirement | Status | Notes |
|-------------|--------|-------|
| FOREGROUND_SERVICE_TYPE_SPECIAL_USE | PASS | Correctly declared for Android 14+ |
| specialUse subtype property | PASS | "overlay" subtype properly configured |
| Required permissions | PASS | All permissions declared in manifest |
| Background activity restrictions | N/A | Overlay doesn't launch activities |
| Predictive back gesture | UNTESTED | May need verification |
| Large screen support | UNTESTED | Consider foldable/desktop modes |

---

## Conclusion

The JetOverlay application has a solid architectural foundation but several critical issues must be addressed before production readiness:

1. **Coroutine Lifecycle Management** - The most pressing concern. Multiple components create coroutine scopes that are never cancelled, leading to memory leaks and orphaned work.

2. **Race Conditions** - The duplicate processing risk in MessageProcessor and the Application initialization race could cause data inconsistencies or crashes.

3. **Testability** - The current manual singleton approach makes unit testing nearly impossible. Quick fixes (reset methods) can help, but long-term Hilt adoption would be beneficial.

4. **Error Handling** - Silent failures throughout the codebase make debugging difficult. Adding proper error propagation and logging will improve reliability.

The good news is that all identified issues are fixable without major architectural changes. The Quick Wins section provides 8 high-impact fixes that can be implemented in under an hour combined.
