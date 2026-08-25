package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.home.Priority
import com.github.garynasser.correction_notebook.ui.screens.yanhe.parseCourseAssistantPriority
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseAssistantViewModelTest {
    @Test
    fun parseCourseAssistantPriorityAcceptsEnglishAndChineseValues() {
        assertEquals(Priority.HIGH, parseCourseAssistantPriority("HIGH"))
        assertEquals(Priority.HIGH, parseCourseAssistantPriority("重要"))
        assertEquals(Priority.LOW, parseCourseAssistantPriority("低"))
    }

    @Test
    fun parseCourseAssistantPriorityFallsBackToMedium() {
        assertEquals(Priority.MEDIUM, parseCourseAssistantPriority(null))
        assertEquals(Priority.MEDIUM, parseCourseAssistantPriority("马上做"))
    }
}
