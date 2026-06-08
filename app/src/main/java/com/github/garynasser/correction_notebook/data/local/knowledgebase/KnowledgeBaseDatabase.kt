package com.github.garynasser.correction_notebook.data.local.knowledgebase

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        KnowledgeBaseFolderEntity::class,
        KnowledgeBaseFileEntity::class,
        KnowledgeBaseChunkEntity::class,
        StudySetEntity::class,
        FlashcardEntity::class,
        QuizQuestionEntity::class,
        AiResultCacheEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class KnowledgeBaseDatabase : RoomDatabase() {
    abstract fun knowledgeBaseDao(): KnowledgeBaseDao
}
