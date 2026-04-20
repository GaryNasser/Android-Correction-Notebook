package com.github.garynasser.correction_notebook.data.local.ai

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType

@Entity(
    tableName = "ai_provider",
    indices = [
        Index(value = ["isActive"]),
        Index(value = ["type"])
    ]
)
data class AiProviderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: AIProviderType,
    val baseUrl: String,
    val apiKeyEncrypted: String,
    val defaultModel: String,
    val customHeadersJson: String = "{}",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
