# Crash Assessment: Room Schema Migration Mismatch

**Date:** 2026-01-12
**Device:** Pixel Phone (4C101FDKD000PZ)
**Package:** `com.yazan.jetoverlay`
**Version:** Debug Build (Phase 08)

## Incident Summary
The application crashed immediately after the onboarding flow (granting notification permissions). The crash logs point to a fatal exception on a background thread (`DefaultDispatcher-worker-5`) initiated during database access.

## Root Cause Analysis
**Exception:** `java.lang.IllegalStateException: Migration didn't properly handle: messages(com.yazan.jetoverlay.data.Message).`

**Explanation:**
Room's schema verification failed. The database schema found on the device does not match the schema expected by the `Message` entity class definition.

**Specific Mismatch:**
- **Column:** `bucket`
- **Expected (Entity Code):** `defaultValue = 'undefined'` (Implies no default value set in `@ColumnInfo`)
- **Found (Database File):** `defaultValue = ''UNKNOWN''` (A default value of string literal "UNKNOWN" exists in the DB)

**Likely Scenario:**
A recent database migration (likely adding the `bucket` column) used an SQL statement like:
`ALTER TABLE messages ADD COLUMN bucket TEXT NOT NULL DEFAULT 'UNKNOWN'`
However, the `Message` entity class in Kotlin was updated *without* the matching default value annotation:
```kotlin
@ColumnInfo(name = "bucket") val bucket: String
// Should be:
// @ColumnInfo(name = "bucket", defaultValue = "UNKNOWN") val bucket: String
```

## Recommended Fix
1.  **Update Entity:** Modify `app/src/main/java/com/yazan/jetoverlay/data/Message.kt` to add `defaultValue = "UNKNOWN"` to the `bucket` field's `@ColumnInfo`.
2.  **Verify Migration:** Check the `AppDatabase` migration path to ensure consistency.
3.  **Clean Install:** Since this is a dev build, uninstalling the app from the device (`adb uninstall com.yazan.jetoverlay`) and reinstalling will also fix it by recreating the DB from scratch (bypassing the migration check if it was an upgrade issue). However, fixing the code is required for correct schema verification.

## Log Excerpt
```text
E AndroidRuntime: FATAL EXCEPTION: DefaultDispatcher-worker-5
E AndroidRuntime: Process: com.yazan.jetoverlay, PID: 16034
E AndroidRuntime: java.lang.IllegalStateException: Migration didn't properly handle: messages(com.yazan.jetoverlay.data.Message).
E AndroidRuntime:  Expected:
E AndroidRuntime: TableInfo { ... name = 'bucket', ... defaultValue = 'undefined' ... }
E AndroidRuntime:  Found:
E AndroidRuntime: TableInfo { ... name = 'bucket', ... defaultValue = ''UNKNOWN'' ... }
```
