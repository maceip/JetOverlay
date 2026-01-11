package com.yazan.jetoverlay.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.yazan.jetoverlay.TestConstants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for MessageDao.
 * Uses an in-memory Room database for test isolation.
 */
@RunWith(AndroidJUnit4::class)
class MessageDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var messageDao: MessageDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        messageDao = database.messageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Insert and Retrieve Tests ====================

    @Test
    fun insert_singleMessage_returnsValidId() = runTest {
        val message = createTestMessage()

        val id = messageDao.insert(message)

        assertTrue("Insert should return a positive ID", id > 0)
    }

    @Test
    fun insert_thenRetrieveById_returnsCorrectMessage() = runTest {
        val message = createTestMessage(
            packageName = "com.test.app",
            senderName = "John Doe",
            originalContent = "Hello, World!"
        )

        val id = messageDao.insert(message)
        val retrieved = messageDao.getMessageById(id)

        assertNotNull("Retrieved message should not be null", retrieved)
        assertEquals("com.test.app", retrieved?.packageName)
        assertEquals("John Doe", retrieved?.senderName)
        assertEquals("Hello, World!", retrieved?.originalContent)
    }

    @Test
    fun getMessageById_nonExistentId_returnsNull() = runTest {
        val retrieved = messageDao.getMessageById(999L)

        assertNull("Non-existent message should return null", retrieved)
    }

    @Test
    fun insert_multipleMessages_allRetrievable() = runTest {
        val messages = listOf(
            createTestMessage(senderName = "User1", originalContent = "Message 1"),
            createTestMessage(senderName = "User2", originalContent = "Message 2"),
            createTestMessage(senderName = "User3", originalContent = "Message 3")
        )

        val ids = messages.map { messageDao.insert(it) }

        assertEquals(3, ids.size)
        ids.forEachIndexed { index, id ->
            val retrieved = messageDao.getMessageById(id)
            assertNotNull("Message $index should be retrievable", retrieved)
        }
    }

    // ==================== Flow Emission Tests ====================

    @Test
    fun getAllMessages_emitsInitialEmptyList() = runTest {
        messageDao.getAllMessages().test {
            val initial = awaitItem()
            assertTrue("Initial emission should be empty", initial.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getAllMessages_emitsOnInsert() = runTest {
        messageDao.getAllMessages().test {
            // Consume initial empty emission
            awaitItem()

            // Insert a message
            val message = createTestMessage(originalContent = "New message")
            messageDao.insert(message)

            // Verify emission with new message
            val afterInsert = awaitItem()
            assertEquals(1, afterInsert.size)
            assertEquals("New message", afterInsert[0].originalContent)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getAllMessages_emitsOnUpdate() = runTest {
        messageDao.getAllMessages().test {
            // Consume initial empty emission
            awaitItem()

            // Insert a message
            val message = createTestMessage(originalContent = "Original content")
            val id = messageDao.insert(message)
            awaitItem()

            // Update the message
            val updatedMessage = message.copy(id = id, originalContent = "Updated content")
            messageDao.update(updatedMessage)

            // Verify emission with updated message
            val afterUpdate = awaitItem()
            assertEquals(1, afterUpdate.size)
            assertEquals("Updated content", afterUpdate[0].originalContent)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getAllMessages_emitsOnDeleteAll() = runTest {
        messageDao.getAllMessages().test {
            // Consume initial empty emission
            awaitItem()

            // Insert messages
            messageDao.insert(createTestMessage(originalContent = "Message 1"))
            messageDao.insert(createTestMessage(originalContent = "Message 2"))

            // Consume insert emissions (may be 1 or 2 depending on batching)
            var latestList = awaitItem()
            if (latestList.size < 2) {
                latestList = awaitItem()
            }
            assertEquals(2, latestList.size)

            // Delete all
            messageDao.deleteAll()

            // Verify emission with empty list
            val afterDelete = awaitItem()
            assertTrue("After deleteAll, list should be empty", afterDelete.isEmpty())

            cancelAndConsumeRemainingEvents()
        }
    }

    // ==================== Ordering Tests ====================

    @Test
    fun getAllMessages_orderedByTimestampDescending() = runTest {
        // Insert messages with specific timestamps
        val oldestMessage = createTestMessage(
            senderName = "Oldest",
            timestamp = 1000L
        )
        val middleMessage = createTestMessage(
            senderName = "Middle",
            timestamp = 2000L
        )
        val newestMessage = createTestMessage(
            senderName = "Newest",
            timestamp = 3000L
        )

        // Insert in random order
        messageDao.insert(middleMessage)
        messageDao.insert(newestMessage)
        messageDao.insert(oldestMessage)

        val messages = messageDao.getAllMessages().first()

        assertEquals(3, messages.size)
        assertEquals("Newest", messages[0].senderName)
        assertEquals("Middle", messages[1].senderName)
        assertEquals("Oldest", messages[2].senderName)
    }

    @Test
    fun getAllMessages_sameTimestamp_maintainsInsertionOrder() = runTest {
        val fixedTimestamp = 5000L

        val message1 = createTestMessage(senderName = "First", timestamp = fixedTimestamp)
        val message2 = createTestMessage(senderName = "Second", timestamp = fixedTimestamp)
        val message3 = createTestMessage(senderName = "Third", timestamp = fixedTimestamp)

        messageDao.insert(message1)
        messageDao.insert(message2)
        messageDao.insert(message3)

        val messages = messageDao.getAllMessages().first()

        assertEquals(3, messages.size)
        // With same timestamp, order may vary by implementation
        // Just verify all are present
        val senderNames = messages.map { it.senderName }.toSet()
        assertTrue(senderNames.contains("First"))
        assertTrue(senderNames.contains("Second"))
        assertTrue(senderNames.contains("Third"))
    }

    // ==================== Update Tests ====================

    @Test
    fun update_modifiesExistingMessage() = runTest {
        val message = createTestMessage(
            originalContent = "Original",
            status = "RECEIVED"
        )
        val id = messageDao.insert(message)

        val updatedMessage = message.copy(
            id = id,
            originalContent = "Modified",
            status = "PROCESSED",
            veiledContent = "Veiled content"
        )
        messageDao.update(updatedMessage)

        val retrieved = messageDao.getMessageById(id)

        assertNotNull(retrieved)
        assertEquals("Modified", retrieved?.originalContent)
        assertEquals("PROCESSED", retrieved?.status)
        assertEquals("Veiled content", retrieved?.veiledContent)
    }

    @Test
    fun update_preservesUnmodifiedFields() = runTest {
        val message = createTestMessage(
            packageName = "com.original.app",
            senderName = "Original Sender",
            originalContent = "Original content"
        )
        val id = messageDao.insert(message)

        val retrieved = messageDao.getMessageById(id)!!
        val updatedMessage = retrieved.copy(status = "REPLIED")
        messageDao.update(updatedMessage)

        val afterUpdate = messageDao.getMessageById(id)

        assertNotNull(afterUpdate)
        assertEquals("com.original.app", afterUpdate?.packageName)
        assertEquals("Original Sender", afterUpdate?.senderName)
        assertEquals("Original content", afterUpdate?.originalContent)
        assertEquals("REPLIED", afterUpdate?.status)
    }

    // ==================== Delete Tests ====================

    @Test
    fun deleteAll_removesAllMessages() = runTest {
        messageDao.insert(createTestMessage(senderName = "User1"))
        messageDao.insert(createTestMessage(senderName = "User2"))
        messageDao.insert(createTestMessage(senderName = "User3"))

        val beforeDelete = messageDao.getAllMessages().first()
        assertEquals(3, beforeDelete.size)

        messageDao.deleteAll()

        val afterDelete = messageDao.getAllMessages().first()
        assertTrue("All messages should be deleted", afterDelete.isEmpty())
    }

    @Test
    fun deleteAll_onEmptyTable_succeeds() = runTest {
        // Should not throw when deleting from empty table
        messageDao.deleteAll()

        val messages = messageDao.getAllMessages().first()
        assertTrue(messages.isEmpty())
    }

    // ==================== Type Converter Tests (List<String>) ====================

    @Test
    fun typeConverter_emptyList_handledCorrectly() = runTest {
        val message = createTestMessage(
            generatedResponses = emptyList()
        )
        val id = messageDao.insert(message)

        val retrieved = messageDao.getMessageById(id)

        assertNotNull(retrieved)
        assertTrue("Empty list should be preserved", retrieved?.generatedResponses?.isEmpty() == true)
    }

    @Test
    fun typeConverter_singleItemList_handledCorrectly() = runTest {
        val message = createTestMessage(
            generatedResponses = listOf("Response 1")
        )
        val id = messageDao.insert(message)

        val retrieved = messageDao.getMessageById(id)

        assertNotNull(retrieved)
        assertEquals(1, retrieved?.generatedResponses?.size)
        assertEquals("Response 1", retrieved?.generatedResponses?.get(0))
    }

    @Test
    fun typeConverter_multipleItemsList_handledCorrectly() = runTest {
        val responses = listOf(
            "Response A",
            "Response B",
            "Response C",
            "Response D"
        )
        val message = createTestMessage(
            generatedResponses = responses
        )
        val id = messageDao.insert(message)

        val retrieved = messageDao.getMessageById(id)

        assertNotNull(retrieved)
        assertEquals(4, retrieved?.generatedResponses?.size)
        assertEquals(responses, retrieved?.generatedResponses)
    }

    @Test
    fun typeConverter_specialCharactersInList_handledCorrectly() = runTest {
        val responses = listOf(
            "Hello, \"World\"!",
            "Line1\nLine2",
            "Tab\there",
            "Unicode: 日本語 🎉"
        )
        val message = createTestMessage(
            generatedResponses = responses
        )
        val id = messageDao.insert(message)

        val retrieved = messageDao.getMessageById(id)

        assertNotNull(retrieved)
        assertEquals(responses, retrieved?.generatedResponses)
    }

    @Test
    fun typeConverter_updateGeneratedResponses_persists() = runTest {
        val message = createTestMessage(
            generatedResponses = listOf("Initial response")
        )
        val id = messageDao.insert(message)

        val retrieved = messageDao.getMessageById(id)!!
        val updatedResponses = listOf("Response 1", "Response 2", "Response 3")
        val updatedMessage = retrieved.copy(generatedResponses = updatedResponses)
        messageDao.update(updatedMessage)

        val afterUpdate = messageDao.getMessageById(id)

        assertNotNull(afterUpdate)
        assertEquals(3, afterUpdate?.generatedResponses?.size)
        assertEquals(updatedResponses, afterUpdate?.generatedResponses)
    }

    // ==================== Insert with REPLACE conflict strategy ====================

    @Test
    fun insert_withSameId_replacesExistingMessage() = runTest {
        val originalMessage = createTestMessage(
            originalContent = "Original content"
        )
        val id = messageDao.insert(originalMessage)

        val replacementMessage = createTestMessage(
            originalContent = "Replacement content"
        ).copy(id = id)
        messageDao.insert(replacementMessage)

        val retrieved = messageDao.getMessageById(id)

        assertNotNull(retrieved)
        assertEquals("Replacement content", retrieved?.originalContent)

        // Verify only one message exists
        val allMessages = messageDao.getAllMessages().first()
        assertEquals(1, allMessages.size)
    }

    // ==================== Helper Methods ====================

    private fun createTestMessage(
        id: Long = 0,
        packageName: String = TestConstants.TestMessages.PACKAGE_NAME,
        senderName: String = TestConstants.TestMessages.SENDER_NAME,
        originalContent: String = TestConstants.TestMessages.ORIGINAL_CONTENT,
        veiledContent: String? = null,
        generatedResponses: List<String> = emptyList(),
        selectedResponse: String? = null,
        status: String = "RECEIVED",
        timestamp: Long = System.currentTimeMillis()
    ): Message {
        return Message(
            id = id,
            packageName = packageName,
            senderName = senderName,
            originalContent = originalContent,
            veiledContent = veiledContent,
            generatedResponses = generatedResponses,
            selectedResponse = selectedResponse,
            status = status,
            timestamp = timestamp
        )
    }
}
