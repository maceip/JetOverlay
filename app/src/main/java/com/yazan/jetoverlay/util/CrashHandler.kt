package com.yazan.jetoverlay.util

import android.util.Log

/**
 * Custom uncaught exception handler for JetOverlay.
 * Logs crash details before the app terminates.
 */
class CrashHandler private constructor(
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "JetOverlay.Crash"

        /**
         * Installs the crash handler as the default uncaught exception handler.
         * Should be called from Application.onCreate().
         */
        fun install() {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(defaultHandler))
            Logger.i("CrashHandler", "Crash handler installed")
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Log the crash details
            Log.e(TAG, "=== UNCAUGHT EXCEPTION ===")
            Log.e(TAG, "Thread: ${thread.name} (id=${thread.id})")
            Log.e(TAG, "Exception: ${throwable.javaClass.name}")
            Log.e(TAG, "Message: ${throwable.message}")
            Log.e(TAG, "Stack trace:", throwable)

            // Log any suppressed exceptions
            throwable.suppressed.forEach { suppressed ->
                Log.e(TAG, "Suppressed: ${suppressed.javaClass.name}: ${suppressed.message}", suppressed)
            }

            // Log the cause chain
            var cause = throwable.cause
            var depth = 0
            while (cause != null && depth < 10) {
                Log.e(TAG, "Caused by [${depth}]: ${cause.javaClass.name}: ${cause.message}", cause)
                cause = cause.cause
                depth++
            }

            Log.e(TAG, "=== END CRASH LOG ===")

            // Also log to our Logger for consistency
            Logger.e("CrashHandler", "App crashed: ${throwable.javaClass.simpleName}: ${throwable.message}", throwable)

        } catch (e: Exception) {
            // Last resort - make sure we don't crash the crash handler
            Log.e(TAG, "Error in crash handler", e)
        } finally {
            // Pass to the default handler to show the crash dialog / terminate
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
