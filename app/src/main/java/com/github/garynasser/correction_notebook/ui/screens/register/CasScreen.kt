package com.github.garynasser.correction_notebook.ui.screens.register

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
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
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val confirmAuthentication = {
        keyboard?.hide()
        focus.clearFocus()
        onConfirm()
    }
    val leaveAuthentication = {
        viewModel.cancelYanheLogin()
        onBackButtonClick()
    }
    BackHandler(onBack = leaveAuthentication)
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = {
            TopAppBar(
                title = { Text("统一认证", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = leaveAuthentication
                    ) {
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
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            AuthFormTemplate(
                title = "北理工账号",
                buttonText = if (viewModel.isCasLoading) "正在验证" else "登录延河课堂",
                onButtonClick = confirmAuthentication,
                isButtonEnabled = viewModel.isCasEnabled,
                isLoading = viewModel.isCasLoading,
                inputFields = {
                    OutlinedTextField(
                        value = viewModel.studentId,
                        onValueChange = {
                            viewModel.studentId = it
                            viewModel.clearError()
                        },
                        label = { Text("学号") },
                        leadingIcon = { Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        enabled = !viewModel.isCasLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        )
                    )

                    OutlinedTextField(
                        value = viewModel.casPassword,
                        onValueChange = {
                            viewModel.casPassword = it
                            viewModel.clearError()
                        },
                        label = { Text("统一认证密码") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        enabled = !viewModel.isCasLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        visualTransformation = if (viewModel.isCasPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (viewModel.isCasEnabled) {
                                    confirmAuthentication()
                                }
                            }
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    viewModel.isCasPasswordVisible = !viewModel.isCasPasswordVisible
                                },
                                enabled = !viewModel.isCasLoading
                            ) {
                                val icon = if (viewModel.isCasPasswordVisible) {
                                    Icons.Default.Visibility
                                } else {
                                    Icons.Default.VisibilityOff
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = if (viewModel.isCasPasswordVisible) "隐藏密码" else "显示密码"
                                )
                            }
                        }
                    )
                },
                footer = {
                    val message = viewModel.errorMessage
                    if (message != null) {
                        AuthMessageCard(message = message, isError = true)
                    }
                }
            )
        }
    }
}
