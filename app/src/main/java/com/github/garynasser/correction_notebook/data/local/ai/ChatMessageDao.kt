package com.github.garynasser.correction_notebook.data.local.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query(
        """
        SELECT * FROM ai_chat_message
        WHERE sessionId = :sessionId
        ORDER BY createdAt ASC, id ASC
        """
    )
    fun observeMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT * FROM ai_chat_message
        WHERE sessionId = :sessionId
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentMessagesForSession(sessionId: Long, limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM ai_chat_message WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)
}
