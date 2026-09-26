package com.github.garynasser.correction_notebook.ui.screens.statistics

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsDateRangeTest {
    private val today = LocalDate.of(2026, 9, 12)

    @Test
    fun dayRangeContainsOnlyToday() {
        assertEquals(today to today, statsDateRange(StatsPeriod.DAY, today))
    }

    @Test
    fun weekRangeContainsSevenCalendarDays() {
        assertEquals(today.minusDays(6) to today, statsDateRange(StatsPeriod.WEEK, today))
    }

    @Test
    fun monthRangeStartsOnFirstDay() {
        assertEquals(LocalDate.of(2026, 9, 1) to today, statsDateRange(StatsPeriod.MONTH, today))
    }

    @Test
    fun periodLabelsMatchActualRanges() {
        assertEquals("今日", periodLabel(StatsPeriod.DAY))
        assertEquals("近 7 天", periodLabel(StatsPeriod.WEEK))
        assertEquals("本月", periodLabel(StatsPeriod.MONTH))
    }

    @Test
    fun blankSubjectsAreGroupedAsUncategorized() {
        assertEquals("高等数学", normalizeStatsSubject("  高等数学 "))
        assertEquals("未分类", normalizeStatsSubject("  "))
    }
}
