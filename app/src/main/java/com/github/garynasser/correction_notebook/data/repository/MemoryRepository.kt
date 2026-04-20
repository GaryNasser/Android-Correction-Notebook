package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.ai.UserMemoryDao
import com.github.garynasser.correction_notebook.data.local.ai.UserMemoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val userMemoryDao: UserMemoryDao
) {
    fun observeMemories(): Flow<List<UserMemoryEntity>> = userMemoryDao.observeMemories()

    suspend fun getMemoriesByCategory(category: String): List<UserMemoryEntity> =
        userMemoryDao.getMemoriesByCategory(category)

    suspend fun saveMemory(memory: UserMemoryEntity): Long = userMemoryDao.insertMemory(memory)

    suspend fun updateMemory(memory: UserMemoryEntity) = userMemoryDao.updateMemory(memory)

    suspend fun deleteMemory(memoryId: Long) = userMemoryDao.deleteMemoryById(memoryId)

    suspend fun clearAll() = userMemoryDao.clearAllMemories()
}
