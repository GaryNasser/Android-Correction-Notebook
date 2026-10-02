package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KnowledgeBaseExportStateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pendingFileIdSurvivesSavedStateRestorationBeforePickerReturns() {
        val registry = RecordingRegistry()
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        val selectedId = mutableStateOf("first")
        val results = mutableListOf<Pair<String?, Uri>>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                CorrectionNotebookTheme {
                    val launchExport = rememberKnowledgeBaseExportLauncher { id, uri -> results += id to uri }
                    TextButton(onClick = { launchExport(file(selectedId.value)) }) { Text("Export") }
                }
            }
        }
        compose.onNodeWithText("Export").performClick()
        restoration.emulateSavedInstanceStateRestore()
        val target = Uri.parse("content://example/first")
        compose.runOnIdle {
            registry.dispatchResult(registry.requestCode, Activity.RESULT_OK, Intent().setData(target))
        }
        compose.runOnIdle { assertEquals(listOf("first" to target), results) }

        compose.runOnIdle { selectedId.value = "second" }
        compose.onNodeWithText("Export").performClick()
        compose.runOnIdle {
            registry.dispatchResult(registry.requestCode, Activity.RESULT_CANCELED, null)
        }
        compose.runOnIdle { assertEquals(1, results.size) }
        compose.onNodeWithText("Export").performClick()
        val nextTarget = Uri.parse("content://example/second")
        compose.runOnIdle {
            registry.dispatchResult(registry.requestCode, Activity.RESULT_OK, Intent().setData(nextTarget))
        }
        compose.runOnIdle { assertEquals(listOf("first" to target, "second" to nextTarget), results) }
    }

    private fun file(id: String) = KnowledgeBaseFileSummary(
        id = id, folderId = null, displayName = "$id.txt", localPath = "/unused/$id.txt",
        mimeType = "text/plain", sizeBytes = 0L, sourceType = "local", sourceTitle = null,
        courseId = null, courseName = null, tags = emptyList(), downloadedAt = null
    )

    private class RecordingRegistry : ActivityResultRegistry() {
        var requestCode = 0

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            this.requestCode = requestCode
        }
    }
}
