package com.github.garynasser.correction_notebook.data.remote.ai

import com.github.garynasser.correction_notebook.data.model.ai.AiAction
import com.github.garynasser.correction_notebook.data.model.ai.AiActionResult
import com.github.garynasser.correction_notebook.data.model.ai.AiActionType
import com.github.garynasser.correction_notebook.data.model.ai.AiPlanBlock
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object AiActionParser {
    fun parse(raw: String): AiActionResult {
        val jsonText = extractJsonObject(raw) ?: return AiActionResult(rawText = raw)
        return runCatching {
            val root = JsonParser.parseString(jsonText).asJsonObject
            AiActionResult(
                rawText = raw,
                summary = root.string("summary"),
                actions = root.arrayObjects("actions").mapNotNull(::parseAction),
                planBlocks = root.arrayObjects("planBlocks").map(::parsePlanBlock),
                referencedMemories = root.arrayStrings("referencedMemories"),
                parsed = true
            )
        }.getOrElse {
            AiActionResult(rawText = raw)
        }
    }

    fun instruction(): String = """
        请优先返回一个 JSON 对象，不要用 Markdown 代码块包裹。结构如下：
        {
          "summary": "面向用户的简短说明",
          "planBlocks": [
            {
              "title": "学习块标题",
              "reason": "为什么建议做",
              "estimatedMinutes": 25,
              "courseId": null,
              "fileId": null,
              "todoId": null,
              "priority": "HIGH|MEDIUM|LOW"
            }
          ],
          "actions": [
            {
              "type": "CREATE_TODO|SAVE_COURSE_NOTE|CREATE_REVIEW_PLAN|SAVE_MEMORY|OPEN_COURSE|OPEN_FILE|CREATE_STUDY_SET|CREATE_FLASHCARDS|CREATE_QUIZ|SCHEDULE_REVIEW|UPDATE_COURSE_GOAL",
              "title": "动作标题",
              "description": "动作说明",
              "payload": {
                "content": "需要保存或执行的内容",
                "courseId": "",
                "courseName": "",
                "sectionId": "",
                "sectionTitle": "",
                "category": ""
              }
            }
          ],
          "referencedMemories": ["学习偏好: ..."]
        }
        如果信息不足，也要返回合法 JSON，并在 summary 中说明不足。
    """.trimIndent()

    private fun parseAction(obj: JsonObject): AiAction? {
        val type = runCatching { AiActionType.valueOf(obj.string("type")) }.getOrNull() ?: return null
        return AiAction(
            type = type,
            title = obj.string("title").ifBlank { type.name },
            description = obj.string("description"),
            payload = obj.get("payload")?.takeIf { it.isJsonObject }?.asJsonObject?.asStringMap().orEmpty()
        )
    }

    private fun parsePlanBlock(obj: JsonObject): AiPlanBlock {
        return AiPlanBlock(
            title = obj.string("title").ifBlank { "学习任务" },
            reason = obj.string("reason"),
            estimatedMinutes = obj.int("estimatedMinutes", 25).coerceIn(5, 240),
            courseId = obj.intOrNull("courseId"),
            fileId = obj.string("fileId").takeIf { it.isNotBlank() && it != "null" },
            todoId = obj.string("todoId").takeIf { it.isNotBlank() && it != "null" },
            priority = obj.string("priority").ifBlank { "MEDIUM" }
        )
    }

    private fun extractJsonObject(raw: String): String? {
        val trimmed = raw.trim()
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        val candidate = fence ?: trimmed
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        return if (start >= 0 && end > start) candidate.substring(start, end + 1) else null
    }

    private fun JsonObject.string(name: String): String =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.int(name: String, fallback: Int): Int = intOrNull(name) ?: fallback

    private fun JsonObject.intOrNull(name: String): Int? =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.let {
            runCatching { it.asInt }.getOrNull()
        }

    private fun JsonObject.arrayObjects(name: String): List<JsonObject> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.asObjectOrNull() }.orEmpty()

    private fun JsonObject.arrayStrings(name: String): List<String> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull {
            it.takeIf { element -> element.isJsonPrimitive }?.asString
        }.orEmpty()

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.asStringMap(): Map<String, String> =
        entrySet().associate { (key, value) ->
            key to when {
                value.isJsonNull -> ""
                value.isJsonPrimitive -> value.asString
                else -> value.toString()
            }
        }
}
