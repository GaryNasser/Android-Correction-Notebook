package com.github.garynasser.correction_notebook.data.model.school

import java.time.LocalDate

data class SchoolTerm(
    val id: String,
    val name: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val isCurrent: Boolean = false
)

data class SchoolCourseRaw(
    val courseName: String,
    val teacherName: String = "",
    val location: String = "",
    val weekday: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>,
    val courseCode: String = "",
    val classCode: String = "",
    val campus: String = "",
    val extraDescription: String = ""
)

data class SchoolScheduleSyncResult(
    val termId: String,
    val termName: String,
    val importedCount: Int,
    val startedAt: Long,
    val finishedAt: Long,
    val message: String
)

class SchoolScheduleException(message: String, cause: Throwable? = null) : Exception(message, cause)
