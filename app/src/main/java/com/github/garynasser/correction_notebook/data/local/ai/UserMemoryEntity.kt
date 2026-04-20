package com.github.garynasser.correction_notebook.data.local.ai

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_user_memory",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceSessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["category"]),
        Index(value = ["updatedAt"]),
        Index(value = ["sourceSessionId"])
    ]
)
data class UserMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val category: String,
    val content: String,
    val confidence: Float,
    val sourceSessionId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
