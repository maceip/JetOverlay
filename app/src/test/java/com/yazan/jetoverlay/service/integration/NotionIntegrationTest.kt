package com.yazan.jetoverlay.service.integration

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for NotionIntegration constants and configuration.
 * Note: Full OAuth, polling, and logging tests require instrumented tests
 * with proper Android context and mocked Log class.
 */
class NotionIntegrationTest {

    @Test
    fun `NOTION_PACKAGE_NAME constant should be correct`() {
        assertEquals("notion", NotionIntegration.NOTION_PACKAGE_NAME)
    }

    @Test
    fun `NotionIntegration should be a singleton object`() {
        val instance1 = NotionIntegration
        val instance2 = NotionIntegration
        assertSame(instance1, instance2)
    }

    @Test
    fun `MockNotionNotification data class should hold correct values`() {
        val mockNotification = NotionIntegration.MockNotionNotification(
            id = "test_123",
            type = NotionIntegration.NotionNotificationType.MENTION,
            pageTitle = "Test Page",
            author = "Test Author",
            content = "This is a test notification"
        )

        assertEquals("test_123", mockNotification.id)
        assertEquals(NotionIntegration.NotionNotificationType.MENTION, mockNotification.type)
        assertEquals("Test Page", mockNotification.pageTitle)
        assertEquals("Test Author", mockNotification.author)
        assertEquals("This is a test notification", mockNotification.content)
    }

    @Test
    fun `MockNotionNotification data class equals should work correctly`() {
        val notification1 = NotionIntegration.MockNotionNotification(
            "id1",
            NotionIntegration.NotionNotificationType.MENTION,
            "Page",
            "Author",
            "Content"
        )
        val notification2 = NotionIntegration.MockNotionNotification(
            "id1",
            NotionIntegration.NotionNotificationType.MENTION,
            "Page",
            "Author",
            "Content"
        )
        val notification3 = NotionIntegration.MockNotionNotification(
            "id2",
            NotionIntegration.NotionNotificationType.MENTION,
            "Page",
            "Author",
            "Content"
        )

        assertEquals(notification1, notification2)
        assertNotEquals(notification1, notification3)
    }

    @Test
    fun `MockNotionNotification data class copy should work correctly`() {
        val original = NotionIntegration.MockNotionNotification(
            "id1",
            NotionIntegration.NotionNotificationType.MENTION,
            "Page",
            "Author",
            "Content"
        )
        val copied = original.copy(pageTitle = "New Page Title")

        assertEquals("id1", copied.id)
        assertEquals(NotionIntegration.NotionNotificationType.MENTION, copied.type)
        assertEquals("New Page Title", copied.pageTitle)
        assertEquals("Author", copied.author)
        assertEquals("Content", copied.content)
    }

    @Test
    fun `MockNotionNotification data class hashCode should work correctly`() {
        val notification1 = NotionIntegration.MockNotionNotification(
            "id1",
            NotionIntegration.NotionNotificationType.COMMENT,
            "Page",
            "Author",
            "Content"
        )
        val notification2 = NotionIntegration.MockNotionNotification(
            "id1",
            NotionIntegration.NotionNotificationType.COMMENT,
            "Page",
            "Author",
            "Content"
        )

        assertEquals(notification1.hashCode(), notification2.hashCode())
    }

    @Test
    fun `MockNotionNotification data class component functions should work`() {
        val notification = NotionIntegration.MockNotionNotification(
            "id1",
            NotionIntegration.NotionNotificationType.PAGE_UPDATE,
            "Page",
            "Author",
            "Content"
        )

        val (id, type, pageTitle, author, content) = notification

        assertEquals("id1", id)
        assertEquals(NotionIntegration.NotionNotificationType.PAGE_UPDATE, type)
        assertEquals("Page", pageTitle)
        assertEquals("Author", author)
        assertEquals("Content", content)
    }

    @Test
    fun `NotionNotificationType enum should have correct displayNames`() {
        assertEquals("Mention", NotionIntegration.NotionNotificationType.MENTION.displayName)
        assertEquals("Comment", NotionIntegration.NotionNotificationType.COMMENT.displayName)
        assertEquals("Page Update", NotionIntegration.NotionNotificationType.PAGE_UPDATE.displayName)
        assertEquals("Database Update", NotionIntegration.NotionNotificationType.DATABASE_UPDATE.displayName)
    }

    @Test
    fun `NotionNotificationType enum should have all expected values`() {
        val types = NotionIntegration.NotionNotificationType.values()
        assertEquals(4, types.size)
        assertTrue(types.contains(NotionIntegration.NotionNotificationType.MENTION))
        assertTrue(types.contains(NotionIntegration.NotionNotificationType.COMMENT))
        assertTrue(types.contains(NotionIntegration.NotionNotificationType.PAGE_UPDATE))
        assertTrue(types.contains(NotionIntegration.NotionNotificationType.DATABASE_UPDATE))
    }

    @Test
    fun `NotionNotificationType valueOf should work correctly`() {
        assertEquals(
            NotionIntegration.NotionNotificationType.MENTION,
            NotionIntegration.NotionNotificationType.valueOf("MENTION")
        )
        assertEquals(
            NotionIntegration.NotionNotificationType.COMMENT,
            NotionIntegration.NotionNotificationType.valueOf("COMMENT")
        )
    }
}
