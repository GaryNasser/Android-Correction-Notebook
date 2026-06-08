package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.knowledgebase.DueReviewRow
import com.github.garynasser.correction_notebook.data.local.knowledgebase.FlashcardEntity
import com.github.garynasser.correction_notebook.data.local.knowledgebase.AiResultCacheEntity
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDao
import com.github.garynasser.correction_notebook.data.local.knowledgebase.QuizQuestionEntity
import com.github.garynasser.correction_notebook.data.local.knowledgebase.StudySetEntity
import com.github.garynasser.correction_notebook.data.local.knowledgebase.StudySetSummaryRow
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import com.github.garynasser.correction_notebook.data.model.studyset.DueReviewItem
import com.github.garynasser.correction_notebook.data.model.studyset.KnowledgeCardDraft
import com.github.garynasser.correction_notebook.data.model.studyset.KnowledgeCardType
import com.github.garynasser.correction_notebook.data.model.studyset.QuizQuestionDraft
import com.github.garynasser.correction_notebook.data.model.studyset.StudySetQuizItem
import com.github.garynasser.correction_notebook.data.model.studyset.StudySetDraft
import com.github.garynasser.correction_notebook.data.model.studyset.StudySetSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudySetRepository @Inject constructor(
    private val dao: KnowledgeBaseDao
) {
    fun observeStudySets(): Flow<List<StudySetSummary>> {
        return dao.observeStudySetSummaries(System.currentTimeMillis())
            .map { rows -> rows.map { it.toSummary() } }
    }

    fun observeDueReviewItems(limit: Int = 5): Flow<List<DueReviewItem>> {
        return dao.observeDueReviewItems(System.currentTimeMillis(), limit)
            .map { rows -> rows.map { it.toDueReviewItem() } }
    }

    fun observeKnowledgeCards(limit: Int = 80): Flow<List<DueReviewItem>> {
        return dao.observeKnowledgeCards(limit)
            .map { rows -> rows.map { it.toDueReviewItem() } }
    }

    fun observeReviewedCards(limit: Int = 80): Flow<List<DueReviewItem>> {
        return dao.observeReviewedCards(limit)
            .map { rows -> rows.map { it.toDueReviewItem() } }
    }

    fun observeQuizQuestions(limit: Int = 200): Flow<List<StudySetQuizItem>> {
        return dao.observeQuizQuestions(limit)
            .map { rows -> rows.map { it.toQuizItem() } }
    }

    suspend fun saveDraftFromFile(
        file: KnowledgeBaseFileSummary,
        draft: StudySetDraft,
        createdByAi: Boolean = true
    ): Result<String> = runCatching {
        val now = System.currentTimeMillis()
        val studySetId = UUID.randomUUID().toString()
        val studySet = StudySetEntity(
            id = studySetId,
            courseId = file.courseId,
            courseName = file.courseName,
            title = draft.title.trim().ifBlank { "${file.displayName} 复习集" },
            sourceType = file.sourceType.ifBlank { "local" },
            sourceRefId = file.id,
            createdByAi = createdByAi,
            createdAt = now,
            updatedAt = now
        )
        dao.insertStudySetWithItems(
            studySet = studySet,
            cards = draft.cards
                .filter { it.title.isNotBlank() && (it.back.isNotBlank() || it.explanation.isNotBlank()) }
                .take(30)
                .map { it.toEntity(studySetId, now, createdByAi) },
            questions = draft.quizQuestions
                .filter { it.question.isNotBlank() && it.answer.isNotBlank() }
                .take(20)
                .map { it.toEntity(studySetId) }
        )
        studySetId
    }

    suspend fun markFlashcardReviewed(flashcardId: String, remembered: Boolean): Result<Unit> = runCatching {
        val card = requireNotNull(dao.getFlashcardById(flashcardId)) { "复习卡片不存在" }
        val now = System.currentTimeMillis()
        val delayDays = when {
            !remembered -> 1L
            card.reviewCount <= 0 -> 2L
            card.reviewCount == 1 -> 4L
            else -> 7L
        }
        dao.updateFlashcard(
            card.copy(
                lastReviewedAt = now,
                reviewCount = card.reviewCount + 1,
                nextReviewAt = now + delayDays * DAY_MILLIS
            )
        )
    }

    suspend fun saveManualCard(
        title: String,
        type: KnowledgeCardType,
        front: String,
        back: String,
        hint: String,
        courseName: String?,
        explanation: String = "",
        example: String = "",
        pitfall: String = "",
        formula: String = "",
        tags: List<String> = emptyList(),
        studySetId: String? = null
    ): Result<String> = runCatching {
        if (type == KnowledgeCardType.QA_FLASHCARD) {
            require(front.isNotBlank()) { "问题不能为空" }
            require(back.isNotBlank()) { "答案不能为空" }
        } else {
            require(title.isNotBlank()) { "标题不能为空" }
            require(explanation.isNotBlank() || back.isNotBlank()) { "解释不能为空" }
        }
        val now = System.currentTimeMillis()
        val normalizedTitle = title.trim().ifBlank {
            if (type == KnowledgeCardType.QA_FLASHCARD) front.trim().take(40) else "手动知识点"
        }
        val targetStudySetId = studySetId ?: UUID.randomUUID().toString()
        val card = createManualFlashcard(
            studySetId = targetStudySetId,
            type = type,
            title = normalizedTitle,
            front = front,
            back = back,
            hint = hint,
            explanation = explanation,
            example = example,
            pitfall = pitfall,
            formula = formula,
            tags = tags,
            now = now
        )
        if (studySetId == null) {
            dao.insertStudySetWithItems(
                studySet = StudySetEntity(
                    id = targetStudySetId,
                    courseId = null,
                    courseName = courseName?.trim()?.takeIf { it.isNotBlank() },
                    title = normalizedTitle,
                    sourceType = "manual",
                    sourceRefId = null,
                    createdByAi = false,
                    createdAt = now,
                    updatedAt = now
                ),
                cards = listOf(card),
                questions = emptyList()
            )
        } else {
            dao.insertCardsToStudySet(targetStudySetId, listOf(card), now)
        }
        targetStudySetId
    }

    suspend fun getCachedAiResult(fileId: String, mode: String): String? {
        return dao.getAiResultCache(fileId, mode)?.content
    }

    suspend fun saveAiResult(fileId: String, mode: String, title: String, content: String) {
        val existing = dao.getAiResultCache(fileId, mode)
        val now = System.currentTimeMillis()
        dao.upsertAiResultCache(
            AiResultCacheEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                fileId = fileId,
                mode = mode,
                title = title,
                content = content,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    suspend fun updateKnowledgeCard(item: DueReviewItem): Result<Unit> = runCatching {
        val existing = requireNotNull(dao.getFlashcardById(item.flashcardId)) { "知识卡片不存在" }
        dao.updateFlashcard(
            existing.copy(
                type = item.type.name,
                title = item.title.cleanCardText().ifBlank { item.front.cleanCardText().take(40) },
                front = item.front.cleanCardText(),
                back = item.back.cleanCardText(),
                explanation = item.explanation.cleanCardText(),
                example = item.example.cleanCardText(),
                pitfall = item.pitfall.cleanCardText(),
                formula = item.formula.cleanCardText(),
                tags = item.tags.joinToString(",") { it.cleanCardText() }.trim(','),
                sourceLocation = item.sourceLocation.cleanCardText(),
                sourceQuote = item.sourceQuote.cleanCardText(),
                hint = item.hint.cleanCardText(),
                difficulty = item.difficulty.trim().ifBlank { "MEDIUM" },
                confidence = item.confidence.coerceIn(0f, 1f),
                editedByUser = true,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteKnowledgeCard(cardId: String): Result<Unit> = runCatching {
        dao.deleteFlashcard(cardId)
    }

    suspend fun renameStudySet(studySetId: String, title: String): Result<Unit> = runCatching {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotBlank()) { "学习集名称不能为空" }
        dao.updateStudySetTitle(studySetId, cleanTitle, System.currentTimeMillis())
    }

    suspend fun deleteStudySet(studySetId: String): Result<Unit> = runCatching {
        dao.deleteStudySet(studySetId)
    }

    suspend fun mergeStudySets(sourceStudySetIds: List<String>, targetStudySetId: String): Result<Unit> = runCatching {
        val sources = sourceStudySetIds.distinct().filter { it != targetStudySetId }
        require(sources.isNotEmpty()) { "请选择要合并的学习集" }
        dao.mergeStudySets(sources, targetStudySetId, System.currentTimeMillis())
    }

    suspend fun moveKnowledgeCard(cardId: String, targetStudySetId: String): Result<Unit> = runCatching {
        val card = requireNotNull(dao.getFlashcardById(cardId)) { "知识卡片不存在" }
        require(card.studySetId != targetStudySetId) { "卡片已经在这个学习集中" }
        dao.moveCardToStudySet(cardId, card.studySetId, targetStudySetId, System.currentTimeMillis())
    }

    private fun createManualFlashcard(
        studySetId: String,
        type: KnowledgeCardType,
        title: String,
        front: String,
        back: String,
        hint: String,
        explanation: String,
        example: String,
        pitfall: String,
        formula: String,
        tags: List<String>,
        now: Long
    ): FlashcardEntity {
        return FlashcardEntity(
            id = UUID.randomUUID().toString(),
            studySetId = studySetId,
            type = type.name,
            title = title.cleanCardText(),
            front = if (type == KnowledgeCardType.QA_FLASHCARD) front.cleanCardText() else title.cleanCardText(),
            back = if (type == KnowledgeCardType.QA_FLASHCARD) back.cleanCardText() else back.cleanCardText().ifBlank { explanation.cleanCardText() },
            explanation = explanation.cleanCardText(),
            example = example.cleanCardText(),
            pitfall = pitfall.cleanCardText(),
            formula = formula.cleanCardText(),
            tags = tags.joinToString(",") { it.cleanCardText() },
            sourceLocation = "",
            sourceQuote = "",
            hint = hint.cleanCardText(),
            difficulty = "MEDIUM",
            confidence = 1f,
            createdByAi = false,
            editedByUser = true,
            nextReviewAt = 0L,
            lastReviewedAt = null,
            reviewCount = 0,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun KnowledgeCardDraft.toEntity(studySetId: String, now: Long, createdByAi: Boolean): FlashcardEntity {
        return FlashcardEntity(
            id = UUID.randomUUID().toString(),
            studySetId = studySetId,
            type = type.name,
            title = title.cleanCardText(),
            front = front.cleanCardText(),
            back = back.cleanCardText(),
            explanation = explanation.cleanCardText(),
            example = example.cleanCardText(),
            pitfall = pitfall.cleanCardText(),
            formula = formula.cleanCardText(),
            tags = tags.joinToString(",") { it.cleanCardText() },
            sourceLocation = sourceLocation.trim(),
            sourceQuote = sourceQuote.trim(),
            hint = hint.trim(),
            difficulty = difficulty.trim().ifBlank { "MEDIUM" },
            confidence = confidence.coerceIn(0f, 1f),
            createdByAi = createdByAi,
            editedByUser = false,
            nextReviewAt = 0L,
            lastReviewedAt = null,
            reviewCount = 0,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun QuizQuestionDraft.toEntity(studySetId: String): QuizQuestionEntity {
        return QuizQuestionEntity(
            id = UUID.randomUUID().toString(),
            studySetId = studySetId,
            type = type.trim().ifBlank { "SHORT_ANSWER" },
            question = question.trim(),
            options = options.joinToString("|||") { it.trim() },
            answer = answer.trim(),
            explanation = explanation.trim(),
            sourceChunkId = null
        )
    }

    private fun QuizQuestionEntity.toQuizItem(): StudySetQuizItem {
        return StudySetQuizItem(
            id = id,
            studySetId = studySetId,
            type = type,
            question = question,
            options = options.split("|||").map { it.trim() }.filter { it.isNotBlank() },
            answer = answer,
            explanation = explanation
        )
    }

    private fun StudySetSummaryRow.toSummary(): StudySetSummary {
        return StudySetSummary(
            id = id,
            courseId = courseId,
            courseName = courseName,
            title = title,
            sourceType = sourceType,
            sourceRefId = sourceRefId,
            createdByAi = createdByAi,
            flashcardCount = flashcardCount,
            quizCount = quizCount,
            dueFlashcardCount = dueFlashcardCount,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun DueReviewRow.toDueReviewItem(): DueReviewItem {
        return DueReviewItem(
            flashcardId = flashcardId,
            studySetId = studySetId,
            studySetTitle = studySetTitle,
            courseName = courseName,
            type = runCatching { KnowledgeCardType.valueOf(type) }.getOrDefault(KnowledgeCardType.QA_FLASHCARD),
            title = title.ifBlank { front.take(40) },
            front = front,
            back = back,
            explanation = explanation,
            example = example,
            pitfall = pitfall,
            formula = formula,
            tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
            sourceLocation = sourceLocation,
            sourceQuote = sourceQuote,
            hint = hint,
            difficulty = difficulty,
            confidence = confidence,
            createdByAi = createdByAi,
            editedByUser = editedByUser,
            nextReviewAt = nextReviewAt,
            lastReviewedAt = lastReviewedAt,
            reviewCount = reviewCount,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}

private fun String.cleanCardText(): String {
    return trim()
        .replace(Regex("^[-*\\s]+"), "")
        .replace(Regex("^(问题|答案|提示|解释|例子|易错点|公式|术语|Question|Answer|Hint)[:：]\\s*", RegexOption.IGNORE_CASE), "")
        .trim()
}
