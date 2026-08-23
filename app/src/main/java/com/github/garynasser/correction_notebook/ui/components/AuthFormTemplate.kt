package com.github.garynasser.correction_notebook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    footer: @Composable (ColumnScope.() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Spacer(Modifier.height(5.dp))
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(text = buttonText, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Text(text = buttonText, style = MaterialTheme.typography.labelLarge)
                }
            }

            if (footer != null) {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    footer()
                }
            }
        }
    }
}

@Composable
fun AuthMessageCard(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    icon: ImageVector = if (isError) Icons.Default.Warning else Icons.Default.Info
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.74f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        },
        border = BorderStroke(
            1.dp,
            if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }
    }
}

@Composable
fun AuthLegalText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
fun AuthScreenFrame(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    FreshScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            content = content
        )
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
