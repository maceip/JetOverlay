package com.yazan.jetoverlay.service.notification

import android.app.Notification
import android.os.Bundle

object NotificationSilencer {
    private val otpRegex = Regex("\\b\\d{4,8}\\b")
    private val otpKeywords = listOf("otp", "one-time", "one time", "verification code", "security code", "code is")
    private val lowSignalKeywords = listOf("battery low", "low battery", "low power", "charging")

    fun shouldSilence(packageName: String, extras: Bundle): Boolean {
        val title = extras.getString(Notification.EXTRA_TITLE)?.lowercase().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.lowercase().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.lowercase().orEmpty()
        val combined = listOf(title, text, bigText).joinToString(" ")

        if (otpKeywords.any { combined.contains(it) } && otpRegex.containsMatchIn(combined)) {
            return true
        }

        if (packageName == "android" && lowSignalKeywords.any { combined.contains(it) }) {
            return true
        }

        return false
    }
}
