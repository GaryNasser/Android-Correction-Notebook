package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseChunkEntity
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDao
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

data class KnowledgeContextChunk(
    val fileId: String,
    val title: String,
    val path: String,
    val content: String,
    val score: Int
)

@Singleton
class KnowledgeBaseAiRepository @Inject constructor(
    private val dao: KnowledgeBaseDao
) {
    suspend fun rebuildIndexForFile(fileId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val file = requireNotNull(dao.getFileById(fileId)) { "文件不存在" }
            rebuildIndexForFile(file)
        }
    }

    suspend fun rebuildAllIndexes(folderId: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val files = folderId?.let { dao.getFilesForFolder(it) } ?: dao.getAllFiles()
            files.sumOf { rebuildIndexForFile(it) }
        }
    }

    suspend fun searchContext(
        question: String,
        folderId: String? = null,
        fileId: String? = null,
        limit: Int = 5
    ): List<KnowledgeContextChunk> = withContext(Dispatchers.IO) {
        val terms = tokenize(question)
        if (terms.isEmpty()) return@withContext emptyList()

        val candidates = if (fileId != null) {
            dao.getChunksForFile(fileId)
        } else {
            terms.take(4).flatMap { term ->
                dao.searchChunks(
                    query = term,
                    folderId = folderId,
                    folderIdFilter = folderId != null,
                    limit = 30
                )
            }
        }.distinctBy { it.id }

        candidates
            .map { chunk -> chunk to scoreChunk(chunk, terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (chunk, score) ->
                KnowledgeContextChunk(
                    fileId = chunk.fileId,
                    title = chunk.title,
                    path = chunk.path,
                    content = chunk.content,
                    score = score
                )
            }
    }

    suspend fun contextForFile(fileId: String, limit: Int = 6): List<KnowledgeContextChunk> = withContext(Dispatchers.IO) {
        ensureIndexed(fileId)
        dao.getChunksForFile(fileId).take(limit).map {
            KnowledgeContextChunk(
                fileId = it.fileId,
                title = it.title,
                path = it.path,
                content = it.content,
                score = 1
            )
        }
    }

    suspend fun ensureIndexed(fileId: String) {
        if (dao.getChunksForFile(fileId).isEmpty()) {
            rebuildIndexForFile(fileId)
        }
    }

    private suspend fun rebuildIndexForFile(file: KnowledgeBaseFileEntity): Int {
        dao.deleteChunksForFile(file.id)
        val text = extractText(file)
        if (text.isBlank()) return 0

        val chunks = chunkText(text).mapIndexed { index, content ->
            KnowledgeBaseChunkEntity(
                fileId = file.id,
                chunkIndex = index,
                title = file.displayName,
                path = file.sourcePath ?: file.displayName,
                content = content,
                keywords = tokenize(content).take(40).joinToString(" "),
                updatedAt = file.updatedAt
            )
        }
        if (chunks.isNotEmpty()) dao.insertChunks(chunks)
        return chunks.size
    }

    private fun extractText(file: KnowledgeBaseFileEntity): String {
        val source = File(file.localPath)
        if (!source.exists()) return ""
        val extension = source.extension.lowercase(Locale.ROOT)
        return when {
            file.mimeType.startsWith("text/") || extension in TEXT_EXTENSIONS ->
                source.readText(Charsets.UTF_8)
            extension == "docx" -> extractDocxText(source)
            extension == "pptx" -> extractPptxText(source)
            extension == "pdf" -> "${file.displayName}\nPDF 暂不做 OCR，仅可基于文件名和用户问题提供学习建议。"
            else -> ""
        }.cleanText()
    }

    private fun extractDocxText(file: File): String {
        return ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return ""
            val document = zip.getInputStream(entry).use { input ->
                secureDocumentBuilder().parse(input)
            }
            document.getElementsByTagNameNS("*", "t").asSequenceText()
        }
    }

    private fun extractPptxText(file: File): String {
        return ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.startsWith("ppt/slides/slide") && it.name.endsWith(".xml") }
                .sortedBy { it.name }
                .joinToString("\n\n") { entry ->
                    val document = zip.getInputStream(entry).use { input ->
                        secureDocumentBuilder().parse(input)
                    }
                    document.documentElement
                        .getElementsByTagNameNS("*", "t")
                        .asSequenceText()
                }
        }
    }

    private fun secureDocumentBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }.newDocumentBuilder()

    private fun org.w3c.dom.NodeList.asSequenceText(): String {
        return (0 until length).joinToString(" ") { index ->
            (item(index) as? Element)?.textContent.orEmpty()
        }
    }

    private fun chunkText(text: String): List<String> {
        val normalized = text.cleanText()
        if (normalized.isBlank()) return emptyList()
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < normalized.length) {
            val end = (start + CHUNK_SIZE).coerceAtMost(normalized.length)
            chunks += normalized.substring(start, end)
            start = (end - CHUNK_OVERLAP).coerceAtLeast(start + 1)
        }
        return chunks
    }

    private fun scoreChunk(chunk: KnowledgeBaseChunkEntity, terms: List<String>): Int {
        val haystack = "${chunk.title} ${chunk.keywords} ${chunk.content}".lowercase(Locale.ROOT)
        return terms.fold(0) { total, term ->
            total + when {
                haystack.contains(term) && chunk.title.lowercase(Locale.ROOT).contains(term) -> 4
                haystack.contains(term) -> 1
                else -> 0
            }
        }
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
    }

    private fun String.cleanText(): String =
        replace(Regex("\\s+"), " ").trim()

    companion object {
        private const val CHUNK_SIZE = 900
        private const val CHUNK_OVERLAP = 120
        private val TEXT_EXTENSIONS = setOf("txt", "md", "markdown", "csv", "json", "xml", "html", "kt", "java")
    }
}
