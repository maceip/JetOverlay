package com.yazan.jetoverlay.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MessageRepositoryTest {

    private lateinit var repository: MessageRepository
    private lateinit var fakeDao: FakeMessageDao

    @Before
    fun setup() {
        fakeDao = FakeMessageDao()
        repository = MessageRepository(fakeDao)
    }

    // ==================== getMessagesByBucket Tests ====================

    @Test
    fun `getMessagesByBucket returns flow of messages for specific bucket`() = runBlocking {
        val socialMessages = listOf(
            createMessage(id = 1, bucket = "SOCIAL"),
            createMessage(id = 2, bucket = "SOCIAL")
        )
        fakeDao.messagesForBucket["SOCIAL"] = MutableStateFlow(socialMessages)

        val result = repository.getMessagesByBucket("SOCIAL").first()

        assertEquals(2, result.size)
        assertTrue(result.all { it.bucket == "SOCIAL" })
    }

    @Test
    fun `getMessagesByBucket returns empty flow when no messages in bucket`() = runBlocking {
        fakeDao.messagesForBucket["URGENT"] = MutableStateFlow(emptyList())

        val result = repository.getMessagesByBucket("URGENT").first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMessagesByBucket returns correct bucket for WORK`() = runBlocking {
        val workMessages = listOf(
            createMessage(id = 1, bucket = "WORK"),
            createMessage(id = 2, bucket = "WORK"),
            createMessage(id = 3, bucket = "WORK")
        )
        fakeDao.messagesForBucket["WORK"] = MutableStateFlow(workMessages)

        val result = repository.getMessagesByBucket("WORK").first()

        assertEquals(3, result.size)
        assertTrue(result.all { it.bucket == "WORK" })
    }

    @Test
    fun `getMessagesByBucket returns correct bucket for PROMOTIONAL`() = runBlocking {
        val promoMessages = listOf(
            createMessage(id = 1, bucket = "PROMOTIONAL")
        )
        fakeDao.messagesForBucket["PROMOTIONAL"] = MutableStateFlow(promoMessages)

        val result = repository.getMessagesByBucket("PROMOTIONAL").first()

        assertEquals(1, result.size)
        assertEquals("PROMOTIONAL", result[0].bucket)
    }

    @Test
    fun `getMessagesByBucket returns correct bucket for TRANSACTIONAL`() = runBlocking {
        val transMessages = listOf(
            createMessage(id = 1, bucket = "TRANSACTIONAL"),
            createMessage(id = 2, bucket = "TRANSACTIONAL")
        )
        fakeDao.messagesForBucket["TRANSACTIONAL"] = MutableStateFlow(transMessages)

        val result = repository.getMessagesByBucket("TRANSACTIONAL").first()

        assertEquals(2, result.size)
        assertTrue(result.all { it.bucket == "TRANSACTIONAL" })
    }

    @Test
    fun `getMessagesByBucket returns correct bucket for UNKNOWN`() = runBlocking {
        val unknownMessages = listOf(
            createMessage(id = 1, bucket = "UNKNOWN")
        )
        fakeDao.messagesForBucket["UNKNOWN"] = MutableStateFlow(unknownMessages)

        val result = repository.getMessagesByBucket("UNKNOWN").first()

        assertEquals(1, result.size)
        assertEquals("UNKNOWN", result[0].bucket)
    }

    // ==================== applyBucket Tests ====================

    @Test
    fun `applyBucket calls dao updateBucket with correct parameters`() = runBlocking {
        repository.applyBucket(1L, "URGENT")

        assertEquals(1L, fakeDao.lastUpdatedBucketId)
        assertEquals("URGENT", fakeDao.lastUpdatedBucket)
    }

    @Test
    fun `applyBucket can apply SOCIAL bucket`() = runBlocking {
        repository.applyBucket(5L, "SOCIAL")

        assertEquals(5L, fakeDao.lastUpdatedBucketId)
        assertEquals("SOCIAL", fakeDao.lastUpdatedBucket)
    }

    @Test
    fun `applyBucket can apply WORK bucket`() = runBlocking {
        repository.applyBucket(10L, "WORK")

        assertEquals(10L, fakeDao.lastUpdatedBucketId)
        assertEquals("WORK", fakeDao.lastUpdatedBucket)
    }

    @Test
    fun `applyBucket can apply PROMOTIONAL bucket`() = runBlocking {
        repository.applyBucket(15L, "PROMOTIONAL")

        assertEquals(15L, fakeDao.lastUpdatedBucketId)
        assertEquals("PROMOTIONAL", fakeDao.lastUpdatedBucket)
    }

    @Test
    fun `applyBucket can apply TRANSACTIONAL bucket`() = runBlocking {
        repository.applyBucket(20L, "TRANSACTIONAL")

        assertEquals(20L, fakeDao.lastUpdatedBucketId)
        assertEquals("TRANSACTIONAL", fakeDao.lastUpdatedBucket)
    }

    @Test
    fun `applyBucket can apply UNKNOWN bucket`() = runBlocking {
        repository.applyBucket(25L, "UNKNOWN")

        assertEquals(25L, fakeDao.lastUpdatedBucketId)
        assertEquals("UNKNOWN", fakeDao.lastUpdatedBucket)
    }

    @Test
    fun `applyBucket handles id of 0`() = runBlocking {
        repository.applyBucket(0L, "URGENT")

        assertEquals(0L, fakeDao.lastUpdatedBucketId)
        assertEquals("URGENT", fakeDao.lastUpdatedBucket)
    }

    @Test
    fun `applyBucket handles large id`() = runBlocking {
        repository.applyBucket(Long.MAX_VALUE, "SOCIAL")

        assertEquals(Long.MAX_VALUE, fakeDao.lastUpdatedBucketId)
        assertEquals("SOCIAL", fakeDao.lastUpdatedBucket)
    }

    // ==================== Flow Emission Tests ====================

    @Test
    fun `getMessagesByBucket emits updates when data changes`() = runBlocking {
        val stateFlow = MutableStateFlow(listOf(createMessage(id = 1, bucket = "SOCIAL")))
        fakeDao.messagesForBucket["SOCIAL"] = stateFlow

        val initialResult = repository.getMessagesByBucket("SOCIAL").first()
        assertEquals(1, initialResult.size)

        // Update the flow
        stateFlow.value = listOf(
            createMessage(id = 1, bucket = "SOCIAL"),
            createMessage(id = 2, bucket = "SOCIAL")
        )

        val updatedResult = repository.getMessagesByBucket("SOCIAL").first()
        assertEquals(2, updatedResult.size)
    }

    @Test
    fun `allMessages returns flow from dao`() = runBlocking {
        // Create a new dao with pre-populated messages
        val messages = listOf(
            createMessage(id = 1, bucket = "SOCIAL"),
            createMessage(id = 2, bucket = "WORK")
        )
        val testDao = FakeMessageDao()
        testDao.allMessagesFlow.value = messages
        val testRepository = MessageRepository(testDao)

        val result = testRepository.allMessages.first()

        assertEquals(2, result.size)
    }

    // ==================== Helper ====================

    private fun createMessage(
        id: Long = 1L,
        bucket: String = "UNKNOWN",
        content: String = "Test content",
        sender: String = "Test Sender"
    ): Message {
        return Message(
            id = id,
            packageName = "com.test.app",
            senderName = sender,
            originalContent = content,
            bucket = bucket,
            status = "RECEIVED"
        )
    }

    // ==================== Fake DAO ====================

    private class FakeMessageDao : MessageDao {
        var allMessagesFlow: MutableStateFlow<List<Message>> = MutableStateFlow(emptyList())
        val messagesForBucket = mutableMapOf<String, MutableStateFlow<List<Message>>>()
        var lastUpdatedBucketId: Long? = null
        var lastUpdatedBucket: String? = null
        var messages = mutableMapOf<Long, Message>()

        override fun getAllMessages(): Flow<List<Message>> = allMessagesFlow

        override suspend fun getMessageById(id: Long): Message? = messages[id]

        override fun getMessagesByBucket(bucket: String): Flow<List<Message>> {
            return messagesForBucket[bucket] ?: flowOf(emptyList())
        }

        override suspend fun updateBucket(id: Long, bucket: String) {
            lastUpdatedBucketId = id
            lastUpdatedBucket = bucket
        }

        override suspend fun insert(message: Message): Long {
            val id = message.id.takeIf { it > 0 } ?: (messages.size + 1L)
            val insertedMessage = message.copy(id = id)
            messages[id] = insertedMessage
            return id
        }

        override suspend fun update(message: Message) {
            messages[message.id] = message
        }

        override suspend fun deleteAll() {
            messages.clear()
        }
    }
}
