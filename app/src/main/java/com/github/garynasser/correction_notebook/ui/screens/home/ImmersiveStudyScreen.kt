package com.github.garynasser.correction_notebook.ui.screens.home

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.rememberAsyncImagePainter
import com.github.garynasser.correction_notebook.data.model.home.PomodoroPhase
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import com.github.garynasser.correction_notebook.domain.usecase.AlertManager
import com.github.garynasser.correction_notebook.domain.usecase.StudyTimerManager
import kotlinx.coroutines.delay

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
    onStop: () -> Unit = {},
    backgroundImageUri: String? = null,
    soundEnabled: Boolean = true,
    vibrationEnabled: Boolean = true,
    onSoundEnabledChange: (Boolean) -> Unit = {},
    onVibrationEnabledChange: (Boolean) -> Unit = {},
    pomodoroSettings: PomodoroSettings = PomodoroSettings(),
    onPomodoroSettingsSave: (PomodoroSettings) -> Unit = {},
    isPomodoroMode: Boolean = true
) {
    val context = LocalContext.current
    val timerState by timerManager.timerState.collectAsState()
    val navigationBottomPadding = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    var selectedNoise by remember { mutableStateOf<WhiteNoise?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var alertManager by remember { mutableStateOf<AlertManager?>(null) }
    var showPomodoroSettingsDialog by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var isAlerting by remember { mutableStateOf(false) }
    val activity = context as? android.app.Activity
    val window = activity?.window
    val decorView = window?.decorView

    LaunchedEffect(showControls, showMoreSheet, timerState) {
        if (showControls && !showMoreSheet && timerState !is TimerState.Idle) {
            delay(3500)
            showControls = false
        }
    }

    SideEffect {
        if (window != null && decorView != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    DisposableEffect(Unit) {
        alertManager = AlertManager(context)
        if (window != null && decorView != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        timerManager.onTimerFinished = {
            isAlerting = true
            if (soundEnabled) {
                alertManager?.playAlarmSound(looping = true)
            }
            if (vibrationEnabled) {
                alertManager?.vibrate(AlertManager.VibrationPattern.POMODORO)
            }
        }

        onDispose {
            if (window != null && decorView != null) {
                WindowInsetsControllerCompat(window, decorView)
                    .show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
            mediaPlayer?.release()
            alertManager?.stop()
            timerManager.onTimerFinished = null
            timerManager.onPomodoroPhaseChanged = null
        }
    }

    BackHandler {
        alertManager?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        onExit()
    }

    LaunchedEffect(isAlerting) {
        if (isAlerting) {
            delay(10_000)
            alertManager?.stop()
            isAlerting = false
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
                showControls = !showControls
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

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            TimerDisplay(
                timerState = timerState,
                compactMode = !showControls
            )
        }

        if (showControls) {
            ImmersiveTopBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 20.dp
                    ),
                timerState = timerState,
                onExit = {
                    alertManager?.stop()
                    mediaPlayer?.release()
                    mediaPlayer = null
                    onExit()
                },
                onMoreClick = { showMoreSheet = true }
            )

            BottomControls(
                timerState = timerState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = navigationBottomPadding + 20.dp
                    ),
                onSkip = { timerManager.skip() },
                onReset = { timerManager.reset() },
                onPlayPause = {
                    val state = timerState
                    when (state) {
                        is TimerState.Idle -> Unit
                        is TimerState.Pomodoro -> {
                            if (state.state.isRunning) timerManager.pause() else timerManager.resume()
                        }
                        is TimerState.Countdown -> {
                            if (state.isRunning) timerManager.pause() else timerManager.resume()
                        }
                        is TimerState.CountdownFinished -> {
                            timerManager.reset()
                        }
                        is TimerState.Stopwatch -> {
                            if (state.isRunning) timerManager.pause() else timerManager.resume()
                        }
                        is TimerState.StopwatchFinished -> {
                            timerManager.reset()
                        }
                    }
                },
                onStop = {
                    alertManager?.stop()
                    mediaPlayer?.release()
                    mediaPlayer = null
                    onStop()
                }
            )
        }

        if (isAlerting) {
            StopAlertButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navigationBottomPadding + 108.dp),
                onStopAlert = {
                    alertManager?.stop()
                    isAlerting = false
                }
            )
        }
    }

    if (showMoreSheet) {
        ImmersiveMoreSheet(
            selectedNoise = selectedNoise,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
            isPomodoroMode = isPomodoroMode,
            onDismiss = { showMoreSheet = false },
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
            },
            onSoundToggle = { onSoundEnabledChange(!soundEnabled) },
            onVibrationToggle = { onVibrationEnabledChange(!vibrationEnabled) },
            onOpenPomodoroSettings = {
                showMoreSheet = false
                if (isPomodoroMode) {
                    showPomodoroSettingsDialog = true
                }
            }
        )
    }

    if (showPomodoroSettingsDialog) {
        PomodoroSettingsDialog(
            currentSettings = pomodoroSettings,
            onDismiss = { showPomodoroSettingsDialog = false },
            onSave = { settings ->
                onPomodoroSettingsSave(settings)
                showPomodoroSettingsDialog = false
            }
        )
    }
}

@Composable
private fun ImmersiveTopBar(
    modifier: Modifier = Modifier,
    timerState: TimerState,
    onExit: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onExit, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.Close, "退出", tint = Color.White)
        }
        Text(
            text = timerState.title(),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium
        )
        IconButton(onClick = onMoreClick, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.MoreVert, "更多", tint = Color.White)
        }
    }
}

@Composable
private fun StopAlertButton(
    modifier: Modifier = Modifier,
    onStopAlert: () -> Unit
) {
    FilledTonalButton(
        onClick = onStopAlert,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF006781)
        ),
        shape = CircleShape
    ) {
        Icon(Icons.Default.VolumeUp, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("停止提醒", fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmersiveMoreSheet(
    selectedNoise: WhiteNoise?,
    onNoiseSelect: (WhiteNoise) -> Unit,
    onNoiseNone: () -> Unit,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    onSoundToggle: () -> Unit,
    onVibrationToggle: () -> Unit,
    isPomodoroMode: Boolean = true,
    onDismiss: () -> Unit,
    onOpenPomodoroSettings: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        sheetMaxWidth = 640.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("沉浸模式选项", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("提醒声音", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = soundEnabled, onCheckedChange = { onSoundToggle() })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("震动提醒", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = vibrationEnabled, onCheckedChange = { onVibrationToggle() })
            }

            if (isPomodoroMode) {
                OutlinedButton(onClick = onOpenPomodoroSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("番茄钟设置")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("白噪音", style = MaterialTheme.typography.titleMedium)
                WhiteNoiseSelector(
                    selectedNoise = selectedNoise,
                    onNoiseSelect = onNoiseSelect,
                    onNoiseNone = onNoiseNone,
                    compact = false,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    selectedBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    unselectedBackground = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WhiteNoiseSelector(
    selectedNoise: WhiteNoise?,
    onNoiseSelect: (WhiteNoise) -> Unit,
    onNoiseNone: () -> Unit,
    compact: Boolean,
    iconTint: Color = Color.White,
    selectedBackground: Color = Color.White.copy(alpha = 0.4f),
    unselectedBackground: Color = Color.White.copy(alpha = 0.15f)
) {
    val buttonSize = if (compact) 36.dp else 44.dp
    val iconSize = if (compact) 18.dp else 22.dp

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(if (selectedNoise == null) selectedBackground else unselectedBackground)
                .clickable { onNoiseNone() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.VolumeOff,
                "无白噪音",
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
        WhiteNoise.entries.forEach { noise ->
            val isSelected = selectedNoise == noise
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(if (isSelected) selectedBackground else unselectedBackground)
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
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun BottomControls(
    timerState: TimerState,
    modifier: Modifier = Modifier,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit
) {
    val sideButtonWidth = 104.dp
    val playButtonSize = 88.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (timerState) {
            is TimerState.Pomodoro -> SecondaryControlButton(
                modifier = Modifier.width(sideButtonWidth),
                onClick = onSkip,
                icon = Icons.Default.SkipNext,
                label = "跳过"
            )
            is TimerState.Countdown,
            is TimerState.Stopwatch,
            is TimerState.CountdownFinished,
            is TimerState.StopwatchFinished -> SecondaryControlButton(
                modifier = Modifier.width(sideButtonWidth),
                onClick = onReset,
                icon = Icons.Default.Refresh,
                label = "重置"
            )
            TimerState.Idle -> Spacer(modifier = Modifier.width(sideButtonWidth))
        }

        IconButton(
            onClick = onPlayPause,
            enabled = timerState !is TimerState.Idle,
            modifier = Modifier
                .size(playButtonSize)
                .clip(CircleShape)
                .background(
                    if (timerState is TimerState.Idle) {
                        Color.White.copy(alpha = 0.22f)
                    } else {
                        Color.White
                    }
                )
        ) {
            Icon(
                imageVector = when (timerState) {
                    TimerState.Idle -> Icons.Default.PlayArrow
                    is TimerState.Pomodoro -> if (timerState.state.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow
                    is TimerState.Countdown -> if (timerState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow
                    is TimerState.Stopwatch -> if (timerState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow
                    is TimerState.CountdownFinished,
                    is TimerState.StopwatchFinished -> Icons.Default.Refresh
                },
                contentDescription = "播放/暂停",
                tint = Color(0xFF006781),
                modifier = Modifier.size(36.dp)
            )
        }

        when (timerState) {
            is TimerState.Pomodoro,
            is TimerState.Countdown,
            is TimerState.Stopwatch -> SecondaryControlButton(
                modifier = Modifier.width(sideButtonWidth),
                onClick = onStop,
                icon = Icons.Default.Stop,
                label = "结束"
            )
            is TimerState.CountdownFinished,
            is TimerState.StopwatchFinished -> SecondaryControlButton(
                modifier = Modifier.width(sideButtonWidth),
                onClick = onReset,
                icon = Icons.Default.Refresh,
                label = "重置"
            )
            TimerState.Idle -> Spacer(modifier = Modifier.width(sideButtonWidth))
        }
    }
}

@Composable
private fun SecondaryControlButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 14.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun TimerDisplay(
    timerState: TimerState,
    compactMode: Boolean
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
        is TimerState.Idle -> "--:--"
        is TimerState.Pomodoro -> formatTime(timerState.state.timeRemainingSeconds)
        is TimerState.Countdown -> formatTime(timerState.remainingSeconds)
        is TimerState.CountdownFinished -> "00:00"
        is TimerState.Stopwatch -> formatTime(timerState.elapsedSeconds)
        is TimerState.StopwatchFinished -> formatTime(timerState.elapsedSeconds)
    }

    val subtitle = when (timerState) {
        TimerState.Idle -> "请先从主页选择番茄钟、倒计时或正计时"
        is TimerState.Pomodoro -> when (timerState.state.phase) {
            PomodoroPhase.FOCUS -> "专注时间"
            PomodoroPhase.SHORT_BREAK -> "短休息"
            PomodoroPhase.LONG_BREAK -> "长休息"
        }
        is TimerState.Countdown -> "倒计时"
        is TimerState.CountdownFinished -> "倒计时结束"
        is TimerState.Stopwatch -> "正计时"
        is TimerState.StopwatchFinished -> "本次计时结束"
    }

    val metaText = when (timerState) {
        is TimerState.Pomodoro -> "已完成 ${timerState.state.completedPomodoros} 个番茄钟"
        is TimerState.Countdown -> "剩余 ${formatTime(timerState.remainingSeconds)}"
        is TimerState.Stopwatch -> "已记录 ${formatTime(timerState.elapsedSeconds)}"
        else -> null
    }

    val showPulse = timerState is TimerState.Pomodoro &&
        timerState.state.phase == PomodoroPhase.FOCUS &&
        !timerState.state.isRunning

    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = displayText,
            fontSize = if (compactMode) 112.sp else 96.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = if (showPulse) alpha else 1f)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.84f),
            textAlign = TextAlign.Center
        )
        if (!compactMode && metaText != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = metaText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun TimerState.title(): String {
    return when (this) {
        TimerState.Idle -> "沉浸学习"
        is TimerState.Pomodoro -> when (state.phase) {
            PomodoroPhase.FOCUS -> "番茄钟"
            PomodoroPhase.SHORT_BREAK -> "短休息"
            PomodoroPhase.LONG_BREAK -> "长休息"
        }
        is TimerState.Countdown,
        is TimerState.CountdownFinished -> "倒计时"
        is TimerState.Stopwatch,
        is TimerState.StopwatchFinished -> "正计时"
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
