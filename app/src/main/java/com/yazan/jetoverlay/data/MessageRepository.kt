package com.yazan.jetoverlay.data

import kotlinx.coroutines.flow.Flow

class MessageRepository(private val messageDao: MessageDao) {

    val allMessages: Flow<List<Message>> = messageDao.getAllMessages()

    suspend fun ingestNotification(packageName: String, sender: String, content: String): Long {
        val message = Message(
            packageName = packageName,
            senderName = sender,
            originalContent = content,
            status = "RECEIVED"
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
                status = "SENT"
            )
            messageDao.update(updated)
        }
    }

    suspend fun updateMessageState(
        id: Long,
        status: String,
        veiledContent: String? = null,
        generatedResponses: List<String> = emptyList()
    ) {
        val message = messageDao.getMessageById(id)
        if (message != null) {
            val updated = message.copy(
                status = status,
                veiledContent = veiledContent ?: message.veiledContent,
                generatedResponses = generatedResponses.ifEmpty { message.generatedResponses }
            )
            messageDao.update(updated)
        }
    }
}
