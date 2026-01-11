package com.yazan.jetoverlay.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageBucketTest {

    @Test
    fun `all buckets have display names`() {
        MessageBucket.entries.forEach { bucket ->
            assert(bucket.displayName.isNotBlank()) {
                "Bucket ${bucket.name} should have a display name"
            }
        }
    }

    @Test
    fun `all buckets have valid colors`() {
        MessageBucket.entries.forEach { bucket ->
            assert(bucket.color > 0) {
                "Bucket ${bucket.name} should have a valid color"
            }
        }
    }

    @Test
    fun `fromString returns correct bucket for valid name`() {
        MessageBucket.entries.forEach { bucket ->
            assertEquals(bucket, MessageBucket.fromString(bucket.name))
        }
    }

    @Test
    fun `fromString returns UNKNOWN for invalid name`() {
        assertEquals(MessageBucket.UNKNOWN, MessageBucket.fromString("INVALID"))
        assertEquals(MessageBucket.UNKNOWN, MessageBucket.fromString(""))
        assertEquals(MessageBucket.UNKNOWN, MessageBucket.fromString("random"))
    }

    @Test
    fun `URGENT bucket has expected display name`() {
        assertEquals("Urgent", MessageBucket.URGENT.displayName)
    }

    @Test
    fun `WORK bucket has expected display name`() {
        assertEquals("Work", MessageBucket.WORK.displayName)
    }

    @Test
    fun `SOCIAL bucket has expected display name`() {
        assertEquals("Social", MessageBucket.SOCIAL.displayName)
    }

    @Test
    fun `PROMOTIONAL bucket has expected display name`() {
        assertEquals("Promotional", MessageBucket.PROMOTIONAL.displayName)
    }

    @Test
    fun `TRANSACTIONAL bucket has expected display name`() {
        assertEquals("Transactional", MessageBucket.TRANSACTIONAL.displayName)
    }

    @Test
    fun `UNKNOWN bucket has expected display name`() {
        assertEquals("Unknown", MessageBucket.UNKNOWN.displayName)
    }

    @Test
    fun `there are exactly 6 buckets`() {
        assertEquals(6, MessageBucket.entries.size)
    }
}
