package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import kotlin.math.roundToInt

@Composable
fun PomodoroSettingsDialog(
    currentSettings: PomodoroSettings,
    onDismiss: () -> Unit,
    onSave: (PomodoroSettings) -> Unit,
    isSaving: Boolean = false,
    errorMessage: String? = null
) {
    var focusMinutes by rememberSaveable { mutableFloatStateOf(currentSettings.focusMinutes.toFloat()) }
    var shortBreakMinutes by rememberSaveable { mutableFloatStateOf(currentSettings.shortBreakMinutes.toFloat()) }
    var longBreakMinutes by rememberSaveable { mutableFloatStateOf(currentSettings.longBreakMinutes.toFloat()) }
    var pomodorosBeforeLongBreak by rememberSaveable { mutableFloatStateOf(currentSettings.pomodorosBeforeLongBreak.toFloat()) }
    val scrollState = rememberScrollState()
    LaunchedEffect(errorMessage) { if (errorMessage != null) scrollState.scrollTo(0) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        shape = RoundedCornerShape(8.dp),
        title = {
            Text(
                text = "番茄钟设置",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                SettingSlider(
                    label = "学习时长",
                    value = focusMinutes,
                    onValueChange = { focusMinutes = it.roundToInt().toFloat() },
                    valueRange = 5f..60f,
                    valueDisplay = "${focusMinutes.toInt()} 分钟",
                    enabled = !isSaving
                )

                SettingSlider(
                    label = "短休息",
                    value = shortBreakMinutes,
                    onValueChange = { shortBreakMinutes = it.roundToInt().toFloat() },
                    valueRange = 1f..15f,
                    valueDisplay = "${shortBreakMinutes.toInt()} 分钟",
                    enabled = !isSaving
                )

                SettingSlider(
                    label = "长休息",
                    value = longBreakMinutes,
                    onValueChange = { longBreakMinutes = it.roundToInt().toFloat() },
                    valueRange = 5f..30f,
                    valueDisplay = "${longBreakMinutes.toInt()} 分钟",
                    enabled = !isSaving
                )

                SettingSlider(
                    label = "循环轮数",
                    value = pomodorosBeforeLongBreak,
                    onValueChange = { pomodorosBeforeLongBreak = it.roundToInt().toFloat() },
                    valueRange = 2f..8f,
                    valueDisplay = "${pomodorosBeforeLongBreak.toInt()} 轮",
                    enabled = !isSaving
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        PomodoroSettings(
                            focusMinutes = focusMinutes.roundToInt(),
                            shortBreakMinutes = shortBreakMinutes.roundToInt(),
                            longBreakMinutes = longBreakMinutes.roundToInt(),
                            pomodorosBeforeLongBreak = pomodorosBeforeLongBreak.roundToInt()
                        )
                    )
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (isSaving) "保存中" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDisplay: String,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueDisplay,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            enabled = enabled,
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = label
                stateDescription = valueDisplay
            },
            steps = (valueRange.endInclusive - valueRange.start).toInt() - 1
        )
    }
}
