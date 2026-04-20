package com.github.garynasser.correction_notebook.data.local.ai

import androidx.room.TypeConverter
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType

class AiTypeConverters {
    @TypeConverter
    fun toProviderType(value: String): AIProviderType = AIProviderType.valueOf(value)

    @TypeConverter
    fun fromProviderType(value: AIProviderType): String = value.name
}
