package com.github.garynasser.correction_notebook.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.local.StudyPreferencesManager
import com.github.garynasser.correction_notebook.data.model.home.Article
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import com.github.garynasser.correction_notebook.data.model.home.SessionType
import com.github.garynasser.correction_notebook.data.model.home.StudySession
import com.github.garynasser.correction_notebook.data.model.home.TodoHistoryItem
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import com.github.garynasser.correction_notebook.data.repository.ArticleRepository
import com.github.garynasser.correction_notebook.data.repository.StudySessionRepository
import com.github.garynasser.correction_notebook.data.repository.TodoHistoryRepository
import com.github.garynasser.correction_notebook.data.repository.TodoRepository
import com.github.garynasser.correction_notebook.domain.usecase.StudyTimerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

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
    val activeTimerMode: ActiveTimerMode = ActiveTimerMode.NONE,  // Tracks what timer was started
    val showStatistics: Boolean = false,
    val backgroundImageUri: String? = null,
    val isLandscapeOrientation: Boolean = false,
    val pomodoroSettings: PomodoroSettings = PomodoroSettings(),
    val showPomodoroSettingsDialog: Boolean = false,
    val showTodoHistory: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)

enum class ActiveTimerMode {
    NONE, POMODORO, COUNTDOWN, STOPWATCH
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val todoRepository = TodoRepository(application)
    private val articleRepository = ArticleRepository()
    private val studyPreferencesManager = StudyPreferencesManager(application)
    private val studySessionRepository = StudySessionRepository(application)
    private val todoHistoryRepository = TodoHistoryRepository(application)

    val timerManager = StudyTimerManager(viewModelScope)
    private var sessionPersisted = false
    private var currentSessionStartedAt: LocalDateTime? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
        loadPomodoroSettings()
        loadAlertSettings()
    }

    private suspend fun refreshTodayMinutes() {
        refreshTodayStats()
    }

    private suspend fun refreshTodayStats() {
        val todayStats = studySessionRepository.getTodayStats()
        _uiState.value = _uiState.value.copy(
            todayStudyMinutes = todayStats.totalStudyMinutes,
            completedPomodoros = todayStats.completedPomodoros
        )
    }

    private fun loadPomodoroSettings() {
        viewModelScope.launch {
            studyPreferencesManager.pomodoroSettings.collect { settings ->
                _uiState.value = _uiState.value.copy(pomodoroSettings = settings)
            }
        }
    }

    private fun loadAlertSettings() {
        viewModelScope.launch {
            studyPreferencesManager.soundEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(soundEnabled = enabled)
            }
        }
        viewModelScope.launch {
            studyPreferencesManager.vibrationEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(vibrationEnabled = enabled)
            }
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            studyPreferencesManager.setSoundEnabled(enabled)
            _uiState.value = _uiState.value.copy(soundEnabled = enabled)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            studyPreferencesManager.setVibrationEnabled(enabled)
            _uiState.value = _uiState.value.copy(vibrationEnabled = enabled)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Load todos - incomplete first, completed at bottom sorted by completion time
            todoRepository.todoItems.collect { todos ->
                val sortedTodos = todos
                    .sortedWith(
                        compareBy<TodoItem> { it.isCompleted }
                            .thenByDescending { it.completedAt ?: 0 }
                    )
                _uiState.value = _uiState.value.copy(
                    todoItems = sortedTodos
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
            refreshTodayStats()
            _uiState.value = _uiState.value.copy(isLoading = false)
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
            // Get the todo before toggling to add to history if completing
            val todo = todoRepository.getTodoById(todoId)
            val wasCompleted = todo?.isCompleted ?: false

            todoRepository.toggleComplete(todoId)

            // If we just completed this todo (was not completed, now is), add to history
            if (!wasCompleted && todo != null) {
                val completedAt = System.currentTimeMillis()
                val historyItem = TodoHistoryItem(
                    id = todo.id,
                    title = todo.title,
                    description = todo.description,
                    priority = todo.priority,
                    dueDate = todo.dueDate,
                    createdAt = todo.createdAt,
                    completedAt = completedAt,
                    completedDate = java.time.LocalDate.now()
                )
                todoHistoryRepository.addHistoryItem(historyItem)
            }
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
        _uiState.value = _uiState.value.copy(selectedMode = null, activeTimerMode = ActiveTimerMode.NONE)
    }

    fun showStatistics() {
        _uiState.value = _uiState.value.copy(showStatistics = true)
    }

    fun hideStatistics() {
        _uiState.value = _uiState.value.copy(showStatistics = false)
    }

    fun startPomodoro() {
        markSessionStarted()
        _uiState.value = _uiState.value.copy(activeTimerMode = ActiveTimerMode.POMODORO)
        timerManager.startPomodoro(_uiState.value.pomodoroSettings)
    }

    fun updatePomodoroSettings(settings: PomodoroSettings) {
        viewModelScope.launch {
            studyPreferencesManager.updatePomodoroSettings(settings)
            _uiState.value = _uiState.value.copy(
                pomodoroSettings = settings,
                showPomodoroSettingsDialog = false
            )
        }
    }

    fun showPomodoroSettingsDialog() {
        _uiState.value = _uiState.value.copy(showPomodoroSettingsDialog = true)
    }

    fun hidePomodoroSettingsDialog() {
        _uiState.value = _uiState.value.copy(showPomodoroSettingsDialog = false)
    }

    fun showTodoHistory() {
        _uiState.value = _uiState.value.copy(showTodoHistory = true)
    }

    fun hideTodoHistory() {
        _uiState.value = _uiState.value.copy(showTodoHistory = false)
    }

    fun startCountdown(minutes: Int) {
        markSessionStarted()
        _uiState.value = _uiState.value.copy(activeTimerMode = ActiveTimerMode.COUNTDOWN)
        timerManager.startCountdown(minutes)
    }

    fun startStopwatch() {
        markSessionStarted()
        _uiState.value = _uiState.value.copy(activeTimerMode = ActiveTimerMode.STOPWATCH)
        timerManager.startStopwatch()
    }

    fun finishCurrentSessionAndExit() {
        viewModelScope.launch {
            persistCurrentSessionIfNeeded()
            timerManager.stop()
            currentSessionStartedAt = null
            clearSelectedMode()
        }
    }

    private fun markSessionStarted() {
        sessionPersisted = false
        currentSessionStartedAt = LocalDateTime.now()
    }

    private suspend fun persistCurrentSessionIfNeeded() {
        if (sessionPersisted) return

        val snapshot = timerManager.getCurrentSessionSnapshot() ?: return
        if (snapshot.durationMinutes <= 0 && snapshot.pomodoroCount <= 0) return

        val endedAt = LocalDateTime.now()
        val fallbackStart = endedAt.minusMinutes(snapshot.durationMinutes.toLong())
        val startAt = currentSessionStartedAt ?: fallbackStart

        sessionPersisted = true
        studySessionRepository.addSession(
            StudySession(
                subject = snapshot.sessionType.defaultSubject(),
                startTime = if (startAt.isAfter(endedAt)) fallbackStart else startAt,
                endTime = endedAt,
                durationMinutes = snapshot.durationMinutes,
                sessionType = snapshot.sessionType,
                pomodoroCount = snapshot.pomodoroCount
            )
        )
        currentSessionStartedAt = null
        refreshTodayStats()
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

private fun SessionType.defaultSubject(): String {
    return when (this) {
        SessionType.POMODORO -> "番茄钟"
        SessionType.COUNTDOWN -> "倒计时"
        SessionType.STOPWATCH -> "正计时"
    }
}
