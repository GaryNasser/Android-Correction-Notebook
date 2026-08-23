package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings

@Composable
fun PomodoroSettingsDialog(
    currentSettings: PomodoroSettings,
    onDismiss: () -> Unit,
    onSave: (PomodoroSettings) -> Unit
) {
    var focusMinutes by remember { mutableFloatStateOf(currentSettings.focusMinutes.toFloat()) }
    var shortBreakMinutes by remember { mutableFloatStateOf(currentSettings.shortBreakMinutes.toFloat()) }
    var longBreakMinutes by remember { mutableFloatStateOf(currentSettings.longBreakMinutes.toFloat()) }
    var pomodorosBeforeLongBreak by remember { mutableFloatStateOf(currentSettings.pomodorosBeforeLongBreak.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingSlider(
                    label = "学习时长",
                    value = focusMinutes,
                    onValueChange = { focusMinutes = it },
                    valueRange = 5f..60f,
                    valueDisplay = "${focusMinutes.toInt()} 分钟"
                )

                SettingSlider(
                    label = "短休息",
                    value = shortBreakMinutes,
                    onValueChange = { shortBreakMinutes = it },
                    valueRange = 1f..15f,
                    valueDisplay = "${shortBreakMinutes.toInt()} 分钟"
                )

                SettingSlider(
                    label = "长休息",
                    value = longBreakMinutes,
                    onValueChange = { longBreakMinutes = it },
                    valueRange = 5f..30f,
                    valueDisplay = "${longBreakMinutes.toInt()} 分钟"
                )

                SettingSlider(
                    label = "循环轮数",
                    value = pomodorosBeforeLongBreak,
                    onValueChange = { pomodorosBeforeLongBreak = it },
                    valueRange = 2f..8f,
                    valueDisplay = "${pomodorosBeforeLongBreak.toInt()} 轮"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        PomodoroSettings(
                            focusMinutes = focusMinutes.toInt(),
                            shortBreakMinutes = shortBreakMinutes.toInt(),
                            longBreakMinutes = longBreakMinutes.toInt(),
                            pomodorosBeforeLongBreak = pomodorosBeforeLongBreak.toInt()
                        )
                    )
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
    valueDisplay: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = (valueRange.endInclusive - valueRange.start).toInt() - 1
        )
    }
}
