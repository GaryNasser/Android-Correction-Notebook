package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.data.model.home.TodoItem

private const val MaxTodoTitleLength = 60
private const val MaxTodoDescriptionLength = 180

@Composable
fun AddTodoDialog(
    onDismiss: () -> Unit,
    onAdd: (TodoItem) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val canAdd = title.isNotBlank()
    val addTodo = {
        if (canAdd) {
            onAdd(TodoItem(title = title.trim(), description = description.trim()))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        title = {
            Text(
                text = "添加待办",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MaxTodoTitleLength) },
                    label = { Text("标题") },
                    placeholder = { Text("输入待办事项标题") },
                    supportingText = {
                        Text("${title.length}/$MaxTodoTitleLength")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(MaxTodoDescriptionLength) },
                    label = { Text("备注（可选）") },
                    placeholder = { Text("把要记的小事、提醒或补充信息写在这里") },
                    supportingText = {
                        Text("${description.length}/$MaxTodoDescriptionLength")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    minLines = 2,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTodo() })
                )
            }
        },
        confirmButton = {
            Button(
                onClick = addTodo,
                enabled = canAdd,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
