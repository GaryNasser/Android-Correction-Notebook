package com.github.garynasser.correction_notebook.ui.screens.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.garynasser.correction_notebook.data.model.home.Article
import com.github.garynasser.correction_notebook.data.model.ai.AiAction
import com.github.garynasser.correction_notebook.data.model.ai.AiActionType
import com.github.garynasser.correction_notebook.data.model.ai.AiPlanBlock
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.data.model.home.PlannerTab
import com.github.garynasser.correction_notebook.data.model.home.ScheduleOccurrence
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSection
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import com.github.garynasser.correction_notebook.data.model.studyset.DueReviewItem
import com.github.garynasser.correction_notebook.data.model.studyset.KnowledgeCardType
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseProgress
import com.github.garynasser.correction_notebook.ui.components.FreshCard
import com.github.garynasser.correction_notebook.ui.components.FreshGradientCard
import com.github.garynasser.correction_notebook.ui.components.FreshScreen
import com.github.garynasser.correction_notebook.ui.components.MetricTile
import com.github.garynasser.correction_notebook.ui.components.SectionHeader
import com.github.garynasser.correction_notebook.ui.screens.statistics.StatisticsScreen
import com.github.garynasser.correction_notebook.ui.screens.statistics.StatisticsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.io.File
import java.util.Locale

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
    var activeMainTab by remember { mutableStateOf(HomeMainTab.BIT) }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    fun jumpToPlanner(tab: PlannerTab) {
        homeViewModel.setPlannerTab(tab)
        homeViewModel.setSelectedWeek(uiState.selectedDate)
        activeMainTab = HomeMainTab.BIT
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

    LaunchedEffect(activeMainTab) {
        if (activeMainTab == HomeMainTab.BIT) {
            homeViewModel.setSelectedWeek(uiState.selectedDate)
        }
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    BitStudySwitcher(
                        activeTab = activeMainTab,
                        onTabSelected = { activeMainTab = it }
                    )
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    if (activeMainTab == HomeMainTab.STUDY) {
                        IconButton(onClick = { homeViewModel.showStatistics() }) {
                            Icon(Icons.Default.BarChart, contentDescription = "统计")
                        }
                        IconButton(onClick = { homeViewModel.showModeSelector() }) {
                            Icon(Icons.Default.Timer, contentDescription = "学习模式")
                        }
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
            when (activeMainTab) {
                HomeMainTab.BIT -> BitSchedulePage(
                    uiState = uiState,
                    onSyncSchoolSchedule = { homeViewModel.syncSchoolSchedule() },
                    onImportIcs = { icsPickerLauncher.launch("*/*") },
                    onAddSchedule = { homeViewModel.showAddScheduleDialog() },
                    onWeekChange = { homeViewModel.setSelectedWeek(it) },
                    onToday = { homeViewModel.setSelectedWeek(LocalDate.now()) },
                    onDeleteSchedule = { homeViewModel.deleteSchedule(it) }
                )
                HomeMainTab.STUDY -> StudyDashboardPage(
                    listState = listState,
                    uiState = uiState,
                    timerState = timerState,
                    onImmersiveModeClick = { homeViewModel.showModeSelector() },
                    onScheduleClick = { jumpToPlanner(PlannerTab.SCHEDULE) },
                    onTodoClick = { homeViewModel.showAddTodoDialog() },
                    onContinueLearningClick = { onNavigateToCourses(uiState.recentCourseProgress.firstOrNull()) },
                    onRecentFilesClick = onNavigateToKnowledgeBase,
                    onOpenStatistics = { homeViewModel.showStatistics() },
                    onReviewDone = { homeViewModel.markReviewDone(it) },
                    onOpenKnowledgeBase = onNavigateToKnowledgeBase,
                    onGenerateAdvice = { homeViewModel.generateTodayAdvice() },
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
                    onOpenCourse = onOpenCourse,
                    onOpenFile = onOpenKnowledgeFile
                )
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

    if (uiState.schoolScheduleSyncMessage != null || uiState.schoolScheduleSyncError != null) {
        AlertDialog(
            onDismissRequest = { homeViewModel.dismissSchoolScheduleSyncMessage() },
            title = { Text(if (uiState.schoolScheduleSyncError == null) "课表同步完成" else "课表同步失败") },
            text = {
                Text(uiState.schoolScheduleSyncMessage ?: uiState.schoolScheduleSyncError.orEmpty())
            },
            confirmButton = {
                TextButton(onClick = { homeViewModel.dismissSchoolScheduleSyncMessage() }) {
                    Text("知道了")
                }
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

private enum class HomeMainTab {
    BIT,
    STUDY
}

@Composable
private fun BitStudySwitcher(
    activeTab: HomeMainTab,
    onTabSelected: (HomeMainTab) -> Unit
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        BrandWord(
            text = "BIT",
            selected = activeTab == HomeMainTab.BIT,
            onClick = { onTabSelected(HomeMainTab.BIT) }
        )
        BrandWord(
            text = "Study",
            selected = activeTab == HomeMainTab.STUDY,
            onClick = { onTabSelected(HomeMainTab.STUDY) }
        )
    }
}

@Composable
private fun BrandWord(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        modifier = Modifier.clickable(onClick = onClick),
        style = if (selected) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
        fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (selected) 1f else 0.38f)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BitSchedulePage(
    uiState: HomeUiState,
    onSyncSchoolSchedule: () -> Unit,
    onImportIcs: () -> Unit,
    onAddSchedule: () -> Unit,
    onWeekChange: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onDeleteSchedule: (String) -> Unit
) {
    var selectedOccurrence by remember { mutableStateOf<ScheduleOccurrence?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val sections = uiState.scheduleSections

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        WeeklyCourseGrid(
            selectedDate = uiState.selectedDate,
            sections = sections,
            modifier = Modifier.fillMaxSize(),
            onItemClick = { selectedOccurrence = it }
        )

        ScheduleSideControls(
            isSyncing = uiState.isSyncingSchoolSchedule,
            isImporting = uiState.isImportingSchedule,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 10.dp),
            onNextWeek = { onWeekChange(uiState.selectedDate.plusWeeks(1)) },
            onPreviousWeek = { onWeekChange(uiState.selectedDate.minusWeeks(1)) },
            onAddSchedule = onAddSchedule,
            onSyncSchoolSchedule = onSyncSchoolSchedule,
            onImportIcs = onImportIcs,
            onPickDate = { showDatePicker = true },
            onToday = onToday
        )
    }

    selectedOccurrence?.let { item ->
        ScheduleOccurrenceDialog(
            item = item,
            onDismiss = { selectedOccurrence = null },
            onDelete = {
                selectedOccurrence = null
                onDeleteSchedule(item.eventId)
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val pickedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            onWeekChange(pickedDate)
                        }
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun WeeklyCourseGrid(
    selectedDate: LocalDate,
    sections: List<ScheduleSection>,
    modifier: Modifier = Modifier,
    onItemClick: (ScheduleOccurrence) -> Unit
) {
    val weekNumber = selectedDate.get(WeekFields.ISO.weekOfWeekBasedYear())
    val days = (0..6).map { selectedDate.plusDays(it.toLong()) }
    val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val itemsByDate = sections.associate { it.date to it.items }
    val headerHeight = 50.dp
    val leftColumnWidth = 42.dp

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val rowHeight = (maxHeight - headerHeight) / COURSE_SECTIONS.size.toFloat()
        val dayColumnWidth = (maxWidth - leftColumnWidth) / 7f
        val gridHeight = rowHeight * COURSE_SECTIONS.size.toFloat()

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(leftColumnWidth)
                        .height(headerHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${weekNumber}\n周",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                }
                days.forEachIndexed { index, date ->
                    val isToday = date == LocalDate.now()
                    Column(
                        modifier = Modifier
                            .width(dayColumnWidth)
                            .height(headerHeight)
                            .background(
                                if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
                                else Color.Transparent
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = weekdayLabels[index],
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                        )
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("MM/dd")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight)
            ) {
                COURSE_SECTIONS.forEachIndexed { index, section ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .offset(y = rowHeight * index.toFloat()),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .width(leftColumnWidth)
                                .height(rowHeight)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = section.index.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                            )
                            Text(
                                text = section.start.format(DateTimeFormatter.ofPattern("HH:mm")),
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                            )
                        }
                        repeat(7) {
                            Box(
                                modifier = Modifier
                                    .width(dayColumnWidth)
                                    .height(rowHeight)
                                    .background(
                                        if (index % 2 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.13f)
                                        else Color.Transparent
                                    )
                            )
                        }
                    }
                }

                COURSE_SECTIONS.drop(1).forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .offset(y = rowHeight * (index + 1).toFloat())
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f))
                    )
                }

                days.forEachIndexed { dayIndex, date ->
                    itemsByDate[date].orEmpty().forEach { item ->
                        val placement = item.toCourseGridPlacement() ?: return@forEach
                        CourseGridBlock(
                            item = item,
                            span = placement.span,
                            modifier = Modifier
                                .width(dayColumnWidth - 3.dp)
                                .height(rowHeight * placement.span.toFloat() - 3.dp)
                                .offset(
                                    x = leftColumnWidth + dayColumnWidth * dayIndex.toFloat() + 1.5.dp,
                                    y = rowHeight * placement.startIndex.toFloat() + 1.5.dp
                                ),
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleSideControls(
    isSyncing: Boolean,
    isImporting: Boolean,
    modifier: Modifier,
    onNextWeek: () -> Unit,
    onPreviousWeek: () -> Unit,
    onAddSchedule: () -> Unit,
    onSyncSchoolSchedule: () -> Unit,
    onImportIcs: () -> Unit,
    onPickDate: () -> Unit,
    onToday: () -> Unit
) {
    var showActions by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        ScheduleFloatingButton(
            icon = Icons.Default.ChevronRight,
            contentDescription = "下一周",
            onClick = onNextWeek
        )
        ScheduleFloatingButton(
            icon = Icons.Default.ChevronLeft,
            contentDescription = "上一周",
            onClick = onPreviousWeek
        )
        ScheduleFloatingButton(
            icon = Icons.Default.Add,
            contentDescription = "添加日程",
            onClick = onAddSchedule
        )
        Box {
            ScheduleFloatingButton(
                icon = Icons.Default.Settings,
                contentDescription = "课表操作",
                onClick = { showActions = true }
            )
            DropdownMenu(
                expanded = showActions,
                onDismissRequest = { showActions = false }
            ) {
                DropdownMenuItem(
                    text = { Text("选择日期") },
                    leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    onClick = {
                        showActions = false
                        onPickDate()
                    }
                )
                DropdownMenuItem(
                    text = { Text("回到本周") },
                    leadingIcon = { Icon(Icons.Default.Today, contentDescription = null) },
                    onClick = {
                        showActions = false
                        onToday()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (isSyncing) "同步中" else "同步教务") },
                    leadingIcon = {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null)
                        }
                    },
                    enabled = !isSyncing,
                    onClick = {
                        showActions = false
                        onSyncSchoolSchedule()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (isImporting) "导入中" else "导入 ICS") },
                    leadingIcon = { Icon(Icons.Default.ImportExport, contentDescription = null) },
                    enabled = !isImporting,
                    onClick = {
                        showActions = false
                        onImportIcs()
                    }
                )
            }
        }
    }
}

@Composable
private fun ScheduleFloatingButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
        tonalElevation = 4.dp,
        shadowElevation = 2.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

@Composable
private fun CourseGridBlock(
    item: ScheduleOccurrence,
    span: Int,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val displayLocation = item.location
        .lineSequence()
        .firstOrNull()
        .orEmpty()
        .trim()
    val hasLocation = displayLocation.isNotBlank()
    val compactBlock = span <= 1
    val longTitle = item.title.length >= 14
    val titleFontSize = when {
        compactBlock -> if (longTitle) 8.sp else 9.sp
        span == 2 -> if (longTitle) 9.sp else 10.sp
        else -> if (longTitle) 10.sp else 11.sp
    }
    val locationFontSize = when {
        compactBlock -> 8.sp
        span == 2 -> 8.5.sp
        else -> 10.sp
    }
    val titleLineHeight = when {
        compactBlock -> if (longTitle) 9.sp else 10.sp
        span == 2 -> if (longTitle) 10.sp else 11.sp
        else -> if (longTitle) 11.sp else 12.sp
    }
    val locationLineHeight = when {
        compactBlock -> 9.sp
        span == 2 -> 9.5.sp
        else -> 11.sp
    }
    val titleMaxLines = when {
        compactBlock -> if (hasLocation) 2 else 3
        span == 2 -> if (hasLocation) 6 else 7
        else -> if (hasLocation) 8 else 10
    }
    val locationMaxLines = when {
        compactBlock -> 2
        span == 2 -> 3
        else -> 4
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(scheduleSourceColor(item.sourceType))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = if (compactBlock) 3.dp else 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.title,
                fontSize = titleFontSize,
                lineHeight = titleLineHeight,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
                textAlign = TextAlign.Center,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                softWrap = true
            )
            if (hasLocation) {
                Spacer(modifier = Modifier.height(if (compactBlock) 1.dp else 3.dp))
                Text(
                    text = displayLocation,
                    fontSize = locationFontSize,
                    lineHeight = locationLineHeight,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    maxLines = locationMaxLines,
                    overflow = TextOverflow.Clip,
                    softWrap = true
                )
            }
        }
    }
}

private data class CourseSectionSlot(
    val index: Int,
    val start: java.time.LocalTime,
    val end: java.time.LocalTime
)

private data class CourseGridPlacement(
    val startIndex: Int,
    val span: Int
)

private val COURSE_SECTIONS = listOf(
    CourseSectionSlot(1, java.time.LocalTime.of(8, 0), java.time.LocalTime.of(8, 45)),
    CourseSectionSlot(2, java.time.LocalTime.of(8, 50), java.time.LocalTime.of(9, 35)),
    CourseSectionSlot(3, java.time.LocalTime.of(9, 55), java.time.LocalTime.of(10, 40)),
    CourseSectionSlot(4, java.time.LocalTime.of(10, 45), java.time.LocalTime.of(11, 30)),
    CourseSectionSlot(5, java.time.LocalTime.of(11, 35), java.time.LocalTime.of(12, 20)),
    CourseSectionSlot(6, java.time.LocalTime.of(13, 20), java.time.LocalTime.of(14, 5)),
    CourseSectionSlot(7, java.time.LocalTime.of(14, 10), java.time.LocalTime.of(14, 55)),
    CourseSectionSlot(8, java.time.LocalTime.of(15, 15), java.time.LocalTime.of(16, 0)),
    CourseSectionSlot(9, java.time.LocalTime.of(16, 5), java.time.LocalTime.of(16, 50)),
    CourseSectionSlot(10, java.time.LocalTime.of(16, 55), java.time.LocalTime.of(17, 40)),
    CourseSectionSlot(11, java.time.LocalTime.of(18, 30), java.time.LocalTime.of(19, 15)),
    CourseSectionSlot(12, java.time.LocalTime.of(19, 20), java.time.LocalTime.of(20, 5)),
    CourseSectionSlot(13, java.time.LocalTime.of(20, 10), java.time.LocalTime.of(20, 55))
)

private fun ScheduleOccurrence.toCourseGridPlacement(): CourseGridPlacement? {
    if (allDay) return CourseGridPlacement(startIndex = 0, span = 1)
    val startTime = startAt.toLocalTime()
    val endTime = endAt.toLocalTime()
    val startIndex = COURSE_SECTIONS.indexOfLast { !it.start.isAfter(startTime) }.coerceAtLeast(0)
    val endIndex = COURSE_SECTIONS.indexOfFirst { !it.end.isBefore(endTime) }
        .takeIf { it >= 0 }
        ?: startIndex
    return CourseGridPlacement(
        startIndex = startIndex,
        span = (endIndex - startIndex + 1).coerceAtLeast(1)
    )
}

@Composable
private fun BitScheduleDayCard(
    section: ScheduleSection,
    onItemClick: (ScheduleOccurrence) -> Unit
) {
    FreshCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = section.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = section.date.format(DateTimeFormatter.ofPattern("M月d日")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                    )
                }
                Text(
                    text = if (section.items.isEmpty()) "无课" else "${section.items.size} 项",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (section.items.isEmpty()) {
                Text(
                    text = "空出来的时间，可以安排复习或专注学习。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    section.items.forEach { item ->
                        BitScheduleItemRow(
                            item = item,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BitScheduleItemRow(
    item: ScheduleOccurrence,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(scheduleSourceColor(item.sourceType))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = compactScheduleTime(item),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(82.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = item.location.ifBlank { sourceLabel(item.sourceType) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                maxLines = 1
            )
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
        ) {
            Text(
                text = sourceLabel(item.sourceType),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
        }
    }
}

@Composable
private fun ScheduleOccurrenceDialog(
    item: ScheduleOccurrence,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fullScheduleTime(item), style = MaterialTheme.typography.bodyMedium)
                if (item.location.isNotBlank()) {
                    Text(item.location, style = MaterialTheme.typography.bodyMedium)
                }
                if (item.description.isNotBlank()) {
                    Text(item.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("删除") }
        }
    )
}

@Composable
private fun StudyDashboardPage(
    listState: LazyListState,
    uiState: HomeUiState,
    timerState: TimerState,
    onImmersiveModeClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onTodoClick: () -> Unit,
    onContinueLearningClick: () -> Unit,
    onRecentFilesClick: () -> Unit,
    onOpenStatistics: () -> Unit,
    onReviewDone: (String) -> Unit,
    onOpenKnowledgeBase: () -> Unit,
    onGenerateAdvice: () -> Unit,
    onSaveAdvice: (String) -> Unit,
    onApplyAction: (AiAction) -> Unit,
    onOpenCourse: (Int, String) -> Unit,
    onOpenFile: (String) -> Unit
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
                onImmersiveModeClick = onImmersiveModeClick,
                onScheduleClick = onScheduleClick,
                onTodoClick = onTodoClick,
                onContinueLearningClick = onContinueLearningClick,
                onRecentFilesClick = onRecentFilesClick
            )
        }
        item {
            QuickStatsPreview(
                todayMinutes = uiState.todayStudyMinutes,
                completedPomodoros = uiState.completedPomodoros,
                onClick = onOpenStatistics
            )
        }
        item {
            DueReviewCard(
                items = uiState.dueReviewItems,
                onReviewDone = onReviewDone,
                onOpenKnowledgeBase = onOpenKnowledgeBase
            )
        }
        item {
            SectionHeader(
                title = "AI 学习建议",
                subtitle = "结合课表、待办和学习记录给你一个轻提醒"
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
                onGenerate = onGenerateAdvice,
                onSaveAdvice = onSaveAdvice,
                onApplyAction = onApplyAction,
                onStartFocus = onImmersiveModeClick,
                onOpenCourse = onOpenCourse,
                onOpenFile = onOpenFile
            )
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun scheduleSourceColor(sourceType: ScheduleSourceType): Color {
    return when (sourceType) {
        ScheduleSourceType.MANUAL -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.34f)
        ScheduleSourceType.ICS_IMPORT -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        ScheduleSourceType.SCHOOL_IMPORT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    }
}

private fun sourceLabel(sourceType: ScheduleSourceType): String {
    return when (sourceType) {
        ScheduleSourceType.MANUAL -> "手动"
        ScheduleSourceType.ICS_IMPORT -> "ICS"
        ScheduleSourceType.SCHOOL_IMPORT -> "学校"
    }
}

private fun compactScheduleTime(item: ScheduleOccurrence): String {
    return if (item.allDay) {
        "全天"
    } else {
        "${item.startAt.format(DateTimeFormatter.ofPattern("HH:mm"))}-${item.endAt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }
}

private fun fullScheduleTime(item: ScheduleOccurrence): String {
    return if (item.allDay) {
        "全天"
    } else {
        "${item.startAt.format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"))} - ${item.endAt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }
}

@Composable
private fun DueReviewCard(
    items: List<DueReviewItem>,
    onReviewDone: (String) -> Unit,
    onOpenKnowledgeBase: () -> Unit
) {
    var selectedCard by remember { mutableStateOf<DueReviewItem?>(null) }

    selectedCard?.let { card ->
        FlashcardDetailDialog(
            item = card,
            onDismiss = { selectedCard = null },
            onReviewDone = {
                onReviewDone(card.flashcardId)
                selectedCard = null
            }
        )
    }

    FreshCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Style, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("今日待复习", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (items.isEmpty()) "暂无到期闪卡，可以从资料页生成学习集" else "${items.size} 张闪卡需要主动回忆",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onOpenKnowledgeBase) {
                    Text(if (items.isEmpty()) "去生成" else "资料")
                }
            }

            if (items.isEmpty()) {
                Text(
                    text = "从知识库文件菜单选择“AI 生成学习集”，BITStudy 会把资料变成闪卡和测验，并安排复习。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
            } else {
                items.take(3).forEach { item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCard = item },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.36f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.front,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                Text(
                                    text = listOfNotNull(item.courseName, item.studySetTitle, item.hint.takeIf { it.isNotBlank() })
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            TextButton(onClick = { onReviewDone(item.flashcardId) }) {
                                Text("已复习")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashcardDetailDialog(
    item: DueReviewItem,
    onDismiss: () -> Unit,
    onReviewDone: () -> Unit
) {
    var showAnswer by remember(item.flashcardId) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.studySetTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item.courseName?.let {
                    Text("课程：$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (item.type == KnowledgeCardType.QA_FLASHCARD) {
                    Text("问题", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(item.front, style = MaterialTheme.typography.bodyMedium)
                    if (item.hint.isNotBlank()) {
                        Text("提示：${item.hint}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (showAnswer) {
                        Text("答案", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(item.back, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        OutlinedButton(onClick = { showAnswer = true }) {
                            Text("显示答案")
                        }
                    }
                } else {
                    KnowledgeDetailBlock("解释", item.explanation.ifBlank { item.back })
                    KnowledgeDetailBlock("例子", item.example)
                    KnowledgeDetailBlock("易错点", item.pitfall)
                    KnowledgeDetailBlock("公式/术语", item.formula)
                }
                KnowledgeDetailBlock("来源", listOf(item.sourceLocation, item.sourceQuote).filter { it.isNotBlank() }.joinToString("\n"))
                if (item.reviewCount > 0) {
                    Text("已复习 ${item.reviewCount} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onReviewDone) { Text("标记已复习") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun KnowledgeDetailBlock(label: String, content: String) {
    if (content.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(content, style = MaterialTheme.typography.bodyMedium)
    }
}

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
                    AiActionType.CREATE_STUDY_SET,
                    AiActionType.CREATE_FLASHCARDS,
                    AiActionType.CREATE_QUIZ,
                    AiActionType.SCHEDULE_REVIEW -> Icons.Default.Style
                    AiActionType.UPDATE_COURSE_GOAL -> Icons.Default.Flag
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
