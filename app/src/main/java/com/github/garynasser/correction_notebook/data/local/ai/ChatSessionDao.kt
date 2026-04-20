package com.github.garynasser.correction_notebook.data.local.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query(
        """
        SELECT * FROM ai_chat_session
        WHERE providerId = :providerId
        ORDER BY updatedAt DESC
        """
    )
    fun observeSessionsForProvider(providerId: Long): Flow<List<ChatSessionEntity>>

    @Query(
        """
        SELECT * FROM ai_chat_session
        WHERE providerId = :providerId
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun getLatestSessionForProvider(providerId: Long): ChatSessionEntity?

    @Query("SELECT * FROM ai_chat_session WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("DELETE FROM ai_chat_session WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)
}
