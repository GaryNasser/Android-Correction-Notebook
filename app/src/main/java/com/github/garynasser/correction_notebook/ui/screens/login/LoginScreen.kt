package com.github.garynasser.correction_notebook.ui.screens.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.garynasser.correction_notebook.data.repository.AuthRepository
import com.github.garynasser.correction_notebook.ui.components.AuthLegalText
import com.github.garynasser.correction_notebook.ui.components.AuthMessageCard
import com.github.garynasser.correction_notebook.ui.components.AuthScreenFrame
import com.github.garynasser.correction_notebook.ui.components.AuthFormTemplate

@Composable
fun UsernameLoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
    onNavigateToRegister: () -> Unit
) {
    AuthScreenFrame(modifier = modifier) {
        AuthFormTemplate(
            title = "登录 BITStudy",
            subtitle = "继续使用学习计划、课程和知识库。",
            buttonText = if (viewModel.isLoading) "正在登录" else "登录",
            onButtonClick = { viewModel.onLoginClick() },
            isButtonEnabled = viewModel.isLoginEnable,
            isLoading = viewModel.isLoading,
            inputFields = {
                OutlinedTextField(
                    value = viewModel.username,
                    onValueChange = { viewModel.username = it },
                    label = { Text("用户名") },
                    placeholder = { Text(("用户名")) },
                    enabled = !viewModel.isLoading,
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = { viewModel.password = it },
                    label = { Text("密码") },
                    placeholder = { Text(("你的密码")) },
                    enabled = !viewModel.isLoading,
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    visualTransformation = if (viewModel.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.isPasswordVisible = !viewModel.isPasswordVisible }) {
                            val icon = if (viewModel.isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            Icon(icon, contentDescription = null)
                        }
                    }
                )
            },
            footer = {
                viewModel.errorMessage?.let { message ->
                    AuthMessageCard(message = message, isError = true)
                }
                Text(
                    text = "测试账号：${AuthRepository.TEST_USERNAME} / ${AuthRepository.TEST_PASSWORD}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        onNavigateToRegister()
                    },
                    enabled = !viewModel.isLoading
                ) {
                    Text(
                        text = "没有账号？立即注册",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
        )

        Spacer(Modifier.height(12.dp))
        AuthLegalText(
            text = "登录即表示同意 BITStudy 用户协议",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(18.dp))
    }
}
