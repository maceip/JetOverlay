---
type: analysis
title: Overlay Lifecycle and Android 16 Compatibility Audit
created: 2026-01-11
tags:
  - architecture
  - android-16
  - overlay
  - lifecycle
related:
  - "[[di-service-audit]]"
  - "[[fragility-matrix]]"
---

# Overlay Lifecycle and Android 16 Compatibility Audit

## Executive Summary

This audit analyzes the JetOverlay SDK's overlay lifecycle management, focusing on `OverlayService.kt` and `OverlayViewWrapper.kt`. The implementation is generally sound with proper handling for Android 14+ foreground service requirements, but several fragility points and potential improvements have been identified.

## Files Reviewed

- `jetoverlay/src/main/java/com/yazan/jetoverlay/service/OverlayService.kt`
- `jetoverlay/src/main/java/com/yazan/jetoverlay/internal/OverlayViewWrapper.kt`
- `jetoverlay/src/main/java/com/yazan/jetoverlay/api/OverlaySdk.kt`
- `jetoverlay/src/main/java/com/yazan/jetoverlay/api/OverlayConfig.kt`
- `jetoverlay/src/main/AndroidManifest.xml`

## Architecture Overview

### Service Architecture

The overlay system uses a **reactive architecture** with the following flow:

1. `OverlaySdk.show()` updates a `StateFlow<Map<String, ActiveOverlay>>`
2. `OverlayService` observes this flow and synchronizes views accordingly
3. Each overlay is wrapped in `OverlayViewWrapper` which provides its own lifecycle

### Key Components

| Component | Responsibility |
|-----------|----------------|
| `OverlaySdk` | Public API, state management via StateFlow |
| `OverlayService` | Foreground service, WindowManager interaction |
| `OverlayViewWrapper` | Lifecycle owner for Compose, ViewModelStore provider |

---

## Android 16 Compatibility Analysis

### Target SDK Configuration

- **compileSdk**: 36 (Android 16)
- **targetSdk**: 36 (Android 16)
- **minSdk**: 26 (Android 8.0)

### FOREGROUND_SERVICE_TYPE_SPECIAL_USE Handling

**Status: COMPLIANT**

The implementation correctly handles Android 14+ (API 34, UPSIDE_DOWN_CAKE) requirements:

```kotlin
// OverlayService.kt:156-165
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
} else {
    startForeground(101, notification)
}
```

The manifest correctly declares:

```xml
<service
    android:name=".service.OverlayService"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.content.pm.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="overlay" />
</service>
```

Required permissions are declared:
- `SYSTEM_ALERT_WINDOW`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- `POST_NOTIFICATIONS`

### Android 16 Specific Concerns

1. **Background Activity Launch Restrictions**: Not directly applicable as overlays don't launch activities.

2. **Foreground Service Restrictions**: The `specialUse` type with "overlay" subtype is appropriate and should continue to work in Android 16.

3. **Exact Alarm Restrictions**: Not applicable.

---

## Lifecycle Callback Analysis

### OverlayViewWrapper Lifecycle Events

The `OverlayViewWrapper` implements a proper lifecycle owner pattern:

| Event | When Triggered | Implementation |
|-------|----------------|----------------|
| `ON_CREATE` | In `init{}` block | `lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)` |
| `ON_START` | `onAttachToWindowCustom()` | Sequential with ON_RESUME |
| `ON_RESUME` | `onAttachToWindowCustom()` | After ON_START |
| `ON_PAUSE` | `onDetachFromWindowCustom()` | First in detach sequence |
| `ON_STOP` | `onDetachFromWindowCustom()` | After ON_PAUSE |
| `ON_DESTROY` | `onDetachFromWindowCustom()` | Last, clears ViewModelStore |

**Finding**: The lifecycle event ordering is correct according to Android guidelines.

### Service Lifecycle

| Method | Implementation | Notes |
|--------|----------------|-------|
| `onCreate()` | Starts foreground, observes overlays | Correct |
| `onStartCommand()` | Returns `START_STICKY` | Appropriate for overlays |
| `onDestroy()` | Cancels scope, removes all overlays | **See Issue #1** |

---

## WindowManager Lifecycle Handling

### Attach/Detach Flow

**Attach** (`addOverlay`):
1. Create `OverlayViewWrapper`
2. Configure `LayoutParams` with `TYPE_APPLICATION_OVERLAY`
3. Add view to WindowManager
4. Call `onAttachToWindowCustom()` to start lifecycle

**Detach** (`removeOverlay`):
1. Call `onDetachFromWindowCustom()` to complete lifecycle
2. Remove view from WindowManager
3. Stop service if no active overlays

### Configuration Change Handling

**Status: PARTIAL CONCERN**

The current implementation does **not** explicitly handle configuration changes. However:

- `TYPE_APPLICATION_OVERLAY` windows are managed independently of Activity configuration changes
- The `START_STICKY` return ensures service restart
- Compose handles internal recomposition

**Potential Issue**: If the system kills and restarts the service, the `StateFlow` in `OverlaySdk` may retain stale state while views are gone.

---

## Drag Gesture Handling Analysis

### Current Implementation

```kotlin
// OverlayService.kt:85-93
val dragModifier = Modifier.pointerInput(Unit) {
    detectDragGestures { change, dragAmount ->
        change.consume()
        params.x += dragAmount.x.toInt()
        params.y += dragAmount.y.toInt()
        if (viewWrapper.isAttachedToWindow) {
            windowManager.updateViewLayout(viewWrapper, params)
        }
    }
}
```

### ANR Risk Assessment

**Risk Level: LOW to MEDIUM**

1. **Positive**:
   - `updateViewLayout` is a relatively fast operation
   - The `isAttachedToWindow` check prevents crashes on detached views

2. **Concerns**:
   - High-frequency drag events call `updateViewLayout` on every frame
   - No throttling or debouncing mechanism
   - `params` object is mutated directly without synchronization

### Potential ANR Scenario

Rapid dragging combined with a slow/busy main thread could queue up many `updateViewLayout` calls. While unlikely to cause ANR directly, it could contribute to frame drops and jank.

---

## Fragility Points Identified

### Issue #1: Race Condition in onDestroy

**Severity: MEDIUM**

```kotlin
// OverlayService.kt:128-134
override fun onDestroy() {
    super.onDestroy()
    serviceScope.cancel()  // Cancels the flow observer
    activeViews.keys.toList().forEach { id ->
        removeOverlay(id)  // May try to update cancelled scope
    }
}
```

**Problem**: The service scope is cancelled before removing overlays. If `removeOverlay` triggers any coroutine operations, they will fail silently.

**Recommendation**: Remove overlays first, then cancel the scope.

### Issue #2: Exception Swallowing

**Severity: LOW to MEDIUM**

```kotlin
// OverlayService.kt:103-109
try {
    windowManager.addView(viewWrapper, params)
    viewWrapper.onAttachToWindowCustom()
    activeViews[id] = viewWrapper
} catch (e: Exception) {
    e.printStackTrace()  // Silent failure
}
```

**Problem**: If `addView` fails (e.g., permission revoked), the overlay silently fails without notifying the caller.

**Recommendation**: Expose error callbacks or state to `OverlaySdk`.

### Issue #3: Potential Memory Leak in OverlaySdk

**Severity: MEDIUM**

```kotlin
// OverlaySdk.kt:14
private val registry = mutableMapOf<String, @Composable (Any?) -> Unit>()
```

**Problem**: Registered content factories are never removed. If a module registers content and then is unloaded, the lambda (potentially capturing module context) remains.

**Recommendation**: Add `unregisterContent(type: String)` method.

### Issue #4: Unsynchronized params Mutation

**Severity: LOW**

```kotlin
// OverlayService.kt:88-89
params.x += dragAmount.x.toInt()
params.y += dragAmount.y.toInt()
```

**Problem**: `params` is captured in the lambda and mutated. While all operations should be on the main thread, this could cause issues if threading assumptions change.

**Recommendation**: Consider using `AtomicInteger` or ensuring main-thread-only access is enforced.

### Issue #5: No Bounds Checking for Overlay Position

**Severity: LOW**

**Problem**: Overlays can be dragged off-screen with no way to recover them.

**Recommendation**: Add optional bounds constraints to `OverlayConfig` or provide reset functionality.

### Issue #6: ViewModelStore Not Restored on Recreation

**Severity: LOW**

```kotlin
// OverlayViewWrapper.kt:28
private val store = ViewModelStore()
```

**Problem**: Each overlay gets a fresh `ViewModelStore`. If the service is recreated, all ViewModel state is lost.

**Recommendation**: This is acceptable for overlay use cases but should be documented.

---

## Android 16 Specific Recommendations

1. **Monitor Foreground Service Changes**: Android 16 may introduce additional restrictions on foreground services. Keep the `specialUse` declaration current and watch for deprecation notices.

2. **Predictive Back Gesture**: Ensure overlays don't interfere with the predictive back gesture system in Android 16.

3. **Large Screen Support**: Consider how overlays behave in multi-window, foldable, and desktop modes as these become more prominent in Android 16.

---

## Summary of Findings

| Issue | Severity | Component | Type |
|-------|----------|-----------|------|
| Race condition in onDestroy | MEDIUM (addressed: cleanup before cancel) | OverlayService | Bug |
| Silent exception swallowing | LOW-MEDIUM (partially mitigated: addView rollback) | OverlayService | Reliability |
| Memory leak in registry | MEDIUM (mitigated: unregisterContent added) | OverlaySdk | Memory |
| Unsynchronized params mutation | LOW | OverlayService | Thread Safety |
| No overlay bounds checking | LOW | OverlayService | UX |
| ViewModelStore not restored | LOW | OverlayViewWrapper | Expected Behavior |

### 2026-01-13 updates
- OverlayService: reordered `onDestroy()` to remove overlays before cancelling scope; adds IME-focus flag toggling when overlays become focusable for the editor flow.
- OverlayService: addView failures now log and roll back active overlay state to prevent stuck FGS with no windows.
- OverlaySdk: content factory now tolerates missing registrations (no crash, logs warning); startForegroundService wrapped with rollback on failure; registry gains `unregisterContent`/`isContentRegistered`.

---

## Recommended Fixes

### Priority 1 (Should Fix)

1. **Fix onDestroy race condition**:
   ```kotlin
   override fun onDestroy() {
       super.onDestroy()
       activeViews.keys.toList().forEach { id ->
           removeOverlay(id)
       }
       serviceScope.cancel()  // Cancel AFTER cleanup
   }
   ```

2. **Add unregisterContent to OverlaySdk**:
   ```kotlin
   fun unregisterContent(type: String) {
       registry.remove(type)
   }
   ```

### Priority 2 (Nice to Have)

3. **Add error callback mechanism** to `OverlaySdk.show()` for permission/window errors

4. **Consider throttling** `updateViewLayout` calls during drag gestures

### Priority 3 (Future Enhancement)

5. **Add bounds constraints** option to `OverlayConfig`

6. **Document ViewModelStore behavior** for overlay consumers

---

## Conclusion

The JetOverlay SDK has a solid foundation for Android 16 compatibility. The foreground service type declaration is correct, and the lifecycle management is generally well-implemented. The identified issues are mostly edge cases and reliability improvements rather than critical bugs. The highest priority fix is the race condition in `onDestroy()`.
