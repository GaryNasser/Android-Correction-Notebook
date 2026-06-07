package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.local.StudyPreferencesManager
import com.github.garynasser.correction_notebook.data.model.ai.AiAction
import com.github.garynasser.correction_notebook.data.model.ai.AiActionType
import com.github.garynasser.correction_notebook.data.model.ai.AiPlanBlock
import com.github.garynasser.correction_notebook.data.model.home.Article
import com.github.garynasser.correction_notebook.data.model.home.IcsImportPreview
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.data.model.home.PlannerTab
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import com.github.garynasser.correction_notebook.data.model.home.Priority
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleOccurrence
import com.github.garynasser.correction_notebook.data.model.home.ScheduleRange
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSection
import com.github.garynasser.correction_notebook.data.model.home.SessionType
import com.github.garynasser.correction_notebook.data.model.home.StudySession
import com.github.garynasser.correction_notebook.data.model.home.TodoHistoryItem
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import com.github.garynasser.correction_notebook.data.model.home.TodoSource
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseProgress
import com.github.garynasser.correction_notebook.data.repository.ArticleRepository
import com.github.garynasser.correction_notebook.data.repository.CourseLearningRepository
import com.github.garynasser.correction_notebook.data.repository.IcsImportRepository
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBaseRepository
import com.github.garynasser.correction_notebook.data.repository.ScheduleRepository
import com.github.garynasser.correction_notebook.data.repository.StudySessionRepository
import com.github.garynasser.correction_notebook.data.repository.TodoHistoryRepository
import com.github.garynasser.correction_notebook.data.repository.TodoRepository
import com.github.garynasser.correction_notebook.domain.usecase.AiStudyUseCase
import com.github.garynasser.correction_notebook.domain.usecase.StudyTimerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class StudyMode {
    POMODORO, COUNTDOWN, STOPWATCH, IMMERSIVE
}

data class HomeUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val todoItems: List<TodoItem> = emptyList(),
    val articles: List<Article> = emptyList(),
    val isArticlesLoading: Boolean = false,
    val articleErrorMessage: String? = null,
    val todayStudyMinutes: Int = 0,
    val completedPomodoros: Int = 0,
    val recentCourseProgress: List<CourseProgress> = emptyList(),
    val recentKnowledgeFiles: List<KnowledgeBaseFileSummary> = emptyList(),
    val plannerTab: PlannerTab = PlannerTab.SCHEDULE,
    val scheduleRange: ScheduleRange = ScheduleRange.TODAY,
    val scheduleSections: List<ScheduleSection> = emptyList(),
    val selectedScheduleEvent: ScheduleOccurrence? = null,
    val isLoading: Boolean = false,
    val isImportingSchedule: Boolean = false,
    val showAddTodoDialog: Boolean = false,
    val showAddScheduleDialog: Boolean = false,
    val showModeSelector: Boolean = false,
    val selectedMode: StudyMode? = null,
    val activeTimerMode: ActiveTimerMode = ActiveTimerMode.NONE,  // Tracks what timer was started
    val showStatistics: Boolean = false,
    val backgroundImageUri: String? = null,
    val isLandscapeOrientation: Boolean = false,
    val pomodoroSettings: PomodoroSettings = PomodoroSettings(),
    val showPomodoroSettingsDialog: Boolean = false,
    val showTodoHistory: Boolean = false,
    val pendingIcsPreview: IcsImportPreview? = null,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val aiAdvice: String? = null,
    val aiPlanBlocks: List<AiPlanBlock> = emptyList(),
    val aiActions: List<AiAction> = emptyList(),
    val aiReferencedMemories: List<String> = emptyList(),
    val isAiAdviceLoading: Boolean = false,
    val aiTodoBreakdown: String? = null,
    val aiErrorMessage: String? = null
)

enum class ActiveTimerMode {
    NONE, POMODORO, COUNTDOWN, STOPWATCH
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val articleRepository: ArticleRepository,
    private val studyPreferencesManager: StudyPreferencesManager,
    private val studySessionRepository: StudySessionRepository,
    private val todoHistoryRepository: TodoHistoryRepository,
    private val scheduleRepository: ScheduleRepository,
    private val icsImportRepository: IcsImportRepository,
    private val courseLearningRepository: CourseLearningRepository,
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val aiStudyUseCase: AiStudyUseCase
) : ViewModel() {

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
                            .thenByDescending { it.createdAt }
                    )
                _uiState.value = _uiState.value.copy(
                    todoItems = sortedTodos
                )
                if (_uiState.value.aiPlanBlocks.isEmpty()) refreshLocalPlan()
            }
        }

        viewModelScope.launch {
            scheduleRepository.scheduleEvents.collect {
                refreshScheduleSections()
                if (_uiState.value.aiPlanBlocks.isEmpty()) refreshLocalPlan()
            }
        }

        viewModelScope.launch {
            courseLearningRepository.progressItems.collect { progressItems ->
                _uiState.value = _uiState.value.copy(
                    recentCourseProgress = progressItems
                        .sortedByDescending { it.lastAccessedAt }
                        .take(3)
                )
                if (_uiState.value.aiPlanBlocks.isEmpty()) refreshLocalPlan()
            }
        }

        viewModelScope.launch {
            knowledgeBaseRepository.observeRecentFiles(limit = 3).collect { files ->
                _uiState.value = _uiState.value.copy(recentKnowledgeFiles = files)
                if (_uiState.value.aiPlanBlocks.isEmpty()) refreshLocalPlan()
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

    fun refreshArticles(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isArticlesLoading = true,
                articleErrorMessage = null
            )
            runCatching {
                articleRepository.getRecommendedArticles(forceRefresh = forceRefresh)
            }.onSuccess { articles ->
                _uiState.value = _uiState.value.copy(
                    articles = articles,
                    isArticlesLoading = false,
                    articleErrorMessage = null
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isArticlesLoading = false,
                    articleErrorMessage = throwable.message?.takeIf { it.isNotBlank() } ?: "推荐内容加载失败"
                )
            }
        }
    }

    fun setSelectedDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            scheduleRange = ScheduleRange.TODAY,
            plannerTab = PlannerTab.SCHEDULE,
            aiAdvice = null,
            aiActions = emptyList(),
            aiReferencedMemories = emptyList(),
            aiPlanBlocks = emptyList()
        )
        viewModelScope.launch {
            refreshScheduleSections()
            refreshLocalPlan()
        }
    }

    fun setPlannerTab(tab: PlannerTab) {
        _uiState.value = _uiState.value.copy(plannerTab = tab)
    }

    fun setScheduleRange(range: ScheduleRange) {
        _uiState.value = _uiState.value.copy(scheduleRange = range)
        viewModelScope.launch {
            refreshScheduleSections()
        }
    }

    private suspend fun refreshScheduleSections() {
        val state = _uiState.value
        val sections = scheduleRepository.getEventsForRange(state.scheduleRange, today = state.selectedDate)
        _uiState.value = _uiState.value.copy(scheduleSections = sections)
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

    fun generateTodayAdvice() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAiAdviceLoading = true,
                aiErrorMessage = null
            )
            aiStudyUseCase.generateTodayPlan(_uiState.value.selectedDate)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        aiAdvice = result.summary.ifBlank { result.rawText },
                        aiPlanBlocks = result.planBlocks.ifEmpty { buildLocalPlanBlocks() },
                        aiActions = result.actions,
                        aiReferencedMemories = result.referencedMemories,
                        isAiAdviceLoading = false
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isAiAdviceLoading = false,
                        aiErrorMessage = throwable.message ?: "AI 建议生成失败",
                        aiPlanBlocks = buildLocalPlanBlocks()
                    )
                }
        }
    }

    fun refreshLocalPlan() {
        _uiState.value = _uiState.value.copy(aiPlanBlocks = buildLocalPlanBlocks())
    }

    fun breakDownTodo(todo: TodoItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAiAdviceLoading = true,
                aiErrorMessage = null
            )
            aiStudyUseCase.breakDownTodoStructured(todo.title, todo.description)
                .onSuccess { breakdown ->
                    _uiState.value = _uiState.value.copy(
                        aiTodoBreakdown = breakdown.summary.ifBlank { breakdown.rawText },
                        aiActions = breakdown.actions,
                        isAiAdviceLoading = false
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isAiAdviceLoading = false,
                        aiErrorMessage = throwable.message ?: "待办拆解失败"
                    )
                }
        }
    }

    fun dismissAiResult() {
        _uiState.value = _uiState.value.copy(
            aiTodoBreakdown = null,
            aiErrorMessage = null
        )
    }

    fun saveAdviceAsTodo(text: String) {
        val normalized = text.trim()
            .trimStart('-', '•', '*')
            .trim()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            todoRepository.addTodo(
                TodoItem(
                    title = normalized.take(40),
                    description = normalized,
                    priority = Priority.MEDIUM,
                    dueDate = LocalDate.now(),
                    source = TodoSource.AI_TODAY_ADVICE
                )
            )
        }
    }

    fun applyAiAction(action: AiAction) {
        when (action.type) {
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
                            dueDate = LocalDate.now(),
                            source = TodoSource.AI_TODAY_ADVICE,
                            sourceRefId = action.id
                        )
                    )
                }
            }
            AiActionType.SAVE_MEMORY -> {
                val content = action.payload["content"] ?: action.description
                val category = action.payload["category"] ?: "学习偏好"
                viewModelScope.launch {
                    aiStudyUseCase.saveMemory(category, content)
                        .onFailure {
                            _uiState.value = _uiState.value.copy(aiErrorMessage = it.message ?: "保存记忆失败")
                        }
                }
            }
            AiActionType.OPEN_COURSE,
            AiActionType.OPEN_FILE,
            AiActionType.SAVE_COURSE_NOTE -> {
                _uiState.value = _uiState.value.copy(aiErrorMessage = "这个动作需要在对应课程或资料页面执行")
            }
        }
    }

    private fun buildLocalPlanBlocks(): List<AiPlanBlock> {
        val state = _uiState.value
        val scheduleBlocks = state.scheduleSections
            .flatMap { it.items }
            .take(2)
            .map {
                AiPlanBlock(
                    title = it.title,
                    reason = "来自 ${state.selectedDate.format(DateTimeFormatter.ofPattern("M月d日"))} 课表/日程",
                    estimatedMinutes = 45,
                    priority = "HIGH"
                )
            }
        val todoBlocks = state.todoItems
            .filterNot { it.isCompleted }
            .take(2)
            .map {
                AiPlanBlock(
                    title = it.title,
                    reason = "未完成待办",
                    estimatedMinutes = 25,
                    todoId = it.id,
                    priority = it.priority.name
                )
            }
        val courseBlock = state.recentCourseProgress.firstOrNull()?.let {
            AiPlanBlock(
                title = "继续学习 ${it.courseName.ifBlank { "最近课程" }}",
                reason = it.lastSectionTitle.ifBlank { "根据最近观看记录推荐" },
                estimatedMinutes = 30,
                courseId = it.courseId,
                priority = "MEDIUM"
            )
        }
        val fileBlock = state.recentKnowledgeFiles.firstOrNull()?.let {
            AiPlanBlock(
                title = "复习资料：${it.displayName}",
                reason = it.courseName?.let { name -> "关联课程：$name" } ?: "最近使用资料",
                estimatedMinutes = 20,
                fileId = it.id,
                priority = "MEDIUM"
            )
        }
        return (scheduleBlocks + todoBlocks + listOfNotNull(courseBlock, fileBlock))
            .take(5)
            .ifEmpty {
                listOf(
                    AiPlanBlock(
                        title = "导入课表或添加一个待办",
                        reason = "BITStudy 会根据课表、待办、课程和资料生成今日计划",
                        estimatedMinutes = 10,
                        priority = "LOW"
                    )
                )
            }
    }

    fun showAddScheduleDialog() {
        _uiState.value = _uiState.value.copy(showAddScheduleDialog = true)
    }

    fun hideAddScheduleDialog() {
        _uiState.value = _uiState.value.copy(showAddScheduleDialog = false)
    }

    fun addSchedule(event: ScheduleEvent) {
        viewModelScope.launch {
            scheduleRepository.addEvent(event)
            hideAddScheduleDialog()
            refreshScheduleSections()
        }
    }

    fun deleteSchedule(eventId: String) {
        viewModelScope.launch {
            scheduleRepository.deleteEvent(eventId)
            refreshScheduleSections()
        }
    }

    fun importIcs(uri: android.net.Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImportingSchedule = true)
            runCatching {
                icsImportRepository.buildPreview(uri)
            }.onSuccess { preview ->
                _uiState.value = _uiState.value.copy(
                    isImportingSchedule = false,
                    pendingIcsPreview = preview,
                    plannerTab = PlannerTab.SCHEDULE
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isImportingSchedule = false)
            }
        }
    }

    fun dismissIcsPreview() {
        _uiState.value = _uiState.value.copy(pendingIcsPreview = null)
    }

    fun applyIcsPreview(decision: ImportDecision) {
        viewModelScope.launch {
            val preview = _uiState.value.pendingIcsPreview ?: return@launch
            scheduleRepository.applyImportPreview(preview, decision)
            _uiState.value = _uiState.value.copy(pendingIcsPreview = null)
            refreshScheduleSections()
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
                    id = java.util.UUID.randomUUID().toString(),
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

    fun startPomodoro(settings: PomodoroSettings = _uiState.value.pomodoroSettings) {
        markSessionStarted()
        _uiState.value = _uiState.value.copy(activeTimerMode = ActiveTimerMode.POMODORO)
        timerManager.startPomodoro(settings)
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
