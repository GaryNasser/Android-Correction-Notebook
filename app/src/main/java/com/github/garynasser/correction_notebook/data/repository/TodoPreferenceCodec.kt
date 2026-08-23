package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.home.Priority
import com.github.garynasser.correction_notebook.data.model.home.TodoHistoryItem
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import com.github.garynasser.correction_notebook.data.model.home.TodoSource
import com.github.garynasser.correction_notebook.data.model.home.TodoTaskType
import com.google.gson.Gson
import java.time.LocalDate

internal object TodoPreferenceCodec {
    private val gson = Gson()

    fun serializeTodoItems(items: List<TodoItem>): String {
        return gson.toJson(items.map(TodoItemDto::from))
    }

    fun parseTodoItems(raw: String): List<TodoItem> {
        if (raw.isBlank()) return emptyList()
        return parseJsonArray(raw, Array<TodoItemDto>::class.java)
            ?.mapNotNull(TodoItemDto::toModel)
            ?: parseLegacyTodoItems(raw)
    }

    fun serializeHistoryItems(items: List<TodoHistoryItem>): String {
        return gson.toJson(items.map(TodoHistoryItemDto::from))
    }

    fun parseHistoryItems(raw: String): List<TodoHistoryItem> {
        if (raw.isBlank()) return emptyList()
        return parseJsonArray(raw, Array<TodoHistoryItemDto>::class.java)
            ?.mapNotNull(TodoHistoryItemDto::toModel)
            ?: parseLegacyHistoryItems(raw)
    }

    private fun <T> parseJsonArray(raw: String, type: Class<Array<T>>): List<T>? {
        if (!raw.trimStart().startsWith("[")) return null
        return runCatching { gson.fromJson(raw, type).toList() }.getOrNull()
    }

    private fun parseLegacyTodoItems(raw: String): List<TodoItem> {
        return raw.split("|||").mapNotNull { itemStr ->
            val parts = itemStr.split(":::")
            if (parts.size < 8) return@mapNotNull null
            TodoItem(
                id = parts[0],
                title = parts[1],
                description = parts[2],
                priority = priorityOrDefault(parts[3]),
                dueDate = parts[4].toLocalDateOrNull(),
                isCompleted = parts[5].toBoolean(),
                createdAt = parts[6].toLongOrNull() ?: System.currentTimeMillis(),
                completedAt = parts[7].toLongOrNull(),
                source = parts.getOrNull(8)?.let(::todoSourceOrDefault) ?: TodoSource.MANUAL,
                sourceRefId = parts.getOrNull(9)?.takeIf { it.isNotBlank() },
                taskType = parts.getOrNull(10)?.let(::todoTaskTypeOrDefault) ?: TodoTaskType.GENERAL,
                courseId = parts.getOrNull(11)?.toIntOrNull(),
                estimatedMinutes = parts.getOrNull(12)?.toIntOrNull(),
                weight = parts.getOrNull(13)?.toFloatOrNull()
            )
        }
    }

    private fun parseLegacyHistoryItems(raw: String): List<TodoHistoryItem> {
        return raw.split("|||").mapNotNull { itemStr ->
            val parts = itemStr.split(":::")
            if (parts.size < 8) return@mapNotNull null
            TodoHistoryItem(
                id = parts[0],
                title = parts[1],
                description = parts[2],
                priority = priorityOrDefault(parts[3]),
                dueDate = parts[4].toLocalDateOrNull(),
                createdAt = parts[5].toLongOrNull() ?: System.currentTimeMillis(),
                completedAt = parts[6].toLongOrNull() ?: System.currentTimeMillis(),
                completedDate = parts[7].toLocalDateOrNull() ?: LocalDate.now()
            )
        }
    }

    private fun priorityOrDefault(raw: String): Priority {
        return runCatching { Priority.valueOf(raw) }.getOrDefault(Priority.MEDIUM)
    }

    private fun todoSourceOrDefault(raw: String): TodoSource {
        return runCatching { TodoSource.valueOf(raw) }.getOrDefault(TodoSource.MANUAL)
    }

    private fun todoTaskTypeOrDefault(raw: String): TodoTaskType {
        return runCatching { TodoTaskType.valueOf(raw) }.getOrDefault(TodoTaskType.GENERAL)
    }

    private fun String.toLocalDateOrNull(): LocalDate? {
        return takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
    }

    private data class TodoItemDto(
        val id: String,
        val title: String,
        val description: String,
        val priority: String,
        val dueDate: String?,
        val isCompleted: Boolean,
        val createdAt: Long,
        val completedAt: Long?,
        val source: String,
        val sourceRefId: String?,
        val taskType: String,
        val courseId: Int?,
        val estimatedMinutes: Int?,
        val weight: Float?
    ) {
        fun toModel(): TodoItem? {
            if (title.isBlank()) return null
            return TodoItem(
                id = id,
                title = title,
                description = description,
                priority = priorityOrDefault(priority),
                dueDate = dueDate?.toLocalDateOrNull(),
                isCompleted = isCompleted,
                createdAt = createdAt,
                completedAt = completedAt,
                source = todoSourceOrDefault(source),
                sourceRefId = sourceRefId,
                taskType = todoTaskTypeOrDefault(taskType),
                courseId = courseId,
                estimatedMinutes = estimatedMinutes,
                weight = weight
            )
        }

        companion object {
            fun from(item: TodoItem): TodoItemDto = TodoItemDto(
                id = item.id,
                title = item.title,
                description = item.description,
                priority = item.priority.name,
                dueDate = item.dueDate?.toString(),
                isCompleted = item.isCompleted,
                createdAt = item.createdAt,
                completedAt = item.completedAt,
                source = item.source.name,
                sourceRefId = item.sourceRefId,
                taskType = item.taskType.name,
                courseId = item.courseId,
                estimatedMinutes = item.estimatedMinutes,
                weight = item.weight
            )
        }
    }

    private data class TodoHistoryItemDto(
        val id: String,
        val title: String,
        val description: String,
        val priority: String,
        val dueDate: String?,
        val createdAt: Long,
        val completedAt: Long,
        val completedDate: String
    ) {
        fun toModel(): TodoHistoryItem? {
            if (title.isBlank()) return null
            return TodoHistoryItem(
                id = id,
                title = title,
                description = description,
                priority = priorityOrDefault(priority),
                dueDate = dueDate?.toLocalDateOrNull(),
                createdAt = createdAt,
                completedAt = completedAt,
                completedDate = completedDate.toLocalDateOrNull() ?: LocalDate.now()
            )
        }

        companion object {
            fun from(item: TodoHistoryItem): TodoHistoryItemDto = TodoHistoryItemDto(
                id = item.id,
                title = item.title,
                description = item.description,
                priority = item.priority.name,
                dueDate = item.dueDate?.toString(),
                createdAt = item.createdAt,
                completedAt = item.completedAt,
                completedDate = item.completedDate.toString()
            )
        }
    }
}
