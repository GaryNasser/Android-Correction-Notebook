package com.github.garynasser.correction_notebook.data.local.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AiProviderDao {
    @Query("SELECT * FROM ai_provider ORDER BY isActive DESC, updatedAt DESC, name COLLATE NOCASE ASC")
    fun observeProviders(): Flow<List<AiProviderEntity>>

    @Query("SELECT * FROM ai_provider WHERE isActive = 1 LIMIT 1")
    fun observeActiveProvider(): Flow<AiProviderEntity?>

    @Query("SELECT * FROM ai_provider WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProvider(): AiProviderEntity?

    @Query("SELECT * FROM ai_provider WHERE id = :providerId LIMIT 1")
    suspend fun getProviderById(providerId: Long): AiProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: AiProviderEntity): Long

    @Update
    suspend fun updateProvider(provider: AiProviderEntity)

    @Query("DELETE FROM ai_provider WHERE id = :providerId")
    suspend fun deleteProviderById(providerId: Long)

    @Query("UPDATE ai_provider SET isActive = 0 WHERE isActive = 1")
    suspend fun clearActiveProvider()

    @Query("UPDATE ai_provider SET isActive = 1, updatedAt = :updatedAt WHERE id = :providerId")
    suspend fun setActiveProvider(providerId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM ai_provider")
    suspend fun countProviders(): Int

    @Transaction
    suspend fun activateProvider(providerId: Long, updatedAt: Long = System.currentTimeMillis()) {
        clearActiveProvider()
        setActiveProvider(providerId, updatedAt)
    }
}
