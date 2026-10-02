package com.github.garynasser.correction_notebook.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class PlannerSectionInputTest {
    @Test
    fun normalizeTimeFieldInputKeepsDigitsInsideRange() {
        assertEquals("09", normalizeTimeFieldInput("09", 0..23))
        assertEquals("7", normalizeTimeFieldInput("7点", 0..23))
    }

    @Test
    fun normalizeTimeFieldInputRejectsOutOfRangeValues() {
        assertNull(normalizeTimeFieldInput("24", 0..23))
        assertNull(normalizeTimeFieldInput("60", 0..59))
    }

    @Test
    fun blankOrInvalidTimeCannotSilentlyReuseThePreviousValue() {
        assertNull(scheduleTimeFromInput("", "00"))
        assertNull(scheduleTimeFromInput("09", ""))
        assertNull(scheduleTimeFromInput("24", "00"))
        assertNull(scheduleTimeFromInput("09", "60"))
    }

    @Test
    fun oneAndTwoDigitTimesAreParsedExactlyAsEntered() {
        assertEquals(LocalTime.of(9, 5), scheduleTimeFromInput("9", "5"))
        assertEquals(LocalTime.of(12, 30), scheduleTimeFromInput("12", "30"))
        assertEquals(LocalTime.MIDNIGHT, scheduleTimeFromInput("00", "00"))
        assertEquals(LocalTime.of(23, 59), scheduleTimeFromInput("23", "59"))
    }
}
