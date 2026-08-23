package com.github.garynasser.correction_notebook.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.home.TodoHistoryItem
import com.github.garynasser.correction_notebook.data.repository.TodoHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodoHistoryUiState(
    val historyItems: List<TodoHistoryItem> = emptyList(),
    val groupedByDate: Map<LocalDate, List<TodoHistoryItem>> = emptyMap(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val message: String? = null
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
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                todoHistoryRepository.repairDuplicateIds()
                todoHistoryRepository.historyItems.collect { items ->
                    val grouped = items
                        .sortedByDescending { it.completedAt }
                        .groupBy { it.completedDate }
                    _uiState.update {
                        it.copy(
                            historyItems = items,
                            groupedByDate = grouped,
                            isLoading = false
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "完成历史加载失败"
                    )
                }
            }
        }
    }

    fun deleteHistoryItem(itemId: String) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, message = null) }
            try {
                todoHistoryRepository.deleteHistoryItem(itemId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "删除失败，请稍后再试") }
            } finally {
                _uiState.update { it.copy(isMutating = false) }
            }
        }
    }

    fun clearAllHistory() {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, message = null) }
            try {
                todoHistoryRepository.clearAllHistory()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "清空失败，请稍后再试") }
            } finally {
                _uiState.update { it.copy(isMutating = false) }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
