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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.ui.components.AuthFormTemplate
import com.github.garynasser.correction_notebook.ui.components.AuthMessageCard
import com.github.garynasser.correction_notebook.ui.components.AuthScreenFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CasScreen(
    modifier: Modifier = Modifier,
    viewModel: RegistrationViewModel,
    onBackButtonClick: () -> Unit,
    onConfirm: () -> Unit = { viewModel.submit() },
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
        AuthScreenFrame(
            modifier = modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            AuthFormTemplate(
                title = "延河课堂登录",
                subtitle = "用于同步课程和视频，验证完成后会回到当前页面。",
                buttonText = if (viewModel.isCasLoading) "正在验证" else "登录延河课堂",
                onButtonClick = { onConfirm() },
                isButtonEnabled = viewModel.isCasEnabled,
                isLoading = viewModel.isCasLoading,
                inputFields = {
                    OutlinedTextField(
                        value = viewModel.studentId,
                        onValueChange = { viewModel.studentId = it },
                        label = { Text("学号") },
                        placeholder = { Text("学号") },
                        enabled = !viewModel.isCasLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = viewModel.casPassword,
                        onValueChange = { viewModel.casPassword = it },
                        label = { Text("统一验证密码") },
                        placeholder = { Text("统一验证密码") },
                        enabled = !viewModel.isCasLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        visualTransformation = if (viewModel.isCasPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = {
                                viewModel.isCasPasswordVisible = !viewModel.isCasPasswordVisible
                            }) {
                                val icon = if (viewModel.isCasPasswordVisible) {
                                    Icons.Default.Visibility
                                } else {
                                    Icons.Default.VisibilityOff
                                }
                                Icon(icon, contentDescription = null)
                            }
                        }
                    )
                },
                footer = {
                    val message = viewModel.errorMessage
                    if (message != null) {
                        AuthMessageCard(message = message, isError = true)
                    } else {
                        AuthMessageCard(message = "使用北理工统一认证，仅用于拉取延河课堂我的课程和视频。")
                    }
                }
            )
        }
    }
}
