package com.yazan.jetoverlay

import android.Manifest
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Base test class for Android instrumented tests.
 * Provides common setup/teardown, ActivityScenario utilities,
 * and helper methods for testing overlay-related functionality.
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseAndroidTest {

    protected lateinit var context: Context
    protected lateinit var instrumentation: Instrumentation

    /**
     * Grant runtime permissions commonly needed for tests.
     * Note: SYSTEM_ALERT_WINDOW cannot be granted via GrantPermissionRule -
     * it requires user interaction or adb shell commands.
     */
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_PHONE_STATE
    )

    @Before
    open fun setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    open fun tearDown() {
        // Clean up any active overlays
        try {
            com.yazan.jetoverlay.api.OverlaySdk.hide("overlay_1")
        } catch (e: Exception) {
            // Ignore - overlay might not be active
        }
    }

    /**
     * Launch MainActivity with a fresh ActivityScenario.
     * The scenario should be closed by the test when done.
     */
    protected fun launchMainActivity(): ActivityScenario<MainActivity> {
        return ActivityScenario.launch(MainActivity::class.java)
    }

    /**
     * Launch an Activity with a custom intent.
     */
    protected inline fun <reified T : ComponentActivity> launchActivity(
        intent: Intent? = null
    ): ActivityScenario<T> {
        return if (intent != null) {
            ActivityScenario.launch(intent)
        } else {
            ActivityScenario.launch(T::class.java)
        }
    }

    /**
     * Check if the app has overlay permission (SYSTEM_ALERT_WINDOW).
     * This permission is required for displaying overlays.
     */
    protected fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Opens the overlay permission settings for the app.
     * Note: This only opens the settings - user interaction is required to grant.
     * In automated tests, use ADB commands instead:
     * `adb shell appops set <package> SYSTEM_ALERT_WINDOW allow`
     */
    protected fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Wait for a condition with timeout.
     * @param timeoutMs Maximum time to wait in milliseconds
     * @param intervalMs Polling interval in milliseconds
     * @param condition Lambda that returns true when condition is met
     * @return true if condition was met, false if timeout
     */
    protected fun waitForCondition(
        timeoutMs: Long = TestConstants.DEFAULT_TIMEOUT_MS,
        intervalMs: Long = TestConstants.DEFAULT_POLL_INTERVAL_MS,
        condition: () -> Boolean
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) return true
            Thread.sleep(intervalMs)
        }
        return false
    }

    /**
     * Execute a block on the UI thread and wait for completion.
     */
    protected fun runOnUiThread(block: () -> Unit) {
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync {
            try {
                block()
            } finally {
                latch.countDown()
            }
        }
        latch.await(TestConstants.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * Get UiDevice for UI Automator interactions (if needed for cross-app testing).
     */
    protected fun getUiDevice(): UiDevice {
        return UiDevice.getInstance(instrumentation)
    }

    /**
     * Grant overlay permission via ADB shell command.
     * This requires the test device to allow shell commands.
     * Should be called before tests that require overlay functionality.
     *
     * Note: This works on emulators and rooted devices.
     * On production devices, manual permission grant may be required.
     */
    protected fun grantOverlayPermissionViaShell(): Boolean {
        return try {
            val device = getUiDevice()
            device.executeShellCommand(
                "appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow"
            )
            // Wait for permission change to take effect
            Thread.sleep(500)
            hasOverlayPermission()
        } catch (e: Exception) {
            android.util.Log.w("BaseAndroidTest", "Failed to grant overlay permission via shell", e)
            false
        }
    }

    /**
     * Revoke overlay permission via ADB shell command.
     */
    protected fun revokeOverlayPermissionViaShell(): Boolean {
        return try {
            val device = getUiDevice()
            device.executeShellCommand(
                "appops set ${context.packageName} SYSTEM_ALERT_WINDOW deny"
            )
            Thread.sleep(500)
            !hasOverlayPermission()
        } catch (e: Exception) {
            android.util.Log.w("BaseAndroidTest", "Failed to revoke overlay permission via shell", e)
            false
        }
    }

    companion object {
        /**
         * Check if we're running on an emulator.
         * Some tests may behave differently on emulators vs real devices.
         */
        fun isEmulator(): Boolean {
            return Build.FINGERPRINT.startsWith("generic") ||
                    Build.FINGERPRINT.startsWith("unknown") ||
                    Build.MODEL.contains("google_sdk") ||
                    Build.MODEL.contains("Emulator") ||
                    Build.MODEL.contains("Android SDK built for x86") ||
                    Build.MANUFACTURER.contains("Genymotion") ||
                    (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                    "google_sdk" == Build.PRODUCT
        }
    }
}
