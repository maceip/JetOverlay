package com.yazan.jetoverlay.service.integration

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for EmailIntegration constants and configuration.
 * Note: Full OAuth, polling, and logging tests require instrumented tests
 * with proper Android context and mocked Log class.
 */
class EmailIntegrationTest {

    @Test
    fun `EMAIL_PACKAGE_NAME constant should be correct`() {
        assertEquals("email", EmailIntegration.EMAIL_PACKAGE_NAME)
    }

    @Test
    fun `EmailIntegration should be a singleton object`() {
        val instance1 = EmailIntegration
        val instance2 = EmailIntegration
        assertSame(instance1, instance2)
    }

    @Test
    fun `MockEmail data class should hold correct values`() {
        val mockEmail = EmailIntegration.MockEmail(
            id = "test_123",
            from = "test@example.com",
            subject = "Test Subject",
            snippet = "This is a test snippet"
        )

        assertEquals("test_123", mockEmail.id)
        assertEquals("test@example.com", mockEmail.from)
        assertEquals("Test Subject", mockEmail.subject)
        assertEquals("This is a test snippet", mockEmail.snippet)
    }

    @Test
    fun `MockEmail data class equals should work correctly`() {
        val email1 = EmailIntegration.MockEmail("id1", "a@b.com", "Subject", "Snippet")
        val email2 = EmailIntegration.MockEmail("id1", "a@b.com", "Subject", "Snippet")
        val email3 = EmailIntegration.MockEmail("id2", "a@b.com", "Subject", "Snippet")

        assertEquals(email1, email2)
        assertNotEquals(email1, email3)
    }

    @Test
    fun `MockEmail data class copy should work correctly`() {
        val original = EmailIntegration.MockEmail("id1", "a@b.com", "Subject", "Snippet")
        val copied = original.copy(subject = "New Subject")

        assertEquals("id1", copied.id)
        assertEquals("a@b.com", copied.from)
        assertEquals("New Subject", copied.subject)
        assertEquals("Snippet", copied.snippet)
    }

    @Test
    fun `MockEmail data class hashCode should work correctly`() {
        val email1 = EmailIntegration.MockEmail("id1", "a@b.com", "Subject", "Snippet")
        val email2 = EmailIntegration.MockEmail("id1", "a@b.com", "Subject", "Snippet")

        assertEquals(email1.hashCode(), email2.hashCode())
    }

    @Test
    fun `MockEmail data class component functions should work`() {
        val email = EmailIntegration.MockEmail("id1", "a@b.com", "Subject", "Snippet")

        val (id, from, subject, snippet) = email

        assertEquals("id1", id)
        assertEquals("a@b.com", from)
        assertEquals("Subject", subject)
        assertEquals("Snippet", snippet)
    }
}
