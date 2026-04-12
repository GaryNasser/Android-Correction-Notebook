package com.github.garynasser.correction_notebook.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.home.TodoHistoryItem
import com.github.garynasser.correction_notebook.data.repository.TodoHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodoHistoryUiState(
    val historyItems: List<TodoHistoryItem> = emptyList(),
    val groupedByDate: Map<LocalDate, List<TodoHistoryItem>> = emptyMap(),
    val isLoading: Boolean = false
)

class TodoHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val todoHistoryRepository = TodoHistoryRepository(application)

    private val _uiState = MutableStateFlow(TodoHistoryUiState())
    val uiState: StateFlow<TodoHistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            todoHistoryRepository.historyItems.collect { items ->
                val grouped = items
                    .sortedByDescending { it.completedAt }
                    .groupBy { it.completedDate }
                _uiState.value = _uiState.value.copy(
                    historyItems = items,
                    groupedByDate = grouped,
                    isLoading = false
                )
            }
        }
    }

    fun deleteHistoryItem(itemId: String) {
        viewModelScope.launch {
            todoHistoryRepository.deleteHistoryItem(itemId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            todoHistoryRepository.clearAllHistory()
        }
    }
}
