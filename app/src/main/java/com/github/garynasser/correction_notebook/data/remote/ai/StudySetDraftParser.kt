package com.github.garynasser.correction_notebook.data.remote.ai

import com.github.garynasser.correction_notebook.data.model.studyset.KnowledgeCardDraft
import com.github.garynasser.correction_notebook.data.model.studyset.KnowledgeCardType
import com.github.garynasser.correction_notebook.data.model.studyset.QuizQuestionDraft
import com.github.garynasser.correction_notebook.data.model.studyset.StudySetDraft
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object StudySetDraftParser {
    fun parse(raw: String, fallbackTitle: String): StudySetDraft {
        val jsonText = extractJsonObject(raw)
        val parsed = jsonText?.let {
            runCatching {
                val root = JsonParser.parseString(it).asJsonObject
                StudySetDraft(
                    title = root.string("title").ifBlank { fallbackTitle },
                    cards = root.arrayObjects("cards").mapNotNull(::parseCard),
                    quizQuestions = root.arrayObjects("quizQuestions").mapNotNull(::parseQuizQuestion)
                )
            }.getOrNull()
        }
        if (parsed != null && (parsed.cards.isNotEmpty() || parsed.quizQuestions.isNotEmpty())) {
            return parsed
        }
        return StudySetDraft(title = fallbackTitle)
    }

    fun instruction(): String = """
        请只返回一个 JSON 对象，不要使用 Markdown 代码块。结构如下：
        {
          "title": "学习集标题",
          "cards": [
            {
              "type": "QA_FLASHCARD|KNOWLEDGE_CARD",
              "title": "具体概念或自测问题标题",
              "front": "问答卡的问题，只写问题本身，不要带“问题：”前缀；知识点卡可为空",
              "back": "问答卡答案，只写答案本身，不要带“答案：”前缀，控制在 1-4 句话",
              "explanation": "知识点卡核心解释",
              "example": "例子，可为空",
              "pitfall": "易错点，可为空",
              "formula": "公式或术语，可为空",
              "tags": ["标签"],
              "sourceLocation": "文件片段或章节位置",
              "sourceQuote": "来自资料的短引用",
              "hint": "提示词，只写关键词，不要带“提示：”前缀，不要重复答案",
              "difficulty": "LOW|MEDIUM|HIGH",
              "confidence": 0.8
            }
          ],
          "quizQuestions": [
            {
              "type": "MULTIPLE_CHOICE|SHORT_ANSWER",
              "question": "题目",
              "options": ["A", "B", "C", "D"],
              "answer": "答案",
              "explanation": "解析"
            }
          ]
        }
        生成 4-6 张 QA_FLASHCARD 和 3-5 张 KNOWLEDGE_CARD。先抽取原文知识点，再生成卡片。
        QA_FLASHCARD 的 front 必须是一个可以自测的问题，back 必须是 1-4 句答案，hint 只能是关键词。
        KNOWLEDGE_CARD 的 title 必须是具体概念/公式/方法名，explanation 是核心解释，example/pitfall/formula 分别填写。
        禁止把“问题：答案：提示：”放进同一个字段；禁止把整段总结塞进 back；禁止使用“复习要点 1”这类空泛标题；禁止编造资料外事实。
    """.trimIndent()

    private fun parseCard(obj: JsonObject): KnowledgeCardDraft? {
        val type = runCatching { KnowledgeCardType.valueOf(obj.string("type")) }
            .getOrDefault(KnowledgeCardType.QA_FLASHCARD)
        val title = obj.string("title").cleanCardText().ifBlank {
            obj.string("front").ifBlank { return null }
        }
        val rawFront = obj.string("front")
        val rawBack = obj.string("back")
        val rawHint = obj.string("hint")
        val sections = splitLabeledSections(listOf(rawFront, rawBack, rawHint).joinToString("\n"))
        val front = rawFront.cleanCardText()
            .takeIf { it.isNotBlank() && !it.containsAnyLabel() }
            ?: sections["问题"]
            ?: sections["question"]
            ?: title
        val back = rawBack.cleanCardText()
            .takeIf { it.isNotBlank() && !it.containsAnyLabel() }
            ?: sections["答案"]
            ?: sections["answer"]
            ?: ""
        val hint = rawHint.cleanCardText()
            .takeIf { it.isNotBlank() && !it.containsAnyLabel() }
            ?: sections["提示"]
            ?: sections["hint"]
            ?: ""
        val explanation = obj.string("explanation").cleanCardText()
        if (type == KnowledgeCardType.QA_FLASHCARD && back.isBlank()) return null
        if (type == KnowledgeCardType.KNOWLEDGE_CARD && explanation.isBlank() && back.isBlank()) return null
        return KnowledgeCardDraft(
            type = type,
            title = title,
            front = front,
            back = back,
            explanation = explanation,
            example = obj.string("example").cleanCardText(),
            pitfall = obj.string("pitfall").cleanCardText(),
            formula = obj.string("formula").cleanCardText(),
            tags = obj.arrayStrings("tags").map { it.cleanCardText() }.filter { it.isNotBlank() },
            sourceLocation = obj.string("sourceLocation").cleanCardText(),
            sourceQuote = obj.string("sourceQuote").cleanCardText(),
            hint = hint,
            difficulty = obj.string("difficulty").ifBlank { "MEDIUM" },
            confidence = obj.float("confidence", 0.7f).coerceIn(0f, 1f)
        )
    }

    private fun parseQuizQuestion(obj: JsonObject): QuizQuestionDraft? {
        val question = obj.string("question").ifBlank { return null }
        val answer = obj.string("answer").ifBlank { return null }
        return QuizQuestionDraft(
            type = obj.string("type").ifBlank { "SHORT_ANSWER" },
            question = question,
            options = obj.arrayStrings("options"),
            answer = answer,
            explanation = obj.string("explanation")
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

    private fun JsonObject.float(name: String, fallback: Float): Float =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.let {
            runCatching { it.asFloat }.getOrNull()
        } ?: fallback

    private fun JsonObject.arrayObjects(name: String): List<JsonObject> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.asObjectOrNull() }.orEmpty()

    private fun JsonObject.arrayStrings(name: String): List<String> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull {
            it.takeIf { element -> element.isJsonPrimitive }?.asString
        }.orEmpty()

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        takeIf { it.isJsonObject }?.asJsonObject

    private fun String.cleanCardText(): String {
        return trim()
            .replace(Regex("^[-*\\s]+"), "")
            .replace(Regex("^(问题|答案|提示|解释|例子|易错点|公式|术语|Question|Answer|Hint)[:：]\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun String.containsAnyLabel(): Boolean {
        return Regex("(问题|答案|提示|Question|Answer|Hint)[:：]", RegexOption.IGNORE_CASE).containsMatchIn(this)
    }

    private fun splitLabeledSections(text: String): Map<String, String> {
        val pattern = Regex("(问题|答案|提示|question|answer|hint)[:：]", RegexOption.IGNORE_CASE)
        val matches = pattern.findAll(text).toList()
        if (matches.isEmpty()) return emptyMap()
        return matches.mapIndexedNotNull { index, match ->
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val key = match.groupValues[1].lowercase()
            val normalizedKey = when (key) {
                "question" -> "question"
                "answer" -> "answer"
                "hint" -> "hint"
                else -> match.groupValues[1]
            }
            val value = text.substring(start, end).cleanCardText()
            if (value.isBlank()) null else normalizedKey to value
        }.toMap()
    }
}
