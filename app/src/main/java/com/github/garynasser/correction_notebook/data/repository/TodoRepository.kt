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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.todoDataStore: DataStore<Preferences> by preferencesDataStore("todo_prefs")

class TodoRepository(private val context: Context) {

    private val todoItemsKey = stringPreferencesKey("todo_items")

    val todoItems: Flow<List<TodoItem>> = context.todoDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[todoItemsKey]?.let { json ->
                TodoPreferenceCodec.parseTodoItems(json)
            } ?: emptyList()
        }

    suspend fun addTodo(todo: TodoItem) {
        context.todoDataStore.edit { prefs ->
            val current = prefs[todoItemsKey]?.let(TodoPreferenceCodec::parseTodoItems) ?: emptyList()
            val updated = current + todo
            prefs[todoItemsKey] = TodoPreferenceCodec.serializeTodoItems(updated)
        }
    }

    suspend fun updateTodo(todo: TodoItem) {
        context.todoDataStore.edit { prefs ->
            val current = prefs[todoItemsKey]?.let(TodoPreferenceCodec::parseTodoItems) ?: emptyList()
            val updated = current.map { if (it.id == todo.id) todo else it }
            prefs[todoItemsKey] = TodoPreferenceCodec.serializeTodoItems(updated)
        }
    }

    suspend fun deleteTodo(todoId: String) {
        context.todoDataStore.edit { prefs ->
            val current = prefs[todoItemsKey]?.let(TodoPreferenceCodec::parseTodoItems) ?: emptyList()
            val updated = current.filter { it.id != todoId }
            prefs[todoItemsKey] = TodoPreferenceCodec.serializeTodoItems(updated)
        }
    }

    suspend fun getTodoById(todoId: String): TodoItem? {
        val current = context.todoDataStore.data.first().let { prefs ->
            prefs[todoItemsKey]?.let(TodoPreferenceCodec::parseTodoItems) ?: emptyList()
        }
        return current.find { it.id == todoId }
    }

    suspend fun toggleComplete(todoId: String) {
        context.todoDataStore.edit { prefs ->
            val current = prefs[todoItemsKey]?.let(TodoPreferenceCodec::parseTodoItems) ?: emptyList()
            val updated = current.map {
                if (it.id == todoId) {
                    if (it.isCompleted) {
                        it.copy(isCompleted = false, completedAt = null)
                    } else {
                        it.copy(isCompleted = true, completedAt = System.currentTimeMillis())
                    }
                } else it
            }
            prefs[todoItemsKey] = TodoPreferenceCodec.serializeTodoItems(updated)
        }
    }
}
