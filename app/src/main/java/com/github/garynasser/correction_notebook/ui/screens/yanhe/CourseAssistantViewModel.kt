package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.home.Priority
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import com.github.garynasser.correction_notebook.data.model.home.TodoSource
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseNote
import com.github.garynasser.correction_notebook.data.repository.CourseLearningRepository
import com.github.garynasser.correction_notebook.data.repository.TodoRepository
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
    private val aiStudyUseCase: AiStudyUseCase,
    private val courseLearningRepository: CourseLearningRepository,
    private val todoRepository: TodoRepository
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

    fun saveResultAsNote(courseId: Int, courseName: String, sectionId: Int, sectionTitle: String) {
        val content = _uiState.value.result?.trim().orEmpty()
        if (content.isBlank()) return
        viewModelScope.launch {
            courseLearningRepository.saveNote(
                CourseNote(
                    courseId = courseId,
                    courseName = courseName,
                    sectionId = sectionId,
                    sectionTitle = sectionTitle,
                    content = content,
                    aiGenerated = true
                )
            )
        }
    }

    fun saveResultAsTodo(courseId: Int, sectionTitle: String) {
        val content = _uiState.value.result?.trim().orEmpty()
        if (content.isBlank()) return
        viewModelScope.launch {
            todoRepository.addTodo(
                TodoItem(
                    title = "复习：${sectionTitle}".take(40),
                    description = content,
                    priority = Priority.MEDIUM,
                    source = TodoSource.COURSE_ASSISTANT,
                    sourceRefId = courseId.toString()
                )
            )
        }
    }
}
