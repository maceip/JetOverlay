package com.yazan.jetoverlay.service.integration

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for GitHubIntegration constants and configuration.
 * Note: Full OAuth, polling, and logging tests require instrumented tests
 * with proper Android context and mocked Log class.
 */
class GitHubIntegrationTest {

    @Test
    fun `GITHUB_PACKAGE_NAME constant should be correct`() {
        assertEquals("github", GitHubIntegration.GITHUB_PACKAGE_NAME)
    }

    @Test
    fun `GitHubIntegration should be a singleton object`() {
        val instance1 = GitHubIntegration
        val instance2 = GitHubIntegration
        assertSame(instance1, instance2)
    }

    @Test
    fun `MockGitHubNotification data class should hold correct values`() {
        val mockNotification = GitHubIntegration.MockGitHubNotification(
            id = "test_123",
            type = GitHubIntegration.GitHubNotificationType.PR_REVIEW,
            repository = "user/test-repo",
            author = "Test Author",
            title = "Test PR Title",
            content = "This is a test notification"
        )

        assertEquals("test_123", mockNotification.id)
        assertEquals(GitHubIntegration.GitHubNotificationType.PR_REVIEW, mockNotification.type)
        assertEquals("user/test-repo", mockNotification.repository)
        assertEquals("Test Author", mockNotification.author)
        assertEquals("Test PR Title", mockNotification.title)
        assertEquals("This is a test notification", mockNotification.content)
    }

    @Test
    fun `MockGitHubNotification data class equals should work correctly`() {
        val notification1 = GitHubIntegration.MockGitHubNotification(
            "id1",
            GitHubIntegration.GitHubNotificationType.PR_REVIEW,
            "repo",
            "Author",
            "Title",
            "Content"
        )
        val notification2 = GitHubIntegration.MockGitHubNotification(
            "id1",
            GitHubIntegration.GitHubNotificationType.PR_REVIEW,
            "repo",
            "Author",
            "Title",
            "Content"
        )
        val notification3 = GitHubIntegration.MockGitHubNotification(
            "id2",
            GitHubIntegration.GitHubNotificationType.PR_REVIEW,
            "repo",
            "Author",
            "Title",
            "Content"
        )

        assertEquals(notification1, notification2)
        assertNotEquals(notification1, notification3)
    }

    @Test
    fun `MockGitHubNotification data class copy should work correctly`() {
        val original = GitHubIntegration.MockGitHubNotification(
            "id1",
            GitHubIntegration.GitHubNotificationType.PR_REVIEW,
            "repo",
            "Author",
            "Title",
            "Content"
        )
        val copied = original.copy(repository = "new-user/new-repo")

        assertEquals("id1", copied.id)
        assertEquals(GitHubIntegration.GitHubNotificationType.PR_REVIEW, copied.type)
        assertEquals("new-user/new-repo", copied.repository)
        assertEquals("Author", copied.author)
        assertEquals("Title", copied.title)
        assertEquals("Content", copied.content)
    }

    @Test
    fun `MockGitHubNotification data class hashCode should work correctly`() {
        val notification1 = GitHubIntegration.MockGitHubNotification(
            "id1",
            GitHubIntegration.GitHubNotificationType.PR_COMMENT,
            "repo",
            "Author",
            "Title",
            "Content"
        )
        val notification2 = GitHubIntegration.MockGitHubNotification(
            "id1",
            GitHubIntegration.GitHubNotificationType.PR_COMMENT,
            "repo",
            "Author",
            "Title",
            "Content"
        )

        assertEquals(notification1.hashCode(), notification2.hashCode())
    }

    @Test
    fun `MockGitHubNotification data class component functions should work`() {
        val notification = GitHubIntegration.MockGitHubNotification(
            "id1",
            GitHubIntegration.GitHubNotificationType.ISSUE_COMMENT,
            "repo",
            "Author",
            "Title",
            "Content"
        )

        val (id, type, repository, author, title, content) = notification

        assertEquals("id1", id)
        assertEquals(GitHubIntegration.GitHubNotificationType.ISSUE_COMMENT, type)
        assertEquals("repo", repository)
        assertEquals("Author", author)
        assertEquals("Title", title)
        assertEquals("Content", content)
    }

    @Test
    fun `GitHubNotificationType enum should have correct displayNames`() {
        assertEquals("PR Review", GitHubIntegration.GitHubNotificationType.PR_REVIEW.displayName)
        assertEquals("PR Comment", GitHubIntegration.GitHubNotificationType.PR_COMMENT.displayName)
        assertEquals("Issue Comment", GitHubIntegration.GitHubNotificationType.ISSUE_COMMENT.displayName)
        assertEquals("Mention", GitHubIntegration.GitHubNotificationType.MENTION.displayName)
        assertEquals("Assignment", GitHubIntegration.GitHubNotificationType.ASSIGN.displayName)
        assertEquals("CI Failure", GitHubIntegration.GitHubNotificationType.CI_FAILURE.displayName)
        assertEquals("Release", GitHubIntegration.GitHubNotificationType.RELEASE.displayName)
    }

    @Test
    fun `GitHubNotificationType enum should have all expected values`() {
        val types = GitHubIntegration.GitHubNotificationType.values()
        assertEquals(7, types.size)
        assertTrue(types.contains(GitHubIntegration.GitHubNotificationType.PR_REVIEW))
        assertTrue(types.contains(GitHubIntegration.GitHubNotificationType.PR_COMMENT))
        assertTrue(types.contains(GitHubIntegration.GitHubNotificationType.ISSUE_COMMENT))
        assertTrue(types.contains(GitHubIntegration.GitHubNotificationType.MENTION))
        assertTrue(types.contains(GitHubIntegration.GitHubNotificationType.ASSIGN))
        assertTrue(types.contains(GitHubIntegration.GitHubNotificationType.CI_FAILURE))
        assertTrue(types.contains(GitHubIntegration.GitHubNotificationType.RELEASE))
    }

    @Test
    fun `GitHubNotificationType valueOf should work correctly`() {
        assertEquals(
            GitHubIntegration.GitHubNotificationType.PR_REVIEW,
            GitHubIntegration.GitHubNotificationType.valueOf("PR_REVIEW")
        )
        assertEquals(
            GitHubIntegration.GitHubNotificationType.MENTION,
            GitHubIntegration.GitHubNotificationType.valueOf("MENTION")
        )
        assertEquals(
            GitHubIntegration.GitHubNotificationType.CI_FAILURE,
            GitHubIntegration.GitHubNotificationType.valueOf("CI_FAILURE")
        )
    }

    @Test
    fun `MockGitHubNotification toString should contain all fields`() {
        val notification = GitHubIntegration.MockGitHubNotification(
            "id1",
            GitHubIntegration.GitHubNotificationType.RELEASE,
            "user/repo",
            "Author",
            "v1.0 Released",
            "New version available"
        )

        val stringRep = notification.toString()
        assertTrue(stringRep.contains("id1"))
        assertTrue(stringRep.contains("RELEASE"))
        assertTrue(stringRep.contains("user/repo"))
        assertTrue(stringRep.contains("Author"))
        assertTrue(stringRep.contains("v1.0 Released"))
        assertTrue(stringRep.contains("New version available"))
    }
}
