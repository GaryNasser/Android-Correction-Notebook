package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.ai.AiAction
import com.github.garynasser.correction_notebook.data.model.ai.AiActionType
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
    val actions: List<AiAction> = emptyList(),
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

    fun summarizeLearningPackage(
        courseId: Int,
        courseName: String,
        sectionId: Int,
        sectionTitle: String,
        note: String
    ) {
        viewModelScope.launch {
            _uiState.value = CourseAssistantUiState(isLoading = true)
            aiStudyUseCase.summarizeCourseSectionStructured(courseId, courseName, sectionId, sectionTitle, note)
                .onSuccess {
                    _uiState.value = CourseAssistantUiState(
                        result = it.summary.ifBlank { it.rawText },
                        actions = it.actions
                    )
                }
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

    fun applyAction(action: AiAction, courseId: Int, courseName: String, sectionId: Int, sectionTitle: String) {
        when (action.type) {
            AiActionType.SAVE_COURSE_NOTE -> {
                val content = action.payload["content"] ?: action.description.ifBlank { action.title }
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
            AiActionType.CREATE_TODO, AiActionType.CREATE_REVIEW_PLAN -> {
                val content = action.payload["content"] ?: action.description.ifBlank { action.title }
                if (content.isBlank()) return
                viewModelScope.launch {
                    todoRepository.addTodo(
                        TodoItem(
                            title = action.title.take(40),
                            description = content,
                            priority = runCatching {
                                Priority.valueOf(action.payload["priority"].orEmpty())
                            }.getOrDefault(Priority.MEDIUM),
                            source = TodoSource.COURSE_ASSISTANT,
                            sourceRefId = courseId.toString()
                        )
                    )
                }
            }
            else -> Unit
        }
    }
}
