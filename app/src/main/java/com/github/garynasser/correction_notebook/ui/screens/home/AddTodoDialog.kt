package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.garynasser.correction_notebook.data.model.home.TodoItem

private const val MaxTodoTitleLength = 60
private const val MaxTodoDescriptionLength = 180

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTodoDialog(
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onAdd: (TodoItem) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    val canAdd = title.isNotBlank() && !isSaving
    var panelBounds by remember { mutableStateOf(Rect.Zero) }
    var contentPosition by remember { mutableStateOf(Offset.Zero) }
    fun dismiss() { if (!isSaving) onDismiss() }

    Dialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val addTodo = {
            if (canAdd) {
                focusManager.clearFocus()
                keyboard?.hide()
                onAdd(TodoItem(title = title.trim(), description = description.trim()))
            }
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { contentPosition = it.positionInRoot() }
                .pointerInput(isSaving) {
                    detectTapGestures { position ->
                        if (!panelBounds.contains(position + contentPosition)) dismiss()
                    }
                }
                .safeDrawingPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val compactEditor = keyboardVisible && maxHeight < 320.dp
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .onGloballyPositioned { panelBounds = it.boundsInRoot() },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "添加待办",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text("取消") } },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = ::dismiss, enabled = !isSaving) {
                                Icon(Icons.Default.Close, contentDescription = "取消", modifier = Modifier.size(20.dp))
                            }
                        }
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text(if (isSaving) "添加中" else "添加") } },
                            state = rememberTooltipState()
                        ) {
                            FilledTonalIconButton(
                                onClick = addTodo,
                                enabled = canAdd,
                                modifier = Modifier.semantics { contentDescription = if (isSaving) "添加中" else "添加" },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                            .heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it.take(MaxTodoTitleLength) },
                            label = { Text("标题", style = MaterialTheme.typography.bodySmall) },
                            placeholder = { Text("输入待办事项标题", style = MaterialTheme.typography.bodyMedium) },
                            supportingText = if (compactEditor) null else {
                                { Text("${title.length}/$MaxTodoTitleLength", style = MaterialTheme.typography.bodySmall) }
                            },
                            suffix = if (!compactEditor) null else {
                                { Text("${title.length}/$MaxTodoTitleLength", style = MaterialTheme.typography.bodySmall) }
                            },
                            enabled = !isSaving,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it.take(MaxTodoDescriptionLength) },
                            label = { Text("备注（可选）", style = MaterialTheme.typography.bodySmall) },
                            placeholder = { Text("输入备注", style = MaterialTheme.typography.bodyMedium) },
                            supportingText = if (compactEditor) null else {
                                { Text("${description.length}/$MaxTodoDescriptionLength", style = MaterialTheme.typography.bodySmall) }
                            },
                            suffix = if (!compactEditor) null else {
                                { Text("${description.length}/$MaxTodoDescriptionLength", style = MaterialTheme.typography.bodySmall) }
                            },
                            enabled = !isSaving,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            minLines = 1,
                            maxLines = if (compactEditor) 1 else 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addTodo() })
                        )
                    }
                }
            }
        }
    }
}
