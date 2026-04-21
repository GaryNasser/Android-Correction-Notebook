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
import com.github.garynasser.correction_notebook.data.model.home.TodoHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

private val Context.todoHistoryDataStore: DataStore<Preferences> by preferencesDataStore("todo_history_prefs")

class TodoHistoryRepository(private val context: Context) {

    private val historyItemsKey = stringPreferencesKey("todo_history_items")

    val historyItems: Flow<List<TodoHistoryItem>> = context.todoHistoryDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[historyItemsKey]?.let { json ->
                normalizeHistoryItems(parseHistoryItems(json))
            } ?: emptyList()
        }

    suspend fun addHistoryItem(item: TodoHistoryItem) {
        context.todoHistoryDataStore.edit { prefs ->
            val current = prefs[historyItemsKey]?.let { parseHistoryItems(it) } ?: emptyList()
            val updated = normalizeHistoryItems(current + item)
            prefs[historyItemsKey] = serializeHistoryItems(updated)
        }
    }

    suspend fun deleteHistoryItem(itemId: String) {
        context.todoHistoryDataStore.edit { prefs ->
            val current = prefs[historyItemsKey]?.let { parseHistoryItems(it) } ?: emptyList()
            val updated = current.filter { it.id != itemId }
            prefs[historyItemsKey] = serializeHistoryItems(updated)
        }
    }

    suspend fun repairDuplicateIds() {
        context.todoHistoryDataStore.edit { prefs ->
            val current = prefs[historyItemsKey]?.let { parseHistoryItems(it) } ?: emptyList()
            val normalized = normalizeHistoryItems(current)
            if (normalized != current) {
                prefs[historyItemsKey] = serializeHistoryItems(normalized)
            }
        }
    }

    suspend fun clearAllHistory() {
        context.todoHistoryDataStore.edit { prefs ->
            prefs.remove(historyItemsKey)
        }
    }

    private fun serializeHistoryItems(items: List<TodoHistoryItem>): String {
        return items.joinToString("|||") { item ->
            listOf(
                item.id,
                item.title,
                item.description,
                item.priority.name,
                item.dueDate?.toString() ?: "",
                item.createdAt.toString(),
                item.completedAt.toString(),
                item.completedDate.toString()
            ).joinToString(":::")
        }
    }

    private fun parseHistoryItems(json: String): List<TodoHistoryItem> {
        if (json.isBlank()) return emptyList()
        return json.split("|||").mapNotNull { itemStr ->
            val parts = itemStr.split(":::")
            if (parts.size >= 8) {
                TodoHistoryItem(
                    id = parts[0],
                    title = parts[1],
                    description = parts[2],
                    priority = try { Priority.valueOf(parts[3]) } catch (e: Exception) { Priority.MEDIUM },
                    dueDate = if (parts[4].isNotBlank()) LocalDate.parse(parts[4]) else null,
                    createdAt = parts[5].toLongOrNull() ?: System.currentTimeMillis(),
                    completedAt = parts[6].toLongOrNull() ?: System.currentTimeMillis(),
                    completedDate = try { LocalDate.parse(parts[7]) } catch (e: Exception) { LocalDate.now() }
                )
            } else null
        }
    }

    private fun normalizeHistoryItems(items: List<TodoHistoryItem>): List<TodoHistoryItem> {
        val usedIds = mutableSetOf<String>()
        return items.map { item ->
            if (usedIds.add(item.id)) {
                item
            } else {
                item.copy(id = UUID.randomUUID().toString())
            }
        }
    }
}
