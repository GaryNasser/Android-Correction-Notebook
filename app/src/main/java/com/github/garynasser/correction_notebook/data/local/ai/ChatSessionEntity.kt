package com.github.garynasser.correction_notebook.data.local.ai

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_chat_session",
    foreignKeys = [
        ForeignKey(
            entity = AiProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["updatedAt"])
    ]
)
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val providerId: Long,
    val model: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
