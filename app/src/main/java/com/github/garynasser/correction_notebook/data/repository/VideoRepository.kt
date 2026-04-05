package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.yanhe.Course
import com.github.garynasser.correction_notebook.data.model.yanhe.PaginatedData
import com.github.garynasser.correction_notebook.data.remote.manager.VideoRemoteManager
import javax.inject.Inject

class VideoRepository @Inject constructor(
    private val videoRemoteManager: VideoRemoteManager
) {
    suspend fun getCourse(
        semester: String,
        page: Int,
        pageSize: Int,
        keyword: String,
    ): List<Course> {
        val response = videoRemoteManager.getCourseList(
            semester = semester,
            page = page.toString(),
            pageSize = "20",
            keyword = ""
        )

        return response?.data?.courses ?: emptyList()
    }

    suspend fun getPersonalCourse(
        semester: String,
        page: Int,
        pageSize: Int,
        keyword: String,
    ): List<Course> {
        val response = videoRemoteManager.getPersonalCourseList(
            semester = semester,
            page = page.toString(),
            pageSize = "20",
            keyword = ""
        )

        return response?.data?.courses ?: emptyList()
    }
}