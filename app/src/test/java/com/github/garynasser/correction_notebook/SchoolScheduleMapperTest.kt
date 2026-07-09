package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import com.github.garynasser.correction_notebook.data.model.school.SchoolCourseRaw
import com.github.garynasser.correction_notebook.data.model.school.SchoolTerm
import com.github.garynasser.correction_notebook.data.repository.school.SchoolScheduleMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SchoolScheduleMapperTest {
    private val mapper = SchoolScheduleMapper()

    @Test
    fun mapCoursesExpandsWeeksIntoStableSchoolEvents() {
        val term = SchoolTerm(
            id = "2025-2026-1",
            name = "2025-2026 学年秋季学期",
            startDate = LocalDate.of(2025, 9, 1),
            isCurrent = true
        )
        val courses = listOf(
            SchoolCourseRaw(
                courseName = "高等数学",
                teacherName = "张老师",
                location = "良乡 A101",
                weekday = 2,
                startSection = 1,
                endSection = 2,
                weeks = listOf(1, 2),
                courseCode = "MATH101",
                classCode = "A"
            )
        )

        val events = mapper.mapCourses(term, courses, importedAt = 1234L)

        assertEquals(2, events.size)
        assertEquals("高等数学", events[0].title)
        assertEquals(LocalDate.of(2025, 9, 2), events[0].startAt.toLocalDate())
        assertEquals(LocalTime.of(8, 0), events[0].startAt.toLocalTime())
        assertEquals(LocalTime.of(9, 35), events[0].endAt.toLocalTime())
        assertEquals(ScheduleSourceType.SCHOOL_IMPORT, events[0].sourceType)
        assertEquals("school_2025-2026-1", events[0].sourceCalendarId)
        assertTrue(events[0].description.contains("张老师"))
        assertTrue(events[0].description.contains("第1周"))
        assertEquals(LocalDate.of(2025, 9, 9), events[1].startAt.toLocalDate())
        assertTrue(events[0].sourceEventUid != events[1].sourceEventUid)
    }
}
