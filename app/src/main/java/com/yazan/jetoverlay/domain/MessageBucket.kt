package com.yazan.jetoverlay.domain

import androidx.compose.ui.graphics.Color

/**
 * Categorizes messages into buckets for prioritization and UI display.
 */
enum class MessageBucket(
    val displayName: String,
    val color: Long
) {
    URGENT("Urgent", 0xFFE53935),          // Red
    WORK("Work", 0xFF1E88E5),              // Blue
    SOCIAL("Social", 0xFF43A047),          // Green
    PROMOTIONAL("Promotional", 0xFFFF9800), // Orange
    TRANSACTIONAL("Transactional", 0xFF8E24AA), // Purple
    UNKNOWN("Unknown", 0xFF757575);        // Grey

    companion object {
        fun fromString(value: String): MessageBucket {
            return entries.find { it.name == value } ?: UNKNOWN
        }
    }
}
