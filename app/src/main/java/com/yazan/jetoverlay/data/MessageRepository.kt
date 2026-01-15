package com.yazan.jetoverlay.data

import kotlinx.coroutines.flow.Flow

class MessageRepository(private val messageDao: MessageDao) {

    val allMessages: Flow<List<Message>> = messageDao.getAllMessages()

    fun getMessagesByBucket(bucket: String): Flow<List<Message>> = messageDao.getMessagesByBucket(bucket)

    suspend fun applyBucket(messageId: Long, bucket: String) {
        messageDao.updateBucket(messageId, bucket)
    }

    suspend fun ingestNotification(
        packageName: String,
        sender: String,
        content: String,
        contextTag: String? = null,
        threadKey: String? = null
    ): Long {
        val message = Message(
            packageName = packageName,
            senderName = sender,
            originalContent = content,
            status = "RECEIVED",
            contextTag = contextTag,
            threadKey = threadKey,
            userInteracted = false
        )
        return messageDao.insert(message)
    }

    suspend fun applyVeil(id: Long, veiledContent: String) {
        val message = messageDao.getMessageById(id)
        if (message != null) {
            val updated = message.copy(
                veiledContent = veiledContent,
                status = "VEILED"
            )
            messageDao.update(updated)
        }
    }

    suspend fun addGeneratedResponses(id: Long, responses: List<String>) {
        val message = messageDao.getMessageById(id)
        if (message != null) {
            val updated = message.copy(
                generatedResponses = responses,
                status = "GENERATED"
            )
            messageDao.update(updated)
        }
    }

    suspend fun queueForSending(id: Long, selectedResponse: String) {
        val message = messageDao.getMessageById(id)
        if (message != null) {
            // Here you could also apply edits if the user edited the response
            val updated = message.copy(
                selectedResponse = selectedResponse,
                status = "QUEUED"
            )
            messageDao.update(updated)
        }
    }

    suspend fun markAsSent(id: Long) {
        val message = messageDao.getMessageById(id)
        if (message != null) {
            val updated = message.copy(
                status = "SENT",
                snoozedUntil = 0L
            )
            messageDao.update(updated)
        }
    }

    suspend fun dismiss(id: Long) {
        val message = messageDao.getMessageById(id)
        if (message != null) {
            val updated = message.copy(
                status = "DISMISSED",
                snoozedUntil = 0L
            )
            messageDao.update(updated)
        }
    }

    suspend fun updateMessageState(
        id: Long,
        status: String,
        veiledContent: String? = null,
        generatedResponses: List<String> = emptyList(),
        bucket: String? = null,
        snoozedUntil: Long? = null,
        retryCount: Int? = null,
        userInteracted: Boolean? = null
    ) {
        val message = messageDao.getMessageById(id)
        if (message != null) {
            val updated = message.copy(
                status = status,
                veiledContent = veiledContent ?: message.veiledContent,
                generatedResponses = generatedResponses.ifEmpty { message.generatedResponses },
                bucket = bucket ?: message.bucket,
                snoozedUntil = snoozedUntil ?: message.snoozedUntil,
                retryCount = retryCount ?: message.retryCount,
                userInteracted = userInteracted ?: message.userInteracted
            )
            messageDao.update(updated)
        }
    }

    suspend fun getMessage(id: Long): Message? {
        return messageDao.getMessageById(id)
    }

    suspend fun snoozeMessage(id: Long, until: Long) {
        updateMessageState(
            id = id,
            status = "SNOOZED",
            snoozedUntil = until
        )
    }

    suspend fun markRetry(id: Long, nextAttemptAt: Long) {
        val message = messageDao.getMessageById(id) ?: return
        updateMessageState(
            id = id,
            status = "RETRY",
            snoozedUntil = nextAttemptAt,
            retryCount = message.retryCount + 1
        )
    }

    suspend fun markUserInteracted(id: Long) {
        val message = messageDao.getMessageById(id) ?: return
        messageDao.update(message.copy(userInteracted = true))
    }
}
