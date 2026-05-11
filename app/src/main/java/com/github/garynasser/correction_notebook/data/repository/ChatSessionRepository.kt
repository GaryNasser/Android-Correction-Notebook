package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.ai.ChatMessageDao
import com.github.garynasser.correction_notebook.data.local.ai.ChatMessageEntity
import com.github.garynasser.correction_notebook.data.local.ai.ChatSessionDao
import com.github.garynasser.correction_notebook.data.local.ai.ChatSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatSessionRepository @Inject constructor(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao
) {
    fun observeSessionsForProvider(providerId: Long): Flow<List<ChatSessionEntity>> =
        sessionDao.observeSessionsForProvider(providerId)

    fun observeAllSessions(): Flow<List<ChatSessionEntity>> =
        sessionDao.observeAllSessions()

    fun observeMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>> =
        messageDao.observeMessagesForSession(sessionId)

    suspend fun getLatestSessionForProvider(providerId: Long): ChatSessionEntity? =
        sessionDao.getLatestSessionForProvider(providerId)

    suspend fun createSession(
        title: String,
        providerId: Long,
        model: String
    ): Long {
        return sessionDao.insertSession(
            ChatSessionEntity(
                title = title,
                providerId = providerId,
                model = model
            )
        )
    }

    suspend fun saveMessage(
        sessionId: Long,
        role: String,
        content: String
    ): Long {
        val id = messageDao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = role,
                content = content
            )
        )
        sessionDao.touchSession(sessionId)
        return id
    }

    suspend fun getRecentMessages(sessionId: Long, limit: Int): List<ChatMessageEntity> =
        messageDao.getRecentMessagesForSession(sessionId, limit)

    suspend fun clearSessionMessages(sessionId: Long) {
        messageDao.deleteMessagesForSession(sessionId)
        sessionDao.touchSession(sessionId)
    }

    suspend fun renameSession(sessionId: Long, title: String) {
        sessionDao.renameSession(sessionId, title.trim().ifBlank { "新的对话" })
    }

    suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteSessionById(sessionId)
    }
}
