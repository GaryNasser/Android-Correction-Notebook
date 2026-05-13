package com.github.garynasser.correction_notebook.data.remote.ai

import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

internal object AiResponseParser {
    fun requireJsonObject(body: String, protocolName: String): JsonObject {
        val trimmed = body.trim()
        if (trimmed.isBlank()) {
            throw IllegalStateException("$protocolName 接口返回了空响应，请检查 Base URL、模型名和 API Key")
        }

        if (!trimmed.startsWith("{")) {
            if (looksLikeHtml(trimmed)) {
                throw IllegalStateException(
                    "$protocolName 接口返回了网页 HTML，不是 API JSON。请把 Base URL 改为服务商的 API 地址：官方 Anthropic 应为 https://api.anthropic.com/v1；如果你用的是 OpenRouter、DeepSeek、Qwen、Kimi、Ollama 等 OpenAI 兼容服务，请把协议类型改为“OpenAI 兼容”。响应片段：${preview(trimmed)}"
                )
            }
            throw IllegalStateException(
                "$protocolName 接口返回了非 JSON 对象响应，通常是 Base URL 填成网页地址、协议类型选错、代理服务异常或服务商返回了纯文本。响应片段：${preview(trimmed)}"
            )
        }

        return try {
            val element = JsonParser.parseString(trimmed)
            if (!element.isJsonObject) {
                throw IllegalStateException(
                    "$protocolName 接口返回的 JSON 不是对象，无法按当前协议解析。响应片段：${preview(trimmed)}"
                )
            }
            element.asJsonObject
        } catch (exception: JsonSyntaxException) {
            throw IllegalStateException(
                "$protocolName 接口返回了无效 JSON，请检查服务商兼容接口配置。响应片段：${preview(trimmed)}"
            )
        }
    }

    fun preview(body: String, limit: Int = 180): String {
        return body
            .replace(Regex("\\s+"), " ")
            .take(limit)
            .ifBlank { "<空>" }
    }

    private fun looksLikeHtml(body: String): Boolean {
        val normalized = body.lowercase()
        return normalized.startsWith("<!doctype html") ||
            normalized.startsWith("<html") ||
            "<head" in normalized ||
            "_next" in normalized
    }

    fun readTextContent(element: JsonElement?): String {
        if (element == null || element.isJsonNull) return ""
        return when {
            element.isJsonPrimitive -> element.asString
            element.isJsonArray -> element.asJsonArray
                .mapNotNull { item ->
                    when {
                        item.isJsonPrimitive -> item.asString
                        item.isJsonObject -> {
                            val obj = item.asJsonObject
                            obj.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                                ?: obj.get("content")?.takeIf { it.isJsonPrimitive }?.asString
                        }
                        else -> null
                    }
                }
                .joinToString("\n")
            element.isJsonObject -> {
                val obj = element.asJsonObject
                obj.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: obj.get("content")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: ""
            }
            else -> ""
        }.trim()
    }

    fun extractModelIds(body: String, protocolName: String): List<String> {
        val root = requireJsonObject(body, protocolName)
        val data = root.get("data")
        val candidates = when {
            data?.isJsonArray == true -> data.asJsonArray.toList()
            root.get("models")?.isJsonArray == true -> root.getAsJsonArray("models").toList()
            else -> emptyList()
        }
        return candidates.mapNotNull { item ->
            when {
                item.isJsonPrimitive -> item.asString
                item.isJsonObject -> {
                    val obj = item.asJsonObject
                    obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: obj.get("model")?.takeIf { it.isJsonPrimitive }?.asString
                }
                else -> null
            }
        }.filter { it.isNotBlank() }.distinct().sorted()
    }
}
