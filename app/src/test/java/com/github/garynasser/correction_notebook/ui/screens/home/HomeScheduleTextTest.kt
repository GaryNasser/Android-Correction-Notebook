package com.github.garynasser.correction_notebook.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

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
}
