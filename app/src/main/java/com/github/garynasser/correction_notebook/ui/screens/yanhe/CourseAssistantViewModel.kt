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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseAssistantUiState(
    val isLoading: Boolean = false,
    val isActionBusy: Boolean = false,
    val applyingActionKeys: Set<String> = emptySet(),
    val appliedActionKeys: Set<String> = emptySet(),
    val result: String? = null,
    val actions: List<AiAction> = emptyList(),
    val error: String? = null,
    val actionMessage: String? = null,
    val actionError: String? = null
)

data class CourseNotesUiState(
    val isLoading: Boolean = true,
    val notes: List<CourseNote> = emptyList(),
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
    private var generationJob: Job? = null
    private var actionJob: Job? = null
    private var generationId = 0L
    private var resultSource: Pair<Int, Int>? = null
    private val notesRetry = MutableStateFlow(0)
    @OptIn(ExperimentalCoroutinesApi::class)
    val notesState: StateFlow<CourseNotesUiState> = notesRetry.flatMapLatest {
        courseLearningRepository.notes
            .map { CourseNotesUiState(isLoading = false, notes = it) }
            .onStart { emit(CourseNotesUiState()) }
            .catch { emit(CourseNotesUiState(isLoading = false, error = it.message ?: "课程笔记加载失败")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseNotesUiState())

    fun retryNotes() { notesRetry.update { it + 1 } }

    fun deleteNote(noteId: String) {
        runAssistantAction("delete-note:$noteId", "已删除课程笔记", "删除课程笔记失败") {
            courseLearningRepository.deleteNote(noteId)
        }
    }

    fun summarize(sectionTitle: String, note: String) {
        if (!beginGeneration()) return
        val owner = generationId
        generationJob = viewModelScope.launch {
            aiStudyUseCase.summarizeCourseSection(sectionTitle, note)
                .onSuccess { if (owner == generationId) _uiState.value = CourseAssistantUiState(result = it) }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (owner == generationId) _uiState.value = CourseAssistantUiState(error = it.message ?: "课程助手生成失败")
                }
        }
    }

    fun summarizeLearningPackage(
        courseId: Int,
        courseName: String,
        sectionId: Int,
        sectionTitle: String,
        note: String
    ) {
        if (!beginGeneration()) return
        val owner = generationId
        resultSource = courseId to sectionId
        generationJob = viewModelScope.launch {
            aiStudyUseCase.summarizeCourseSectionStructured(courseId, courseName, sectionId, sectionTitle, note)
                .onSuccess {
                    if (owner == generationId) _uiState.value = CourseAssistantUiState(
                        result = it.summary.ifBlank { it.rawText },
                        actions = it.actions
                    )
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    if (owner == generationId) _uiState.value = CourseAssistantUiState(error = it.message ?: "课程助手生成失败")
                }
        }
    }

    fun clear() {
        generationId++
        resultSource = null
        generationJob?.cancel()
        actionJob?.cancel()
        generationJob = null
        actionJob = null
        _uiState.value = CourseAssistantUiState()
    }

    fun consumeActionMessage() {
        _uiState.update { it.copy(actionMessage = null, actionError = null) }
    }

    fun saveResultAsNote(courseId: Int, courseName: String, sectionId: Int, sectionTitle: String) {
        if (!ownsResult(courseId, sectionId)) return
        val content = _uiState.value.result?.trim().orEmpty()
        if (content.isBlank()) {
            reportActionError("没有可保存的课程笔记")
            return
        }
        runAssistantAction(
            actionKey = courseAssistantResultNoteKey(courseId, sectionId),
            successMessage = "已保存为课程笔记",
            failureMessage = "保存课程笔记失败"
        ) {
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

    fun saveResultAsTodo(courseId: Int, sectionId: Int, sectionTitle: String) {
        if (!ownsResult(courseId, sectionId)) return
        val content = _uiState.value.result?.trim().orEmpty()
        if (content.isBlank()) {
            reportActionError("没有可转为待办的内容")
            return
        }
        runAssistantAction(
            actionKey = courseAssistantResultTodoKey(courseId, sectionId),
            successMessage = "已创建复习待办",
            failureMessage = "创建待办失败"
        ) {
            todoRepository.addTodo(
                TodoItem(
                    title = "复习：${sectionTitle}",
                    description = content,
                    priority = Priority.MEDIUM,
                    source = TodoSource.COURSE_ASSISTANT,
                    sourceRefId = courseId.toString()
                )
            )
        }
    }

    fun applyAction(action: AiAction, courseId: Int, courseName: String, sectionId: Int, sectionTitle: String) {
        if (!ownsResult(courseId, sectionId) || action !in _uiState.value.actions) return
        when (action.type) {
            AiActionType.SAVE_COURSE_NOTE -> {
                val content = action.payload["content"] ?: action.description.ifBlank { action.title }
                if (content.isBlank()) {
                    reportActionError("AI 动作缺少课程笔记内容")
                    return
                }
                runAssistantAction(
                    actionKey = courseAssistantAiActionKey(action.id),
                    successMessage = "已保存课程笔记",
                    failureMessage = "保存课程笔记失败"
                ) {
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
                if (content.isBlank()) {
                    reportActionError("AI 动作缺少待办内容")
                    return
                }
                runAssistantAction(
                    actionKey = courseAssistantAiActionKey(action.id),
                    successMessage = "已创建待办",
                    failureMessage = "创建待办失败"
                ) {
                    todoRepository.addTodo(
                        TodoItem(
                            title = action.title,
                            description = content,
                            priority = parseCourseAssistantPriority(action.payload["priority"]),
                            source = TodoSource.COURSE_ASSISTANT,
                            sourceRefId = courseId.toString()
                        )
                    )
                }
            }
            else -> reportActionError("当前课程页面暂不支持这个 AI 动作")
        }
    }

    private fun runAssistantAction(
        actionKey: String,
        successMessage: String,
        failureMessage: String,
        action: suspend () -> Unit
    ) {
        val state = _uiState.value
        if (state.isLoading || !canStartCourseAssistantAction(actionKey, state.isActionBusy, state.appliedActionKeys)) return
        val owner = generationId
        _uiState.value = state.copy(
            isActionBusy = true,
            applyingActionKeys = state.applyingActionKeys + actionKey,
            actionMessage = null,
            actionError = null
        )
        actionJob = viewModelScope.launch {
            try {
                action()
                if (owner != generationId) return@launch
                _uiState.update {
                    it.copy(
                        isActionBusy = false,
                        applyingActionKeys = it.applyingActionKeys - actionKey,
                        appliedActionKeys = it.appliedActionKeys + actionKey,
                        actionMessage = successMessage
                    )
                }
            } catch (error: CancellationException) {
                if (owner == generationId) _uiState.update {
                    it.copy(
                        isActionBusy = false,
                        applyingActionKeys = it.applyingActionKeys - actionKey
                    )
                }
                throw error
            } catch (error: Exception) {
                if (owner == generationId) _uiState.update {
                    it.copy(
                        isActionBusy = false,
                        applyingActionKeys = it.applyingActionKeys - actionKey,
                        actionError = error.message?.takeIf(String::isNotBlank) ?: failureMessage
                    )
                }
            }
        }
    }

    private fun beginGeneration(): Boolean {
        val state = _uiState.value
        if (state.isLoading || state.isActionBusy) return false
        generationId++
        resultSource = null
        _uiState.value = CourseAssistantUiState(isLoading = true)
        return true
    }

    private fun ownsResult(courseId: Int, sectionId: Int): Boolean {
        val state = _uiState.value
        if (state.isLoading || state.result == null) return false
        if (resultSource == (courseId to sectionId)) return true
        reportActionError("请先为当前章节生成学习包")
        return false
    }

    private fun reportActionError(message: String) {
        _uiState.update { it.copy(actionMessage = null, actionError = message) }
    }
}

internal fun courseAssistantAiActionKey(actionId: String): String = "ai:$actionId"

internal fun courseAssistantResultNoteKey(courseId: Int, sectionId: Int): String =
    "result-note:$courseId:$sectionId"

internal fun courseAssistantResultTodoKey(courseId: Int, sectionId: Int): String =
    "result-todo:$courseId:$sectionId"

internal fun canStartCourseAssistantAction(
    actionKey: String,
    isActionBusy: Boolean,
    appliedActionKeys: Set<String>
): Boolean = actionKey.isNotBlank() && !isActionBusy && actionKey !in appliedActionKeys

internal fun parseCourseAssistantPriority(raw: String?): Priority {
    return when (raw?.trim()?.uppercase()) {
        Priority.HIGH.name, "高", "重要", "URGENT" -> Priority.HIGH
        Priority.LOW.name, "低", "稍后" -> Priority.LOW
        else -> Priority.MEDIUM
    }
}
