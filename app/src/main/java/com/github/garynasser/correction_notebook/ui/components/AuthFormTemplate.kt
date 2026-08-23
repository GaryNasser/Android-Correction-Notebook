package com.github.garynasser.correction_notebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun AuthFormTemplate(
    title: String,
    subtitle: String? = null,
    buttonText: String,
    onButtonClick: () -> Unit,
    isButtonEnabled: Boolean = false,
    isLoading: Boolean = false,
    inputFields: @Composable ColumnScope.() -> Unit,
    footer: @Composable (RowScope.() -> Unit) ? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .padding(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            inputFields()
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onButtonClick,
            enabled = isButtonEnabled && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(text = buttonText, style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (footer != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
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
