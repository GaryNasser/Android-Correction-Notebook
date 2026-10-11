package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.garynasser.correction_notebook.data.model.home.DeadlineItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import org.jsoup.Jsoup

@Composable
internal fun DeadlinePage(viewModel: DeadlineViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DeadlinePageContent(state, viewModel::refresh)
}

@Composable
internal fun DeadlinePageContent(state: DeadlineUiState, onRefresh: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("all") }
    var selected by remember { mutableStateOf<DeadlineItem?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var openError by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val rows = remember(state.items, filter, now) { deadlineRows(state.items, filter, now) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("作业与考试", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(state.updatedAt?.let { "更新于 ${formatDeadlineTime(it)}" } ?: "BIT 学习平台",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.isSignedIn) {
                    IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                        if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "同步 DDL", Modifier.size(22.dp))
                    }
                } else TextButton(onClick = onRefresh) { Text("登录学校账号") }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "全部", "pending" to "待完成", "submitted" to "已提交").forEach { (value, label) ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
                }
            }
        }
        (state.error ?: openError)?.let { message -> item {
            Text(message, Modifier.fillMaxWidth().padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        } }
        if (state.isLoading && state.items.isEmpty()) item {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
        }
        if (rows.isEmpty() && !state.isLoading) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Schedule, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(when {
                    !state.isSignedIn -> "登录后查看课程作业"
                    state.updatedAt == null -> "尚未同步 DDL"
                    filter == "submitted" -> "暂无已提交记录"
                    filter == "pending" -> "暂无待完成任务"
                    else -> "暂无作业或考试"
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.isSignedIn && state.updatedAt == null) TextButton(onClick = onRefresh) { Text("同步") }
            }
        }
        items(rows, key = { it.id }) { item ->
            Column(Modifier.fillMaxWidth().clickable { selected = item }.padding(vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (item.submitted == true) Icons.Default.CheckCircle else Icons.Default.Schedule, null,
                        Modifier.padding(top = 2.dp).size(20.dp), tint = if (item.submitted == true) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.courseName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(item.deadline?.let { "截止 ${formatDeadlineTime(it)}" } ?: "未设置截止时间",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.submitted != true && (item.deadline ?: Long.MAX_VALUE) < now) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${if (item.kind == "exam") "考试" else "作业"} · ${deadlineStatus(item, now)}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
    selected?.let { item ->
        AlertDialog(onDismissRequest = { selected = null },
            title = { Text(item.title, style = MaterialTheme.typography.titleMedium) },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.courseName, style = MaterialTheme.typography.bodyMedium)
                item.startTime?.let { Text("开始 ${formatDeadlineTime(it)}", style = MaterialTheme.typography.bodySmall) }
                Text(item.deadline?.let { "截止 ${formatDeadlineTime(it)}" } ?: "未设置截止时间", style = MaterialTheme.typography.bodySmall)
                Text(deadlineStatus(item, now), style = MaterialTheme.typography.bodySmall)
                if (item.description.isNotBlank()) Text(Jsoup.parse(item.description).text(), style = MaterialTheme.typography.bodyMedium)
            } },
            confirmButton = { TextButton(onClick = {
                runCatching { uriHandler.openUri(item.platformUrl) }.onFailure { openError = "无法打开学习平台，请检查浏览器" }
                selected = null
            }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("打开学习平台") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("关闭") } }
        )
    }
}

internal fun deadlineRows(items: List<DeadlineItem>, filter: String, now: Long): List<DeadlineItem> = items
    .filter { when (filter) { "pending" -> it.submitted != true; "submitted" -> it.submitted == true; else -> true } }
    .sortedWith(compareBy<DeadlineItem> { if (it.deadline == null) 2 else if (it.deadline < now) 1 else 0 }
        .thenBy { it.deadline ?: Long.MAX_VALUE }.thenBy { it.id })

internal fun deadlineStatus(item: DeadlineItem, now: Long): String = when {
    item.submitted == true -> "已提交"
    item.kind == "exam" -> if (item.closed || (item.deadline ?: Long.MAX_VALUE) < now) "已结束" else "未查询考试提交状态"
    item.submitted == null -> "提交状态未知"
    item.closed || (item.deadline ?: Long.MAX_VALUE) < now -> "未提交 · 已截止"
    (item.startTime ?: Long.MIN_VALUE) > now -> "未开始"
    else -> "未提交"
}

private fun formatDeadlineTime(time: Long): String = DateTimeFormatter.ofPattern("MM/dd HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
