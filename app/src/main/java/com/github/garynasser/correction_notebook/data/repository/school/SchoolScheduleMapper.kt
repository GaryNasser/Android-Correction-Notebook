package com.github.garynasser.correction_notebook.data.repository.school

import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import com.github.garynasser.correction_notebook.data.model.school.SchoolCourseRaw
import com.github.garynasser.correction_notebook.data.model.school.SchoolTerm
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

class SchoolScheduleMapper @Inject constructor() {
    private val sectionTimes: Map<Int, Pair<LocalTime, LocalTime>> = defaultSectionTimes
    fun mapCourses(term: SchoolTerm, courses: List<SchoolCourseRaw>, importedAt: Long): List<ScheduleEvent> {
        val termStart = term.startDate ?: inferTermStart()
        val firstMonday = termStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val calendarId = schoolCalendarId(term.id)

        return courses.flatMap { course ->
            val startTime = sectionTimes[course.startSection]?.first ?: return@flatMap emptyList()
            val endTime = sectionTimes[course.endSection]?.second ?: return@flatMap emptyList()
            course.weeks.distinct().sorted().mapNotNull { week ->
                if (week <= 0 || course.weekday !in 1..7) return@mapNotNull null
                val date = firstMonday.plusWeeks((week - 1).toLong()).plusDays((course.weekday - 1).toLong())
                ScheduleEvent(
                    id = buildEventId(term.id, course, week),
                    title = course.courseName.ifBlank { "未命名课程" },
                    description = buildDescription(course, week),
                    location = course.location,
                    startAt = LocalDateTime.of(date, startTime),
                    endAt = LocalDateTime.of(date, endTime),
                    allDay = false,
                    sourceType = ScheduleSourceType.SCHOOL_IMPORT,
                    sourceCalendarId = calendarId,
                    sourceEventUid = buildEventUid(term.id, course, week),
                    lastImportedAt = importedAt,
                    updatedAt = importedAt
                )
            }
        }.sortedWith(compareBy<ScheduleEvent> { it.startAt }.thenBy { it.title })
    }

    private fun buildDescription(course: SchoolCourseRaw, week: Int): String {
        return listOfNotNull(
            course.teacherName.takeIf { it.isNotBlank() }?.let { "教师：$it" },
            "第${week}周 周${course.weekday} 第${course.startSection}-${course.endSection}节",
            course.campus.takeIf { it.isNotBlank() }?.let { "校区：$it" },
            course.extraDescription.takeIf { it.isNotBlank() }
        ).joinToString("\n")
    }

    private fun buildEventId(termId: String, course: SchoolCourseRaw, week: Int): String {
        return "school_${buildEventUid(termId, course, week).stableHash()}"
    }

    private fun buildEventUid(termId: String, course: SchoolCourseRaw, week: Int): String {
        return listOf(
            termId,
            course.courseCode.ifBlank { course.courseName },
            course.classCode,
            week.toString(),
            course.weekday.toString(),
            course.startSection.toString(),
            course.endSection.toString(),
            course.location
        ).joinToString("#")
    }

    private fun inferTermStart(today: LocalDate = LocalDate.now()): LocalDate {
        val month = today.monthValue
        val year = today.year
        val roughStart = if (month in 2..7) LocalDate.of(year, 2, 24) else LocalDate.of(year, 9, 1)
        return roughStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    private fun String.stableHash(): String {
        return java.security.MessageDigest.getInstance("SHA-1")
            .digest(toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun schoolCalendarId(termId: String): String = "school_${termId.trim()}"

        val defaultSectionTimes: Map<Int, Pair<LocalTime, LocalTime>> = mapOf(
            1 to (LocalTime.of(8, 0) to LocalTime.of(8, 45)),
            2 to (LocalTime.of(8, 50) to LocalTime.of(9, 35)),
            3 to (LocalTime.of(9, 55) to LocalTime.of(10, 40)),
            4 to (LocalTime.of(10, 45) to LocalTime.of(11, 30)),
            5 to (LocalTime.of(11, 35) to LocalTime.of(12, 20)),
            6 to (LocalTime.of(13, 20) to LocalTime.of(14, 5)),
            7 to (LocalTime.of(14, 10) to LocalTime.of(14, 55)),
            8 to (LocalTime.of(15, 15) to LocalTime.of(16, 0)),
            9 to (LocalTime.of(16, 5) to LocalTime.of(16, 50)),
            10 to (LocalTime.of(16, 55) to LocalTime.of(17, 40)),
            11 to (LocalTime.of(18, 30) to LocalTime.of(19, 15)),
            12 to (LocalTime.of(19, 20) to LocalTime.of(20, 5)),
            13 to (LocalTime.of(20, 10) to LocalTime.of(20, 55))
        )
    }
}
