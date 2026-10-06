package com.github.garynasser.correction_notebook.data.repository

import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.knowledgebase.*
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFileDetail
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class KnowledgeBaseFolderLifecycleTest {
    private lateinit var directory: File
    private lateinit var database: KnowledgeBaseDatabase
    private lateinit var storage: KnowledgeBaseFileStorage
    private lateinit var repository: KnowledgeBaseRepository
    private var failInsert = false
    private var failDelete = false
    private var failUpdate = false
    private var afterInsert: (() -> Unit)? = null
    private var afterFileInsert: (() -> Unit)? = null
    private val base get() = File(directory, "knowledge_base")
    private val payload = "矩阵分析讲义与课后练习".toByteArray()
    private val detail = BitShareFileDetail("remote", "矩阵分析", "Notes.txt", "txt", null, null, "text/plain", payload.size.toLong(), null, 0)

    @Before fun setup() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File.createTempFile("folder-lifecycle-", "", target.cacheDir).apply { delete(); mkdir() }
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = directory
        }
        database = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        val realDao = database.knowledgeBaseDao()
        val dao = object : KnowledgeBaseDao by realDao {
            override suspend fun insertFolder(folder: KnowledgeBaseFolderEntity) {
                if (failInsert) throw IOException("folder insert failed")
                realDao.insertFolder(folder)
                afterInsert?.invoke()
            }
            override suspend fun deleteFolder(folder: KnowledgeBaseFolderEntity) {
                if (failDelete) throw IOException("folder delete failed")
                realDao.deleteFolder(folder)
            }
            override suspend fun updateFile(file: KnowledgeBaseFileEntity) {
                if (failUpdate) throw IOException("file update failed")
                realDao.updateFile(file)
            }
            override suspend fun insertFile(file: KnowledgeBaseFileEntity) {
                withContext(Dispatchers.IO) {
                    realDao.insertFile(file)
                    afterFileInsert?.invoke()
                }
            }
        }
        storage = KnowledgeBaseFileStorage(context)
        repository = KnowledgeBaseRepository(dao, storage, context)
    }

    @After fun cleanup() { database.close(); directory.deleteRecursively() }

    @Test fun deletingAnEmptyNestedFolderRemovesItsDirectoryButNotItsParent() = runBlocking {
        val parent = create("课程资料")
        val child = create("矩阵分析", parent.id)
        val childPath = File(File(base, parent.id), child.id)
        assertTrue(childPath.isDirectory)
        repository.deleteFolder(child.id).getOrThrow()
        assertNull(database.knowledgeBaseDao().getFolderById(child.id))
        assertFalse("Deleted folder must not leave its directory behind", childPath.exists())
        assertTrue(File(base, parent.id).isDirectory)
    }

    @Test fun creatingInsideAMissingParentIsRejectedWithoutAnInvisibleFolder() = runBlocking {
        assertTrue(repository.createFolder("deleted-parent", "矩阵分析").isFailure)
        assertTrue(database.knowledgeBaseDao().getAllFolders().isEmpty())
        assertTrue(base.listFiles().isNullOrEmpty())
    }

    @Test fun aFailedDirectoryCreationDoesNotLeaveASuccessfulDatabaseRecord() = runBlocking {
        base.writeText("not a directory")
        assertTrue(repository.createFolder(null, "矩阵分析").isFailure)
        assertTrue(database.knowledgeBaseDao().getAllFolders().isEmpty())
        assertEquals("not a directory", base.readText())
    }

    @Test fun failedFolderInsertionCleansItsDirectoryAndCanBeRetried() = runBlocking {
        failInsert = true
        assertTrue(repository.createFolder(null, "矩阵分析").isFailure)
        assertTrue(database.knowledgeBaseDao().getAllFolders().isEmpty())
        assertTrue(base.listFiles().isNullOrEmpty())
        failInsert = false
        val folder = create("矩阵分析")
        assertTrue(File(base, folder.id).isDirectory)
    }

    @Test fun failedFolderDeletionKeepsItsRecordAndDirectory() = runBlocking {
        val folder = create("矩阵分析")
        failDelete = true
        assertTrue(repository.deleteFolder(folder.id).isFailure)
        assertNotNull(database.knowledgeBaseDao().getFolderById(folder.id))
        assertTrue(File(base, folder.id).isDirectory)
        failDelete = false
        repository.deleteFolder(folder.id).getOrThrow()
        assertFalse(File(base, folder.id).exists())
    }

    @Test fun aFolderWithAnUnindexedLocalFileCannotBeDeleted() = runBlocking {
        val folder = create("矩阵分析")
        val pending = File(File(base, folder.id), "pending-import.txt").apply { writeBytes(payload) }
        assertTrue(repository.deleteFolder(folder.id).isFailure)
        assertNotNull(database.knowledgeBaseDao().getFolderById(folder.id))
        assertArrayEquals(payload, pending.readBytes())
    }

    @Test fun movingToADeletedFolderCannotRemoveTheOriginalRootFile() = runBlocking {
        val target = create("矩阵分析")
        repository.deleteFolder(target.id).getOrThrow()
        val file = import()
        assertTrue(repository.moveFile(file.id, target.id).isFailure)
        assertEquals(file, database.knowledgeBaseDao().getFileById(file.id))
        assertArrayEquals(payload, File(file.localPath).readBytes())
    }

    @Test fun aDownloadToADeletedFolderClosesItsStreamAndDoesNotCreateAnInvisibleFile() = runBlocking {
        val folder = create("矩阵分析")
        repository.deleteFolder(folder.id).getOrThrow()
        var closed = false
        val input = object : ByteArrayInputStream(payload) { override fun close() { closed = true; super.close() } }
        assertTrue(repository.importDownloadedFile(detail, folder.id, input).isFailure)
        assertTrue(closed)
        assertTrue(database.knowledgeBaseDao().getAllFiles().isEmpty())
        assertTrue(base.walkTopDown().none { it.isFile })
    }

    @Test fun aLocalImportToADeletedFolderPreservesTheSelectedSource() = runBlocking {
        val folder = create("矩阵分析")
        repository.deleteFolder(folder.id).getOrThrow()
        val source = File(directory, "selected.txt").apply { writeBytes(payload) }
        assertTrue(repository.importLocalFile(folder.id, Uri.fromFile(source)).isFailure)
        assertArrayEquals(payload, source.readBytes())
        assertTrue(database.knowledgeBaseDao().getAllFiles().isEmpty())
        assertTrue(base.walkTopDown().none { it.isFile })
    }

    @Test fun cancellationDuringAMoveDoesNotLeaveAMissingSourceInTheDatabase() = runBlocking {
        val target = create("矩阵分析")
        val original = import()
        val size = 64L * 1024L * 1024L
        RandomAccessFile(original.localPath, "rw").use { it.setLength(size) }
        database.knowledgeBaseDao().insertFile(original.copy(sizeBytes = size))
        val destination = File(File(base, target.id), File(original.localPath).name)
        val moving = async(Dispatchers.Default) {
            repository.moveFile(original.id, target.id)
        }
        withTimeout(5_000) {
            while (!destination.exists()) { assertTrue(moving.isActive); yield() }
        }
        moving.cancelAndJoin()
        assertTrue(moving.isCancelled)
        val saved = requireNotNull(database.knowledgeBaseDao().getFileById(original.id))
        assertTrue("Cancelled move must leave a readable indexed file", File(saved.localPath).isFile)
        assertEquals(size, File(saved.localPath).length())
        val prefix = ByteArray(payload.size)
        File(saved.localPath).inputStream().use { assertEquals(prefix.size, it.read(prefix)) }
        assertArrayEquals(payload, prefix)
    }

    @Test fun cancellationAfterFolderInsertionCannotLeaveAGhostFolder() = runBlocking {
        val creating = async(Dispatchers.Default) {
            val owner = requireNotNull(currentCoroutineContext()[Job])
            afterInsert = { owner.cancel() }
            repository.createFolder(null, "矩阵分析")
        }
        creating.join()
        assertTrue(creating.isCancelled)
        val folder = database.knowledgeBaseDao().getAllFolders().single()
        assertTrue("Inserted folder must have a real directory", File(base, folder.id).isDirectory)
    }

    @Test fun movingToTheSamePhysicalPathPreservesAllBytes() = runBlocking {
        val original = import()
        assertEquals(original.localPath, storage.moveFile(original.localPath, emptyList()))
        assertArrayEquals(payload, File(original.localPath).readBytes())
    }

    @Test fun cancellationAfterImportIndexInsertionCannotDeleteTheIndexedFile() = runBlocking {
        val importing = async(Dispatchers.Default) {
            val owner = requireNotNull(currentCoroutineContext()[Job])
            afterFileInsert = { owner.cancel() }
            repository.importDownloadedFile(detail, KnowledgeBaseRepository.ROOT_FOLDER_ID, ByteArrayInputStream(payload))
        }
        importing.join()
        assertTrue(importing.isCancelled)
        val saved = database.knowledgeBaseDao().getAllFiles().single()
        assertTrue("Index insertion must not be followed by deleting its file", File(saved.localPath).isFile)
        assertArrayEquals(payload, File(saved.localPath).readBytes())
    }

    @Test fun aFailedFileUpdateRollsBackTheMoveAndPreservesItsBytes() = runBlocking {
        val target = create("矩阵分析")
        val original = import()
        failUpdate = true
        assertTrue(repository.moveFile(original.id, target.id).isFailure)
        assertEquals(original, database.knowledgeBaseDao().getFileById(original.id))
        assertArrayEquals(payload, File(original.localPath).readBytes())
        assertTrue(File(base, target.id).listFiles().isNullOrEmpty())
    }

    @Test fun nestedImportRenameMoveAndDeleteKeepTheVisibleTreeAndFilesInAgreement() = runBlocking {
        val parent = create("课程资料")
        val child = create("矩阵分析", parent.id)
        val file = import(child.id)
        assertEquals(file.id, repository.observeFolderContent(child.id, "").first().files.single().id)
        assertTrue(repository.deleteFolder(child.id).isFailure)
        repository.renameFolder(child.id, "矩阵分析复习").getOrThrow()
        assertEquals("知识库 / 课程资料 / 矩阵分析复习",
            repository.observeFolderChoices().first().single { it.id == child.id }.path)
        repository.renameFile(file.id, "复习讲义.txt").getOrThrow()
        repository.moveFile(file.id, null).getOrThrow()
        val moved = requireNotNull(database.knowledgeBaseDao().getFileById(file.id))
        assertNull(moved.folderId)
        assertEquals("复习讲义.txt", moved.displayName)
        assertEquals(base.absolutePath, File(moved.localPath).parent)
        assertArrayEquals(payload, File(moved.localPath).readBytes())
        assertEquals("复习讲义.txt", repository.observeFolderContent(null, "").first().files.single().displayName)
        repository.deleteFolder(child.id).getOrThrow()
        repository.deleteFolder(parent.id).getOrThrow()
        repository.deleteFile(file.id).getOrThrow()
        assertTrue(database.knowledgeBaseDao().getAllFolders().isEmpty())
        assertTrue(database.knowledgeBaseDao().getAllFiles().isEmpty())
        assertTrue("Entire completed lifecycle must leave no orphaned files or directories", base.listFiles().isNullOrEmpty())
    }

    private suspend fun create(name: String, parentId: String? = null): KnowledgeBaseFolderEntity {
        repository.createFolder(parentId, name).getOrThrow()
        return database.knowledgeBaseDao().getAllFolders().single { it.name == name }
    }

    private suspend fun import(folderId: String? = null): KnowledgeBaseFileEntity {
        repository.importDownloadedFile(detail, folderId ?: KnowledgeBaseRepository.ROOT_FOLDER_ID,
            ByteArrayInputStream(payload)).getOrThrow()
        return database.knowledgeBaseDao().getAllFiles().single()
    }
}
