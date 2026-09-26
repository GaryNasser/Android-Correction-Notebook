package com.github.garynasser.correction_notebook.ui.screens.yanhe

import com.github.garynasser.correction_notebook.data.model.yanhe.Course
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseSemesterFilterTest {
    @Test
    fun personalSemesterOptionsIncludeAllAndNewestFirst() {
        val courses = listOf(
            course(id = 1, semester = "2024-2025 秋季"),
            course(id = 2, semester = "2025-2026 春季"),
            course(id = 3, semester = "2025-2026 春季"),
            course(id = 4, semester = "")
        )

        assertEquals(
            listOf(ALL_SEMESTERS, "2025-2026 春季", "2024-2025 秋季"),
            buildCourseSemesters(courses)
        )
    }

    @Test
    fun emptyCourseListOnlyOffersAllSemesters() {
        assertEquals(listOf(ALL_SEMESTERS), buildCourseSemesters(emptyList()))
    }

    private fun course(id: Int, semester: String) = Course(
        nameZh = "测试课程",
        orientation = 0,
        code = "",
        collegeName = "",
        imageUrl = "",
        collegeCode = "",
        schoolYear = "",
        universityCode = "",
        lxUrl = "",
        number = "",
        universityId = 0,
        semester = semester,
        id = id,
        state = 0,
        views = 0,
        nameEn = "",
        participantCount = 0,
        professors = emptyList(),
        classrooms = emptyList()
    )
}
