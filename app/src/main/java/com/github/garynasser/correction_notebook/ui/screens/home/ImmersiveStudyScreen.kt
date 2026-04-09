package com.github.garynasser.correction_notebook.ui.screens.home

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.net.Uri
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.github.garynasser.correction_notebook.data.model.home.PomodoroPhase
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import com.github.garynasser.correction_notebook.domain.usecase.StudyTimerManager

enum class WhiteNoise(val displayName: String) {
    RAIN("雨声"),
    OCEAN("海浪"),
    FOREST("森林"),
    CAFE("咖啡馆")
}

@Composable
fun ImmersiveStudyScreen(
    timerManager: StudyTimerManager,
    onExit: () -> Unit,
    backgroundImageUri: String? = null,
    isLandscapeOrientation: Boolean = false,
    onOrientationChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val timerState by timerManager.timerState.collectAsState()

    var selectedNoise by remember { mutableStateOf<WhiteNoise?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isLandscape by remember { mutableStateOf(isLandscapeOrientation) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    // Handle orientation changes
    LaunchedEffect(isLandscape) {
        val activity = context as? android.app.Activity
        activity?.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Auto-hide controls in fullscreen after 3 seconds
    LaunchedEffect(isFullscreen, showControls) {
        if (isFullscreen && showControls) {
            kotlinx.coroutines.delay(3000)
            showControls = false
        }
    }

    // Reset orientation and show controls when exiting
    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? android.app.Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            mediaPlayer?.release()
        }
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
            showControls = true
        } else {
            mediaPlayer?.release()
            mediaPlayer = null
            timerManager.stop()
            onExit()
        }
    }

    val backgroundGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF006781),
                Color(0xFF004D61),
                Color(0xFF003344)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (isFullscreen) {
                    showControls = !showControls
                }
            }
    ) {
        // Background image if set
        if (backgroundImageUri != null) {
            Image(
                painter = rememberAsyncImagePainter(Uri.parse(backgroundImageUri)),
                contentDescription = "背景图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.6f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f)
                            )
                        )
                    )
            )
        }

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section - only show when not fullscreen or controls visible
            if (!isFullscreen || showControls) {
                TopControls(
                    isLandscape = isLandscape,
                    selectedNoise = selectedNoise,
                    onExit = {
                        mediaPlayer?.release()
                        mediaPlayer = null
                        timerManager.stop()
                        onExit()
                    },
                    onOrientationToggle = {
                        isLandscape = !isLandscape
                        onOrientationChange(isLandscape)
                    },
                    onFullscreenToggle = { isFullscreen = true },
                    onNoiseSelect = { noise ->
                        if (selectedNoise == noise) {
                            mediaPlayer?.release()
                            mediaPlayer = null
                            selectedNoise = null
                        } else {
                            selectedNoise = noise
                            mediaPlayer?.release()
                            mediaPlayer = createMediaPlayer(context, noise)
                            mediaPlayer?.start()
                        }
                    },
                    onNoiseNone = {
                        mediaPlayer?.release()
                        mediaPlayer = null
                        selectedNoise = null
                    }
                )
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }

            // Center section - Timer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimerDisplay(
                    timerState = timerState,
                    isFullscreen = isFullscreen && !showControls
                )
            }

            // Bottom section - Controls (hidden in fullscreen minimal mode)
            if (!isFullscreen || showControls) {
                BottomControls(
                    timerState = timerState,
                    isLandscape = isLandscape,
                    onSkip = { timerManager.skip() },
                    onReset = { timerManager.reset() },
                    onPlayPause = {
                        val state = timerState
                        when (state) {
                            is TimerState.Idle -> timerManager.startPomodoro()
                            is TimerState.Pomodoro -> {
                                if (state.state.isRunning) timerManager.pause() else timerManager.resume()
                            }
                            is TimerState.Countdown -> {
                                if (state.isRunning) timerManager.pause() else timerManager.resume()
                            }
                            is TimerState.Stopwatch -> {
                                if (state.isRunning) timerManager.pause() else timerManager.resume()
                            }
                        }
                    },
                    onStop = { timerManager.stop() }
                )
            } else {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun TopControls(
    isLandscape: Boolean,
    selectedNoise: WhiteNoise?,
    onExit: () -> Unit,
    onOrientationToggle: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onNoiseSelect: (WhiteNoise) -> Unit,
    onNoiseNone: () -> Unit
) {
    if (isLandscape) {
        // Horizontal layout for landscape
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onExit, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, "退出", tint = Color.White)
                }
                IconButton(onClick = onOrientationToggle, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ScreenRotation, "横竖屏", tint = Color.White)
                }
                IconButton(onClick = onFullscreenToggle, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Fullscreen, "全屏", tint = Color.White)
                }
            }
            WhiteNoiseSelector(
                selectedNoise = selectedNoise,
                onNoiseSelect = onNoiseSelect,
                onNoiseNone = onNoiseNone,
                compact = true
            )
        }
    } else {
        // Vertical layout for portrait
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExit, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, "退出", tint = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onOrientationToggle, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.ScreenRotation, "横竖屏", tint = Color.White)
                    }
                    IconButton(onClick = onFullscreenToggle, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Fullscreen, "全屏", tint = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            WhiteNoiseSelector(
                selectedNoise = selectedNoise,
                onNoiseSelect = onNoiseSelect,
                onNoiseNone = onNoiseNone,
                compact = false
            )
        }
    }
}

@Composable
private fun WhiteNoiseSelector(
    selectedNoise: WhiteNoise?,
    onNoiseSelect: (WhiteNoise) -> Unit,
    onNoiseNone: () -> Unit,
    compact: Boolean
) {
    val buttonSize = if (compact) 36.dp else 40.dp
    val iconSize = if (compact) 18.dp else 20.dp

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // None button
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(
                    if (selectedNoise == null) Color.White.copy(alpha = 0.4f)
                    else Color.White.copy(alpha = 0.15f)
                )
                .clickable { onNoiseNone() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.VolumeOff,
                "无白噪音",
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }

        WhiteNoise.entries.forEach { noise ->
            val isSelected = selectedNoise == noise
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.4f)
                        else Color.White.copy(alpha = 0.15f)
                    )
                    .clickable { onNoiseSelect(noise) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (noise) {
                        WhiteNoise.RAIN -> Icons.Default.Water
                        WhiteNoise.OCEAN -> Icons.Default.Waves
                        WhiteNoise.FOREST -> Icons.Default.Forest
                        WhiteNoise.CAFE -> Icons.Default.LocalCafe
                    },
                    noise.displayName,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun BottomControls(
    timerState: TimerState,
    isLandscape: Boolean,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit
) {
    val buttonSize = 56.dp
    val playButtonSize = 72.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left button
        when {
            timerState is TimerState.Pomodoro -> {
                IconButton(
                    onClick = onSkip,
                    modifier = Modifier.size(buttonSize).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.SkipNext, "跳过", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            timerState is TimerState.Stopwatch -> {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(buttonSize).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.Refresh, "复位", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            else -> Spacer(modifier = Modifier.size(buttonSize))
        }

        // Play/Pause button
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(playButtonSize).clip(CircleShape).background(Color.White)
        ) {
            Icon(
                when (timerState) {
                    is TimerState.Idle -> Icons.Default.PlayArrow
                    is TimerState.Pomodoro -> if (timerState.state.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow
                    is TimerState.Countdown -> if (timerState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow
                    is TimerState.Stopwatch -> if (timerState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow
                },
                "播放/暂停",
                tint = Color(0xFF006781),
                modifier = Modifier.size(36.dp)
            )
        }

        // Right button
        when {
            timerState is TimerState.Pomodoro -> {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(buttonSize).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.Stop, "停止", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            timerState is TimerState.Countdown -> {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(buttonSize).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.Refresh, "复位", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            else -> Spacer(modifier = Modifier.size(buttonSize))
        }
    }
}

@Composable
private fun TimerDisplay(
    timerState: TimerState,
    isFullscreen: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val displayText = when (timerState) {
        is TimerState.Idle -> "25:00"
        is TimerState.Pomodoro -> formatTime(timerState.state.timeRemainingSeconds)
        is TimerState.Countdown -> formatTime(timerState.remainingSeconds)
        is TimerState.Stopwatch -> formatTime(timerState.elapsedSeconds)
    }

    val showPulse = when (timerState) {
        is TimerState.Pomodoro -> timerState.state.phase == PomodoroPhase.FOCUS && !timerState.state.isRunning
        else -> false
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = displayText,
            fontSize = if (isFullscreen) 120.sp else 96.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = if (showPulse) alpha else 1f)
        )

        if (!isFullscreen) {
            if (timerState is TimerState.Pomodoro) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (timerState.state.phase) {
                        PomodoroPhase.FOCUS -> "专注时间"
                        PomodoroPhase.SHORT_BREAK -> "短休息"
                        PomodoroPhase.LONG_BREAK -> "长休息"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "已完成 ${timerState.state.completedPomodoros} 个番茄钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}

private fun createMediaPlayer(context: Context, noise: WhiteNoise): MediaPlayer? {
    return try {
        val resName = when (noise) {
            WhiteNoise.RAIN -> "rain"
            WhiteNoise.OCEAN -> "ocean"
            WhiteNoise.FOREST -> "forest"
            WhiteNoise.CAFE -> "cafe"
        }
        val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
        if (resId == 0) return null
        MediaPlayer.create(context, resId)?.apply {
            isLooping = true
            setVolume(0.5f, 0.5f)
        }
    } catch (e: Exception) {
        null
    }
}
