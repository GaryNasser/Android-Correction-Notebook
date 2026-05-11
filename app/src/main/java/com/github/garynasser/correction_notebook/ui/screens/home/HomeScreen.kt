package com.github.garynasser.correction_notebook.ui.screens.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.data.model.home.PlannerTab
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import com.github.garynasser.correction_notebook.ui.screens.statistics.StatisticsScreen
import com.github.garynasser.correction_notebook.ui.screens.statistics.StatisticsViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    statisticsViewModel: StatisticsViewModel = hiltViewModel(),
    onNavigateToStatistics: () -> Unit = {},
    onImmersiveModeChanged: (Boolean) -> Unit = {},
    onOpenArticle: (Article) -> Unit = {}
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val timerState by homeViewModel.timerManager.timerState.collectAsState()
    var showCustomTimer by remember { mutableStateOf(false) }
    var startPomodoroAfterSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
                title = { Text("BITStudy") },
                windowInsets = WindowInsets(0, 0, 0, 0),
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Current Time Card (1/3 screen height)
            item {
                CurrentTimeCard(
                    timerState = timerState,
                    onImmersiveModeClick = { homeViewModel.showModeSelector() }
                )
            }

            // Date Selector
            item {
                DateSelector(
                    selectedDate = uiState.selectedDate,
                    onDateChange = { homeViewModel.setSelectedDate(it) }
                )
            }

            // Quick Stats Preview
            item {
                QuickStatsPreview(
                    todayMinutes = uiState.todayStudyMinutes,
                    completedPomodoros = uiState.completedPomodoros,
                    onClick = { homeViewModel.showStatistics() }
                )
            }

            item {
                AiStudyAdviceCard(
                    advice = uiState.aiAdvice,
                    isLoading = uiState.isAiAdviceLoading,
                    onGenerate = { homeViewModel.generateTodayAdvice() }
                )
            }

            item {
                PlannerSection(
                    uiState = uiState,
                    onPlannerTabChange = { homeViewModel.setPlannerTab(it) },
                    onScheduleRangeChange = { homeViewModel.setScheduleRange(it) },
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

@Composable
private fun AiStudyAdviceCard(
    advice: String?,
    isLoading: Boolean,
    onGenerate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "今日 AI 学习建议",
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
            Text(
                text = advice ?: "结合今日日程、待办和学习记录，生成 3-5 条可执行建议。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (advice == null) 0.72f else 1f)
            )
        }
    }
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
fun CurrentTimeCard(
    timerState: TimerState,
    onImmersiveModeClick: () -> Unit
) {
    val currentTime = remember { mutableStateOf(java.time.LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = java.time.LocalTime.now()
            kotlinx.coroutines.delay(1000)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTime.value.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 E")),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )

            // Show timer status if running
            if (timerState !is TimerState.Idle) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (timerState) {
                            is TimerState.Pomodoro -> "番茄钟运行中"
                            is TimerState.Countdown -> "倒计时运行中"
                            is TimerState.Stopwatch -> "计时中"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onImmersiveModeClick) {
                Icon(Icons.Default.Fullscreen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("进入沉浸模式")
            }
        }
    }
}

@Composable
fun DateSelector(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit
) {
    val dates = remember { (-3..3).map { LocalDate.now().plusDays(it.toLong()) } }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        dates.forEach { date ->
            val isSelected = date == selectedDate
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDateChange(date) }
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (date == LocalDate.now()) {
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatMinutesToDisplay(todayMinutes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "今日学习",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }

            Divider(
                modifier = Modifier
                    .height(48.dp)
                    .width(1.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$completedPomodoros",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "番茄钟",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
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
