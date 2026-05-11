package com.github.garynasser.correction_notebook.data.local.knowledgebase

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class FolderFileCountRow(
    val folderId: String?,
    val count: Int
)

@Dao
interface KnowledgeBaseDao {
    @Query(
        """
        SELECT * FROM kb_folder
        WHERE ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId)
          AND (:query = '' OR name LIKE '%' || :query || '%')
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun observeFolders(parentId: String?, query: String): Flow<List<KnowledgeBaseFolderEntity>>

    @Query(
        """
        SELECT * FROM kb_file
        WHERE ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId)
          AND (:query = '' OR displayName LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC, displayName COLLATE NOCASE ASC
        """
    )
    fun observeFiles(folderId: String?, query: String): Flow<List<KnowledgeBaseFileEntity>>

    @Query("SELECT * FROM kb_folder ORDER BY name COLLATE NOCASE ASC")
    fun observeAllFolders(): Flow<List<KnowledgeBaseFolderEntity>>

    @Query("SELECT * FROM kb_file ORDER BY downloadedAt DESC, updatedAt DESC LIMIT :limit")
    fun observeRecentFiles(limit: Int): Flow<List<KnowledgeBaseFileEntity>>

    @Query("SELECT folderId, COUNT(*) AS count FROM kb_file GROUP BY folderId")
    fun observeFolderFileCounts(): Flow<List<FolderFileCountRow>>

    @Query("SELECT * FROM kb_folder ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllFolders(): List<KnowledgeBaseFolderEntity>

    @Query("SELECT * FROM kb_folder WHERE id = :folderId LIMIT 1")
    suspend fun getFolderById(folderId: String): KnowledgeBaseFolderEntity?

    @Query("SELECT * FROM kb_file WHERE id = :fileId LIMIT 1")
    suspend fun getFileById(fileId: String): KnowledgeBaseFileEntity?

    @Query("SELECT * FROM kb_file ORDER BY updatedAt DESC, displayName COLLATE NOCASE ASC")
    suspend fun getAllFiles(): List<KnowledgeBaseFileEntity>

    @Query(
        """
        SELECT * FROM kb_file
        WHERE ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId)
        ORDER BY updatedAt DESC, displayName COLLATE NOCASE ASC
        """
    )
    suspend fun getFilesForFolder(folderId: String?): List<KnowledgeBaseFileEntity>

    @Query(
        """
        SELECT * FROM kb_file
        WHERE ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId)
        """
    )
    suspend fun getFilesByFolder(folderId: String?): List<KnowledgeBaseFileEntity>

    @Query(
        """
        SELECT COUNT(*) FROM kb_folder
        WHERE ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId)
        """
    )
    suspend fun countFoldersByParent(parentId: String?): Int

    @Query(
        """
        SELECT COUNT(*) FROM kb_file
        WHERE ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId)
        """
    )
    suspend fun countFilesByFolder(folderId: String?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: KnowledgeBaseFolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: KnowledgeBaseFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<KnowledgeBaseChunkEntity>)

    @Update
    suspend fun updateFolder(folder: KnowledgeBaseFolderEntity)

    @Update
    suspend fun updateFile(file: KnowledgeBaseFileEntity)

    @Delete
    suspend fun deleteFolder(folder: KnowledgeBaseFolderEntity)

    @Delete
    suspend fun deleteFile(file: KnowledgeBaseFileEntity)

    @Query("DELETE FROM kb_chunk WHERE fileId = :fileId")
    suspend fun deleteChunksForFile(fileId: String)

    @Query("SELECT * FROM kb_chunk WHERE fileId = :fileId ORDER BY chunkIndex ASC")
    suspend fun getChunksForFile(fileId: String): List<KnowledgeBaseChunkEntity>

    @Query(
        """
        SELECT * FROM kb_chunk
        WHERE (:folderIdFilter = 0 OR fileId IN (
            SELECT id FROM kb_file
            WHERE ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId)
        ))
          AND (
            content LIKE '%' || :query || '%'
            OR keywords LIKE '%' || :query || '%'
            OR title LIKE '%' || :query || '%'
          )
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchChunks(
        query: String,
        folderId: String?,
        folderIdFilter: Boolean,
        limit: Int
    ): List<KnowledgeBaseChunkEntity>
}
