package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.model.ai.AiActionType
import com.github.garynasser.correction_notebook.ui.components.FreshScreen



@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseVideoListScreen(
    viewModel: VideoListViewModel = hiltViewModel(),
    assistantViewModel: CourseAssistantViewModel = hiltViewModel(),
    onNavigateToPlayer: (String, String, String) -> Unit,
    onBackButtonClick: () -> Unit
) {
    val assistantState by assistantViewModel.uiState.collectAsStateWithLifecycle()
    var selectedSection by remember { mutableStateOf<CourseSection?>(null) }
    var noteInput by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.playState) {
        val state = viewModel.playState
        if (state is PlayState.Success) {
            onNavigateToPlayer(state.url, state.videoTitle, state.courseName)
            viewModel.resetPlayState()
        }
    }

    LaunchedEffect(viewModel.sectionActionMessage) {
        val message = viewModel.sectionActionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSectionActionMessage()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = viewModel.courseName.ifBlank { "视频列表" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (viewModel.courseName.isNotBlank()) {
                            Text(
                                text = "延河课堂视频",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBackButtonClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = viewModel.uiState) {
                is VideoUIState.Error -> {
                    VideoListMessageState(
                        title = "视频列表加载失败",
                        message = state.message,
                        actionText = "重试",
                        onAction = { viewModel.getVideoList(viewModel.courseId) },
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    )
                }

                is VideoUIState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is VideoUIState.Success -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            CourseProgressHeader(
                                progressPercent = viewModel.progress?.progressPercent ?: 0,
                                lastTitle = viewModel.progress?.lastSectionTitle,
                                completedCount = viewModel.progress?.completedCount ?: 0,
                                totalCount = state.videos.size
                            )
                        }
                        when (val playState = viewModel.playState) {
                            is PlayState.Loading -> {
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Text("正在获取延河课堂视频地址...")
                                        }
                                    }
                                }
                            }
                            is PlayState.Error -> {
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.62f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = playState.message,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            IconButton(
                                                onClick = { viewModel.resetPlayState() },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "关闭播放错误",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            else -> Unit
                        }
                        if (state.videos.isEmpty()) {
                            item {
                                EmptyVideoState(onRefresh = { viewModel.getVideoList(viewModel.courseId) })
                            }
                        }
                        items(state.videos) { video ->
                            val isCompleted = viewModel.progress?.completedSectionIds?.contains(video.id) == true
                            val isResolvingVideo = viewModel.playState is PlayState.Loading
                            val isUpdatingCompletion = video.id in viewModel.updatingCompletionSectionIds
                            VideoCard(
                                section = video,
                                isCompleted = isCompleted,
                                isResolvingVideo = isResolvingVideo,
                                isUpdatingCompletion = isUpdatingCompletion,
                                onCompletedChange = { checked ->
                                    viewModel.setSectionCompleted(video, checked)
                                },
                                onAiAssistantClick = {
                                    selectedSection = video
                                    noteInput = ""
                                },
                                onCameraPlayClick = {
                                    viewModel.playSection(video, preferScreen = false)
                                },
                                onScreenPlayClick = {
                                    viewModel.playSection(video, preferScreen = true)
                                }
                            )
                        }
                    }
                }
            }
        }
        }
    }

    selectedSection?.let { section ->
        AlertDialog(
            onDismissRequest = {
                selectedSection = null
                assistantViewModel.clear()
            },
            shape = RoundedCornerShape(8.dp),
            title = {
                Text(
                    text = "课程助手",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(section.title, style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("补充课堂笔记，可留空") },
                        minLines = 3,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    when {
                        assistantState.isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI 正在整理课程内容...")
                        }
                        assistantState.result != null -> Text(assistantState.result.orEmpty())
                        assistantState.error != null -> Text(
                            assistantState.error.orEmpty(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    assistantState.actions.forEach { action ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (action.type == AiActionType.SAVE_COURSE_NOTE) Icons.Default.NoteAlt else Icons.Default.AddTask,
                                    contentDescription = null
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(action.title, fontWeight = FontWeight.SemiBold)
                                    if (action.description.isNotBlank()) {
                                        Text(action.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                    }
                                }
                                TextButton(onClick = {
                                    assistantViewModel.applyAction(
                                        action = action,
                                        courseId = viewModel.courseId,
                                        courseName = viewModel.courseName,
                                        sectionId = section.id,
                                        sectionTitle = section.title
                                    )
                                }) { Text("确认") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (assistantState.result != null) {
                        TextButton(
                            onClick = {
                                assistantViewModel.saveResultAsNote(
                                    viewModel.courseId,
                                    viewModel.courseName,
                                    section.id,
                                    section.title
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("存笔记") }
                        TextButton(
                            onClick = {
                                assistantViewModel.saveResultAsTodo(viewModel.courseId, section.title)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("转待办") }
                    }
                    TextButton(
                        onClick = {
                            assistantViewModel.summarizeLearningPackage(
                                viewModel.courseId,
                                viewModel.courseName,
                                section.id,
                                section.title,
                                noteInput
                            )
                        },
                        enabled = !assistantState.isLoading,
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(if (assistantState.result == null) "生成学习包" else "重新生成") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedSection = null
                    assistantViewModel.clear()
                }, shape = RoundedCornerShape(8.dp)) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun VideoListMessageState(
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .heightIn(min = 132.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(actionText)
            }
        }
    }
}

@Composable
private fun EmptyVideoState(
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.School,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "暂无课程视频",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "这个课程暂时没有可播放章节，稍后刷新或换一门课程试试。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onRefresh,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("刷新")
            }
        }
    }
}

@Composable
fun VideoCard(
    section: CourseSection,
    isCompleted: Boolean,
    isResolvingVideo: Boolean,
    isUpdatingCompletion: Boolean,
    onCompletedChange: (Boolean) -> Unit,
    onAiAssistantClick: () -> Unit,
    onCameraPlayClick: () -> Unit,
    onScreenPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeInfo = buildString {
        append("第 ${section.weekNumber} 周")
        append(" · ")
        if (section.sectionBigStart == section.sectionBigEnd) {
            append("第 ${section.sectionBigStart} 大节")
        } else {
            append("第 ${section.sectionBigStart}-${section.sectionBigEnd} 大节")
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 11.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = section.title.ifBlank { timeInfo },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timeInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Checkbox(
                    checked = isCompleted,
                    onCheckedChange = onCompletedChange,
                    enabled = !isUpdatingCompletion,
                    modifier = Modifier.size(36.dp)
                )
            }

            val canResolveVideo = section.videos.isNotEmpty() || section.id > 0
            val canClickPlay = canResolveVideo && !isResolvingVideo

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onAiAssistantClick,
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "课程助手",
                        modifier = Modifier.size(19.dp)
                    )
                }

                FilledIconButton(
                    onClick = onCameraPlayClick,
                    enabled = canClickPlay,
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "播放摄像头视频",
                        modifier = Modifier.size(19.dp)
                    )
                }

                FilledIconButton(
                    onClick = onScreenPlayClick,
                    enabled = canClickPlay,
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放屏幕录像",
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseProgressHeader(
    progressPercent: Int,
    lastTitle: String?,
    completedCount: Int,
    totalCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "课程进度",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text("$completedCount/$totalCount")
            }
            LinearProgressIndicator(
                progress = { progressPercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = lastTitle?.takeIf { it.isNotBlank() }?.let { "上次看到：$it" } ?: "播放任意章节后会记录继续学习位置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
