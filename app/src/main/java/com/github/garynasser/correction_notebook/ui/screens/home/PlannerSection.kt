package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.github.garynasser.correction_notebook.data.repository.scheduleDateRange
import com.github.garynasser.correction_notebook.ui.components.FreshCard
import java.time.LocalDate
import java.time.LocalTime
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    mutatingTodoIds = uiState.mutatingTodoIds,
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sections.filter { it.items.isNotEmpty() }.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    section.items.forEach { item ->
                        CompactWeekScheduleItem(item = item, onClick = { selectedOccurrence = item })
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
    mutatingTodoIds: Set<String>,
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            todos.forEach { todo ->
                key(todo.id) {
                    TodoItemCard(
                        todo = todo,
                        isBusy = todo.id in mutatingTodoIds,
                        onToggleComplete = { onToggleTodo(todo.id) },
                        onAiBreakdown = if (LocalAiEnabled.current) ({ onBreakDownTodo(todo) }) else null,
                        onDelete = { onDeleteTodo(todo.id) }
                    )
                }
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sections.forEachIndexed { index, section ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp),
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
                if (section.items.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                maxLines = 2
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
        )
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
        ScheduleRange.WEEK -> {
            val (monday, sunday) = scheduleDateRange(ScheduleRange.WEEK, selectedDate)
            "显示 ${monday.format(DateTimeFormatter.ofPattern("M月d日"))} 至 ${sunday.format(DateTimeFormatter.ofPattern("M月d日"))} 的课程和日程"
        }
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
    initialDate: LocalDate = LocalDate.now(),
    isSaving: Boolean = false,
    saveError: String? = null,
    onDismiss: () -> Unit,
    onAdd: (ScheduleEvent) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var allDay by rememberSaveable { mutableStateOf(false) }
    var selectedDateText by rememberSaveable { mutableStateOf(initialDate.toString()) }
    val selectedDate = LocalDate.parse(selectedDateText)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var validationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var startHour by rememberSaveable { mutableStateOf("09") }
    var startMinute by rememberSaveable { mutableStateOf("00") }
    var endHour by rememberSaveable { mutableStateOf("10") }
    var endMinute by rememberSaveable { mutableStateOf("00") }
    val fieldShape = RoundedCornerShape(8.dp)
    var panelBounds by remember { mutableStateOf(Rect.Zero) }
    var contentPosition by remember { mutableStateOf(Offset.Zero) }
    fun dismiss() { if (!isSaving) onDismiss() }

    Dialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val save = {
            if (!isSaving && title.isNotBlank()) {
                val start = if (allDay) selectedDate.atStartOfDay() else {
                    scheduleTimeFromInput(startHour, startMinute)?.let(selectedDate::atTime)
                }
                val end = if (allDay) selectedDate.plusDays(1).atStartOfDay() else {
                    scheduleTimeFromInput(endHour, endMinute)?.let(selectedDate::atTime)
                }
                when {
                    start == null || end == null -> validationMessage = "请填写完整的开始和结束时间"
                    !end.isAfter(start) -> validationMessage = "结束时间需要晚于开始时间"
                    else -> {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        onAdd(ScheduleEvent(title = title.trim(), description = description.trim(),
                            location = location.trim(), startAt = start, endAt = end, allDay = allDay))
                    }
                }
            }
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { contentPosition = it.positionInRoot() }
                .pointerInput(isSaving) {
                    detectTapGestures { position ->
                        if (!panelBounds.contains(position + contentPosition)) dismiss()
                    }
                }
                .safeDrawingPadding().imePadding().padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val compactEditor = keyboardVisible && maxHeight < 320.dp
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .onGloballyPositioned { panelBounds = it.boundsInRoot() },
                shape = fieldShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    Modifier.padding(if (compactEditor) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactEditor) 4.dp else 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("添加日程", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text("取消") } }, state = rememberTooltipState()
                        ) {
                            IconButton(onClick = ::dismiss, enabled = !isSaving) {
                                Icon(Icons.Default.Close, contentDescription = "取消", modifier = Modifier.size(20.dp))
                            }
                        }
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text(if (isSaving) "保存中" else "保存") } }, state = rememberTooltipState()
                        ) {
                            FilledTonalIconButton(
                                onClick = save, enabled = title.isNotBlank() && !isSaving, shape = fieldShape,
                                modifier = Modifier.semantics { contentDescription = if (isSaving) "保存中" else "保存" }
                            ) {
                                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    (validationMessage ?: saveError)?.let { message ->
                        Text(text = message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                            .heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                validationMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("活动标题", style = MaterialTheme.typography.bodySmall) },
                            enabled = !isSaving,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = fieldShape
                        )
                        OutlinedTextField(
                            value = location,
                            onValueChange = {
                                location = it
                                validationMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("地点", style = MaterialTheme.typography.bodySmall) },
                            enabled = !isSaving,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = fieldShape
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("备注", style = MaterialTheme.typography.bodySmall) },
                            enabled = !isSaving,
                            maxLines = if (compactEditor) 1 else 3,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = fieldShape
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("全天安排", style = MaterialTheme.typography.bodyMedium)
                            androidx.compose.material3.Switch(
                                checked = allDay,
                                onCheckedChange = {
                                    allDay = it
                                    validationMessage = null
                                },
                                enabled = !isSaving,
                                modifier = Modifier.semantics { contentDescription = "全天安排" }
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                showDatePicker = true
                            },
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = fieldShape
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
                        }
                        if (!allDay) {
                            CompactTimeInput(
                                label = "开始",
                                hour = startHour,
                                minute = startMinute,
                                enabled = !isSaving,
                                onHourChange = {
                                    startHour = it
                                    validationMessage = null
                                },
                                onMinuteChange = {
                                    startMinute = it
                                    validationMessage = null
                                }
                            )
                            CompactTimeInput(
                                label = "结束",
                                hour = endHour,
                                minute = endMinute,
                                enabled = !isSaving,
                                onHourChange = {
                                    endHour = it
                                    validationMessage = null
                                },
                                onMinuteChange = {
                                    endMinute = it
                                    validationMessage = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        PlannerDatePickerDialog(
            initialDate = selectedDate,
            enabled = !isSaving,
            onDismiss = { showDatePicker = false },
            onDateSelected = { date ->
                selectedDateText = date.toString()
                validationMessage = null
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun CompactTimeInput(
    label: String,
    hour: String,
    minute: String,
    enabled: Boolean,
    onHourChange: (String) -> Unit,
    onMinuteChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp))
        CompactTimeField(
            value = hour,
            label = "时",
            accessibilityLabel = "${label}小时",
            enabled = enabled,
            valueRange = 0..23,
            imeAction = ImeAction.Next,
            modifier = Modifier.weight(1f),
            onValueChange = onHourChange
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
        )
        CompactTimeField(
            value = minute,
            label = "分",
            accessibilityLabel = "${label}分钟",
            enabled = enabled,
            valueRange = 0..59,
            imeAction = ImeAction.Done,
            modifier = Modifier.weight(1f),
            onValueChange = onMinuteChange
        )
    }
}

@Composable
private fun CompactTimeField(
    value: String,
    label: String,
    accessibilityLabel: String,
    enabled: Boolean,
    valueRange: IntRange,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            normalizeTimeFieldInput(raw, valueRange)?.let(onValueChange)
        },
        modifier = modifier.semantics { contentDescription = accessibilityLabel },
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction
        )
    )
}

internal fun normalizeTimeFieldInput(raw: String, valueRange: IntRange): String? {
    val digits = raw.filter { it.isDigit() }.take(2)
    return digits.takeIf { it.isEmpty() || it.toInt() in valueRange }
}

internal fun scheduleTimeFromInput(hour: String, minute: String): LocalTime? {
    val parsedHour = hour.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val parsedMinute = minute.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return LocalTime.of(parsedHour, parsedMinute)
}

@Composable
fun IcsImportPreviewDialog(
    preview: IcsImportPreview,
    isApplying: Boolean = false,
    onDismiss: () -> Unit,
    onApply: (ImportDecision) -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isApplying) onDismiss()
        },
        shape = RoundedCornerShape(8.dp),
        title = { Text("导入预览", style = MaterialTheme.typography.titleMedium) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = preview.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ImportCountRow("新增", preview.added.size, Modifier.weight(1f))
                            ImportCountRow("更新", preview.updated.size, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ImportCountRow("冲突", preview.conflicts.size, Modifier.weight(1f))
                            ImportCountRow("待删除", preview.deleted.size, Modifier.weight(1f))
                        }
                    }
                }
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (preview.added.isEmpty() && preview.updated.isEmpty() &&
                    preview.conflicts.isEmpty() && preview.deleted.isEmpty()
                ) {
                    item {
                        Text("没有变更", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                diffPreviewGroup("新增事件", preview.added)
                diffPreviewGroup("将更新", preview.updated)
                diffPreviewGroup("冲突提醒", preview.conflicts)
                diffPreviewGroup("覆盖时删除", preview.deleted)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onApply(ImportDecision.MERGE) },
                    enabled = !isApplying,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isApplying) "导入中" else "合并")
                }
                TextButton(
                    onClick = { onApply(ImportDecision.OVERWRITE) },
                    enabled = !isApplying,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("覆盖")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isApplying,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ImportCountRow(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text("$count 项", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun LazyListScope.diffPreviewGroup(
    title: String,
    changes: List<IcsDiffItem>
) {
    if (changes.isEmpty()) return
    item {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
    items(changes) { item ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                item.startsAt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                item.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}
