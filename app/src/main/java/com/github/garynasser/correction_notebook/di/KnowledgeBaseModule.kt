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
            .addMigrations(MIGRATION_1_2)
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
}
