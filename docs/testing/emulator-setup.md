---
type: reference
title: Android Emulator Setup for E2E Testing
created: 2026-01-11
tags:
  - testing
  - emulator
  - setup
related:
  - "[[running-tests]]"
  - "[[../architecture/fragility-matrix]]"
---

# Android Emulator Setup for E2E Testing

This document describes the emulator configuration and setup for running E2E tests on the JetOverlay application.

## Current Environment

### SDK Installation

The Android SDK is installed at:
```
C:\Users\mac\AppData\Local\Android\Sdk
```

> **Note:** The `ANDROID_HOME` environment variable is not set. This may cause issues with some command-line tools. Consider setting it in your environment:
> ```powershell
> [Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
> ```

### Installed Components

| Component | Version/API Level | Path |
|-----------|-------------------|------|
| Emulator | Latest (v36+) | `emulator/` |
| Platform | android-34 | `platforms/android-34/` |
| Platform | android-35 | `platforms/android-35/` |
| Platform | android-36 | `platforms/android-36/` |
| System Image | android-36.1 (Google Play, x86_64) | `system-images/android-36.1/google_apis_playstore/x86_64/` |

### Available AVD

An AVD is already configured and ready for testing:

| Property | Value |
|----------|-------|
| **AVD Name** | `Medium_Phone_API_36.1` |
| **Display Name** | Medium Phone API 36.1 |
| **Target API** | android-36.1 |
| **ABI** | x86_64 |
| **Device** | medium_phone (Generic) |
| **Resolution** | 1080x2400 |
| **Density** | 420 dpi |
| **RAM** | 2048 MB |
| **VM Heap** | 336 MB |
| **Storage** | 6 GB data partition, 512 MB SD card |
| **Google Play** | Enabled |
| **GPU** | Auto (hardware acceleration) |

## Emulator Launch Commands

### From Android Studio

The emulator can be launched directly from Android Studio's Device Manager.

### From Command Line

Using the full path (recommended for scripts):

```powershell
# Windows PowerShell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone_API_36.1 -netdelay none -netspeed full
```

```bash
# Git Bash / WSL
"C:/Users/mac/AppData/Local/Android/Sdk/emulator/emulator.exe" -avd Medium_Phone_API_36.1 -netdelay none -netspeed full
```

### Headless Mode (for CI)

For automated testing in CI environments without a display:

```bash
emulator -avd Medium_Phone_API_36.1 -no-window -no-audio -no-boot-anim
```

### List Available AVDs

```bash
"C:/Users/mac/AppData/Local/Android/Sdk/emulator/emulator.exe" -list-avds
```

## Verifying Emulator Connectivity

Once the emulator is running, verify ADB connectivity:

```bash
# Check connected devices
adb devices

# Expected output:
# List of devices attached
# emulator-5554   device
```

Wait for the device to fully boot:

```bash
# Wait for boot to complete
adb wait-for-device
adb shell getprop sys.boot_completed
# Should return "1" when ready
```

## Running the Application

### Install and Launch

```bash
# Build and install debug APK
./gradlew installDebug

# Launch the app
adb shell am start -n com.yazan.jetoverlay/.MainActivity
```

### Verify Overlay Permission

The app requires `SYSTEM_ALERT_WINDOW` permission. On the emulator, you may need to grant it manually:

1. Open Settings > Apps > JetOverlay
2. Enable "Display over other apps"

Or via ADB:
```bash
adb shell appops set com.yazan.jetoverlay SYSTEM_ALERT_WINDOW allow
```

## Creating a New AVD (If Needed)

If you need to create an additional AVD for API 34+ testing:

### Step 1: Download System Image

```bash
# Using sdkmanager
sdkmanager "system-images;android-34;google_apis_playstore;x86_64"
```

Or from Android Studio: Tools > SDK Manager > SDK Tools > System Images

### Step 2: Create AVD

```bash
avdmanager create avd \
  --name "Test_Phone_API_34" \
  --package "system-images;android-34;google_apis_playstore;x86_64" \
  --device "pixel_6"
```

Or from Android Studio: Tools > Device Manager > Create Device

### Recommended AVD Settings for Testing

| Setting | Recommended Value | Rationale |
|---------|-------------------|-----------|
| RAM | 2048 MB+ | Prevents OOM during tests |
| VM Heap | 336 MB | Standard for medium phones |
| GPU | Auto/Host | Better performance |
| Quick Boot | Enabled | Faster test iterations |

## Troubleshooting

### Emulator Won't Start

1. **Check HAXM/Hyper-V**: Ensure Intel HAXM or Windows Hypervisor Platform is enabled
2. **Check disk space**: AVDs can grow to several GB
3. **Kill stale processes**: `adb kill-server && adb start-server`

### Slow Emulator Performance

1. Enable GPU acceleration in AVD config
2. Increase RAM allocation
3. Use x86_64 system images (not ARM)
4. Enable Quick Boot snapshots

### ADB Connection Issues

```bash
# Restart ADB server
adb kill-server
adb start-server

# If emulator shows "offline"
adb reconnect
```

### Permission Denied Errors

For overlay permissions specifically:
```bash
# Grant overlay permission
adb shell appops set com.yazan.jetoverlay SYSTEM_ALERT_WINDOW allow

# Verify permission
adb shell appops get com.yazan.jetoverlay SYSTEM_ALERT_WINDOW
```

## CI Integration Notes

For GitHub Actions or other CI systems:

1. Use `reactivecircus/android-emulator-runner` action for easier setup
2. Prefer API 30-34 for CI (better stability)
3. Use `-no-window -no-audio` flags
4. Consider caching AVD snapshots for faster boot times

Example GitHub Actions snippet:
```yaml
- name: Run Android Tests
  uses: reactivecircus/android-emulator-runner@v2
  with:
    api-level: 34
    target: google_apis
    arch: x86_64
    script: ./gradlew connectedAndroidTest
```

## Related Documents

- [[running-tests]] - How to execute the test suite
- [[../architecture/fragility-matrix]] - Known issues that tests should validate
