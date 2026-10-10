package com.github.garynasser.correction_notebook.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.data.remote.cas.CasCodeKind
import com.github.garynasser.correction_notebook.data.remote.cas.CasCodePrompt

@Composable
fun CasChallengeDialog(prompt: CasCodePrompt, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var code by remember(prompt.id) { mutableStateOf("") }
    var submitted by remember(prompt.id) { mutableStateOf(false) }
    val sms = prompt.kind == CasCodeKind.SMS
    val image = remember(prompt.id) {
        prompt.image?.let { bytes -> runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull() }
    }
    val enabled = !submitted && code.trim().isNotEmpty() && (sms || image != null)
    val submit = {
        if (enabled) { submitted = true; onSubmit(code.trim()); code = "" }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(8.dp),
        title = { Text(if (sms) "短信验证" else "图形验证", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (sms) {
                    Text("验证码已发送至 ${prompt.maskedPhone}", style = MaterialTheme.typography.bodyMedium)
                } else if (image != null) {
                    Image(image, "学校图形验证码", Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 96.dp))
                } else {
                    Text("验证码图片无法显示，请取消后重试", color = MaterialTheme.colorScheme.error)
                }
                prompt.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                OutlinedTextField(
                    value = code, onValueChange = { code = it.take(32) },
                    label = { Text(if (sms) "短信验证码" else "图形验证码") },
                    singleLine = true, enabled = !submitted,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = if (sms) KeyboardType.Number else KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().testTag("cas-code-input")
                )
            }
        },
        confirmButton = { Button(onClick = submit, enabled = enabled, shape = RoundedCornerShape(8.dp)) { Text("验证") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}
