package com.github.garynasser.correction_notebook.data.local.knowledgebase

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "study_set",
    indices = [
        Index(value = ["courseId"]),
        Index(value = ["sourceType", "sourceRefId"])
    ]
)
data class StudySetEntity(
    @PrimaryKey val id: String,
    val courseId: Int?,
    val courseName: String?,
    val title: String,
    val sourceType: String,
    val sourceRefId: String?,
    val createdByAi: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "flashcard",
    foreignKeys = [
        ForeignKey(
            entity = StudySetEntity::class,
            parentColumns = ["id"],
            childColumns = ["studySetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["studySetId"]),
        Index(value = ["nextReviewAt"])
    ]
)
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val studySetId: String,
    val type: String,
    val title: String,
    val front: String,
    val back: String,
    val explanation: String,
    val example: String,
    val pitfall: String,
    val formula: String,
    val tags: String,
    val sourceLocation: String,
    val sourceQuote: String,
    val hint: String,
    val difficulty: String,
    val confidence: Float,
    val createdByAi: Boolean,
    val editedByUser: Boolean,
    val nextReviewAt: Long,
    val lastReviewedAt: Long?,
    val reviewCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "quiz_question",
    foreignKeys = [
        ForeignKey(
            entity = StudySetEntity::class,
            parentColumns = ["id"],
            childColumns = ["studySetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["studySetId"]),
        Index(value = ["sourceChunkId"])
    ]
)
data class QuizQuestionEntity(
    @PrimaryKey val id: String,
    val studySetId: String,
    val type: String,
    val question: String,
    val options: String,
    val answer: String,
    val explanation: String,
    val sourceChunkId: Long?
)

@Entity(
    tableName = "ai_result_cache",
    indices = [
        Index(value = ["fileId", "mode"], unique = true),
        Index(value = ["updatedAt"])
    ]
)
data class AiResultCacheEntity(
    @PrimaryKey val id: String,
    val fileId: String,
    val mode: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class StudySetSummaryRow(
    val id: String,
    val courseId: Int?,
    val courseName: String?,
    val title: String,
    val sourceType: String,
    val sourceRefId: String?,
    val createdByAi: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val flashcardCount: Int,
    val quizCount: Int,
    val dueFlashcardCount: Int
)

data class DueReviewRow(
    val flashcardId: String,
    val studySetId: String,
    val studySetTitle: String,
    val courseName: String?,
    val type: String,
    val title: String,
    val front: String,
    val back: String,
    val explanation: String,
    val example: String,
    val pitfall: String,
    val formula: String,
    val tags: String,
    val sourceLocation: String,
    val sourceQuote: String,
    val hint: String,
    val difficulty: String,
    val confidence: Float,
    val createdByAi: Boolean,
    val editedByUser: Boolean,
    val nextReviewAt: Long,
    val lastReviewedAt: Long?,
    val reviewCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)
