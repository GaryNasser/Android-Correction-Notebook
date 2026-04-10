package com.github.garynasser.correction_notebook.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.local.StudyPreferencesManager
import com.github.garynasser.correction_notebook.data.model.home.Article
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import com.github.garynasser.correction_notebook.data.repository.ArticleRepository
import com.github.garynasser.correction_notebook.data.repository.StudySessionRepository
import com.github.garynasser.correction_notebook.data.repository.TodoRepository
import com.github.garynasser.correction_notebook.domain.usecase.StudyTimerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class StudyMode {
    POMODORO, COUNTDOWN, STOPWATCH, IMMERSIVE
}

data class HomeUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val todoItems: List<TodoItem> = emptyList(),
    val articles: List<Article> = emptyList(),
    val todayStudyMinutes: Int = 0,
    val completedPomodoros: Int = 0,
    val isLoading: Boolean = false,
    val showAddTodoDialog: Boolean = false,
    val showModeSelector: Boolean = false,
    val selectedMode: StudyMode? = null,
    val showStatistics: Boolean = false,
    val backgroundImageUri: String? = null,
    val isLandscapeOrientation: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val todoRepository = TodoRepository(application)
    private val articleRepository = ArticleRepository()
    private val studyPreferencesManager = StudyPreferencesManager(application)
    private val studySessionRepository = StudySessionRepository(application)

    val timerManager = StudyTimerManager(viewModelScope)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Load todos
            todoRepository.todoItems.collect { todos ->
                _uiState.value = _uiState.value.copy(
                    todoItems = todos.filter { !it.isCompleted }
                )
            }
        }

        viewModelScope.launch {
            // Load articles
            articleRepository.getArticles().collect { articles ->
                _uiState.value = _uiState.value.copy(articles = articles)
            }
        }

        viewModelScope.launch {
            // Load today's stats
            val todayMinutes = studyPreferencesManager.getTodayStudyMinutes()
            val pomodoros = studyPreferencesManager.pomodorosCompleted.let { flow ->
                var result = 0
                flow.collect { result = it }
                result
            }
            _uiState.value = _uiState.value.copy(
                todayStudyMinutes = todayMinutes,
                completedPomodoros = pomodoros,
                isLoading = false
            )
        }

        viewModelScope.launch {
            // Load background settings
            val bgUri = studyPreferencesManager.backgroundImageUri.first()
            val isLandscape = studyPreferencesManager.isLandscapeOrientation.first()
            _uiState.value = _uiState.value.copy(
                backgroundImageUri = bgUri,
                isLandscapeOrientation = isLandscape
            )
        }
    }

    fun setSelectedDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
    }

    fun showAddTodoDialog() {
        _uiState.value = _uiState.value.copy(showAddTodoDialog = true)
    }

    fun hideAddTodoDialog() {
        _uiState.value = _uiState.value.copy(showAddTodoDialog = false)
    }

    fun addTodo(todo: TodoItem) {
        viewModelScope.launch {
            todoRepository.addTodo(todo)
            hideAddTodoDialog()
        }
    }

    fun toggleTodoComplete(todoId: String) {
        viewModelScope.launch {
            todoRepository.toggleComplete(todoId)
        }
    }

    fun deleteTodo(todoId: String) {
        viewModelScope.launch {
            todoRepository.deleteTodo(todoId)
        }
    }

    fun showModeSelector() {
        _uiState.value = _uiState.value.copy(showModeSelector = true)
    }

    fun hideModeSelector() {
        _uiState.value = _uiState.value.copy(showModeSelector = false)
    }

    fun selectMode(mode: StudyMode) {
        _uiState.value = _uiState.value.copy(
            selectedMode = mode,
            showModeSelector = false
        )
    }

    fun clearSelectedMode() {
        _uiState.value = _uiState.value.copy(selectedMode = null)
    }

    fun showStatistics() {
        _uiState.value = _uiState.value.copy(showStatistics = true)
    }

    fun hideStatistics() {
        _uiState.value = _uiState.value.copy(showStatistics = false)
    }

    fun startPomodoro() {
        timerManager.startPomodoro()
    }

    fun startCountdown(minutes: Int) {
        timerManager.startCountdown(minutes)
    }

    fun startStopwatch() {
        timerManager.startStopwatch()
    }

    fun setBackgroundImage(uri: String?) {
        viewModelScope.launch {
            studyPreferencesManager.setBackgroundImage(uri)
            _uiState.value = _uiState.value.copy(backgroundImageUri = uri)
        }
    }

    fun setLandscapeOrientation(isLandscape: Boolean) {
        viewModelScope.launch {
            studyPreferencesManager.setLandscapeOrientation(isLandscape)
            _uiState.value = _uiState.value.copy(isLandscapeOrientation = isLandscape)
        }
    }
}
