package com.github.garynasser.correction_notebook.ui.screens.yanhe

import android.view.LayoutInflater
import com.github.garynasser.correction_notebook.R
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playState = viewModel.playState
    val controller by viewModel.controller // 监听 MediaController 的变化
    val videoTitle = viewModel.videoTitle.ifBlank { "延河课堂视频" }
    val courseName = viewModel.courseName

    val view = LocalView.current
    DisposableEffect(view) {
        val wasKeepingScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = wasKeepingScreenOn }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, _ ->
            viewModel.setScreenActive(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setScreenActive(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setScreenActive(false)
        }
    }
    PlayerScreenContent(controller, playState, videoTitle, courseName, onBack, viewModel::retryPlayback,
        screenPlayer = viewModel.screenPlayer, screenPlayState = viewModel.screenPlayState,
        displayMode = viewModel.displayMode, primaryIsScreen = viewModel.primaryIsScreen,
        onDisplayModeChange = viewModel::setDisplayMode, onRetryScreen = viewModel::retryScreenPlayback)
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerScreenContent(
    controller: Player?,
    playState: PlayState,
    videoTitle: String,
    courseName: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    screenPlayer: Player? = null,
    screenPlayState: PlayState = PlayState.Idle,
    displayMode: VideoDisplayMode = VideoDisplayMode.CAMERA,
    primaryIsScreen: Boolean = false,
    onDisplayModeChange: (VideoDisplayMode) -> Unit = {},
    onRetryScreen: () -> Unit = {}
) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(videoTitle, color = Color.White, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (courseName.isNotBlank()) Text(courseName, color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (screenPlayer != null) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoDisplayMode.entries.forEach { mode ->
                    FilterChip(selected = displayMode == mode, onClick = { onDisplayModeChange(mode) },
                        modifier = Modifier.semantics { contentDescription = "显示${mode.label}" },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = Color.White, selectedLabelColor = Color.Black,
                            selectedContainerColor = Color.White))
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f).testTag("videoViewport"),
            horizontalArrangement = Arrangement.spacedBy(if (displayMode == VideoDisplayMode.BOTH) 4.dp else 0.dp)) {
            if (screenPlayer != null && displayMode != VideoDisplayMode.CAMERA) {
                key("screen") {
                    VideoPane(screenPlayer, screenPlayState, "PPT", "screenSurface", onRetryScreen,
                        Modifier.weight(1f).fillMaxHeight())
                }
            }
            if (screenPlayer == null || displayMode != VideoDisplayMode.SCREEN) {
                key("primary") {
                    VideoPane(controller, playState, if (primaryIsScreen) "PPT" else "教室", "playerSurface", onRetry,
                        Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        if (controller != null) {
            AndroidView(factory = { context -> (LayoutInflater.from(context)
                .inflate(R.layout.course_player_controls, null) as PlayerControlView).apply {
                player = controller
                showTimeoutMs = 0
                setShowNextButton(false)
                setShowPreviousButton(false)
            } }, update = { it.player = controller }, onRelease = { it.player = null },
                modifier = Modifier.fillMaxWidth().height(88.dp).testTag("playbackControls"))
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPane(player: Player?, state: PlayState, label: String, tag: String,
    onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black)) {
        if (player != null) AndroidView(factory = { context -> PlayerView(context).apply {
            this.player = player
            useController = false
            setBackgroundColor(android.graphics.Color.BLACK)
        } }, update = { it.player = player }, onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize().testTag(tag))
        when (state) {
            is PlayState.Loading -> PlayerLoadingOverlay(Modifier.fillMaxSize())
            is PlayState.Error -> PlayerErrorOverlay(state.message, onRetry, Modifier.fillMaxSize())
            else -> Unit
        }
        Text(label, modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White,
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PlayerLoadingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.56f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.62f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                Text("正在缓冲", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PlayerErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.88f)).padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            color = Color(0xFF181818),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFFD2D2), modifier = Modifier.size(24.dp))
                    Text("播放失败", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    text = message,
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    color = Color(0xFFC9C9C9),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onRetry, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("重试")
                }
            }
        }
    }
}
