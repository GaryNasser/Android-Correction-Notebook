package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KnowledgeBaseSharingTest {
    private lateinit var context: RecordingContext
    private lateinit var storedFile: File
    private lateinit var summary: KnowledgeBaseFileSummary
    private val payload = "Local document content"
    private val displayName = "Linear algebra notes.txt"

    @Before
    fun setup() {
        context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val directory = File(context.filesDir, "knowledge_base").apply { mkdirs() }
        storedFile = File.createTempFile("internal-id-", ".txt", directory)
        storedFile.writeText(payload)
        summary = KnowledgeBaseFileSummary(
            id = "qa", folderId = null, displayName = displayName,
            localPath = storedFile.absolutePath, mimeType = "text/plain",
            sizeBytes = storedFile.length(), sourceType = "local", sourceTitle = null,
            courseId = null, courseName = null, tags = emptyList(), downloadedAt = null
        )
    }

    @After
    fun cleanup() {
        storedFile.delete()
    }

    @Test
    fun libraryShareUsesDisplayNameAndReadableOriginalContent() {
        shareFile(context, summary.localPath, summary.mimeType, summary.displayName)
        assertSingleShare()
    }

    @Test
    fun viewerShareUsesDisplayNameAndReadableOriginalContent() {
        shareViewerFile(context, summary.localPath, summary.mimeType, summary.displayName)
        assertSingleShare()
    }

    @Test
    fun externalOpenUsesDisplayNameAndGrantsReadPermission() {
        openFileExternally(context, summary)
        val intent = context.targetIntent()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertNamedReadableUri(requireNotNull(intent.data))
        assertReadPermission(intent)
    }

    @Test
    fun batchSharePreservesEveryDisplayNameAndSkipsMissingFiles() {
        shareFiles(context, listOf(
            summary,
            summary.copy(id = "second", displayName = "Renamed notes.txt"),
            summary.copy(id = "missing", localPath = "${summary.localPath}.missing")
        ))
        val intent = context.targetIntent()
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        val uris = requireNotNull(IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(2, uris.size)
        assertNamedReadableUri(uris[0])
        assertNamedReadableUri(uris[1], "Renamed notes.txt")
        assertEquals(2, requireNotNull(intent.clipData).itemCount)
        assertReadPermission(intent)
    }

    @Test
    fun exportUsesTheFileMimeTypeAndKeepsTheDisplayName() {
        val contract = KnowledgeBaseExportContract()
        val intent = contract.createIntent(context, summary)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(displayName, intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals("application/octet-stream", contract.createIntent(context, summary.copy(mimeType = "")).type)
        val uri = Uri.parse("content://example/export")
        assertEquals(uri, contract.parseResult(Activity.RESULT_OK, Intent().setData(uri)))
        assertEquals(null, contract.parseResult(Activity.RESULT_CANCELED, Intent().setData(uri)))
    }

    private fun assertSingleShare() {
        val intent = context.targetIntent()
        assertEquals(Intent.ACTION_SEND, intent.action)
        val uri = requireNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        assertNamedReadableUri(uri)
        assertEquals(uri, requireNotNull(intent.clipData).getItemAt(0).uri)
        assertReadPermission(intent)
    }

    private fun assertNamedReadableUri(uri: Uri, name: String = displayName) {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            assertEquals(name, it.getString(0))
        }
        context.contentResolver.openInputStream(uri)!!.bufferedReader().use {
            assertEquals(payload, it.readText())
        }
    }

    private fun assertReadPermission(intent: Intent) {
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(context.chooserIntent().flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(intent.clipData?.itemCount ?: 1, requireNotNull(context.chooserIntent().clipData).itemCount)
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        private var launchedIntent: Intent? = null

        override fun startActivity(intent: Intent) {
            launchedIntent = intent
        }

        fun chooserIntent(): Intent = requireNotNull(launchedIntent)

        fun targetIntent(): Intent = requireNotNull(
            IntentCompat.getParcelableExtra(chooserIntent(), Intent.EXTRA_INTENT, Intent::class.java)
        )
    }
}
