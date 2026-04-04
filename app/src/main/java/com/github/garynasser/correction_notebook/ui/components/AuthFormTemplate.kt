package com.github.garynasser.correction_notebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthFormTemplate(
    title: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    isButtonEnabled: Boolean = false,
    inputFields: @Composable ColumnScope.() -> Unit,
    footer: @Composable (RowScope.() -> Unit) ? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            inputFields()
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onButtonClick,
            enabled = isButtonEnabled,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(text = buttonText)
        }

        Spacer(Modifier.height(32.dp))

        if (footer != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                footer()
            }
        }
    }
}


@Preview(
    showBackground = true,
    backgroundColor = 0xFFF0F0F0
)
@Composable
fun AuthFormTemplatePreview() {
    AuthFormTemplate(
        title = "测试",
        buttonText = "确认",
        onButtonClick = {  },
        inputFields = {
            OutlinedTextField(value = "user", onValueChange = {}, label = { Text("账号") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = "pass", onValueChange = {}, label = { Text("密码") }, modifier = Modifier.fillMaxWidth())
        },
        footer = {
            TextButton(onClick = {}) { Text("忘记密码") }
            TextButton(onClick = {}) { Text("注册账号") }
        }
    )
}