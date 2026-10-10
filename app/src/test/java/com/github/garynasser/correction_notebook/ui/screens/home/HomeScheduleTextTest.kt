package com.github.garynasser.correction_notebook.ui.screens.home

import com.github.garynasser.correction_notebook.data.model.home.ScheduleOccurrence
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSection
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class HomeScheduleTextTest {
    @Test
    fun gridLocationTextKeepsOnlyTheFirstAddressLine() {
        val rawLocation = """
            文萃楼 F602
            良乡校区 备用教室
        """.trimIndent()

        assertEquals("文萃楼\nF602", rawLocation.toGridLocationText())
    }

    @Test
    fun gridLocationTextWrapsAtSpacesForNarrowCourseBlocks() {
        val rawLocation = "  游泳馆   浅水区   南侧  "

        assertEquals("游泳馆\n浅水区\n南侧", rawLocation.toGridLocationText())
    }

    @Test
    fun gridLocationTextSplitsCompactBuildingAndRoomCode() {
        assertEquals("文萃楼\nF602", "文萃楼F602".toGridLocationText())
        assertEquals("综教\nA303", "综教A303".toGridLocationText())
    }

    @Test
    fun gridLocationTextDoesNotSplitBareRoomCodes() {
        assertEquals("3204", "3204".toGridLocationText())
        assertEquals("A303", "A303".toGridLocationText())
    }

    @Test
    fun gridLocationTextSplitsOnlyFirstAddressLineWhenCompact() {
        val rawLocation = """
            文萃楼F602
            良乡校区 备用教室
        """.trimIndent()

        assertEquals("文萃楼\nF602", rawLocation.toGridLocationText())
    }

    @Test
    fun gridLocationTextSplitsCompactCampusBuildingAndRoomCode() {
        val rawLocation = """
            良乡校区文萃楼F602
            候补教室
        """.trimIndent()

        assertEquals("良乡校区\n文萃楼\nF602", rawLocation.toGridLocationText())
    }

    @Test
    fun gridLocationTextSplitsCompactCampusLocationWithoutRoomCode() {
        assertEquals("良乡校区\n游泳馆", "良乡校区游泳馆".toGridLocationText())
    }

    @Test
    fun visibleBitWeekDaysAlwaysStartOnMonday() {
        val friday = LocalDate.of(2026, 7, 3)

        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 29),
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 2),
                LocalDate.of(2026, 7, 3),
                LocalDate.of(2026, 7, 4),
                LocalDate.of(2026, 7, 5)
            ),
            visibleBitWeekDays(friday)
        )
    }

    @Test
    fun datePickerUsesUtcMidnightWithoutTimezoneDrift() {
        val date = LocalDate.of(2026, 7, 3)
        val millis = date.toDatePickerUtcMillis()

        assertEquals(Instant.parse("2026-07-03T00:00:00Z").toEpochMilli(), millis)
        assertEquals(date, datePickerMillisToLocalDate(millis))
    }

    @Test
    fun courseGridPlacementSkipsAllDayItems() {
        assertNull(sampleOccurrence(allDay = true).toCourseGridPlacement())
    }

    @Test
    fun courseGridPlacementSkipsCrossDayItems() {
        assertNull(sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 2, 8, 0),
            endAt = LocalDateTime.of(2026, 7, 3, 9, 35)
        ).toCourseGridPlacement())
    }

    @Test
    fun courseGridPlacementSkipsItemsOutsideCourseSections() {
        assertNull(
            sampleOccurrence(
                startAt = LocalDateTime.of(2026, 7, 3, 7, 0),
                endAt = LocalDateTime.of(2026, 7, 3, 8, 0)
            ).toCourseGridPlacement()
        )
        assertNull(
            sampleOccurrence(
                startAt = LocalDateTime.of(2026, 7, 3, 20, 55),
                endAt = LocalDateTime.of(2026, 7, 3, 22, 0)
            ).toCourseGridPlacement()
        )
    }

    @Test
    fun courseGridPlacementSkipsBreakOnlyItems() {
        assertNull(
            sampleOccurrence(
                startAt = LocalDateTime.of(2026, 7, 3, 12, 30),
                endAt = LocalDateTime.of(2026, 7, 3, 13, 0)
            ).toCourseGridPlacement()
        )
    }

    @Test
    fun courseGridPlacementUsesOnlyActuallyOverlappedSections() {
        val placement = sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 3, 13, 0),
            endAt = LocalDateTime.of(2026, 7, 3, 13, 30)
        ).toCourseGridPlacement()

        assertEquals(CourseGridPlacement(startIndex = 5, span = 1), placement)
    }

    @Test
    fun courseGridPlacementMapsNormalCourseSections() {
        val placement = sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 3, 13, 20),
            endAt = LocalDateTime.of(2026, 7, 3, 14, 55)
        ).toCourseGridPlacement()

        assertEquals(CourseGridPlacement(startIndex = 5, span = 2), placement)
    }

    @Test
    fun overlappingCoursesUseSeparateLanes() {
        val first = sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 3, 8, 0),
            endAt = LocalDateTime.of(2026, 7, 3, 9, 35)
        ).copy(occurrenceId = "first", title = "高等数学")
        val second = sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 3, 8, 50),
            endAt = LocalDateTime.of(2026, 7, 3, 10, 40)
        ).copy(occurrenceId = "second", title = "大学物理")

        val layouts = layoutCourseGridItems(listOf(first, second))

        assertEquals(listOf(0, 1), layouts.map { it.laneIndex })
        assertEquals(listOf(2, 2), layouts.map { it.laneCount })
    }

    @Test
    fun nonOverlappingCoursesKeepTheFullDayColumn() {
        val morning = sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 3, 8, 0),
            endAt = LocalDateTime.of(2026, 7, 3, 8, 45)
        ).copy(occurrenceId = "morning")
        val afternoon = sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 3, 13, 20),
            endAt = LocalDateTime.of(2026, 7, 3, 14, 5)
        ).copy(occurrenceId = "afternoon")

        val layouts = layoutCourseGridItems(listOf(afternoon, morning))

        assertEquals(listOf("morning", "afternoon"), layouts.map { it.item.occurrenceId })
        assertEquals(listOf(1, 1), layouts.map { it.laneCount })
        assertEquals(listOf(0, 0), layouts.map { it.laneIndex })
    }

    @Test
    fun weeklyOffGridOccurrencesKeepsAllDayAndNonClassTimeItemsInOrder() {
        val inGrid = sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 3, 8, 0),
            endAt = LocalDateTime.of(2026, 7, 3, 8, 45)
        )
        val evening = sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 3, 21, 10),
            endAt = LocalDateTime.of(2026, 7, 3, 22, 0)
        ).copy(occurrenceId = "evening", title = "晚间活动")
        val allDay = sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 2, 0, 0),
            endAt = LocalDateTime.of(2026, 7, 3, 0, 0),
            allDay = true
        ).copy(occurrenceId = "all-day", title = "全天事项")

        val result = weeklyOffGridOccurrences(
            listOf(
                ScheduleSection("周五", LocalDate.of(2026, 7, 3), listOf(evening, inGrid)),
                ScheduleSection("周四", LocalDate.of(2026, 7, 2), listOf(allDay))
            )
        )

        assertEquals(listOf("all-day", "evening"), result.map { it.occurrenceId })
    }

    @Test
    fun aMultiDayOccurrenceAppearsOnlyOnceInTheWeeklyOffGridList() {
        val occurrence = sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 2, 0, 0),
            endAt = LocalDateTime.of(2026, 7, 4, 0, 0),
            allDay = true
        )
        val result = weeklyOffGridOccurrences(listOf(
            ScheduleSection("周四", LocalDate.of(2026, 7, 2), listOf(occurrence)),
            ScheduleSection("周五", LocalDate.of(2026, 7, 3), listOf(occurrence))
        ))
        assertEquals(listOf(occurrence), result)
    }

    @Test
    fun differentInstancesOfTheSameEventRemainInTheWeeklyOffGridList() {
        val first = sampleOccurrence(allDay = true).copy(occurrenceId = "first")
        val second = first.copy(occurrenceId = "second", startAt = first.startAt.plusDays(1), endAt = first.endAt.plusDays(1))
        assertEquals(listOf(first, second), weeklyOffGridOccurrences(listOf(
            ScheduleSection("周五", LocalDate.of(2026, 7, 3), listOf(first)),
            ScheduleSection("周六", LocalDate.of(2026, 7, 4), listOf(second))
        )))
    }

    @Test
    fun aSameDayTimeRangeKeepsItsCompactEndTime() {
        assertEquals("07月03日 08:00 - 08:45", fullScheduleTime(sampleOccurrence()))
    }

    @Test
    fun anOvernightTimeRangeIncludesBothDates() {
        assertEquals("07月03日 23:30 - 07月04日 01:00", fullScheduleTime(sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 3, 23, 30), endAt = LocalDateTime.of(2026, 7, 4, 1, 0)
        )))
    }

    @Test
    fun aSingleAllDayEventDoesNotIncludeItsExclusiveEndDate() {
        assertEquals("07月03日 · 全天", fullScheduleTime(sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 3, 0, 0), endAt = LocalDateTime.of(2026, 7, 4, 0, 0), allDay = true
        )))
    }

    @Test
    fun aMultiDayEventShowsItsLastIncludedDay() {
        assertEquals("07月02日 - 07月03日 · 全天", fullScheduleTime(sampleOccurrence(
            startAt = LocalDateTime.of(2026, 7, 2, 0, 0), endAt = LocalDateTime.of(2026, 7, 4, 0, 0), allDay = true
        )))
    }

    @Test
    fun anOvernightNewYearEventIncludesBothYears() {
        assertEquals("2026年12月31日 23:30 - 2027年01月01日 01:00", fullScheduleTime(sampleOccurrence(
            startAt = LocalDateTime.of(2026, 12, 31, 23, 30), endAt = LocalDateTime.of(2027, 1, 1, 1, 0)
        )))
    }

    @Test
    fun anAllDayNewYearEventIncludesBothYears() {
        assertEquals("2026年12月31日 - 2027年01月01日 · 全天", fullScheduleTime(sampleOccurrence(
            startAt = LocalDateTime.of(2026, 12, 31, 0, 0), endAt = LocalDateTime.of(2027, 1, 2, 0, 0), allDay = true
        )))
    }

    private fun sampleOccurrence(
        startAt: LocalDateTime = LocalDateTime.of(2026, 7, 3, 8, 0),
        endAt: LocalDateTime = LocalDateTime.of(2026, 7, 3, 8, 45),
        allDay: Boolean = false
    ): ScheduleOccurrence {
        return ScheduleOccurrence(
            occurrenceId = "occurrence",
            eventId = "event",
            title = "测试课程",
            description = "",
            location = "文萃楼F602",
            startAt = startAt,
            endAt = endAt,
            allDay = allDay,
            sourceType = ScheduleSourceType.MANUAL
        )
    }
}
