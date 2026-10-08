package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.todoDataStore: DataStore<Preferences> by preferencesDataStore("todo_prefs")

class TodoRepository internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.todoDataStore)

    private val todoItemsKey = stringPreferencesKey("todo_items")

    val todoItems: Flow<List<TodoItem>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[todoItemsKey]?.let { json ->
                TodoPreferenceCodec.parseTodoItems(json)
            } ?: emptyList()
        }

    suspend fun addTodo(todo: TodoItem) {
        dataStore.edit { prefs ->
            val current = prefs[todoItemsKey]?.let(TodoPreferenceCodec::parseTodoItems) ?: emptyList()
            val existing = current.firstOrNull { it.id == todo.id }
            // A restored draft must not reset the lifecycle of an already saved task.
            val saved = existing?.let {
                todo.copy(createdAt = it.createdAt, isCompleted = it.isCompleted, completedAt = it.completedAt)
            } ?: todo
            val updated = current.filterNot { it.id == todo.id } + saved
            prefs[todoItemsKey] = TodoPreferenceCodec.serializeTodoItems(updated)
        }
    }

    suspend fun updateTodo(todo: TodoItem) {
        dataStore.edit { prefs ->
            val current = prefs[todoItemsKey]?.let(TodoPreferenceCodec::parseTodoItems) ?: emptyList()
            val updated = current.map { if (it.id == todo.id) todo else it }
            prefs[todoItemsKey] = TodoPreferenceCodec.serializeTodoItems(updated)
        }
    }

    suspend fun deleteTodo(todoId: String) {
        dataStore.edit { prefs ->
            val current = prefs[todoItemsKey]?.let(TodoPreferenceCodec::parseTodoItems) ?: emptyList()
            val updated = current.filter { it.id != todoId }
            prefs[todoItemsKey] = TodoPreferenceCodec.serializeTodoItems(updated)
        }
    }

    suspend fun toggleComplete(todoId: String): TodoItem {
        var toggledTodo: TodoItem? = null
        dataStore.edit { prefs ->
            val current = prefs[todoItemsKey]?.let(TodoPreferenceCodec::parseTodoItems) ?: emptyList()
            val updated = current.map {
                if (it.id == todoId) {
                    val toggled = if (it.isCompleted) {
                        it.copy(isCompleted = false, completedAt = null)
                    } else {
                        it.copy(isCompleted = true, completedAt = System.currentTimeMillis())
                    }
                    toggledTodo = toggled
                    toggled
                } else it
            }
            checkNotNull(toggledTodo) { "待办不存在" }
            prefs[todoItemsKey] = TodoPreferenceCodec.serializeTodoItems(updated)
        }
        return checkNotNull(toggledTodo) { "待办状态更新失败" }
    }
}
