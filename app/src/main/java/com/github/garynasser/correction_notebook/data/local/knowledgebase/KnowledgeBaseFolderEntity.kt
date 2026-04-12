package com.github.garynasser.correction_notebook.data.local.knowledgebase

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_folder",
    indices = [Index(value = ["parentId"])]
)
data class KnowledgeBaseFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?,
    val createdAt: Long,
    val updatedAt: Long
)
