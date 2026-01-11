# 1. Keep the Public API
# We must ensure the consumer app can always see your SDK's entry points,
# even if they obfuscate their own code.
-keep class com.yazan.jetoverlay.api.** { *; }

# 2. Keep the Service
# The Android System starts this service using the class name string found in the
# AndroidManifest.xml. If R8 renames this class to 'a.b.c', the system won't find it.
-keep class com.yazan.jetoverlay.service.OverlayService {
    <init>();
}

# 3. Keep the View Wrapper
# This custom view handles Lifecycle/SavedState. We keep it to ensure
# system calls to View constructors work correctly.
-keep class com.yazan.jetoverlay.internal.OverlayViewWrapper {
    <init>(...);
}