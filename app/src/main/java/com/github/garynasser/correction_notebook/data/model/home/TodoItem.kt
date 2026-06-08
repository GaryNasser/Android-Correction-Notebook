package com.github.garynasser.correction_notebook.data.model.home

import java.time.LocalDate

data class TodoItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val priority: Priority = Priority.MEDIUM,
    val dueDate: LocalDate? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val source: TodoSource = TodoSource.MANUAL,
    val sourceRefId: String? = null,
    val taskType: TodoTaskType = TodoTaskType.GENERAL,
    val courseId: Int? = null,
    val estimatedMinutes: Int? = null,
    val weight: Float? = null
)

enum class Priority {
    LOW, MEDIUM, HIGH
}

enum class TodoSource {
    MANUAL,
    AI_TODAY_ADVICE,
    AI_TODO_BREAKDOWN,
    COURSE_ASSISTANT,
    KNOWLEDGE_ASSISTANT
}

enum class TodoTaskType {
    GENERAL,
    HOMEWORK,
    EXAM,
    PROJECT,
    READING,
    REVIEW
}
