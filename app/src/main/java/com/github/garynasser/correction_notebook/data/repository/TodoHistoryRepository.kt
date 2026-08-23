package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.garynasser.correction_notebook.data.model.home.TodoHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.todoHistoryDataStore: DataStore<Preferences> by preferencesDataStore("todo_history_prefs")

class TodoHistoryRepository(private val context: Context) {

    private val historyItemsKey = stringPreferencesKey("todo_history_items")

    val historyItems: Flow<List<TodoHistoryItem>> = context.todoHistoryDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[historyItemsKey]?.let { json ->
                normalizeHistoryItems(TodoPreferenceCodec.parseHistoryItems(json))
            } ?: emptyList()
        }

    suspend fun addHistoryItem(item: TodoHistoryItem) {
        context.todoHistoryDataStore.edit { prefs ->
            val current = prefs[historyItemsKey]?.let(TodoPreferenceCodec::parseHistoryItems) ?: emptyList()
            val updated = normalizeHistoryItems(current + item)
            prefs[historyItemsKey] = TodoPreferenceCodec.serializeHistoryItems(updated)
        }
    }

    suspend fun deleteHistoryItem(itemId: String) {
        context.todoHistoryDataStore.edit { prefs ->
            val current = prefs[historyItemsKey]?.let(TodoPreferenceCodec::parseHistoryItems) ?: emptyList()
            val updated = current.filter { it.id != itemId }
            prefs[historyItemsKey] = TodoPreferenceCodec.serializeHistoryItems(updated)
        }
    }

    suspend fun repairDuplicateIds() {
        context.todoHistoryDataStore.edit { prefs ->
            val current = prefs[historyItemsKey]?.let(TodoPreferenceCodec::parseHistoryItems) ?: emptyList()
            val normalized = normalizeHistoryItems(current)
            if (normalized != current) {
                prefs[historyItemsKey] = TodoPreferenceCodec.serializeHistoryItems(normalized)
            }
        }
    }

    suspend fun clearAllHistory() {
        context.todoHistoryDataStore.edit { prefs ->
            prefs.remove(historyItemsKey)
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
