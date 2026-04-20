package com.github.garynasser.correction_notebook.data.local.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserMemoryDao {
    @Query("SELECT * FROM ai_user_memory ORDER BY updatedAt DESC, id DESC")
    fun observeMemories(): Flow<List<UserMemoryEntity>>

    @Query(
        """
        SELECT * FROM ai_user_memory
        WHERE category = :category
        ORDER BY updatedAt DESC, id DESC
        """
    )
    suspend fun getMemoriesByCategory(category: String): List<UserMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: UserMemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: UserMemoryEntity)

    @Query("DELETE FROM ai_user_memory WHERE id = :memoryId")
    suspend fun deleteMemoryById(memoryId: Long)

    @Query("DELETE FROM ai_user_memory")
    suspend fun clearAllMemories()
}
