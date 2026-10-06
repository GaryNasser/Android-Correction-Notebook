package com.github.garynasser.correction_notebook.ui.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.garynasser.correction_notebook.data.model.appupdate.AppVersionInfo

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppUpdateDialog(
    update: AppVersionInfo,
    currentVersionName: String,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    var panelBounds by remember { mutableStateOf(Rect.Zero) }
    var contentPosition by remember { mutableStateOf(Offset.Zero) }
    fun dismiss() { if (!update.forceUpdate) onDismiss() }

    Dialog(onDismissRequest = ::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .onGloballyPositioned { contentPosition = it.positionInRoot() }
            .pointerInput(update.forceUpdate) {
                detectTapGestures { position -> if (!panelBounds.contains(position + contentPosition)) dismiss() }
            }, contentAlignment = Alignment.Center) {
            Box(Modifier.padding(12.dp)) {
                Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 560.dp)
                    .onGloballyPositioned { panelBounds = it.boundsInRoot() },
                    shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(update.updateTitle.ifBlank { "发现新版本" }, style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("当前 ${currentVersionName.ifBlank { "未知版本" }}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("更新到 ${update.latestVersionName}", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(update.updateContent.ifBlank { "修复问题并改善使用体验。" }, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (update.forceUpdate) Text("当前版本需要更新后继续使用。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!update.forceUpdate) TextButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) { Text("稍后") }
                            Button(onClick = onUpdate, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("前往更新")
                            }
                        }
                    }
                }
            }
        }
    }
}
