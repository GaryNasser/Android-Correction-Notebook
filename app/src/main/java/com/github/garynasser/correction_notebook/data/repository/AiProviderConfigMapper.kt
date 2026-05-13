package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderConfig
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.google.gson.Gson
import com.google.gson.JsonObject

object AiProviderConfigMapper {
    fun normalizeBaseUrl(raw: String, type: AIProviderType): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isNotBlank()) return trimmed
        return when (type) {
            AIProviderType.OPENAI_COMPATIBLE -> AISettingsManager.DEFAULT_API_URL
            AIProviderType.ANTHROPIC_COMPATIBLE -> "https://api.anthropic.com/v1"
        }
    }

    fun normalizeHeadersJson(raw: String, gson: Gson): String {
        val parsed = parseHeaders(raw, gson)
        if (parsed.isEmpty()) return "{}"
        return gson.toJson(parsed.toSortedMap())
    }

    fun parseHeaders(raw: String, gson: Gson): Map<String, String> {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "{}") return emptyMap()

        val jsonHeaders = runCatching {
            val json = gson.fromJson(trimmed, JsonObject::class.java)
            if (json == null || !json.isJsonObject) {
                emptyMap()
            } else {
                json.entrySet().mapNotNull { entry ->
                    entry.value.takeIf { it.isJsonPrimitive }?.asString?.let { value ->
                        entry.key.trim().takeIf { it.isNotBlank() }?.let { key -> key to value.trim() }
                    }
                }.toMap()
            }
        }.getOrDefault(emptyMap())

        if (jsonHeaders.isNotEmpty()) return jsonHeaders

        return trimmed.lineSequence()
            .mapNotNull { line ->
                val separator = when {
                    ":" in line -> ":"
                    "=" in line -> "="
                    else -> return@mapNotNull null
                }
                val parts = line.split(separator, limit = 2)
                val key = parts.getOrNull(0)?.trim().orEmpty()
                val value = parts.getOrNull(1)?.trim().orEmpty()
                if (key.isNotBlank()) key to value else null
            }
            .toMap()
    }

    fun toConfig(form: AiProviderForm, gson: Gson): AIProviderConfig {
        return AIProviderConfig(
            id = form.id.takeIf { it > 0 },
            name = form.name.trim().ifBlank { "AI Provider" },
            type = form.type,
            baseUrl = normalizeBaseUrl(form.baseUrl, form.type),
            apiKey = form.apiKey.trim(),
            defaultModel = form.model.trim(),
            customHeaders = parseHeaders(form.customHeaders, gson),
            temperature = form.temperature.toDoubleOrNull()?.coerceIn(0.0, 2.0),
            maxTokens = form.maxTokens.toIntOrNull()?.coerceAtLeast(1),
            contextMessageLimit = form.contextMessageLimit.toIntOrNull()?.coerceIn(1, 60) ?: 12
        )
    }

    fun toRecord(form: AiProviderForm, gson: Gson): ProviderRecord {
        val config = toConfig(form, gson)
        return ProviderRecord(
            id = form.id,
            name = config.name,
            type = config.type,
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            defaultModel = config.defaultModel.ifBlank { AISettingsManager.DEFAULT_MODEL },
            customHeadersJson = normalizeHeadersJson(form.customHeaders, gson),
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            contextMessageLimit = config.contextMessageLimit,
            isActive = form.isActive,
            createdAt = 0L,
            updatedAt = 0L
        )
    }
}
