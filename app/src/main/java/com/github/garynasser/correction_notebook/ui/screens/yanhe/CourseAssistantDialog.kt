package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CourseAssistantDialog(
    courseId: Int,
    courseName: String,
    section: CourseSection,
    noteInput: String,
    onNoteInputChange: (String) -> Unit,
    viewModel: CourseAssistantViewModel,
    onDismiss: () -> Unit,
    showAllNotes: Boolean = false
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val aiEnabled = LocalAiEnabled.current && !showAllNotes
    var selectedTab by rememberSaveable(section.id) { mutableIntStateOf(0) }
    var expandedNoteId by rememberSaveable(section.id) { mutableStateOf<String?>(null) }
    var deletingNoteId by rememberSaveable(section.id) { mutableStateOf<String?>(null) }
    val showNotes = !aiEnabled || selectedTab == 1
    val notes = notesState.notes.filter { it.courseId == courseId && (showAllNotes || it.sectionId == section.id) }
    var panelBounds by remember { mutableStateOf(Rect.Zero) }
    var contentPosition by remember { mutableStateOf(Offset.Zero) }
    fun dismiss() { if (!state.isActionBusy) onDismiss() }

    Dialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize()
            .onGloballyPositioned { contentPosition = it.positionInRoot() }
            .pointerInput(state.isActionBusy) {
                detectTapGestures { position -> if (!panelBounds.contains(position + contentPosition)) dismiss() }
            }.safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
            Box(Modifier.padding(12.dp)) {
                Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().onGloballyPositioned { panelBounds = it.boundsInRoot() },
                    shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (aiEnabled) "课程助手" else "课程笔记", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = ::dismiss, enabled = !state.isActionBusy) {
                                Icon(Icons.Default.Close, contentDescription = "关闭课程助手")
                            }
                        }
                        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (aiEnabled) {
                                PrimaryTabRow(selectedTabIndex = selectedTab) {
                                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("学习包") })
                                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("笔记") })
                                }
                            }
                            if (showNotes) {
                                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    when {
                                        notesState.isLoading -> item { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                                        notesState.error != null -> item {
                                            Text(notesState.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                            TextButton(onClick = viewModel::retryNotes) { Text("重试") }
                                        }
                                        notes.isEmpty() -> item { Text(if (showAllNotes) "本课程暂无笔记" else "本节暂无笔记", style = MaterialTheme.typography.bodyMedium) }
                                        else -> items(notes, key = { it.id }) { note ->
                                            val expanded = expandedNoteId == note.id
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (showAllNotes) Text(note.sectionTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val date = remember(note.createdAt) {
                                                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(note.createdAt))
                                                    }
                                                    Text("$date · ${if (note.aiGenerated) "AI 笔记" else "课堂笔记"}", Modifier.weight(1f),
                                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    IconButton(onClick = { deletingNoteId = note.id }, enabled = !state.isActionBusy && !state.isLoading) {
                                                        Icon(Icons.Default.Delete, contentDescription = "删除笔记 $date", modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                                SelectionContainer {
                                                    Text(note.content, style = MaterialTheme.typography.bodySmall,
                                                        maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                                                }
                                                TextButton(onClick = { expandedNoteId = if (expanded) null else note.id }) {
                                                    Text(if (expanded) "收起笔记" else "展开笔记")
                                                }
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = noteInput, onValueChange = onNoteInputChange,
                                        label = { Text("补充课堂笔记，可留空") }, minLines = 2, maxLines = 4,
                                        textStyle = MaterialTheme.typography.bodySmall, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth())
                                    when {
                                        state.isLoading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Text("正在生成学习包", style = MaterialTheme.typography.bodySmall)
                                        }
                                        state.result != null -> SelectionContainer { Text(state.result.orEmpty(), style = MaterialTheme.typography.bodySmall) }
                                        state.error != null -> Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                    state.actions.forEach { action ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                            Text(action.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            if (action.description.isNotBlank()) Text(action.description, style = MaterialTheme.typography.bodySmall)
                                            CourseAssistantActionButton(courseAssistantAiActionKey(action.id), "确认", "已完成", state) {
                                                viewModel.applyAction(action, courseId, courseName, section.id, section.title)
                                            }
                                        }
                                    }
                                }
                            }
                            state.actionError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            state.actionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        }
                        if (!showNotes) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (state.result != null) {
                                CourseAssistantActionButton(courseAssistantResultNoteKey(courseId, section.id), "存笔记", "已保存", state) {
                                    viewModel.saveResultAsNote(courseId, courseName, section.id, section.title)
                                }
                                CourseAssistantActionButton(courseAssistantResultTodoKey(courseId, section.id), "转待办", "已添加", state) {
                                    viewModel.saveResultAsTodo(courseId, section.id, section.title)
                                }
                            }
                            TextButton(onClick = { viewModel.summarizeLearningPackage(courseId, courseName, section.id, section.title, noteInput) },
                                enabled = !state.isLoading && !state.isActionBusy, shape = RoundedCornerShape(8.dp)) {
                                Text(if (state.result == null) "生成学习包" else "重新生成")
                            }
                        }
                    }
                }
            }
        }
    }

    notes.firstOrNull { it.id == deletingNoteId }?.let { note ->
        AlertDialog(onDismissRequest = { if (!state.isActionBusy) deletingNoteId = null }, shape = RoundedCornerShape(8.dp),
            title = { Text("删除这条笔记？", style = MaterialTheme.typography.titleMedium) },
            text = { Text(note.content, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton(onClick = { viewModel.deleteNote(note.id); deletingNoteId = null }, enabled = !state.isActionBusy) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deletingNoteId = null }, enabled = !state.isActionBusy) { Text("取消") } })
    }
}

@Composable
private fun CourseAssistantActionButton(actionKey: String, idleLabel: String, completedLabel: String, state: CourseAssistantUiState, onClick: () -> Unit) {
    val applying = actionKey in state.applyingActionKeys
    val applied = actionKey in state.appliedActionKeys
    TextButton(onClick = onClick, enabled = !state.isLoading && !state.isActionBusy && !applied, shape = RoundedCornerShape(8.dp)) {
        if (applying) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
        if (applied) Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
        if (applying || applied) Spacer(Modifier.width(4.dp))
        Text(if (applying) "处理中" else if (applied) completedLabel else idleLabel)
    }
}
