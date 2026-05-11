package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.domain.usecase.AiStudyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseAssistantUiState(
    val isLoading: Boolean = false,
    val result: String? = null,
    val error: String? = null
)

@HiltViewModel
class CourseAssistantViewModel @Inject constructor(
    private val aiStudyUseCase: AiStudyUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(CourseAssistantUiState())
    val uiState: StateFlow<CourseAssistantUiState> = _uiState.asStateFlow()

    fun summarize(sectionTitle: String, note: String) {
        viewModelScope.launch {
            _uiState.value = CourseAssistantUiState(isLoading = true)
            aiStudyUseCase.summarizeCourseSection(sectionTitle, note)
                .onSuccess { _uiState.value = CourseAssistantUiState(result = it) }
                .onFailure { _uiState.value = CourseAssistantUiState(error = it.message ?: "课程助手生成失败") }
        }
    }

    fun clear() {
        _uiState.value = CourseAssistantUiState()
    }
}
