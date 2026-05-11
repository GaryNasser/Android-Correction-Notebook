package com.github.garynasser.correction_notebook.data.local.ai

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AiProviderEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        UserMemoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(AiTypeConverters::class)
abstract class AiDatabase : RoomDatabase() {
    abstract fun aiProviderDao(): AiProviderDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userMemoryDao(): UserMemoryDao
}
