package com.github.garynasser.correction_notebook.data.local.knowledgebase

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_file",
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["sourceFileId"]),
        Index(value = ["courseId"])
    ]
)
data class KnowledgeBaseFileEntity(
    @PrimaryKey val id: String,
    val folderId: String?,
    val displayName: String,
    val storedName: String,
    val localPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sourceType: String,
    val sourceFileId: String?,
    val sourceTitle: String?,
    val sourcePath: String?,
    val courseId: Int?,
    val courseName: String?,
    val tags: String,
    val downloadedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
