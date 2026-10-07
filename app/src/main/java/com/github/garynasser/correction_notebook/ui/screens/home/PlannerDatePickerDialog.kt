package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlannerDatePickerDialog(
    initialDate: LocalDate,
    enabled: Boolean = true,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate.toDatePickerUtcMillis())
    var panelBounds by remember { mutableStateOf(Rect.Zero) }
    var contentPosition by remember { mutableStateOf(Offset.Zero) }
    fun dismiss() { if (enabled) onDismiss() }

    Dialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        Box(
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { contentPosition = it.positionInRoot() }
                .pointerInput(enabled) {
                    detectTapGestures { position ->
                        if (!panelBounds.contains(position + contentPosition)) dismiss()
                    }
                }
                .safeDrawingPadding().imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
                    .onGloballyPositioned { panelBounds = it.boundsInRoot() }
                    .semantics { paneTitle = "选择日期" },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                BoxWithConstraints {
                    // Material's calendar has 12.dp side insets and 40.dp date circles.
                    // Keep all seven columns within a narrow screen without changing its text size.
                    val daySize = ((maxWidth - 24.dp) / 7).coerceIn(40.dp, 48.dp)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.selectedDateMillis?.let {
                                    datePickerMillisToLocalDate(it).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                                } ?: "选择日期",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                                tooltip = { PlainTooltip { Text("取消") } }, state = rememberTooltipState()
                            ) {
                                IconButton(onClick = ::dismiss, enabled = enabled) {
                                    Icon(Icons.Default.Close, contentDescription = "取消", modifier = Modifier.size(20.dp))
                                }
                            }
                            val inputMode = state.displayMode == DisplayMode.Input
                            val modeLabel = if (inputMode) "日历选择" else "输入日期"
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                                tooltip = { PlainTooltip { Text(modeLabel) } }, state = rememberTooltipState()
                            ) {
                                IconButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                        state.displayMode = if (inputMode) DisplayMode.Picker else DisplayMode.Input
                                    }, enabled = enabled
                                ) {
                                    Icon(
                                        if (inputMode) Icons.Default.CalendarMonth else Icons.Default.Edit,
                                        contentDescription = modeLabel, modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                                tooltip = { PlainTooltip { Text("确定") } }, state = rememberTooltipState()
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (enabled) state.selectedDateMillis?.let {
                                            focusManager.clearFocus()
                                            keyboard?.hide()
                                            onDateSelected(datePickerMillisToLocalDate(it))
                                        }
                                    }, enabled = enabled && state.selectedDateMillis != null,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "确定", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides daySize) {
                            DatePicker(
                                state = state,
                                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                title = null,
                                headline = null,
                                showModeToggle = false,
                                colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                            )
                        }
                    }
                }
            }
        }
    }
}
