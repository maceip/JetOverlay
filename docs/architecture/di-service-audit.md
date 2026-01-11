---
type: analysis
title: Dependency Injection and Service Architecture Audit
created: 2026-01-11
tags:
  - architecture
  - dependency-injection
  - services
related:
  - "[[overlay-lifecycle-audit]]"
  - "[[data-layer-audit]]"
  - "[[fragility-matrix]]"
---

# Dependency Injection and Service Architecture Audit

## Executive Summary

This audit analyzes the JetOverlay application's dependency injection approach and service architecture. The application uses a **manual singleton pattern** rather than a DI framework like Hilt/Dagger. While this approach is simpler and has fewer dependencies, it introduces several testability concerns and potential race conditions. Key findings include tight coupling between components, service initialization ordering issues, and limited mock injection capability for testing.

## Files Reviewed

- `app/src/main/java/com/yazan/jetoverlay/JetOverlayApplication.kt`
- `app/src/main/java/com/yazan/jetoverlay/service/AppNotificationListenerService.kt`
- `app/src/main/java/com/yazan/jetoverlay/domain/MessageProcessor.kt`
- `app/src/main/java/com/yazan/jetoverlay/data/AppDatabase.kt`
- `app/src/main/java/com/yazan/jetoverlay/data/MessageRepository.kt`
- `app/src/main/java/com/yazan/jetoverlay/data/ReplyActionCache.kt`
- `jetoverlay/src/main/java/com/yazan/jetoverlay/api/OverlaySdk.kt`
- `jetoverlay/src/main/java/com/yazan/jetoverlay/service/OverlayService.kt`

---

## Current Dependency Injection Approach

### Pattern: Manual Singleton with Application-Level Initialization

The application uses a **Service Locator** pattern via the `Application` class:

```kotlin
// JetOverlayApplication.kt
class JetOverlayApplication : Application() {
    companion object {
        lateinit var instance: JetOverlayApplication
            private set
    }

    lateinit var repository: MessageRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val db = AppDatabase.getDatabase(applicationContext)
        repository = MessageRepository(db.messageDao())
        // ...
    }
}
```

### Dependency Graph

```
JetOverlayApplication (Singleton)
├── AppDatabase (Singleton via synchronized companion)
│   └── MessageDao (Interface, Room-generated)
├── MessageRepository (Instance held by Application)
└── OverlaySdk (Object singleton)
    └── notificationConfig
    └── registry (content factories)

AppNotificationListenerService (System-managed)
├── JetOverlayApplication.instance.repository (Access via Service Locator)
├── MessageProcessor (New instance per service)
│   └── MessageRepository (Shared reference)
├── MessageNotificationFilter (New instance per service)
├── NotificationMapper (New instance per service)
└── ReplyActionCache (Object singleton)

OverlayService (System-managed)
├── OverlaySdk (Object singleton)
└── WindowManager (System service)
```

---

## Analysis of DI Approach

### Advantages of Current Approach

| Aspect | Benefit |
|--------|---------|
| Simplicity | No annotation processing, faster build times |
| No Dependencies | No Hilt/Dagger dependency required |
| Explicit Control | Clear initialization order in `onCreate()` |
| Smaller APK | No generated DI code |

### Disadvantages of Current Approach

| Aspect | Issue |
|--------|-------|
| Testability | Cannot inject mocks without modifying production code |
| Coupling | Services directly access `JetOverlayApplication.instance` |
| Initialization Order | Manual ordering prone to errors |
| Lifecycle Awareness | No automatic scoping of dependencies |

---

## Testability Concerns

### Issue #1: Cannot Inject Mock Repository

**Severity: HIGH (for testing)**

```kotlin
// AppNotificationListenerService.kt:27
repository = JetOverlayApplication.instance.repository
```

**Problem**: The service directly accesses the Application singleton. In tests:
- Cannot provide a fake/mock `MessageRepository`
- Cannot isolate the service behavior
- Requires Robolectric or full instrumentation tests

**Recommendation for Testability**:

Option A - Constructor Injection (Preferred for new projects):
```kotlin
// Would require Hilt or manual factory
class AppNotificationListenerService @Inject constructor(
    private val repository: MessageRepository
) : NotificationListenerService()
```

Option B - Interface Abstraction (Less invasive):
```kotlin
interface RepositoryProvider {
    val repository: MessageRepository
}

// In tests, replace the provider
object TestRepositoryProvider : RepositoryProvider {
    override val repository = FakeMessageRepository()
}
```

Option C - Property Injection (Minimal change):
```kotlin
// In service
var repositoryProvider: () -> MessageRepository = {
    JetOverlayApplication.instance.repository
}
private val repository by lazy { repositoryProvider() }

// In tests
service.repositoryProvider = { mockRepository }
```

### Issue #2: MessageProcessor Cannot Be Mocked

**Severity: MEDIUM**

```kotlin
// AppNotificationListenerService.kt:30-31
processor = MessageProcessor(repository)
processor.start()
```

**Problem**: `MessageProcessor` is instantiated directly with `new`. Cannot:
- Verify processor interactions
- Control processor behavior in tests
- Prevent real coroutine execution in unit tests

**Recommendation**: Extract to factory pattern or inject via interface.

### Issue #3: ReplyActionCache is Object Singleton

**Severity: MEDIUM**

```kotlin
// ReplyActionCache.kt
object ReplyActionCache {
    private val cache = ConcurrentHashMap<Long, Notification.Action>()
    // ...
}
```

**Problem**:
- Cannot reset state between tests without calling `clear()`
- Shared global state across tests
- Test isolation requires careful cleanup

**Recommendation**: Consider interface extraction for testability:
```kotlin
interface ActionCache {
    fun save(messageId: Long, action: Notification.Action)
    fun get(messageId: Long): Notification.Action?
    fun remove(messageId: Long)
    fun clear()
}

// Production implementation
object ReplyActionCache : ActionCache { ... }

// Test implementation
class FakeActionCache : ActionCache { ... }
```

### Issue #4: OverlaySdk is Object Singleton

**Severity: MEDIUM**

```kotlin
// OverlaySdk.kt
object OverlaySdk {
    private val registry = mutableMapOf<String, @Composable (Any?) -> Unit>()
    private val _activeOverlays = MutableStateFlow<Map<String, ActiveOverlay>>(emptyMap())
    // ...
}
```

**Problem**:
- Internal state accumulates across tests
- No way to inject test doubles
- Registry persists between test runs

**Recommendation**: Add reset method for testing:
```kotlin
@VisibleForTesting
internal fun reset() {
    registry.clear()
    _activeOverlays.value = emptyMap()
    notificationConfig = OverlayNotificationConfig()
}
```

---

## Service Initialization Order Analysis

### Current Initialization Sequence

```
1. JetOverlayApplication.onCreate()
   ├── instance = this
   ├── AppDatabase.getDatabase()
   ├── MessageRepository created
   ├── OverlaySdk.initialize()
   └── OverlaySdk.registerContent("overlay_1")

2. [User Action: App Launch or Notification]

3. AppNotificationListenerService.onCreate() [System binds service]
   ├── repository = JetOverlayApplication.instance.repository
   ├── MessageProcessor created
   └── processor.start()

4. [Notification Posted]

5. OverlayService.onCreate() [Started via startForegroundService]
   ├── startForegroundNotification()
   └── observeOverlays()
```

### Race Condition #1: Application Not Yet Initialized

**Severity: HIGH**

**Scenario**: If Android's system binds `AppNotificationListenerService` before `JetOverlayApplication.onCreate()` completes:

```kotlin
// AppNotificationListenerService.kt:27
repository = JetOverlayApplication.instance.repository
// ↑ Will crash if instance is not yet initialized
```

**Evidence**: `lateinit var instance` will throw `UninitializedPropertyAccessException`

**Likelihood**: LOW in practice (Application.onCreate runs before any component), but:
- Process death and restart scenarios could be risky
- Split APK delivery on some OEMs has shown issues

**Recommendation**:
```kotlin
// Defensive check
override fun onCreate() {
    super.onCreate()
    if (!::instance.isInitialized) {
        Log.e(TAG, "Application not initialized, stopping service")
        stopSelf()
        return
    }
    repository = JetOverlayApplication.instance.repository
    // ...
}
```

### Race Condition #2: Repository Access Before Database Ready

**Severity: LOW**

```kotlin
// AppDatabase.kt:18-25
fun getDatabase(context: Context): AppDatabase {
    return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(...).build()
        INSTANCE = instance
        instance
    }
}
```

**Analysis**: Room's `databaseBuilder().build()` does not block on database creation. The actual database file is created lazily on first query. This is safe but means:
- First `ingestNotification()` call may have I/O latency
- No explicit "database ready" signal

**Recommendation**: Consider adding a "warm-up" query in Application.onCreate() if fast first-notification response is critical.

### Race Condition #3: Multiple MessageProcessor Instances

**Severity: MEDIUM**

**Scenario**: If `AppNotificationListenerService` is destroyed and recreated:

```kotlin
// Each onCreate creates a new processor
processor = MessageProcessor(repository)
processor.start()
```

**Problem**:
- Previous processor's coroutine scope may still be running (SupervisorJob keeps children alive)
- Multiple processors may process the same "RECEIVED" messages
- No cancellation of old processor

**Current State**: No explicit cleanup in `onDestroy()` for the processor

```kotlin
// AppNotificationListenerService.kt:95-98
override fun onDestroy() {
    super.onDestroy()
    ReplyActionCache.clear()  // Only clears cache, not processor
}
```

**Recommendation**:
```kotlin
override fun onDestroy() {
    super.onDestroy()
    processor.stop()  // Add stop() method to cancel scope
    ReplyActionCache.clear()
}
```

---

## Memory Leak Analysis

### Potential Leak #1: MessageProcessor CoroutineScope

**Severity: MEDIUM**

```kotlin
// MessageProcessor.kt:19
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
```

**Problem**:
- `scope` is never cancelled
- If service is destroyed but processor reference is held, scope leaks
- Flow collection continues indefinitely

**Recommendation**: Add lifecycle management:
```kotlin
class MessageProcessor(private val repository: MessageRepository) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() { ... }

    fun stop() {
        scope.cancel()
    }
}
```

### Potential Leak #2: ReplyActionCache Holding Notification.Action

**Severity: LOW to MEDIUM**

```kotlin
// ReplyActionCache.kt
private val cache = ConcurrentHashMap<Long, Notification.Action>()
```

**Problem**:
- `Notification.Action` contains a `PendingIntent`
- `PendingIntent` may reference the source app's context
- Cache is only cleared on service destroy, not per-message

**Observation**: Current code clears cache on service destroy, but:
- Long-running service accumulates entries
- Entries for processed/sent messages are never removed

**Current Mitigation**: None - entries persist until service death

**Recommendation**:
```kotlin
// After sending a reply
ReplyActionCache.remove(messageId)

// Or add TTL-based cleanup
```

### Potential Leak #3: OverlaySdk Registry

**Severity: MEDIUM** (Already documented in [[overlay-lifecycle-audit]])

The registry never removes entries. Repeated calls to `registerContent()` with closures capturing context could leak.

---

## Testability Improvement Recommendations

### Short-Term (No Hilt Required)

1. **Add `@VisibleForTesting` reset methods** to all singletons
2. **Extract interfaces** for `MessageRepository`, `ActionCache`
3. **Add constructor parameters** with default values pointing to singletons
4. **Add `stop()` method** to `MessageProcessor`

### Medium-Term (Optional Hilt Integration)

```kotlin
// If migrating to Hilt:
@HiltAndroidApp
class JetOverlayApplication : Application()

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides @Singleton
    fun provideMessageRepository(db: AppDatabase): MessageRepository =
        MessageRepository(db.messageDao())
}

@AndroidEntryPoint
class AppNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var repository: MessageRepository
    @Inject lateinit var processor: MessageProcessor
    // ...
}
```

### Testing Configuration

For instrumentated tests without Hilt, add to `build.gradle.kts`:

```kotlin
dependencies {
    androidTestImplementation("io.mockk:mockk-android:1.13.8")
    androidTestImplementation("app.cash.turbine:turbine:1.0.0")
}
```

---

## Service Architecture Diagrams

### Component Coupling

```
                    ┌─────────────────────┐
                    │ JetOverlayApplication│
                    │     (Singleton)      │
                    └──────────┬──────────┘
                               │ holds instance
                    ┌──────────▼──────────┐
                    │  MessageRepository   │
                    └──────────┬──────────┘
                               │ shared by
          ┌────────────────────┼────────────────────┐
          │                    │                    │
┌─────────▼─────────┐ ┌───────▼────────┐ ┌─────────▼─────────┐
│ NotificationListener│ │MessageProcessor│ │    AgentOverlay    │
│     Service        │ │    (Brain)     │ │    (Compose UI)   │
└───────────────────┘ └────────────────┘ └───────────────────┘
          │                                         │
          │ triggers                                │ launched by
          └─────────────┬───────────────────────────┘
                        │
              ┌─────────▼─────────┐
              │   OverlayService  │
              │  (WindowManager)  │
              └───────────────────┘
```

### Data Flow

```
[Notification]
    │
    ▼
AppNotificationListenerService.onNotificationPosted()
    │
    ├──► MessageNotificationFilter.shouldProcess()
    │
    ├──► NotificationMapper.map()
    │
    ├──► MessageRepository.ingestNotification() ──► Room DB
    │
    ├──► ReplyActionCache.save()
    │
    └──► OverlaySdk.show()
              │
              ▼
         OverlayService
              │
              ▼
         OverlayViewWrapper
              │
              ▼
         AgentOverlay (Compose)
              │
              ▼
         repository.allMessages (Flow)
```

---

## Summary of Findings

| Issue | Severity | Type | Testability Impact |
|-------|----------|------|-------------------|
| Cannot inject mock repository | HIGH | Testability | Blocks unit testing |
| MessageProcessor not cancellable | MEDIUM | Memory/Lifecycle | Scope leak potential |
| ReplyActionCache accumulates entries | LOW-MEDIUM | Memory | Unbounded growth |
| No Application initialized check | HIGH | Race Condition | Potential crash |
| Multiple MessageProcessor instances | MEDIUM | Race Condition | Duplicate processing |
| OverlaySdk registry never cleared | MEDIUM | Memory | Already documented |

---

## Recommended Priority Actions

### Priority 1: Critical for Testing

1. **Add stop() method to MessageProcessor** with scope cancellation
2. **Add defensive null check** for Application instance in services
3. **Add @VisibleForTesting reset methods** to OverlaySdk, ReplyActionCache

### Priority 2: Improve Testability

4. **Extract interfaces** for Repository and ActionCache
5. **Use constructor parameters with defaults** for dependency injection points
6. **Add ReplyActionCache entry cleanup** after message processing

### Priority 3: Future Architecture

7. **Consider Hilt migration** for automatic scope management
8. **Add warm-up query** for database in Application.onCreate()
9. **Document singleton lifecycle expectations** for consumers

---

## Conclusion

The current manual DI approach is functional but creates significant barriers to unit testing. The tightest coupling is between `AppNotificationListenerService` and `JetOverlayApplication.instance`, which prevents mock injection. Several race conditions exist around service initialization order and MessageProcessor lifecycle. Immediate priorities should focus on adding cancellation to MessageProcessor and defensive checks for Application initialization state.
