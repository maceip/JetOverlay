package com.yazan.jetoverlay

/**
 * Constants used across instrumented tests.
 */
object TestConstants {
    /** Default timeout for async operations in milliseconds */
    const val DEFAULT_TIMEOUT_MS = 5000L

    /** Extended timeout for slower operations (e.g., service startup) */
    const val EXTENDED_TIMEOUT_MS = 10000L

    /** Short timeout for quick UI assertions */
    const val SHORT_TIMEOUT_MS = 2000L

    /** Default polling interval for wait conditions */
    const val DEFAULT_POLL_INTERVAL_MS = 100L

    /** Delay after launching activities to allow UI to settle */
    const val ACTIVITY_LAUNCH_DELAY_MS = 500L

    /** Delay for service to start and become ready */
    const val SERVICE_START_DELAY_MS = 1000L

    /** Default overlay ID used in tests */
    const val TEST_OVERLAY_ID = "overlay_1"

    /** Test message data */
    object TestMessages {
        const val PACKAGE_NAME = "com.test.app"
        const val SENDER_NAME = "Test Sender"
        const val ORIGINAL_CONTENT = "This is a test message"
        const val VEILED_CONTENT = "This is a veiled test message"
    }
}
