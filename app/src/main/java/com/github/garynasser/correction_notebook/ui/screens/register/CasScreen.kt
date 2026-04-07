package com.github.garynasser.correction_notebook.ui.screens.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.ui.components.AuthFormTemplate
import com.github.garynasser.correction_notebook.ui.components.FloatingBackButton
import com.github.garynasser.correction_notebook.ui.screens.register.RegistrationViewModel

@Composable
fun CasScreen(
    modifier: Modifier = Modifier,
    viewModel: RegistrationViewModel,
    onBackButtonClick: () -> Unit,
    onConfirm: () -> Unit = { viewModel.submit() },
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(200.dp))

            AuthFormTemplate(
                title = "统一认证",
                buttonText = "确定",
                onButtonClick = { onConfirm() },
                isButtonEnabled = viewModel.isCasEnabled,
                inputFields = {
                    OutlinedTextField(
                        value = viewModel.studentId,
                        onValueChange = { viewModel.studentId = it },
                        label = { Text("学号") },
                        placeholder = { Text(("学号")) },
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = viewModel.casPassword,
                        onValueChange = { viewModel.casPassword = it },
                        label = { Text("统一验证密码") },
                        placeholder = { Text(("统一验证密码")) },
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (viewModel.isCasPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.isCasPasswordVisible = !viewModel.isCasPasswordVisible }) {
                                val icon = if (viewModel.isCasPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                Icon(icon, contentDescription = null)
                            }
                        }
                    )
                },
                footer = {

                }
            )
        }

        FloatingBackButton(onBackButtonClick)
    }
}