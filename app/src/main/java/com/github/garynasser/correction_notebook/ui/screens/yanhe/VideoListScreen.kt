package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
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
    var selectedSectionId by rememberSaveable(viewModel.courseId) { mutableStateOf<Int?>(null) }
    var noteInput by rememberSaveable(viewModel.courseId) { mutableStateOf("") }
    var showCourseNotes by rememberSaveable(viewModel.courseId) { mutableStateOf(false) }
    val selectedSection = (viewModel.uiState as? VideoUIState.Success)?.videos?.firstOrNull { it.id == selectedSectionId }
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

    LaunchedEffect(assistantState.actionMessage, assistantState.actionError) {
        val message = assistantState.actionMessage ?: assistantState.actionError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        assistantViewModel.consumeActionMessage()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            VideoListTopBar(
                courseName = viewModel.courseName,
                isLoading = viewModel.uiState is VideoUIState.Loading,
                onBack = onBackButtonClick,
                onRefresh = { viewModel.getVideoList(viewModel.courseId) },
                onNotes = { showCourseNotes = true }
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
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                    item { VideoResolvingStatus(onCancel = viewModel::cancelPlayback) }
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
                            items(state.videos, key = { it.id }) { video ->
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
                                        assistantViewModel.clear()
                                        selectedSectionId = video.id
                                        noteInput = ""
                                    },
                                    aiEnabled = LocalAiEnabled.current,
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
        CourseAssistantDialog(viewModel.courseId, viewModel.courseName, section, noteInput,
            { noteInput = it }, assistantViewModel, onDismiss = {
                selectedSectionId = null
                assistantViewModel.clear()
            })
    }
    if (showCourseNotes) {
        CourseAssistantDialog(viewModel.courseId, viewModel.courseName,
            CourseSection(id = 0, courseId = viewModel.courseId, title = viewModel.courseName), "", {}, assistantViewModel,
            onDismiss = { showCourseNotes = false }, showAllNotes = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoListTopBar(courseName: String, isLoading: Boolean, onBack: () -> Unit, onRefresh: () -> Unit, onNotes: (() -> Unit)? = null) {
    TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = courseName.ifBlank { "视频列表" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (courseName.isNotBlank()) {
                    Text("延河课堂视频", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            onNotes?.let { openNotes ->
                IconButton(onClick = openNotes) {
                    Icon(Icons.Default.NoteAlt, contentDescription = "查看课程笔记")
                }
            }
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新课程视频")
            }
        }
    )
}

@Composable
internal fun VideoResolvingStatus(onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Text(
                text = "正在获取视频地址...",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "取消获取视频地址", modifier = Modifier.size(18.dp))
            }
        }
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
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
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
    onAiAssistantClick: (() -> Unit)?,
    onCameraPlayClick: () -> Unit,
    onScreenPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    aiEnabled: Boolean = true
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
    val hasTitle = section.title.isNotBlank()

    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                }
                Checkbox(
                    checked = isCompleted,
                    onCheckedChange = onCompletedChange,
                    enabled = !isUpdatingCompletion,
                    modifier = Modifier.size(32.dp).semantics { contentDescription = "章节完成状态" }
                )
            }

            val canResolveVideo = section.videos.isNotEmpty() || section.id > 0
            val canClickPlay = canResolveVideo && !isResolvingVideo

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VideoMeta(
                    text = if (hasTitle) timeInfo else "课程录像",
                    isCompleted = isCompleted,
                    modifier = Modifier.weight(1f)
                )
                onAiAssistantClick?.let { openAssistant ->
                FilledTonalIconButton(
                    onClick = openAssistant,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = if (aiEnabled) Icons.Default.Psychology else Icons.Default.NoteAlt,
                        contentDescription = if (aiEnabled) "课程助手" else "课程笔记",
                        modifier = Modifier.size(18.dp)
                    )
                }
                }

                FilledTonalIconButton(
                    onClick = onCameraPlayClick,
                    enabled = canClickPlay,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "播放摄像头视频",
                        modifier = Modifier.size(18.dp)
                    )
                }

                FilledIconButton(
                    onClick = onScreenPlayClick,
                    enabled = canClickPlay,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放屏幕录像",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoMeta(
    text: String,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isCompleted) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = if (isCompleted) "已完成 · $text" else text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isCompleted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun CourseProgressHeader(
    progressPercent: Int,
    lastTitle: String?,
    completedCount: Int,
    totalCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "课程进度",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$completedCount/$totalCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            LinearProgressIndicator(
                progress = { progressPercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = lastTitle?.takeIf { it.isNotBlank() }?.let { "上次看到：$it" } ?: "播放任意章节后会记录继续学习位置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
