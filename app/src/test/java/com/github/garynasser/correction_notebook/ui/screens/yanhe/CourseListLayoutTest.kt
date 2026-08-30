package com.github.garynasser.correction_notebook.ui.screens.yanhe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseListLayoutTest {
    @Test
    fun courseFiltersStackOnPhoneWidth() {
        assertTrue(shouldStackCourseFilters(360f))
    }

    @Test
    fun courseFiltersStayInlineOnWideWidth() {
        assertFalse(shouldStackCourseFilters(420f))
        assertFalse(shouldStackCourseFilters(600f))
    }
}
