package com.github.garynasser.correction_notebook.data.model.home

import java.time.LocalDate

data class TodoHistoryItem(
    val id: String,
    val title: String,
    val description: String,
    val priority: Priority,
    val dueDate: LocalDate?,
    val createdAt: Long,
    val completedAt: Long,
    val completedDate: LocalDate
)
