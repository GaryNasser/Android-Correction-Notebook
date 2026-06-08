package com.github.garynasser.correction_notebook.data.model.studyset

data class StudySetDraft(
    val title: String,
    val cards: List<KnowledgeCardDraft> = emptyList(),
    val quizQuestions: List<QuizQuestionDraft> = emptyList()
)

enum class KnowledgeCardType {
    QA_FLASHCARD,
    KNOWLEDGE_CARD
}

data class KnowledgeCardDraft(
    val type: KnowledgeCardType = KnowledgeCardType.QA_FLASHCARD,
    val title: String,
    val front: String,
    val back: String,
    val explanation: String = "",
    val example: String = "",
    val pitfall: String = "",
    val formula: String = "",
    val tags: List<String> = emptyList(),
    val sourceLocation: String = "",
    val sourceQuote: String = "",
    val hint: String = "",
    val difficulty: String = "MEDIUM",
    val confidence: Float = 0.7f
)

data class QuizQuestionDraft(
    val type: String = "SHORT_ANSWER",
    val question: String,
    val options: List<String> = emptyList(),
    val answer: String,
    val explanation: String = ""
)

data class StudySetSummary(
    val id: String,
    val courseId: Int?,
    val courseName: String?,
    val title: String,
    val sourceType: String,
    val sourceRefId: String?,
    val createdByAi: Boolean,
    val flashcardCount: Int,
    val quizCount: Int,
    val dueFlashcardCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

data class StudySetQuizItem(
    val id: String,
    val studySetId: String,
    val type: String,
    val question: String,
    val options: List<String> = emptyList(),
    val answer: String,
    val explanation: String = ""
)

data class DueReviewItem(
    val flashcardId: String,
    val studySetId: String,
    val studySetTitle: String,
    val courseName: String?,
    val type: KnowledgeCardType = KnowledgeCardType.QA_FLASHCARD,
    val title: String,
    val front: String,
    val back: String,
    val explanation: String = "",
    val example: String = "",
    val pitfall: String = "",
    val formula: String = "",
    val tags: List<String> = emptyList(),
    val sourceLocation: String = "",
    val sourceQuote: String = "",
    val hint: String,
    val difficulty: String = "MEDIUM",
    val confidence: Float = 0.7f,
    val createdByAi: Boolean = true,
    val editedByUser: Boolean = false,
    val nextReviewAt: Long,
    val lastReviewedAt: Long? = null,
    val reviewCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
