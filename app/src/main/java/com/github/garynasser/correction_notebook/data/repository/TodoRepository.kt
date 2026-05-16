package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.garynasser.correction_notebook.data.model.home.Priority
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import com.github.garynasser.correction_notebook.data.model.home.TodoSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.todoDataStore: DataStore<Preferences> by preferencesDataStore("todo_prefs")

class TodoRepository(private val context: Context) {

    private val todoItemsKey = stringPreferencesKey("todo_items")

    val todoItems: Flow<List<TodoItem>> = context.todoDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[todoItemsKey]?.let { json ->
                parseTodoItems(json)
            } ?: emptyList()
        }

    suspend fun addTodo(todo: TodoItem) {
        context.todoDataStore.edit { prefs ->
            val current = prefs[todoItemsKey]?.let { parseTodoItems(it) } ?: emptyList()
            val updated = current + todo
            prefs[todoItemsKey] = serializeTodoItems(updated)
        }
    }

    suspend fun updateTodo(todo: TodoItem) {
        context.todoDataStore.edit { prefs ->
            val current = prefs[todoItemsKey]?.let { parseTodoItems(it) } ?: emptyList()
            val updated = current.map { if (it.id == todo.id) todo else it }
            prefs[todoItemsKey] = serializeTodoItems(updated)
        }
    }

    suspend fun deleteTodo(todoId: String) {
        context.todoDataStore.edit { prefs ->
            val current = prefs[todoItemsKey]?.let { parseTodoItems(it) } ?: emptyList()
            val updated = current.filter { it.id != todoId }
            prefs[todoItemsKey] = serializeTodoItems(updated)
        }
    }

    suspend fun getTodoById(todoId: String): TodoItem? {
        val current = context.todoDataStore.data.first().let { prefs ->
            prefs[todoItemsKey]?.let { parseTodoItems(it) } ?: emptyList()
        }
        return current.find { it.id == todoId }
    }

    suspend fun toggleComplete(todoId: String) {
        context.todoDataStore.edit { prefs ->
            val current = prefs[todoItemsKey]?.let { parseTodoItems(it) } ?: emptyList()
            val updated = current.map {
                if (it.id == todoId) {
                    if (it.isCompleted) {
                        it.copy(isCompleted = false, completedAt = null)
                    } else {
                        it.copy(isCompleted = true, completedAt = System.currentTimeMillis())
                    }
                } else it
            }
            prefs[todoItemsKey] = serializeTodoItems(updated)
        }
    }

    private fun serializeTodoItems(items: List<TodoItem>): String {
        return items.joinToString("|||") { item ->
            listOf(
                item.id,
                item.title,
                item.description,
                item.priority.name,
                item.dueDate?.toString() ?: "",
                item.isCompleted.toString(),
                item.createdAt.toString(),
                item.completedAt?.toString() ?: "",
                item.source.name,
                item.sourceRefId ?: ""
            ).joinToString(":::")
        }
    }

    private fun parseTodoItems(json: String): List<TodoItem> {
        if (json.isBlank()) return emptyList()
        return json.split("|||").mapNotNull { itemStr ->
            val parts = itemStr.split(":::")
            if (parts.size >= 8) {
                TodoItem(
                    id = parts[0],
                    title = parts[1],
                    description = parts[2],
                    priority = try { Priority.valueOf(parts[3]) } catch (e: Exception) { Priority.MEDIUM },
                    dueDate = if (parts[4].isNotBlank()) LocalDate.parse(parts[4]) else null,
                    isCompleted = parts[5].toBoolean(),
                    createdAt = parts[6].toLongOrNull() ?: System.currentTimeMillis(),
                    completedAt = if (parts[7].isNotBlank()) parts[7].toLongOrNull() else null,
                    source = parts.getOrNull(8)?.let {
                        runCatching { TodoSource.valueOf(it) }.getOrDefault(TodoSource.MANUAL)
                    } ?: TodoSource.MANUAL,
                    sourceRefId = parts.getOrNull(9)?.takeIf { it.isNotBlank() }
                )
            } else null
        }
    }
}
