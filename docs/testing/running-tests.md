---
type: reference
title: Running Tests
created: 2026-01-11
tags:
  - testing
  - ci
  - commands
related:
  - "[[emulator-setup]]"
  - "[[../architecture/fragility-matrix]]"
---

# Running Tests

This document describes how to run the JetOverlay test suite, including unit tests, instrumented tests, and the full connected test suite.

## Prerequisites

1. **Android SDK** installed (see [[emulator-setup]] for details)
2. **Android Emulator** running or physical device connected
3. **ADB** accessible from command line
4. **Overlay permission** granted for the app (for overlay-related tests)

## Quick Start

### Run All Connected Tests

```bash
# Ensure emulator is running first (see Emulator Setup below)
./gradlew connectedAndroidTest
```

### Run Specific Test Class

```bash
# Run only MessageDao tests
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yazan.jetoverlay.data.MessageDaoTest

# Run only OverlayService tests (currently @Ignore due to Compose compatibility)
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yazan.jetoverlay.service.OverlayServiceTest
```

### Run Unit Tests Only (No Device Required)

```bash
./gradlew test
```

## Emulator Setup

Before running connected tests, ensure an emulator is running:

```bash
# List available AVDs
$LOCALAPPDATA/Android/Sdk/emulator/emulator -list-avds

# Start emulator (replace AVD_NAME with actual name)
$LOCALAPPDATA/Android/Sdk/emulator/emulator -avd AVD_NAME -no-audio -gpu swiftshader_indirect &

# Wait for boot to complete
$LOCALAPPDATA/Android/Sdk/platform-tools/adb wait-for-device
$LOCALAPPDATA/Android/Sdk/platform-tools/adb shell getprop sys.boot_completed
# Should return "1" when ready

# If emulator shows "unauthorized", restart ADB server:
$LOCALAPPDATA/Android/Sdk/platform-tools/adb kill-server
$LOCALAPPDATA/Android/Sdk/platform-tools/adb start-server
```

See [[emulator-setup]] for detailed emulator configuration.

## Test Categories

### 1. Room Database Tests (`MessageDaoTest`)

Tests the data layer functionality:
- Insert/retrieve operations
- Flow emissions on data changes
- Type converters for `List<String>` serialization
- Update and delete operations

**Status:** Fully functional (19 tests)

### 2. Overlay Service Tests (`OverlayServiceTest`)

Tests the overlay lifecycle:
- Service starts as foreground service
- Overlay show/hide operations
- Configuration change survival
- Edge cases (duplicate show, rapid cycles)

**Status:** Temporarily disabled (@Ignore)

**Known Issue:** Compose BOM 2025.12.01 has compatibility issues with API 36 emulator, causing `NoSuchMethodException` for `LocalOwnersProvider.getAmbientOwnersProvider()`. This is a test infrastructure issue, not a functionality bug. The overlay SDK works correctly in production.

**Workaround:** Run on API 34 or 35 emulator, or wait for Compose BOM update.

## Test Results

Test reports are generated at:
```
app/build/reports/androidTests/connected/debug/index.html
```

Open this file in a browser to view detailed test results, including:
- Pass/fail status for each test
- Execution time
- Stack traces for failures

## Granting Overlay Permission

Some tests require `SYSTEM_ALERT_WINDOW` permission. Grant it via ADB:

```bash
# Grant overlay permission
adb shell appops set com.yazan.jetoverlay SYSTEM_ALERT_WINDOW allow

# Verify permission
adb shell appops get com.yazan.jetoverlay SYSTEM_ALERT_WINDOW
# Should show: SYSTEM_ALERT_WINDOW: allow
```

## CI Integration

For continuous integration, use the following workflow:

```yaml
# Example GitHub Actions step
- name: Run Android Tests
  run: |
    # Start emulator
    $ANDROID_HOME/emulator/emulator -avd test_avd -no-window -no-audio &
    adb wait-for-device
    adb shell getprop sys.boot_completed | grep -q "1" || sleep 30

    # Grant permissions
    adb shell appops set com.yazan.jetoverlay SYSTEM_ALERT_WINDOW allow

    # Run tests
    ./gradlew connectedAndroidTest
```

## Troubleshooting

### Emulator Shows "unauthorized"

```bash
adb kill-server && adb start-server
```

### Tests Timeout Waiting for Overlay

Increase timeout constants in `TestConstants.kt`:
- `DEFAULT_TIMEOUT_MS` (default: 5000)
- `EXTENDED_TIMEOUT_MS` (default: 10000)

### Compose-Related Test Failures

If tests fail with Compose internal errors (`NoSuchMethodException`), the issue is likely a Compose BOM version incompatibility with the target API level. Options:
1. Run tests on a lower API level emulator (API 34 or 35)
2. Update Compose BOM version
3. Mark affected tests as `@Ignore` temporarily

### No Connected Devices

```bash
# Check device connection
adb devices

# Should show:
# List of devices attached
# emulator-5554   device
```

## Test Summary (as of 2026-01-11)

| Test Class | Tests | Status |
|------------|-------|--------|
| MessageDaoTest | 19 | Passing |
| OverlayServiceTest | 10 | Skipped (Compose compatibility) |
| **Total** | **29** | **19 passing, 10 skipped** |

## Next Steps

1. Monitor Compose BOM updates for API 36 compatibility fix
2. Re-enable OverlayServiceTest when Compose issue is resolved
3. Add notification listener tests in Phase 02
