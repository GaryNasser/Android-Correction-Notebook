package com.github.garynasser.correction_notebook.data.model.knowledgebase

data class KnowledgeBaseBreadcrumb(
    val id: String?,
    val name: String
)

data class KnowledgeBaseFolderSummary(
    val id: String,
    val name: String,
    val path: String,
    val directFileCount: Int
)

data class KnowledgeBaseFileSummary(
    val id: String,
    val folderId: String?,
    val displayName: String,
    val localPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sourceType: String,
    val sourceTitle: String?,
    val downloadedAt: Long?
)

data class KnowledgeBaseFolderChoice(
    val id: String?,
    val name: String,
    val path: String,
    val depth: Int
)

data class KnowledgeBaseFolderContent(
    val breadcrumbs: List<KnowledgeBaseBreadcrumb>,
    val folders: List<KnowledgeBaseFolderSummary>,
    val files: List<KnowledgeBaseFileSummary>
)
