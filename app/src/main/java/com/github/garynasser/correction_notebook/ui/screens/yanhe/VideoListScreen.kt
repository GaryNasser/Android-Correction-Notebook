package com.github.garynasser.correction_notebook.ui.screens.yanhe

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.model.yanhe.Video



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseVideoListScreen(
    viewModel: VideoListViewModel = hiltViewModel(),
    assistantViewModel: CourseAssistantViewModel = hiltViewModel(),
    onNavigateToPlayer: (String) -> Unit,
    onBackButtonClick: () -> Unit
) {
    val context = LocalContext.current
    val assistantState by assistantViewModel.uiState.collectAsState()
    var selectedSectionTitle by remember { mutableStateOf<String?>(null) }
    var noteInput by remember { mutableStateOf("") }

    LaunchedEffect(viewModel.playState) {
        val state = viewModel.playState
        if (state is PlayState.Success) {
            onNavigateToPlayer(state.url)
            viewModel.resetPlayState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "视频列表", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBackButtonClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = viewModel.uiState) {
                is VideoUIState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.getVideoList(viewModel.courseId) }) { Text("重试") }
                    }
                }

                is VideoUIState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is VideoUIState.Success -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        items(state.videos) { video ->
                            VideoCard(
                                section = video,
                                onAiAssistantClick = { sectionTitle ->
                                    selectedSectionTitle = sectionTitle
                                    noteInput = ""
                                },
                                onCameraPlayClick = { videos ->
                                    Log.i("VIDEO", "Play btn pressed")
                                    if (videos.isNotEmpty()) {
                                        onNavigateToPlayer(videos[0].mainUrl)
                                    }
                                },
                                onScreenPlayClick = {videos ->
                                    Log.i("VIDEO", "Play btn pressed")
                                    if (videos.isNotEmpty()) {
                                        onNavigateToPlayer(videos[0].vgaUrl)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedSectionTitle?.let { title ->
        AlertDialog(
            onDismissRequest = {
                selectedSectionTitle = null
                assistantViewModel.clear()
            },
            title = { Text("课程助手") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("补充课堂笔记，可留空") },
                        minLines = 3,
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
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { assistantViewModel.summarize(title, noteInput) },
                    enabled = !assistantState.isLoading
                ) { Text(if (assistantState.result == null) "生成" else "重新生成") }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedSectionTitle = null
                    assistantViewModel.clear()
                }) { Text("关闭") }
            }
        )
    }
}

@Composable
fun VideoCard(
    section: CourseSection,
    onAiAssistantClick: (String) -> Unit,
    onCameraPlayClick: (List<Video>) -> Unit,
    onScreenPlayClick: (List<Video>) -> Unit,
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

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = timeInfo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.width(16.dp))

            val hasVideo = section.videos.isNotEmpty()

            FilledIconButton(
                onClick = { onAiAssistantClick(timeInfo) },
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "课程助手"
                )
            }

            FilledIconButton(
                onClick = { onCameraPlayClick(section.videos) },
                enabled = hasVideo,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = "播放摄像头视频"
                )
            }

            FilledIconButton(
                onClick = { onScreenPlayClick(section.videos) },
                enabled = hasVideo,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放屏幕录像"
                )
            }
        }
    }
}
