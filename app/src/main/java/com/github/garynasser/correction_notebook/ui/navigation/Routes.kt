package com.github.garynasser.correction_notebook.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object Home

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
object AITutor

@Serializable
data class VideoList(val courseId: Int)