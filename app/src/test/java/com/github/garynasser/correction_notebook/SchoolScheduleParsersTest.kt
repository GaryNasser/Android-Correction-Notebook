package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.school.SchoolScheduleParsers
import org.junit.Assert.assertEquals
import org.junit.Test

class SchoolScheduleParsersTest {
    @Test
    fun parseWeeksHandlesPlainRangesAndSingleWeeks() {
        assertEquals(
            listOf(1, 2, 3, 4, 5, 8, 10),
            SchoolScheduleParsers.parseWeeks("1-5周, 8周, 10周")
        )
    }

    @Test
    fun parseWeeksAppliesOddEvenRulesPerSegment() {
        assertEquals(
            listOf(1, 3, 5, 7, 10, 12, 14, 16),
            SchoolScheduleParsers.parseWeeks("1-8单周, 10-16双周")
        )
    }

    @Test
    fun parseWeeksAppliesOddEvenRulesPerRangeWithoutPunctuation() {
        assertEquals(
            listOf(1, 3, 5, 7, 10, 12, 14, 16),
            SchoolScheduleParsers.parseWeeks("1-8单周 10-16双周")
        )
    }

    @Test
    fun parseWeeksSupportsChineseRangeSeparators() {
        assertEquals(
            listOf(2, 4, 6),
            SchoolScheduleParsers.parseWeeks("2～6双周")
        )
    }
}
