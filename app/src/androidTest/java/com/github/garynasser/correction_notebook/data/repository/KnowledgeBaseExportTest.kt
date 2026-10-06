package com.github.garynasser.correction_notebook.data.repository

import android.net.Uri
import androidx.room.Room
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileEntity
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KnowledgeBaseExportTest {
    private lateinit var database: KnowledgeBaseDatabase
    private lateinit var repository: KnowledgeBaseRepository
    private lateinit var directory: File
    private lateinit var source: File
    private lateinit var target: File
    private lateinit var entity: KnowledgeBaseFileEntity
    private val payload = ByteArray(65_537) { (it % 251).toByte() }

    @Before
    fun setup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File.createTempFile("export-qa-", "", context.cacheDir).apply {
            delete()
            mkdir()
        }
        source = File(directory, "source.txt").apply { writeBytes(payload) }
        target = File(directory, "target.txt").apply { writeBytes(ByteArray(payload.size * 2) { 42 }) }
        database = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        repository = KnowledgeBaseRepository(database.knowledgeBaseDao(), KnowledgeBaseFileStorage(context), context)
        entity = KnowledgeBaseFileEntity(
            id = "file", folderId = null, displayName = "Notes.txt", storedName = source.name,
            localPath = source.absolutePath, mimeType = "text/plain", sizeBytes = source.length(),
            sourceType = "local", sourceFileId = null, sourceTitle = null, sourcePath = null,
            courseId = null, courseName = null, tags = "", downloadedAt = null, createdAt = 0L, updatedAt = 0L
        )
        database.knowledgeBaseDao().insertFile(entity)
    }

    @After
    fun cleanup() {
        database.close()
        directory.deleteRecursively()
    }

    @Test
    fun exportCalledFromMainCopiesFullContentAndTruncatesTheDestination() = runBlocking {
        withContext(Dispatchers.Main) {
            repository.exportFile(entity.id, Uri.fromFile(target)).getOrThrow()
        }
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun exportResolvesTheCurrentFilePathFromTheSavedId() = runBlocking {
        val moved = File(directory, "moved.txt")
        assertTrue(source.renameTo(moved))
        database.knowledgeBaseDao().insertFile(entity.copy(localPath = moved.absolutePath))
        repository.exportFile(entity.id, Uri.fromFile(target)).getOrThrow()
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun missingSourceDoesNotTruncateTheDestination() = runBlocking {
        val originalTarget = target.readBytes()
        assertTrue(source.delete())
        assertTrue(repository.exportFile(entity.id, Uri.fromFile(target)).isFailure)
        assertArrayEquals(originalTarget, target.readBytes())
        assertTrue(repository.exportFile("unknown", Uri.fromFile(target)).isFailure)
        assertArrayEquals(originalTarget, target.readBytes())
    }

    @Test
    fun unwritableDestinationReportsFailureAndPreservesTheSource() = runBlocking {
        val missingDirectory = File(directory, "missing/target.txt")
        assertTrue(repository.exportFile(entity.id, Uri.fromFile(missingDirectory)).isFailure)
        assertArrayEquals(payload, source.readBytes())
    }

    @Test
    fun exportingOntoTheSourceFileIsRejectedWithoutChangingItsContent() = runBlocking {
        assertTrue(repository.exportFile(entity.id, Uri.fromFile(source)).isFailure)
        assertArrayEquals(payload, source.readBytes())
    }

    @Test
    fun exportingOntoASymbolicLinkToTheSourceIsRejectedWithoutChangingItsContent() = runBlocking {
        val alias = File(directory, "alias.txt")
        android.system.Os.symlink(source.absolutePath, alias.absolutePath)
        assertTrue(repository.exportFile(entity.id, Uri.fromFile(alias)).isFailure)
        assertArrayEquals(payload, source.readBytes())
    }

    @Test
    fun anApplicationsOwnShareUriCannotBeUsedToOverwriteItsKnowledgeFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val managed = File(File(context.filesDir, "knowledge_base").apply { mkdirs() }, "export-qa-${System.nanoTime()}.txt")
        try {
            managed.writeBytes(payload)
            database.knowledgeBaseDao().insertFile(entity.copy(localPath = managed.absolutePath))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", managed, managed.name)
            assertTrue(repository.exportFile(entity.id, uri).isFailure)
            assertArrayEquals(payload, managed.readBytes())
        } finally { managed.delete() }
    }
}
