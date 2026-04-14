package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDao
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileEntity
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFolderEntity
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFileDetail
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseBreadcrumb
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFolderChoice
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFolderContent
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFolderSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeBaseRepository @Inject constructor(
    private val dao: KnowledgeBaseDao,
    private val fileStorage: KnowledgeBaseFileStorage
) {
    fun observeFolderContent(folderId: String?, query: String): Flow<KnowledgeBaseFolderContent> {
        return combine(
            dao.observeFolders(folderId, query.trim()),
            dao.observeFiles(folderId, query.trim()),
            dao.observeAllFolders(),
            dao.observeFolderFileCounts()
        ) { folders, files, allFolders, fileCounts ->
            val folderMap = allFolders.associateBy { it.id }
            val countMap = fileCounts.associate { it.folderId to it.count }

            KnowledgeBaseFolderContent(
                breadcrumbs = buildBreadcrumbs(folderId, folderMap),
                folders = folders.map { folder ->
                    KnowledgeBaseFolderSummary(
                        id = folder.id,
                        name = folder.name,
                        path = buildPath(folder.id, folderMap),
                        directFileCount = countMap[folder.id] ?: 0
                    )
                },
                files = files.map { file ->
                    KnowledgeBaseFileSummary(
                        id = file.id,
                        folderId = file.folderId,
                        displayName = file.displayName,
                        localPath = file.localPath,
                        mimeType = file.mimeType,
                        sizeBytes = file.sizeBytes,
                        sourceType = file.sourceType,
                        sourceTitle = file.sourceTitle,
                        downloadedAt = file.downloadedAt
                    )
                }
            )
        }
    }

    fun observeFolderChoices(): Flow<List<KnowledgeBaseFolderChoice>> {
        return dao.observeAllFolders().combine(dao.observeFolderFileCounts()) { folders, _ ->
            val folderMap = folders.associateBy { it.id }
            listOf(
                KnowledgeBaseFolderChoice(
                    id = null,
                    name = "知识库根目录",
                    path = "知识库",
                    depth = 0
                )
            ) + folders.map { folder ->
                val chain = buildFolderChain(folder.id, folderMap)
                KnowledgeBaseFolderChoice(
                    id = folder.id,
                    name = folder.name,
                    path = chain.joinToString(" / ") { it.name }.let { "知识库 / $it" },
                    depth = chain.size
                )
            }
        }
    }

    fun observeRecentFiles(limit: Int = 6): Flow<List<KnowledgeBaseFileSummary>> {
        return dao.observeRecentFiles(limit).combine(dao.observeAllFolders()) { files, _ ->
            files.map { file ->
                KnowledgeBaseFileSummary(
                    id = file.id,
                    folderId = file.folderId,
                    displayName = file.displayName,
                    localPath = file.localPath,
                    mimeType = file.mimeType,
                    sizeBytes = file.sizeBytes,
                    sourceType = file.sourceType,
                    sourceTitle = file.sourceTitle,
                    downloadedAt = file.downloadedAt
                )
            }
        }
    }

    suspend fun createFolder(parentId: String?, name: String): Result<Unit> = runCatching {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "文件夹名称不能为空" }

        val now = System.currentTimeMillis()
        val folder = KnowledgeBaseFolderEntity(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            parentId = parentId,
            createdAt = now,
            updatedAt = now
        )

        dao.insertFolder(folder)
        fileStorage.ensureFolderPath(getFolderPathIds(folder.id))
    }

    suspend fun renameFolder(folderId: String, newName: String): Result<Unit> = runCatching {
        val folder = requireNotNull(dao.getFolderById(folderId)) { "文件夹不存在" }
        val normalizedName = newName.trim()
        require(normalizedName.isNotEmpty()) { "文件夹名称不能为空" }

        dao.updateFolder(
            folder.copy(
                name = normalizedName,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteFolder(folderId: String): Result<Unit> = runCatching {
        val folder = requireNotNull(dao.getFolderById(folderId)) { "文件夹不存在" }
        val childFolderCount = dao.countFoldersByParent(folderId)
        val childFileCount = dao.countFilesByFolder(folderId)
        require(childFolderCount == 0 && childFileCount == 0) { "请先清空文件夹内容" }

        dao.deleteFolder(folder)
        fileStorage.deleteFolder(getFolderPathIds(folderId))
    }

    suspend fun renameFile(fileId: String, newName: String): Result<Unit> = runCatching {
        val file = requireNotNull(dao.getFileById(fileId)) { "文件不存在" }
        val normalizedName = newName.trim()
        require(normalizedName.isNotEmpty()) { "文件名不能为空" }

        dao.updateFile(
            file.copy(
                displayName = normalizedName,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun moveFile(fileId: String, targetFolderId: String?): Result<Unit> = runCatching {
        val file = requireNotNull(dao.getFileById(fileId)) { "文件不存在" }
        val newPath = fileStorage.moveFile(file.localPath, getFolderPathIds(targetFolderId))

        dao.updateFile(
            file.copy(
                folderId = targetFolderId,
                localPath = newPath,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteFile(fileId: String): Result<Unit> = runCatching {
        val file = requireNotNull(dao.getFileById(fileId)) { "文件不存在" }
        fileStorage.deleteFile(file.localPath)
        dao.deleteFile(file)
    }

    suspend fun importDownloadedFile(
        detail: BitShareFileDetail,
        targetFolderId: String,
        inputBytes: ByteArray
    ): Result<Unit> = runCatching {
        val resolvedFolderId = targetFolderId.takeUnless { it == ROOT_FOLDER_ID }
        val now = System.currentTimeMillis()
        val displayName = resolveUniqueDisplayName(
            folderId = resolvedFolderId,
            preferredName = detail.originalName
        )

        val stored = fileStorage.writeFile(
            folderPathIds = getFolderPathIds(resolvedFolderId),
            preferredName = displayName,
            inputStream = inputBytes.inputStream()
        )

        dao.insertFile(
            KnowledgeBaseFileEntity(
                id = UUID.randomUUID().toString(),
                folderId = resolvedFolderId,
                displayName = displayName,
                storedName = stored.storedName,
                localPath = stored.absolutePath,
                mimeType = detail.mimeType,
                sizeBytes = stored.sizeBytes,
                sourceType = "bitshare",
                sourceFileId = detail.id,
                sourceTitle = detail.title,
                sourcePath = detail.path,
                downloadedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun getFolderName(folderId: String?): String {
        return folderId?.let { dao.getFolderById(it)?.name } ?: "知识库根目录"
    }

    private suspend fun getFolderPathIds(folderId: String?): List<String> {
        if (folderId == null) return emptyList()
        val folderMap = dao.getAllFolders().associateBy { it.id }
        return buildFolderChain(folderId, folderMap).map { it.id }
    }

    private suspend fun resolveUniqueDisplayName(folderId: String?, preferredName: String): String {
        return dao.getFilesByFolder(folderId)
            .map { it.displayName }
            .toSet()
            .let { names ->
                if (preferredName !in names) {
                    preferredName
                } else {
                    generateSequence(1) { it + 1 }
                        .map { index -> preferredName.appendSuffix(index) }
                        .first { candidate -> candidate !in names }
                }
            }
    }

    private fun buildBreadcrumbs(
        folderId: String?,
        folderMap: Map<String, KnowledgeBaseFolderEntity>
    ): List<KnowledgeBaseBreadcrumb> {
        return listOf(KnowledgeBaseBreadcrumb(id = null, name = "知识库")) +
            buildFolderChain(folderId, folderMap).map {
                KnowledgeBaseBreadcrumb(id = it.id, name = it.name)
            }
    }

    private fun buildPath(
        folderId: String,
        folderMap: Map<String, KnowledgeBaseFolderEntity>
    ): String {
        return buildFolderChain(folderId, folderMap)
            .joinToString(" / ") { it.name }
            .let { "知识库 / $it" }
    }

    private fun buildFolderChain(
        folderId: String?,
        folderMap: Map<String, KnowledgeBaseFolderEntity>
    ): List<KnowledgeBaseFolderEntity> {
        if (folderId == null) return emptyList()

        val chain = mutableListOf<KnowledgeBaseFolderEntity>()
        var currentId = folderId

        while (currentId != null) {
            val folder = folderMap[currentId] ?: break
            chain += folder
            currentId = folder.parentId
        }

        return chain.reversed()
    }

    private fun String.appendSuffix(index: Int): String {
        val dotIndex = lastIndexOf('.')
        return if (dotIndex > 0) {
            val name = substring(0, dotIndex)
            val extension = substring(dotIndex)
            "$name ($index)$extension"
        } else {
            "$this ($index)"
        }
    }

    companion object {
        const val ROOT_FOLDER_ID = "__root__"
    }
}
