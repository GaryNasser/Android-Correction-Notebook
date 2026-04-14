package com.github.garynasser.correction_notebook.data.local.knowledgebase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoredKnowledgeBaseFile(
    val storedName: String,
    val absolutePath: String,
    val sizeBytes: Long
)

@Singleton
class KnowledgeBaseFileStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val baseDirectory: File
        get() = File(context.filesDir, "knowledge_base")

    suspend fun ensureFolderPath(folderPathIds: List<String>): File = withContext(Dispatchers.IO) {
        var current = baseDirectory
        if (!current.exists()) {
            current.mkdirs()
        }
        folderPathIds.forEach { folderId ->
            current = File(current, folderId)
            if (!current.exists()) {
                current.mkdirs()
            }
        }
        current
    }

    suspend fun writeFile(
        folderPathIds: List<String>,
        preferredName: String,
        inputStream: InputStream
    ): StoredKnowledgeBaseFile = withContext(Dispatchers.IO) {
        val folder = ensureFolderPath(folderPathIds)
        val storedName = "${UUID.randomUUID()}-${preferredName.sanitizeFileName()}"
        val targetFile = File(folder, storedName)

        inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        StoredKnowledgeBaseFile(
            storedName = storedName,
            absolutePath = targetFile.absolutePath,
            sizeBytes = targetFile.length()
        )
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

        sourceFile.copyTo(destinationFile, overwrite = true)
        sourceFile.delete()
        destinationFile.absolutePath
    }

    suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) {
        File(path).takeIf { it.exists() }?.delete()
    }

    suspend fun deleteFolder(folderPathIds: List<String>) = withContext(Dispatchers.IO) {
        var folder = baseDirectory
        folderPathIds.forEach { folderId ->
            folder = File(folder, folderId)
        }
        if (folder.exists() && folder.listFiles().isNullOrEmpty()) {
            folder.delete()
        }
    }

    private fun String.sanitizeFileName(): String {
        return replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "file" }
    }
}
