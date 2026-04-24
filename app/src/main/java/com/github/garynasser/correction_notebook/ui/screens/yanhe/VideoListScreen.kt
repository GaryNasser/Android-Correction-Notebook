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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    onNavigateToPlayer: (String) -> Unit,
    onBackButtonClick: () -> Unit
) {
    val context = LocalContext.current

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
}

@Composable
fun VideoCard(
    section: CourseSection,
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
