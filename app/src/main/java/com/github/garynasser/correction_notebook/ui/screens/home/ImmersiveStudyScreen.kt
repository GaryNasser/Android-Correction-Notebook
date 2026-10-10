package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.github.garynasser.correction_notebook.data.model.home.PomodoroPhase
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import com.github.garynasser.correction_notebook.data.model.home.WhiteNoise
import com.github.garynasser.correction_notebook.data.model.home.WhiteNoiseState
import com.github.garynasser.correction_notebook.domain.usecase.AlertManager
import com.github.garynasser.correction_notebook.domain.usecase.StudyTimerManager
import kotlinx.coroutines.delay

@Composable
fun ImmersiveStudyScreen(
    timerManager: StudyTimerManager,
    onExit: () -> Unit,
    onStop: () -> Unit = {},
    onReset: () -> Unit = {},
    isSavingSession: Boolean = false,
    sessionError: String? = null,
    onDismissSessionError: () -> Unit = {},
    backgroundImageUri: String? = null,
    soundEnabled: Boolean = true,
    vibrationEnabled: Boolean = true,
    onSoundEnabledChange: (Boolean) -> Unit = {},
    onVibrationEnabledChange: (Boolean) -> Unit = {},
    onOpenPomodoroSettings: () -> Unit = {},
    isPomodoroMode: Boolean = true,
    isSavingSoundSetting: Boolean = false,
    isSavingVibrationSetting: Boolean = false,
    soundSettingError: String? = null,
    vibrationSettingError: String? = null,
    noiseState: WhiteNoiseState = WhiteNoiseState(),
    onNoiseSelect: (WhiteNoise) -> Unit = {},
    onNoiseNone: () -> Unit = {},
    onNoiseRetry: () -> Unit = {}
) {
    val context = LocalContext.current
    val timerState by timerManager.timerState.collectAsStateWithLifecycle()
    val navigationBottomPadding = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    var alertManager by remember { mutableStateOf<AlertManager?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var isAlerting by remember { mutableStateOf(false) }
    val currentSoundEnabled by rememberUpdatedState(soundEnabled)
    val currentVibrationEnabled by rememberUpdatedState(vibrationEnabled)
    val activity = context as? android.app.Activity
    val window = activity?.window
    val decorView = window?.decorView

    val timerRunning = when (val state = timerState) {
        is TimerState.Pomodoro -> state.state.isRunning
        is TimerState.Countdown -> state.isRunning
        is TimerState.Stopwatch -> state.isRunning
        else -> false
    }
    LaunchedEffect(showControls, showMoreSheet, timerRunning, isAlerting) {
        if (!timerRunning || isAlerting) showControls = true
        else if (showControls && !showMoreSheet) {
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
            if (currentSoundEnabled) {
                alertManager?.playAlarmSound(looping = true)
            }
            if (currentVibrationEnabled) {
                alertManager?.vibrate(AlertManager.VibrationPattern.POMODORO)
            }
        }

        onDispose {
            if (window != null && decorView != null) {
                WindowInsetsControllerCompat(window, decorView)
                    .show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
            alertManager?.stop()
            timerManager.onTimerFinished = null
            timerManager.onPomodoroPhaseChanged = null
        }
    }

    BackHandler {
        alertManager?.stop()
        onExit()
    }

    LaunchedEffect(isAlerting) {
        if (isAlerting) {
            delay(10_000)
            alertManager?.stop()
            isAlerting = false
        }
    }

    fun resetSession() {
        alertManager?.stop()
        isAlerting = false
        onReset()
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
            .background(if (backgroundImageUri == null) backgroundGradient else Brush.verticalGradient(listOf(Color.Black, Color.Black)))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                showControls = if (timerRunning) !showControls else true
            }
    ) {
        // Background image if set
        if (backgroundImageUri != null) {
            Image(
                painter = rememberAsyncImagePainter(backgroundImageUri.toUri()),
                contentDescription = "背景图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 1f
            )
            // Keep even the secondary white text readable over the brightest uploaded photos.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
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
                    onExit()
                },
                onMoreClick = { showMoreSheet = true }
            )

            BottomControls(
                timerState = timerState,
                enabled = !isSavingSession,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = navigationBottomPadding + 20.dp
                    ),
                onSkip = { timerManager.skip() },
                onReset = ::resetSession,
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
                        is TimerState.Stopwatch -> {
                            if (state.isRunning) timerManager.pause() else timerManager.resume()
                        }
                        is TimerState.CountdownFinished, is TimerState.StopwatchFinished -> {
                            resetSession()
                        }
                    }
                },
                onStop = {
                    alertManager?.stop()
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

    if (sessionError != null) {
        AlertDialog(
            onDismissRequest = onDismissSessionError,
            shape = RoundedCornerShape(8.dp),
            title = { Text("记录未保存", style = MaterialTheme.typography.titleMedium) },
            text = { Text(sessionError) },
            confirmButton = { TextButton(onClick = onDismissSessionError) { Text("知道了") } }
        )
    }

    if (showMoreSheet) {
        ImmersiveMoreSheet(
            noiseState = noiseState,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
            isSavingSoundSetting = isSavingSoundSetting,
            isSavingVibrationSetting = isSavingVibrationSetting,
            soundSettingError = soundSettingError,
            vibrationSettingError = vibrationSettingError,
            isPomodoroMode = isPomodoroMode,
            onDismiss = { showMoreSheet = false },
            onNoiseSelect = onNoiseSelect,
            onNoiseNone = onNoiseNone,
            onNoiseRetry = onNoiseRetry,
            onSoundChange = onSoundEnabledChange,
            onVibrationChange = onVibrationEnabledChange,
            onOpenPomodoroSettings = {
                showMoreSheet = false
                if (isPomodoroMode) {
                    onOpenPomodoroSettings()
                }
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
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("停止提醒", fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmersiveMoreSheet(
    noiseState: WhiteNoiseState,
    onNoiseSelect: (WhiteNoise) -> Unit,
    onNoiseNone: () -> Unit,
    onNoiseRetry: () -> Unit,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    isSavingSoundSetting: Boolean,
    isSavingVibrationSetting: Boolean,
    soundSettingError: String?,
    vibrationSettingError: String?,
    onSoundChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    isPomodoroMode: Boolean = true,
    onDismiss: () -> Unit,
    onOpenPomodoroSettings: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        sheetMaxWidth = 640.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("沉浸模式选项", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            AlertSettingRow("提醒声音", soundEnabled, isSavingSoundSetting, soundSettingError, onSoundChange)
            AlertSettingRow("振动提醒", vibrationEnabled, isSavingVibrationSetting, vibrationSettingError, onVibrationChange)

            if (isPomodoroMode) {
                OutlinedButton(
                    onClick = onOpenPomodoroSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("番茄钟设置")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("白噪音", style = MaterialTheme.typography.titleSmall)
                WhiteNoiseSelector(
                    selectedNoise = noiseState.selectedNoise,
                    onNoiseSelect = onNoiseSelect,
                    onNoiseNone = onNoiseNone
                )
                val noise = noiseState.selectedNoise
                if (noise != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (noiseState.isLoading) {
                            CircularProgressIndicator(Modifier.size(16.dp).clearAndSetSemantics {}, strokeWidth = 2.dp)
                        }
                        Text(
                            text = noiseState.error ?: when {
                                noiseState.isLoading -> "正在加载：${noise.displayName}"
                                noiseState.isPlaying -> "正在播放：${noise.displayName}"
                                else -> "已暂停：${noise.displayName}"
                            },
                            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (noiseState.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (noiseState.error != null) {
                            IconButton(onClick = onNoiseRetry) {
                                Icon(Icons.Default.Refresh, "重试白噪音", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AlertSettingRow(
    label: String,
    checked: Boolean,
    isSaving: Boolean,
    error: String?,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(checked, enabled = !isSaving, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics {
                    if (isSaving) stateDescription = if (checked) "保存中，当前已开启" else "保存中，当前已关闭"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (isSaving) {
                CircularProgressIndicator(Modifier.size(16.dp).clearAndSetSemantics {}, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Switch(checked = checked, onCheckedChange = null, enabled = !isSaving)
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhiteNoiseSelector(
    selectedNoise: WhiteNoise?,
    onNoiseSelect: (WhiteNoise) -> Unit,
    onNoiseNone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (listOf<WhiteNoise?>(null) + WhiteNoise.entries).forEach { noise ->
            val label = noise?.displayName ?: "无白噪音"
            val active = selectedNoise == noise
            Box(Modifier.weight(1f)) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(label) } },
                    state = rememberTooltipState()
                ) {
                    FilledTonalIconButton(
                        onClick = { if (noise == null) onNoiseNone() else onNoiseSelect(noise) },
                        modifier = Modifier.fillMaxWidth().height(48.dp).semantics { selected = active },
                        shape = RoundedCornerShape(8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(when (noise) {
                            null -> Icons.AutoMirrored.Filled.VolumeOff
                            WhiteNoise.RAIN -> Icons.Default.Water
                            WhiteNoise.OCEAN -> Icons.Default.Waves
                            WhiteNoise.FOREST -> Icons.Default.Forest
                            WhiteNoise.CAFE -> Icons.Default.LocalCafe
                        }, label, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomControls(
    timerState: TimerState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (timerState) {
            is TimerState.Pomodoro -> SecondaryControlButton(
                onClick = onSkip,
                enabled = enabled,
                icon = Icons.Default.SkipNext,
                label = "跳过"
            )
            is TimerState.Countdown,
            is TimerState.Stopwatch,
            is TimerState.CountdownFinished,
            is TimerState.StopwatchFinished -> SecondaryControlButton(
                onClick = onReset,
                enabled = enabled,
                icon = Icons.Default.Refresh,
                label = "重置"
            )
            TimerState.Idle -> Spacer(modifier = Modifier.size(48.dp))
        }

        IconButton(
            onClick = onPlayPause,
            enabled = enabled && timerState !is TimerState.Idle,
            modifier = Modifier
                .size(64.dp)
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
                contentDescription = when (timerState) {
                    is TimerState.Pomodoro -> if (timerState.state.isRunning) "暂停" else "继续"
                    is TimerState.Countdown -> if (timerState.isRunning) "暂停" else "继续"
                    is TimerState.Stopwatch -> if (timerState.isRunning) "暂停" else "继续"
                    is TimerState.CountdownFinished, is TimerState.StopwatchFinished -> "重新计时"
                    TimerState.Idle -> "开始"
                },
                tint = Color(0xFF006781),
                modifier = Modifier.size(28.dp)
            )
        }

        when (timerState) {
            is TimerState.Pomodoro,
            is TimerState.Countdown,
            is TimerState.Stopwatch,
            is TimerState.CountdownFinished,
            is TimerState.StopwatchFinished -> SecondaryControlButton(
                onClick = onStop,
                enabled = enabled,
                icon = Icons.Default.Stop,
                label = "结束"
            )
            TimerState.Idle -> Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondaryControlButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        OutlinedIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(48.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
            colors = IconButtonDefaults.outlinedIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.12f),
                contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.38f)
            )
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun TimerDisplay(
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
        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = displayText,
            modifier = Modifier.fillMaxWidth(),
            fontSize = if (compactMode) 88.sp else 80.sp,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = if (compactMode) 88.sp else 80.sp),
            textAlign = TextAlign.Center,
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
