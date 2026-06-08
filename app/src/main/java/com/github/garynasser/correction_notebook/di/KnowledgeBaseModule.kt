package com.github.garynasser.correction_notebook.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDao
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.repository.BitShareRepository
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBaseRepository
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import com.github.garynasser.correction_notebook.utils.BitShareNetworkDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object KnowledgeBaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val nowExpression = "strftime('%s','now') * 1000"
            val hasFolderTable = db.hasTable("kb_folder")
            val hasFileTable = db.hasTable("kb_file")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `kb_folder_new` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `parentId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            if (hasFolderTable) {
                val folderCreatedAt = if (db.hasColumn("kb_folder", "createdAt")) "`createdAt`" else nowExpression
                val folderUpdatedAt = when {
                    db.hasColumn("kb_folder", "updatedAt") -> "`updatedAt`"
                    db.hasColumn("kb_folder", "createdAt") -> "`createdAt`"
                    else -> nowExpression
                }
                db.execSQL(
                    """
                    INSERT INTO `kb_folder_new` (`id`, `name`, `parentId`, `createdAt`, `updatedAt`)
                    SELECT
                        `id`,
                        `name`,
                        `parentId`,
                        COALESCE($folderCreatedAt, $nowExpression),
                        COALESCE($folderUpdatedAt, COALESCE($folderCreatedAt, $nowExpression))
                    FROM `kb_folder`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `kb_folder`")
            }
            db.execSQL("ALTER TABLE `kb_folder_new` RENAME TO `kb_folder`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_kb_folder_parentId` ON `kb_folder` (`parentId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `kb_file_new` (
                    `id` TEXT NOT NULL,
                    `folderId` TEXT,
                    `displayName` TEXT NOT NULL,
                    `storedName` TEXT NOT NULL,
                    `localPath` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `sourceType` TEXT NOT NULL,
                    `sourceFileId` TEXT,
                    `sourceTitle` TEXT,
                    `sourcePath` TEXT,
                    `downloadedAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            if (hasFileTable) {
                val storedName = if (db.hasColumn("kb_file", "storedName")) "`storedName`" else "`displayName`"
                val mimeType = if (db.hasColumn("kb_file", "mimeType")) "`mimeType`" else "'application/octet-stream'"
                val sizeBytes = if (db.hasColumn("kb_file", "sizeBytes")) "`sizeBytes`" else "0"
                val sourceType = if (db.hasColumn("kb_file", "sourceType")) "`sourceType`" else "'local'"
                val sourceFileId = if (db.hasColumn("kb_file", "sourceFileId")) "`sourceFileId`" else "NULL"
                val sourceTitle = if (db.hasColumn("kb_file", "sourceTitle")) "`sourceTitle`" else "NULL"
                val sourcePath = if (db.hasColumn("kb_file", "sourcePath")) "`sourcePath`" else "NULL"
                val downloadedAt = if (db.hasColumn("kb_file", "downloadedAt")) "`downloadedAt`" else "NULL"
                val createdAt = when {
                    db.hasColumn("kb_file", "createdAt") -> "`createdAt`"
                    db.hasColumn("kb_file", "downloadedAt") -> "`downloadedAt`"
                    else -> nowExpression
                }
                val updatedAt = when {
                    db.hasColumn("kb_file", "updatedAt") -> "`updatedAt`"
                    db.hasColumn("kb_file", "createdAt") -> "`createdAt`"
                    db.hasColumn("kb_file", "downloadedAt") -> "`downloadedAt`"
                    else -> nowExpression
                }
                db.execSQL(
                    """
                    INSERT INTO `kb_file_new` (
                        `id`, `folderId`, `displayName`, `storedName`, `localPath`, `mimeType`,
                        `sizeBytes`, `sourceType`, `sourceFileId`, `sourceTitle`, `sourcePath`,
                        `downloadedAt`, `createdAt`, `updatedAt`
                    )
                    SELECT
                        `id`,
                        `folderId`,
                        `displayName`,
                        COALESCE($storedName, `displayName`),
                        `localPath`,
                        COALESCE($mimeType, 'application/octet-stream'),
                        COALESCE($sizeBytes, 0),
                        COALESCE($sourceType, 'local'),
                        $sourceFileId,
                        $sourceTitle,
                        $sourcePath,
                        $downloadedAt,
                        COALESCE($createdAt, COALESCE($downloadedAt, $nowExpression)),
                        COALESCE($updatedAt, COALESCE($createdAt, $downloadedAt, $nowExpression))
                    FROM `kb_file`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `kb_file`")
            }
            db.execSQL("ALTER TABLE `kb_file_new` RENAME TO `kb_file`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_kb_file_folderId` ON `kb_file` (`folderId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_kb_file_sourceFileId` ON `kb_file` (`sourceFileId`)")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `kb_chunk` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `fileId` TEXT NOT NULL,
                    `chunkIndex` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `path` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `keywords` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`fileId`) REFERENCES `kb_file`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_kb_chunk_fileId` ON `kb_chunk` (`fileId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_kb_chunk_updatedAt` ON `kb_chunk` (`updatedAt`)")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!db.hasColumn("kb_file", "courseId")) {
                db.execSQL("ALTER TABLE `kb_file` ADD COLUMN `courseId` INTEGER")
            }
            if (!db.hasColumn("kb_file", "courseName")) {
                db.execSQL("ALTER TABLE `kb_file` ADD COLUMN `courseName` TEXT")
            }
            if (!db.hasColumn("kb_file", "tags")) {
                db.execSQL("ALTER TABLE `kb_file` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_kb_file_courseId` ON `kb_file` (`courseId`)")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `study_set` (
                    `id` TEXT NOT NULL,
                    `courseId` INTEGER,
                    `courseName` TEXT,
                    `title` TEXT NOT NULL,
                    `sourceType` TEXT NOT NULL,
                    `sourceRefId` TEXT,
                    `createdByAi` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_set_courseId` ON `study_set` (`courseId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_set_sourceType_sourceRefId` ON `study_set` (`sourceType`, `sourceRefId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `flashcard` (
                    `id` TEXT NOT NULL,
                    `studySetId` TEXT NOT NULL,
                    `front` TEXT NOT NULL,
                    `back` TEXT NOT NULL,
                    `hint` TEXT NOT NULL,
                    `difficulty` TEXT NOT NULL,
                    `nextReviewAt` INTEGER NOT NULL,
                    `lastReviewedAt` INTEGER,
                    `reviewCount` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`studySetId`) REFERENCES `study_set`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcard_studySetId` ON `flashcard` (`studySetId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcard_nextReviewAt` ON `flashcard` (`nextReviewAt`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `quiz_question` (
                    `id` TEXT NOT NULL,
                    `studySetId` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `question` TEXT NOT NULL,
                    `options` TEXT NOT NULL,
                    `answer` TEXT NOT NULL,
                    `explanation` TEXT NOT NULL,
                    `sourceChunkId` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`studySetId`) REFERENCES `study_set`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_question_studySetId` ON `quiz_question` (`studySetId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_question_sourceChunkId` ON `quiz_question` (`sourceChunkId`)")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createAiResultCacheTable(db)
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addFlashcardColumn(db, "type", "TEXT NOT NULL DEFAULT 'QA_FLASHCARD'")
            addFlashcardColumn(db, "title", "TEXT NOT NULL DEFAULT ''")
            addFlashcardColumn(db, "explanation", "TEXT NOT NULL DEFAULT ''")
            addFlashcardColumn(db, "example", "TEXT NOT NULL DEFAULT ''")
            addFlashcardColumn(db, "pitfall", "TEXT NOT NULL DEFAULT ''")
            addFlashcardColumn(db, "formula", "TEXT NOT NULL DEFAULT ''")
            addFlashcardColumn(db, "tags", "TEXT NOT NULL DEFAULT ''")
            addFlashcardColumn(db, "sourceLocation", "TEXT NOT NULL DEFAULT ''")
            addFlashcardColumn(db, "sourceQuote", "TEXT NOT NULL DEFAULT ''")
            addFlashcardColumn(db, "confidence", "REAL NOT NULL DEFAULT 0.7")
            addFlashcardColumn(db, "createdByAi", "INTEGER NOT NULL DEFAULT 1")
            addFlashcardColumn(db, "editedByUser", "INTEGER NOT NULL DEFAULT 0")
            addFlashcardColumn(db, "createdAt", "INTEGER NOT NULL DEFAULT 0")
            addFlashcardColumn(db, "updatedAt", "INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `flashcard` SET `title` = `front` WHERE `title` = ''")
            val nowExpression = "strftime('%s','now') * 1000"
            db.execSQL("UPDATE `flashcard` SET `createdAt` = $nowExpression WHERE `createdAt` = 0")
            db.execSQL("UPDATE `flashcard` SET `updatedAt` = $nowExpression WHERE `updatedAt` = 0")
        }
    }

    @Provides
    @Singleton
    fun provideKnowledgeBaseDatabase(
        @ApplicationContext context: Context
    ): KnowledgeBaseDatabase {
        return Room.databaseBuilder(
            context,
            KnowledgeBaseDatabase::class.java,
            "knowledge_base.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    @Singleton
    fun provideKnowledgeBaseDao(
        database: KnowledgeBaseDatabase
    ): KnowledgeBaseDao = database.knowledgeBaseDao()

    @Provides
    @Singleton
    fun provideKnowledgeBaseRepository(
        @ApplicationContext context: Context,
        dao: KnowledgeBaseDao,
        storage: com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
    ): KnowledgeBaseRepository {
        return KnowledgeBaseRepository(dao, storage, context)
    }

    @Provides
    @Singleton
    fun provideBitShareRepository(
        apiService: BitShareApiService,
        @BasicRetrofit okHttpClient: OkHttpClient,
        networkDetector: BitShareNetworkDetector
    ): BitShareRepository {
        return BitShareRepository(apiService, okHttpClient, networkDetector)
    }

    private fun SupportSQLiteDatabase.hasTable(tableName: String): Boolean {
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$tableName'").use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun SupportSQLiteDatabase.hasColumn(tableName: String, columnName: String): Boolean {
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) {
                    return true
                }
            }
        }
        return false
    }

    private fun createAiResultCacheTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ai_result_cache` (
                `id` TEXT NOT NULL,
                `fileId` TEXT NOT NULL,
                `mode` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_result_cache_fileId_mode` ON `ai_result_cache` (`fileId`, `mode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_result_cache_updatedAt` ON `ai_result_cache` (`updatedAt`)")
    }

    private fun addFlashcardColumn(db: SupportSQLiteDatabase, columnName: String, definition: String) {
        if (!db.hasColumn("flashcard", columnName)) {
            db.execSQL("ALTER TABLE `flashcard` ADD COLUMN `$columnName` $definition")
        }
    }
}
