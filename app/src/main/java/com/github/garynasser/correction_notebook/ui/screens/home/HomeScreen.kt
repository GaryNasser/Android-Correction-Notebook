package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var isImmersiveMode by remember { mutableStateOf(false) }
    var showModeSelector by remember { mutableStateOf(false) }

    if (isImmersiveMode) {
        ImmersiveModeScreen(
            onExit = { isImmersiveMode = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("StudyBIT") },
                    actions = {
                        IconButton(onClick = { showModeSelector = true }) {
                            Icon(Icons.Default.Timer, contentDescription = "沉浸模式")
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

                // 当前时间卡片
                item {
                    CurrentTimeCard(
                        onImmersiveModeClick = { showModeSelector = true }
                    )
                }

                // 日期切换器
                item {
                    DateSelector(
                        selectedDate = selectedDate,
                        onDateChange = { selectedDate = it }
                    )
                }

                // 日程列表标题
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "今日日程",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { /* TODO: 添加日程 */ }) {
                            Icon(Icons.Default.Add, contentDescription = "添加日程")
                        }
                    }
                }

                // 日程列表
                item {
                    ScheduleList()
                }

                // 沉浸模式快捷入口
                item {
                    ImmersiveModeCard(
                        onClick = { showModeSelector = true }
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        // 模式选择对话框
        if (showModeSelector) {
            ModeSelectorDialog(
                onDismiss = { showModeSelector = false },
                onModeSelected = { mode ->
                    showModeSelector = false
                    if (mode == "immersive") {
                        isImmersiveMode = true
                    }
                }
            )
        }
    }
}

@Composable
fun CurrentTimeCard(onImmersiveModeClick: () -> Unit) {
    val currentTime = remember { mutableStateOf(java.time.LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = java.time.LocalTime.now()
            kotlinx.coroutines.delay(1000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTime.value.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 E")),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onImmersiveModeClick) {
                Icon(Icons.Default.Timer, contentDescription = null)
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
                    text = if (date == LocalDate.now()) "今天" else date.dayOfWeek.name.take(3),
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
fun ScheduleList() {
    // 示例日程数据
    val schedules = remember {
        listOf(
            ScheduleItem("09:00", "11:30", "高等数学", "3号楼301", true),
            ScheduleItem("14:00", "16:00", "大学物理", "2号楼205", false),
            ScheduleItem("19:00", "21:00", "自习", "图书馆", false)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        schedules.forEach { schedule ->
            ScheduleCard(schedule)
        }
    }
}

data class ScheduleItem(
    val startTime: String,
    val endTime: String,
    val title: String,
    val location: String,
    val isCompleted: Boolean
)

@Composable
fun ScheduleCard(schedule: ScheduleItem) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 时间信息
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp)
            ) {
                Text(
                    text = schedule.startTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = schedule.endTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 竖线装饰
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (schedule.isCompleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.primary
                    )
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 课程信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = schedule.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // 完成状态
            if (schedule.isCompleted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已完成",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ImmersiveModeCard(onClick: () -> Unit) {
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "沉浸学习模式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "倒计时 / 正计时 / 番茄钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun ModeSelectorDialog(
    onDismiss: () -> Unit,
    onModeSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择学习模式") },
        text = {
            Column {
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
                ModeOption(
                    icon = Icons.Default.Timer,
                    title = "番茄钟",
                    description = "25分钟专注，5分钟休息",
                    onClick = { onModeSelected("pomodoro") }
                )
                ModeOption(
                    icon = Icons.Default.Fullscreen,
                    title = "全屏沉浸",
                    description = "只显示时间，屏蔽干扰",
                    onClick = { onModeSelected("immersive") }
                )
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

@Composable
fun ImmersiveModeScreen(onExit: () -> Unit) {
    var currentTime by remember { mutableStateOf(java.time.LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = java.time.LocalTime.now()
            kotlinx.coroutines.delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onExit),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "点击任意位置退出",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }
    }
}
