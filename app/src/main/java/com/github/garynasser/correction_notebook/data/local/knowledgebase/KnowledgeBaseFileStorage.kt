package com.github.garynasser.correction_notebook.data.local.knowledgebase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoredKnowledgeBaseFile(
    val storedName: String,
    val absolutePath: String,
    val sizeBytes: Long
)

internal data class PendingKnowledgeBaseFileDeletion(
    val originalPath: String,
    val pendingPath: String
)

@Singleton
class KnowledgeBaseFileStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val baseDirectory: File
        get() = File(context.filesDir, "knowledge_base")

    suspend fun ensureFolderPath(folderPathIds: List<String>): File = withContext(Dispatchers.IO) {
        var current = baseDirectory
        check(current.isDirectory || current.mkdirs() || current.isDirectory) { "无法创建知识库目录" }
        folderPathIds.forEach { folderId ->
            current = File(current, folderId)
            check(current.isDirectory || current.mkdirs() || current.isDirectory) { "无法创建本地文件夹" }
        }
        current
    }

    suspend fun writeFile(
        folderPathIds: List<String>,
        preferredName: String,
        inputStream: InputStream
    ): StoredKnowledgeBaseFile {
        var targetFile: File? = null
        var inputOwned = false
        try {
            return withContext(Dispatchers.IO) {
                inputOwned = true
                inputStream.use { input ->
                    val folder = ensureFolderPath(folderPathIds)
                    val storedName = "${UUID.randomUUID()}-${preferredName.sanitizeFileName()}"
                    val target = File(folder, storedName).also { targetFile = it }
                    val coroutineContext = currentCoroutineContext()
                    runInterruptible {
                        target.outputStream().use { output ->
                            copyKnowledgeBaseFile(input, output, checkCancelled = { coroutineContext.ensureActive() })
                        }
                    }
                    coroutineContext.ensureActive()
                    StoredKnowledgeBaseFile(storedName, target.absolutePath, target.length())
                }
            }
        } catch (error: Throwable) {
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    targetFile?.let { target ->
                        check(!target.exists() || target.delete()) { "无法清理未完成的导入文件" }
                    }
                    if (!inputOwned) inputStream.close()
                }
            } catch (cleanupError: Throwable) {
                error.addSuppressed(cleanupError)
            }
            throw error
        }
    }

    suspend fun moveFile(
        sourcePath: String,
        destinationFolderPathIds: List<String>
    ): String = withContext(Dispatchers.IO) {
        val sourceFile = File(sourcePath)
        val destinationFolder = ensureFolderPath(destinationFolderPathIds)
        val destinationFile = File(destinationFolder, sourceFile.name)

        if (!sourceFile.exists()) {
            throw IllegalStateException("源文件不存在")
        }
        if (sourceFile.canonicalFile == destinationFile.canonicalFile) return@withContext sourceFile.absolutePath

        sourceFile.copyTo(destinationFile, overwrite = true)
        if (!sourceFile.delete()) {
            destinationFile.delete()
            throw IllegalStateException("无法删除移动前的原文件")
        }
        destinationFile.absolutePath
    }

    suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("无法删除本地文件")
        }
    }

    internal suspend fun stageFileForDeletion(path: String): PendingKnowledgeBaseFileDeletion? = withContext(Dispatchers.IO) {
        stageKnowledgeBaseFileForDeletion(File(path))
    }

    internal suspend fun restoreStagedFile(deletion: PendingKnowledgeBaseFileDeletion) = withContext(Dispatchers.IO) {
        restoreStagedKnowledgeBaseFile(deletion)
    }

    internal suspend fun commitStagedFileDeletion(deletion: PendingKnowledgeBaseFileDeletion) = withContext(Dispatchers.IO) {
        commitStagedKnowledgeBaseFileDeletion(deletion)
    }

    suspend fun deleteFolder(folderPathIds: List<String>) = withContext(Dispatchers.IO) {
        require(folderPathIds.isNotEmpty()) { "不能删除知识库根目录" }
        var folder = baseDirectory
        folderPathIds.forEach { folderId ->
            folder = File(folder, folderId)
        }
        if (!folder.exists()) return@withContext
        check(folder.isDirectory) { "本地文件夹路径无效" }
        val children = checkNotNull(folder.listFiles()) { "无法读取本地文件夹" }
        check(children.isEmpty()) { "文件夹仍有本地文件，请等待导入完成或清空内容" }
        check(folder.delete()) { "无法删除本地文件夹" }
    }

    private fun String.sanitizeFileName(): String {
        return replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "file" }
    }
}

internal const val MAX_KNOWLEDGE_BASE_FILE_BYTES = 100L * 1024L * 1024L

internal fun stageKnowledgeBaseFileForDeletion(source: File): PendingKnowledgeBaseFileDeletion? {
    if (!source.exists()) return null
    val pending = File(source.parentFile, ".pending-delete-${UUID.randomUUID()}-${source.name}")
    check(source.renameTo(pending)) { "无法准备删除本地文件" }
    return PendingKnowledgeBaseFileDeletion(
        originalPath = source.absolutePath,
        pendingPath = pending.absolutePath
    )
}

internal fun restoreStagedKnowledgeBaseFile(deletion: PendingKnowledgeBaseFileDeletion) {
    val pending = File(deletion.pendingPath)
    if (!pending.exists()) return
    val original = File(deletion.originalPath)
    check(!original.exists()) { "无法恢复本地文件：原路径已被占用" }
    check(pending.renameTo(original)) { "无法恢复本地文件" }
}

internal fun commitStagedKnowledgeBaseFileDeletion(deletion: PendingKnowledgeBaseFileDeletion) {
    val pending = File(deletion.pendingPath)
    if (pending.exists() && !pending.delete()) {
        throw IllegalStateException("无法删除本地文件")
    }
}

internal fun copyKnowledgeBaseFile(
    input: InputStream,
    output: OutputStream,
    maxBytes: Long = MAX_KNOWLEDGE_BASE_FILE_BYTES,
    checkCancelled: () -> Unit = {}
): Long {
    require(maxBytes > 0) { "文件大小上限必须大于 0" }
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalBytes = 0L
    while (true) {
        checkCancelled()
        val read = input.read(buffer)
        checkCancelled()
        if (read < 0) break
        if (totalBytes + read > maxBytes) {
            val megabyte = 1024L * 1024L
            val limitText = if (maxBytes >= megabyte && maxBytes % megabyte == 0L) {
                "${maxBytes / megabyte} MB"
            } else {
                "$maxBytes 字节"
            }
            throw IllegalArgumentException("文件不能超过 $limitText")
        }
        output.write(buffer, 0, read)
        totalBytes += read
    }
    return totalBytes
}
