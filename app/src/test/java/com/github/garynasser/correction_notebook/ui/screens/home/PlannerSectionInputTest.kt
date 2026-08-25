package com.github.garynasser.correction_notebook.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
