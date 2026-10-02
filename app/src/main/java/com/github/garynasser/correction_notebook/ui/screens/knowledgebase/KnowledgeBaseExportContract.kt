package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary

internal class KnowledgeBaseExportContract : ActivityResultContract<KnowledgeBaseFileSummary, Uri?>() {
    override fun createIntent(context: Context, input: KnowledgeBaseFileSummary): Intent {
        return ActivityResultContracts.CreateDocument(input.mimeType.ifBlank { "application/octet-stream" })
            .createIntent(context, input.displayName)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return ActivityResultContracts.CreateDocument("application/octet-stream").parseResult(resultCode, intent)
    }
}

@Composable
internal fun rememberKnowledgeBaseExportLauncher(
    onExportResult: (String?, Uri) -> Unit
): (KnowledgeBaseFileSummary) -> Unit {
    var pendingFileId by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(KnowledgeBaseExportContract()) { uri ->
        val fileId = pendingFileId
        pendingFileId = null
        if (uri != null) onExportResult(fileId, uri)
    }
    return { file ->
        pendingFileId = file.id
        launcher.launch(file)
    }
}
