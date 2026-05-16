package com.github.garynasser.correction_notebook.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object Home

@Serializable
data class ArticleDetailRoute(val articleId: String)

@Serializable
object Login

@Serializable
object Register

@Serializable
object CasAuth

@Serializable
object Profile

@Serializable
object CourseList

@Serializable
object KnowledgeBase

@Serializable
data class KnowledgeBaseFileViewer(val fileId: String)

@Serializable
object AITutor

@Serializable
data class VideoList(val courseId: Int, val courseName: String = "")

@Serializable
data class VideoPlayer(val url: String)
