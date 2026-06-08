package com.github.garynasser.correction_notebook.ui.screens.register

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.ui.components.AuthFormTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CasScreen(
    modifier: Modifier = Modifier,
    viewModel: RegistrationViewModel,
    onBackButtonClick: () -> Unit,
    onConfirm: () -> Unit = { viewModel.submit() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBackButtonClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(200.dp))

                AuthFormTemplate(
                    title = "延河课堂登录",
                    buttonText = if (viewModel.isCasLoading) "正在验证" else "登录延河课堂",
                    onButtonClick = { onConfirm() },
                    isButtonEnabled = viewModel.isCasEnabled,
                    inputFields = {
                        OutlinedTextField(
                            value = viewModel.studentId,
                            onValueChange = { viewModel.studentId = it },
                            label = { Text("学号") },
                            placeholder = { Text("学号") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = viewModel.casPassword,
                            onValueChange = { viewModel.casPassword = it },
                            label = { Text("统一验证密码") },
                            placeholder = { Text("统一验证密码") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (viewModel.isCasPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.isCasPasswordVisible = !viewModel.isCasPasswordVisible
                                }) {
                                    val icon = if (viewModel.isCasPasswordVisible)
                                        Icons.Default.Visibility else Icons.Default.VisibilityOff
                                    Icon(icon, contentDescription = null)
                                }
                            }
                        )
                    },
                    footer = {
                        val message = viewModel.errorMessage
                        if (message != null) {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                text = "使用北理工统一认证，仅用于拉取延河课堂我的课程和视频。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                )
            }
        }
    }
}
