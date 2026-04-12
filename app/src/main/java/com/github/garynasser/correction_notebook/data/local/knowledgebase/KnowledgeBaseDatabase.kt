package com.github.garynasser.correction_notebook.data.local.knowledgebase

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [KnowledgeBaseFolderEntity::class, KnowledgeBaseFileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KnowledgeBaseDatabase : RoomDatabase() {
    abstract fun knowledgeBaseDao(): KnowledgeBaseDao
}
