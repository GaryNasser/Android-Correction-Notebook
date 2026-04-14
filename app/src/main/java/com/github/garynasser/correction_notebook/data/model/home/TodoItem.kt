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
    val completedAt: Long? = null
)

enum class Priority {
    LOW, MEDIUM, HIGH
}
