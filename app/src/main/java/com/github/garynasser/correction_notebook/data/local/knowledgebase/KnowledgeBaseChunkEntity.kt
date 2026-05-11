package com.github.garynasser.correction_notebook.data.local.knowledgebase

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_chunk",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeBaseFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["fileId"]),
        Index(value = ["updatedAt"])
    ]
)
data class KnowledgeBaseChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fileId: String,
    val chunkIndex: Int,
    val title: String,
    val path: String,
    val content: String,
    val keywords: String,
    val updatedAt: Long
)
