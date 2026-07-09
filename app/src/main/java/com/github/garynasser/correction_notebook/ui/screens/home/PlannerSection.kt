package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.data.model.home.IcsDiffItem
import com.github.garynasser.correction_notebook.data.model.home.IcsImportPreview
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.data.model.home.PlannerTab
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleOccurrence
import com.github.garynasser.correction_notebook.data.model.home.ScheduleRange
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSection
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import com.github.garynasser.correction_notebook.ui.components.FreshCard
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PlannerSection(
    uiState: HomeUiState,
    onPlannerTabChange: (PlannerTab) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onScheduleRangeChange: (ScheduleRange) -> Unit,
    onSyncSchoolSchedule: () -> Unit,
    onImportIcs: () -> Unit,
    onAddSchedule: () -> Unit,
    onAddTodo: () -> Unit,
    onShowTodoHistory: () -> Unit,
    onToggleTodo: (String) -> Unit,
    onBreakDownTodo: (TodoItem) -> Unit,
    onDeleteTodo: (String) -> Unit,
    onDeleteSchedule: (String) -> Unit
) {
    FreshCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlannerHeader(
                selectedDate = uiState.selectedDate,
                isImporting = uiState.isImportingSchedule,
                isSyncing = uiState.isSyncingSchoolSchedule,
                onSyncSchoolSchedule = onSyncSchoolSchedule,
                onImportIcs = onImportIcs,
                onAddSchedule = onAddSchedule
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PlannerTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = uiState.plannerTab == tab,
                        onClick = { onPlannerTabChange(tab) },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = PlannerTab.entries.size
                        ),
                        label = {
                            Text(
                                when (tab) {
                                    PlannerTab.SCHEDULE -> "日程表"
                                    PlannerTab.TODO -> "ToDo List"
                                }
                            )
                        }
                    )
                }
            }

            DateSelector(
                selectedDate = uiState.selectedDate,
                onDateChange = onDateChange
            )

            when (uiState.plannerTab) {
                PlannerTab.SCHEDULE -> SchedulePlannerContent(
                    selectedDate = uiState.selectedDate,
                    selectedRange = uiState.scheduleRange,
                    sections = uiState.scheduleSections,
                    onRangeChange = onScheduleRangeChange,
                    onDeleteSchedule = onDeleteSchedule
                )
                PlannerTab.TODO -> TodoPlannerContent(
                    todos = uiState.todoItems,
                    onAddTodo = onAddTodo,
                    onShowHistory = onShowTodoHistory,
                    onToggleTodo = onToggleTodo,
                    onBreakDownTodo = onBreakDownTodo,
                    onDeleteTodo = onDeleteTodo
                )
            }
        }
    }
}

@Composable
private fun PlannerHeader(
    selectedDate: LocalDate,
    isImporting: Boolean,
    isSyncing: Boolean,
    onSyncSchoolSchedule: () -> Unit,
    onImportIcs: () -> Unit,
    onAddSchedule: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${selectedDate.format(DateTimeFormatter.ofPattern("M月d日"))}规划",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "把课程、日程和待办放到同一条学习节奏里",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onSyncSchoolSchedule, enabled = !isSyncing) {
                androidx.compose.material3.Icon(
                    Icons.Default.Sync,
                    contentDescription = if (isSyncing) "同步中" else "同步教务课表"
                )
            }
            IconButton(onClick = onImportIcs, enabled = !isImporting) {
                androidx.compose.material3.Icon(
                    Icons.Default.ImportExport,
                    contentDescription = if (isImporting) "导入中" else "导入 ICS"
                )
            }
            IconButton(onClick = onAddSchedule) {
                androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = "添加日程")
            }
        }
    }
}

@Composable
private fun SchedulePlannerContent(
    selectedDate: LocalDate,
    selectedRange: ScheduleRange,
    sections: List<ScheduleSection>,
    onRangeChange: (ScheduleRange) -> Unit,
    onDeleteSchedule: (String) -> Unit
) {
    var selectedOccurrence by remember { mutableStateOf<ScheduleOccurrence?>(null) }

    ScheduleRangeSelector(
        selectedRange = selectedRange,
        onRangeChange = onRangeChange
    )

    Text(
        text = scheduleRangeSummary(selectedDate, selectedRange),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    )

    if (sections.all { it.items.isEmpty() }) {
        PlannerEmptyState(
            title = "近期还没有日程",
            description = "你可以同步教务课表、导入 ICS 文件，或者手动添加一个时间、地点、活动安排。"
        )
    } else if (selectedRange == ScheduleRange.WEEK) {
        WeekScheduleContent(
            sections = sections,
            onItemClick = { selectedOccurrence = it }
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sections.filter { it.items.isNotEmpty() }.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    section.items.forEach { item ->
                        ScheduleOccurrenceCard(
                            item = item,
                            onClick = { selectedOccurrence = item }
                        )
                    }
                }
            }
        }
    }

    selectedOccurrence?.let { item ->
        ScheduleOccurrenceDetailDialog(
            item = item,
            onDismiss = { selectedOccurrence = null },
            onDelete = {
                selectedOccurrence = null
                onDeleteSchedule(item.eventId)
            }
        )
    }
}

@Composable
private fun TodoPlannerContent(
    todos: List<TodoItem>,
    onAddTodo: () -> Unit,
    onShowHistory: () -> Unit,
    onToggleTodo: (String) -> Unit,
    onBreakDownTodo: (TodoItem) -> Unit,
    onDeleteTodo: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "把没有明确时间限制的小事记在这里",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        Row {
            TextButton(onClick = onShowHistory) {
                androidx.compose.material3.Icon(Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("已完成")
            }
            TextButton(onClick = onAddTodo) {
                androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("添加")
            }
        }
    }

    if (todos.isEmpty()) {
        PlannerEmptyState(
            title = "还没有待办小事",
            description = "把今天想完成的小事、提醒或备注写进 ToDo List。"
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            todos.forEach { todo ->
                TodoItemCard(
                    todo = todo,
                    onToggleComplete = { onToggleTodo(todo.id) },
                    onAiBreakdown = { onBreakDownTodo(todo) },
                    onDelete = { onDeleteTodo(todo.id) }
                )
            }
        }
    }
}

@Composable
private fun ScheduleRangeSelector(
    selectedRange: ScheduleRange,
    onRangeChange: (ScheduleRange) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val ranges = listOf(ScheduleRange.TODAY, ScheduleRange.TOMORROW, ScheduleRange.WEEK)
        ranges.forEachIndexed { index, range ->
            SegmentedButton(
                selected = selectedRange == range,
                onClick = { onRangeChange(range) },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = ranges.size
                ),
                label = {
                    Text(
                        when (range) {
                            ScheduleRange.TODAY -> "今天"
                            ScheduleRange.TOMORROW -> "明天"
                            ScheduleRange.WEEK -> "本周"
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun WeekScheduleContent(
    sections: List<ScheduleSection>,
    onItemClick: (ScheduleOccurrence) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sections.forEach { section ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${section.date.format(DateTimeFormatter.ofPattern("M月d日"))} ${section.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.CHINA)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (section.items.isEmpty()) "无课" else "${section.items.size} 项",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                        )
                    }
                    if (section.items.isEmpty()) {
                        Text(
                            text = "留给复习、自习或临时安排",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                        )
                    } else {
                        section.items.forEach { item ->
                            CompactWeekScheduleItem(item = item, onClick = { onItemClick(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactWeekScheduleItem(
    item: ScheduleOccurrence,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(sourceContainerColor(item.sourceType))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatScheduleTimeCompact(item).replace("\n", "-"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(86.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (item.location.isNotBlank()) {
                Text(
                    text = item.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1
                )
            }
        }
        SourceBadge(sourceType = item.sourceType)
    }
}

@Composable
private fun PlannerEmptyState(
    title: String,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
            )
        }
    }
}

@Composable
private fun ScheduleOccurrenceCard(
    item: ScheduleOccurrence,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = sourceContainerColor(item.sourceType)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatScheduleTimeCompact(item),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(76.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (item.location.isNotBlank()) {
                    Text(
                        text = item.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                        maxLines = 1
                    )
                }
            }
            SourceBadge(sourceType = item.sourceType)
        }
    }
}

@Composable
private fun SourceBadge(sourceType: ScheduleSourceType) {
    val (label, color) = when (sourceType) {
        ScheduleSourceType.MANUAL -> "手动" to MaterialTheme.colorScheme.tertiaryContainer
        ScheduleSourceType.ICS_IMPORT -> "ICS" to MaterialTheme.colorScheme.secondaryContainer
        ScheduleSourceType.SCHOOL_IMPORT -> "学校" to MaterialTheme.colorScheme.primaryContainer
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.7f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 1
        )
    }
}

@Composable
private fun sourceContainerColor(sourceType: ScheduleSourceType): Color {
    return when (sourceType) {
        ScheduleSourceType.MANUAL -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.34f)
        ScheduleSourceType.ICS_IMPORT -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        ScheduleSourceType.SCHOOL_IMPORT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
    }
}

@Composable
private fun ScheduleOccurrenceDetailDialog(
    item: ScheduleOccurrence,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlannerMetaRow(
                    icon = Icons.Default.Event,
                    text = formatScheduleTime(item)
                )
                if (item.location.isNotBlank()) {
                    PlannerMetaRow(
                        icon = Icons.Default.LocationOn,
                        text = item.location
                    )
                }
                if (item.description.isNotBlank()) {
                    PlannerMetaRow(
                        icon = Icons.Default.NoteAlt,
                        text = item.description
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                androidx.compose.material3.Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("删除")
            }
        }
    )
}

@Composable
private fun PlannerMetaRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatScheduleTime(item: ScheduleOccurrence): String {
    return if (item.allDay) {
        "全天"
    } else {
        "${item.startAt.format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"))} - ${item.endAt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }
}

private fun scheduleRangeSummary(selectedDate: LocalDate, selectedRange: ScheduleRange): String {
    return when (selectedRange) {
        ScheduleRange.TODAY -> "显示 ${selectedDate.format(DateTimeFormatter.ofPattern("M月d日"))} 的课程和日程"
        ScheduleRange.TOMORROW -> "显示 ${selectedDate.plusDays(1).format(DateTimeFormatter.ofPattern("M月d日"))} 的课程和日程"
        ScheduleRange.WEEK -> "显示从 ${selectedDate.format(DateTimeFormatter.ofPattern("M月d日"))} 起 7 天的课程和日程"
    }
}

private fun formatScheduleTimeCompact(item: ScheduleOccurrence): String {
    return if (item.allDay) {
        "全天"
    } else {
        "${item.startAt.format(DateTimeFormatter.ofPattern("HH:mm"))}\n${item.endAt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduleDialog(
    onDismiss: () -> Unit,
    onAdd: (ScheduleEvent) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val startTimeState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
    val endTimeState = rememberTimePickerState(initialHour = 10, initialMinute = 0, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加日程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("活动标题") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("地点") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    maxLines = 3
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("全天安排")
                    androidx.compose.material3.Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("日期：${selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))}")
                }
                if (!allDay) {
                    Text("开始时间", style = MaterialTheme.typography.labelMedium)
                    CompactTimeInput(state = startTimeState)
                    Text("结束时间", style = MaterialTheme.typography.labelMedium)
                    CompactTimeInput(state = endTimeState)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val start = LocalDateTime.of(selectedDate, LocalTime.of(startTimeState.hour, startTimeState.minute))
                    val end = if (allDay) {
                        LocalDateTime.of(selectedDate.plusDays(1), LocalTime.MIDNIGHT)
                    } else {
                        LocalDateTime.of(selectedDate, LocalTime.of(endTimeState.hour, endTimeState.minute))
                    }
                    if (title.isNotBlank() && (allDay || end.isAfter(start))) {
                        onAdd(
                            ScheduleEvent(
                                title = title.trim(),
                                description = description.trim(),
                                location = location.trim(),
                                startAt = if (allDay) LocalDateTime.of(selectedDate, LocalTime.MIDNIGHT) else start,
                                endAt = end,
                                allDay = allDay
                            )
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
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
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactTimeInput(state: TimePickerState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        TimeInput(
            state = state,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun IcsImportPreviewDialog(
    preview: IcsImportPreview,
    onDismiss: () -> Unit,
    onApply: (ImportDecision) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入预览") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        text = preview.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    ImportCountRow("新增", preview.added.size)
                    ImportCountRow("更新", preview.updated.size)
                    ImportCountRow("冲突", preview.conflicts.size)
                    ImportCountRow("覆盖时删除", preview.deleted.size)
                }
                if (preview.added.isNotEmpty()) {
                    item { DiffPreviewGroup("新增事件", preview.added) }
                }
                if (preview.updated.isNotEmpty()) {
                    item { DiffPreviewGroup("将更新", preview.updated) }
                }
                if (preview.conflicts.isNotEmpty()) {
                    item { DiffPreviewGroup("冲突提醒", preview.conflicts) }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onApply(ImportDecision.MERGE) }) {
                    Text("合并")
                }
                TextButton(onClick = { onApply(ImportDecision.OVERWRITE) }) {
                    Text("覆盖")
                }
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
private fun ImportCountRow(
    label: String,
    count: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text("$count 项", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DiffPreviewGroup(
    title: String,
    items: List<IcsDiffItem>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        items.take(4).forEach { item ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    Text(item.title, fontWeight = FontWeight.Medium)
                    Text(
                        item.startsAt.format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm")),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        item.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
