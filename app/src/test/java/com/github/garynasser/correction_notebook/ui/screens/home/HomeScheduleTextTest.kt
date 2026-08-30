package com.github.garynasser.correction_notebook.ui.screens.home

import com.github.garynasser.correction_notebook.data.model.home.ScheduleOccurrence
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
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
    fun courseGridPlacementSkipsAllDayItems() {
        assertNull(sampleOccurrence(allDay = true).toCourseGridPlacement())
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
