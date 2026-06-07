package com.github.garynasser.correction_notebook.ui.screens.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.garynasser.correction_notebook.data.model.home.Article
import com.github.garynasser.correction_notebook.data.model.ai.AiAction
import com.github.garynasser.correction_notebook.data.model.ai.AiActionType
import com.github.garynasser.correction_notebook.data.model.ai.AiPlanBlock
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.data.model.home.PlannerTab
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseProgress
import com.github.garynasser.correction_notebook.ui.components.FreshCard
import com.github.garynasser.correction_notebook.ui.components.FreshGradientCard
import com.github.garynasser.correction_notebook.ui.components.FreshScreen
import com.github.garynasser.correction_notebook.ui.components.MetricTile
import com.github.garynasser.correction_notebook.ui.components.SectionHeader
import com.github.garynasser.correction_notebook.ui.screens.statistics.StatisticsScreen
import com.github.garynasser.correction_notebook.ui.screens.statistics.StatisticsViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    statisticsViewModel: StatisticsViewModel = hiltViewModel(),
    onNavigateToStatistics: () -> Unit = {},
    onNavigateToCourses: (CourseProgress?) -> Unit = {},
    onOpenCourse: (Int, String) -> Unit = { _, _ -> },
    onNavigateToKnowledgeBase: () -> Unit = {},
    onOpenKnowledgeFile: (String) -> Unit = {},
    onImmersiveModeChanged: (Boolean) -> Unit = {},
    onOpenArticle: (Article) -> Unit = {}
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val timerState by homeViewModel.timerManager.timerState.collectAsState()
    var showCustomTimer by remember { mutableStateOf(false) }
    var startPomodoroAfterSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    fun jumpToPlanner(tab: PlannerTab) {
        homeViewModel.setPlannerTab(tab)
        coroutineScope.launch {
            listState.animateScrollToItem(HOME_PLANNER_INDEX)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            saveBackgroundImageToAppStorage(context, it)?.let(homeViewModel::setBackgroundImage)
        }
    }
    val icsPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { homeViewModel.importIcs(it) }
    }

    // Handle immersive mode
    if (uiState.selectedMode == StudyMode.IMMERSIVE) {
        LaunchedEffect(Unit) {
            onImmersiveModeChanged(true)
        }
        DisposableEffect(Unit) {
            onDispose {
                onImmersiveModeChanged(false)
            }
        }
        ImmersiveStudyScreen(
            timerManager = homeViewModel.timerManager,
            onExit = { homeViewModel.finishCurrentSessionAndExit() },
            onStop = { homeViewModel.finishCurrentSessionAndExit() },
            backgroundImageUri = uiState.backgroundImageUri,
            soundEnabled = uiState.soundEnabled,
            vibrationEnabled = uiState.vibrationEnabled,
            onSoundEnabledChange = { homeViewModel.setSoundEnabled(it) },
            onVibrationEnabledChange = { homeViewModel.setVibrationEnabled(it) },
            pomodoroSettings = uiState.pomodoroSettings,
            onPomodoroSettingsSave = { settings -> homeViewModel.updatePomodoroSettings(settings) },
            isPomodoroMode = uiState.activeTimerMode == ActiveTimerMode.POMODORO
        )
        return
    }

    // Handle statistics screen
    if (uiState.showStatistics) {
        StatisticsScreen(
            viewModel = statisticsViewModel,
            onBack = { homeViewModel.hideStatistics() }
        )
        return
    }

    // Handle todo history screen
    if (uiState.showTodoHistory) {
        TodoHistoryScreen(
            onBack = { homeViewModel.hideTodoHistory() }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "BITStudy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { homeViewModel.showStatistics() }) {
                        Icon(Icons.Default.BarChart, contentDescription = "统计")
                    }
                    IconButton(onClick = { homeViewModel.showModeSelector() }) {
                        Icon(Icons.Default.Timer, contentDescription = "学习模式")
                    }
                }
            )
        }
    ) { innerPadding ->
        FreshScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(10.dp)) }

            item {
                TodayStudyWorkbench(
                    timerState = timerState,
                    todayMinutes = uiState.todayStudyMinutes,
                    completedPomodoros = uiState.completedPomodoros,
                    scheduleCount = uiState.scheduleSections.firstOrNull()?.items?.size ?: 0,
                    todoCount = uiState.todoItems.count { !it.isCompleted },
                    recentCourses = uiState.recentCourseProgress,
                    recentFiles = uiState.recentKnowledgeFiles,
                    onImmersiveModeClick = { homeViewModel.showModeSelector() },
                    onScheduleClick = { jumpToPlanner(PlannerTab.SCHEDULE) },
                    onTodoClick = { jumpToPlanner(PlannerTab.TODO) },
                    onContinueLearningClick = { onNavigateToCourses(uiState.recentCourseProgress.firstOrNull()) },
                    onRecentFilesClick = onNavigateToKnowledgeBase
                )
            }

            item {
                QuickStatsPreview(
                    todayMinutes = uiState.todayStudyMinutes,
                    completedPomodoros = uiState.completedPomodoros,
                    onClick = { homeViewModel.showStatistics() }
                )
            }

            item {
                SectionHeader(
                    title = "AI 学习建议",
                    subtitle = "结合日程、待办和学习记录给你一个轻提醒"
                )
            }

            item {
                AiStudyAdviceCard(
                    advice = uiState.aiAdvice,
                    planBlocks = uiState.aiPlanBlocks,
                    actions = uiState.aiActions,
                    referencedMemories = uiState.aiReferencedMemories,
                    selectedDate = uiState.selectedDate,
                    isLoading = uiState.isAiAdviceLoading,
                    onGenerate = { homeViewModel.generateTodayAdvice() },
                    onSaveAdvice = { homeViewModel.saveAdviceAsTodo(it) },
                    onApplyAction = { action ->
                        when (action.type) {
                            AiActionType.OPEN_COURSE -> {
                                val courseId = action.payload["courseId"]?.toIntOrNull()
                                if (courseId != null) {
                                    onOpenCourse(courseId, action.payload["courseName"] ?: action.title)
                                } else {
                                    homeViewModel.applyAiAction(action)
                                }
                            }
                            AiActionType.OPEN_FILE -> {
                                val fileId = action.payload["fileId"]
                                if (!fileId.isNullOrBlank()) {
                                    onOpenKnowledgeFile(fileId)
                                } else {
                                    homeViewModel.applyAiAction(action)
                                }
                            }
                            else -> homeViewModel.applyAiAction(action)
                        }
                    },
                    onStartFocus = { homeViewModel.showModeSelector() },
                    onOpenCourse = onOpenCourse,
                    onOpenFile = onOpenKnowledgeFile
                )
            }

            item {
                PlannerSection(
                    uiState = uiState,
                    onPlannerTabChange = { homeViewModel.setPlannerTab(it) },
                    onDateChange = { homeViewModel.setSelectedDate(it) },
                    onImportIcs = { icsPickerLauncher.launch("*/*") },
                    onAddSchedule = { homeViewModel.showAddScheduleDialog() },
                    onAddTodo = { homeViewModel.showAddTodoDialog() },
                    onShowTodoHistory = { homeViewModel.showTodoHistory() },
                    onToggleTodo = { homeViewModel.toggleTodoComplete(it) },
                    onBreakDownTodo = { homeViewModel.breakDownTodo(it) },
                    onDeleteTodo = { homeViewModel.deleteTodo(it) },
                    onDeleteSchedule = { homeViewModel.deleteSchedule(it) }
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
        }
    }

    // Add Todo Dialog
    if (uiState.showAddTodoDialog) {
        AddTodoDialog(
            onDismiss = { homeViewModel.hideAddTodoDialog() },
            onAdd = { todo -> homeViewModel.addTodo(todo) }
        )
    }

    if (uiState.showAddScheduleDialog) {
        AddScheduleDialog(
            onDismiss = { homeViewModel.hideAddScheduleDialog() },
            onAdd = { event -> homeViewModel.addSchedule(event) }
        )
    }

    uiState.pendingIcsPreview?.let { preview ->
        IcsImportPreviewDialog(
            preview = preview,
            onDismiss = { homeViewModel.dismissIcsPreview() },
            onApply = { decision: ImportDecision ->
                homeViewModel.applyIcsPreview(decision)
            }
        )
    }

    if (uiState.aiTodoBreakdown != null || uiState.aiErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { homeViewModel.dismissAiResult() },
            title = { Text(if (uiState.aiTodoBreakdown != null) "AI 待办拆解" else "AI 提示") },
            text = {
                Text(uiState.aiTodoBreakdown ?: uiState.aiErrorMessage.orEmpty())
            },
            confirmButton = {
                TextButton(onClick = { homeViewModel.dismissAiResult() }) {
                    Text("知道了")
                }
            }
        )
    }

    // Mode Selector Dialog
    if (uiState.showModeSelector) {
        ModeSelectorDialog(
            currentBackgroundUri = uiState.backgroundImageUri,
            onDismiss = { homeViewModel.hideModeSelector() },
            onModeSelected = { mode ->
                homeViewModel.hideModeSelector()
                when (mode) {
                    "pomodoro" -> {
                        startPomodoroAfterSettings = true
                        homeViewModel.showPomodoroSettingsDialog()
                    }
                    "countdown" -> {
                        showCustomTimer = true
                    }
                    "stopwatch" -> {
                        homeViewModel.startStopwatch()
                        homeViewModel.selectMode(StudyMode.IMMERSIVE)
                    }
                }
            },
            onSelectBackground = {
                imagePickerLauncher.launch("image/*")
            },
            onClearBackground = {
                homeViewModel.setBackgroundImage(null)
            }
        )
    }

    // Custom Timer Dialog
    if (showCustomTimer) {
        CustomTimerDialog(
            onDismiss = { showCustomTimer = false },
            onConfirm = { minutes ->
                homeViewModel.startCountdown(minutes)
                homeViewModel.selectMode(StudyMode.IMMERSIVE)
                showCustomTimer = false
            }
        )
    }

    // Pomodoro Settings Dialog
    if (uiState.showPomodoroSettingsDialog) {
        PomodoroSettingsDialog(
            currentSettings = uiState.pomodoroSettings,
            onDismiss = {
                startPomodoroAfterSettings = false
                homeViewModel.hidePomodoroSettingsDialog()
            },
            onSave = { settings ->
                homeViewModel.updatePomodoroSettings(settings)
                if (startPomodoroAfterSettings) {
                    homeViewModel.startPomodoro(settings)
                    homeViewModel.selectMode(StudyMode.IMMERSIVE)
                    startPomodoroAfterSettings = false
                }
            }
        )
    }
}

private const val HOME_PLANNER_INDEX = 5

@Composable
private fun AiStudyAdviceCard(
    advice: String?,
    planBlocks: List<AiPlanBlock>,
    actions: List<AiAction>,
    referencedMemories: List<String>,
    selectedDate: LocalDate,
    isLoading: Boolean,
    onGenerate: () -> Unit,
    onSaveAdvice: (String) -> Unit,
    onApplyAction: (AiAction) -> Unit,
    onStartFocus: () -> Unit,
    onOpenCourse: (Int, String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    FreshCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${selectedDate.format(DateTimeFormatter.ofPattern("M月d日"))}建议",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onGenerate, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (advice == null) "生成" else "刷新")
                    }
                }
            }
            if (advice.isNullOrBlank()) {
                Text(
                    text = "结合今日日程、待办和学习记录，生成 3-5 条可执行建议。AI 未配置时，课表、待办、资料和计时功能仍然可用。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
            } else {
                Text(
                    text = advice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                )
            }

            if (referencedMemories.isNotEmpty()) {
                Text(
                    text = "参考记忆：${referencedMemories.joinToString("、")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (planBlocks.isNotEmpty()) {
                Text(
                    text = "${selectedDate.format(DateTimeFormatter.ofPattern("M月d日"))}学习计划",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    planBlocks.forEach { block ->
                        AiPlanBlockRow(
                            block = block,
                            onStartFocus = onStartFocus,
                            onSaveAdvice = onSaveAdvice,
                            onOpenCourse = onOpenCourse,
                            onOpenFile = onOpenFile
                        )
                    }
                }
            } else if (!advice.isNullOrBlank()) {
                adviceLines(advice).forEach { line ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onSaveAdvice(line) }) {
                                Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("转待办")
                            }
                        }
                    }
                }
            }

            if (actions.isNotEmpty()) {
                Text(
                    text = "可确认执行",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { action ->
                        AiActionRow(action = action, onApply = { onApplyAction(action) })
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AiPlanBlockRow(
    block: AiPlanBlock,
    onStartFocus: () -> Unit,
    onSaveAdvice: (String) -> Unit,
    onOpenCourse: (Int, String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = { Text("${block.estimatedMinutes} 分钟") }
                )
            }
            if (block.reason.isNotBlank()) {
                Text(
                    text = block.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onStartFocus) {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("开始专注")
                }
                TextButton(onClick = { onSaveAdvice("${block.title}：${block.reason}") }) {
                    Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("转待办")
                }
                block.courseId?.let { courseId ->
                    TextButton(onClick = { onOpenCourse(courseId, block.title.removePrefix("继续学习 ")) }) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("打开课程")
                    }
                }
                block.fileId?.let { fileId ->
                    TextButton(onClick = { onOpenFile(fileId) }) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("打开资料")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiActionRow(
    action: AiAction,
    onApply: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = when (action.type) {
                    AiActionType.CREATE_TODO,
                    AiActionType.CREATE_REVIEW_PLAN -> Icons.Default.AddTask
                    AiActionType.SAVE_MEMORY -> Icons.Default.BookmarkAdd
                    AiActionType.SAVE_COURSE_NOTE -> Icons.Default.NoteAlt
                    AiActionType.OPEN_COURSE -> Icons.Default.School
                    AiActionType.OPEN_FILE -> Icons.Default.Description
                },
                contentDescription = null
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(action.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (action.description.isNotBlank()) {
                    Text(
                        action.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        maxLines = 2
                    )
                }
            }
            TextButton(onClick = onApply) { Text("确认") }
        }
    }
}

private fun adviceLines(advice: String): List<String> {
    return advice.lines()
        .map { it.trim().trimStart('-', '•', '*').trim() }
        .filter { it.isNotBlank() }
        .take(5)
        .ifEmpty { listOf(advice.trim()) }
}

private fun saveBackgroundImageToAppStorage(context: Context, sourceUri: Uri): String? {
    val backgroundsDir = File(context.filesDir, "immersive_backgrounds").apply {
        mkdirs()
    }
    val targetFile = File(backgroundsDir, "background_${System.currentTimeMillis()}.jpg")
    return runCatching {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null
        backgroundsDir.listFiles()
            ?.filter { it != targetFile }
            ?.forEach { it.delete() }
        Uri.fromFile(targetFile).toString()
    }.getOrNull()
}

@Composable
fun TodayStudyWorkbench(
    timerState: TimerState,
    todayMinutes: Int,
    completedPomodoros: Int,
    scheduleCount: Int,
    todoCount: Int,
    recentCourses: List<CourseProgress>,
    recentFiles: List<KnowledgeBaseFileSummary>,
    onImmersiveModeClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onTodoClick: () -> Unit,
    onContinueLearningClick: () -> Unit,
    onRecentFilesClick: () -> Unit
) {
    val currentTime = remember { mutableStateOf(java.time.LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = java.time.LocalTime.now()
            kotlinx.coroutines.delay(1000)
        }
    }

    FreshGradientCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 188.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "今天的学习节奏",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
                Text(
                    text = currentTime.value.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 56.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 E")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
                ) {
                    Text(
                        text = buildString {
                            append("今日 ")
                            append(formatMinutesToDisplay(todayMinutes))
                            append(" · ")
                            append(completedPomodoros)
                            append(" 个番茄钟")
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    )
                }
                FilledTonalButton(onClick = onImmersiveModeClick) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("专注")
                }
            }

            TodaySummaryGrid(
                scheduleCount = scheduleCount,
                todoCount = todoCount,
                recentCourses = recentCourses,
                recentFiles = recentFiles,
                onScheduleClick = onScheduleClick,
                onTodoClick = onTodoClick,
                onContinueLearningClick = onContinueLearningClick,
                onRecentFilesClick = onRecentFilesClick
            )

            if (timerState !is TimerState.Idle) {
                AssistChip(
                    onClick = onImmersiveModeClick,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            when (timerState) {
                                is TimerState.Pomodoro -> "番茄钟运行中"
                                is TimerState.Countdown -> "倒计时运行中"
                                is TimerState.Stopwatch -> "计时中"
                                else -> ""
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TodaySummaryGrid(
    scheduleCount: Int,
    todoCount: Int,
    recentCourses: List<CourseProgress>,
    recentFiles: List<KnowledgeBaseFileSummary>,
    onScheduleClick: () -> Unit,
    onTodoClick: () -> Unit,
    onContinueLearningClick: () -> Unit,
    onRecentFilesClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            WorkbenchChip(
                icon = Icons.Default.Event,
                label = "今日课程/日程",
                value = if (scheduleCount == 0) "待导入课表" else "$scheduleCount 项",
                onClick = onScheduleClick,
                modifier = Modifier.weight(1f)
            )
            WorkbenchChip(
                icon = Icons.Default.Checklist,
                label = "待办",
                value = if (todoCount == 0) "暂无压力" else "$todoCount 项",
                onClick = onTodoClick,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            WorkbenchChip(
                icon = Icons.Default.School,
                label = "继续学习",
                value = recentCourses.firstOrNull()?.lastSectionTitle?.ifBlank { recentCourses.first().courseName } ?: "先看一节课",
                onClick = onContinueLearningClick,
                modifier = Modifier.weight(1f)
            )
            WorkbenchChip(
                icon = Icons.Default.FolderOpen,
                label = "最近资料",
                value = recentFiles.firstOrNull()?.displayName ?: "去 BITShare 找资料",
                onClick = onRecentFilesClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WorkbenchChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.64f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun DateSelector(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }
    val dates = remember(today) { (-30..30).map { today.plusDays(it.toLong()) } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 27)

    LaunchedEffect(selectedDate) {
        val selectedIndex = dates.indexOf(selectedDate)
        if (selectedIndex >= 0) {
            listState.animateScrollToItem((selectedIndex - 3).coerceAtLeast(0))
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(dates) { date ->
            val isSelected = date == selectedDate
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDateChange(date) }
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .width(56.dp)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (date == today) {
                        "今天"
                    } else {
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
                    },
                    fontSize = 12.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun QuickStatsPreview(
    todayMinutes: Int,
    completedPomodoros: Int,
    onClick: () -> Unit
) {
    FreshCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricTile(
                icon = Icons.Default.Timer,
                value = formatMinutesToDisplay(todayMinutes),
                label = "今日学习",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                icon = Icons.Default.EmojiEvents,
                value = "$completedPomodoros",
                label = "番茄钟",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun EmptyTodoState(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无待办事项",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加待办")
            }
        }
    }
}

@Composable
fun ModeSelectorDialog(
    currentBackgroundUri: String?,
    onDismiss: () -> Unit,
    onModeSelected: (String) -> Unit,
    onSelectBackground: () -> Unit,
    onClearBackground: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择学习模式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModeOption(
                    icon = Icons.Default.Timer,
                    title = "番茄钟",
                    description = "25分钟专注，5分钟休息",
                    onClick = { onModeSelected("pomodoro") }
                )
                ModeOption(
                    icon = Icons.Default.HourglassEmpty,
                    title = "倒计时",
                    description = "设定时间，专注完成",
                    onClick = { onModeSelected("countdown") }
                )
                ModeOption(
                    icon = Icons.Default.PlayArrow,
                    title = "正计时",
                    description = "记录学习时长",
                    onClick = { onModeSelected("stopwatch") }
                )

                HorizontalDivider()

                Text(
                    text = "沉浸模式背景图片",
                    style = MaterialTheme.typography.titleSmall
                )

                if (currentBackgroundUri != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        coil.compose.AsyncImage(
                            model = Uri.parse(currentBackgroundUri),
                            contentDescription = "当前背景",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSelectBackground,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (currentBackgroundUri == null) "上传背景图" else "更换背景图")
                    }

                    if (currentBackgroundUri != null) {
                        OutlinedButton(
                            onClick = onClearBackground,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("清除")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun ModeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private fun formatMinutesToDisplay(minutes: Int): String {
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        "${hours}h${mins}m"
    } else {
        "${minutes}m"
    }
}

@Composable
fun CustomTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("25") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义计时器") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "设置您想要的学习时长",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours input
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                hours = newValue.take(2)
                            }
                        },
                        label = { Text("小时") },
                        modifier = Modifier.width(80.dp),
                        singleLine = true
                    )
                    Text(":", style = MaterialTheme.typography.headlineMedium)
                    // Minutes input
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || (newValue.all { it.isDigit() } && (newValue.toIntOrNull() ?: 0) <= 59)) {
                                minutes = newValue.take(2)
                            }
                        },
                        label = { Text("分钟") },
                        modifier = Modifier.width(80.dp),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Quick presets
                Text(
                    text = "快速选择",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(15, 25, 45, 60).forEach { preset ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                hours = (preset / 60).toString()
                                minutes = (preset % 60).toString()
                            },
                            label = { Text("${preset}m") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val totalMinutes = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
                    if (totalMinutes > 0) {
                        onConfirm(totalMinutes)
                    }
                },
                enabled = ((hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)) > 0
            ) {
                Text("开始")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun SettingsDialog(
    currentBackgroundUri: String?,
    onDismiss: () -> Unit,
    onSelectImage: () -> Unit,
    onClearImage: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("沉浸模式设置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "背景图片",
                    style = MaterialTheme.typography.titleSmall
                )

                // Current background preview
                if (currentBackgroundUri != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        coil.compose.AsyncImage(
                            model = Uri.parse(currentBackgroundUri),
                            contentDescription = "当前背景",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }

                // Image selection buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSelectImage,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("选择图片")
                    }

                    if (currentBackgroundUri != null) {
                        OutlinedButton(
                            onClick = onClearImage,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("清除")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}
