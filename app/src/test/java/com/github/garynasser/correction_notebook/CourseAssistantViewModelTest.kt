package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.home.Priority
import com.github.garynasser.correction_notebook.ui.screens.yanhe.parseCourseAssistantPriority
import com.github.garynasser.correction_notebook.ui.screens.yanhe.canStartCourseAssistantAction
import com.github.garynasser.correction_notebook.ui.screens.yanhe.courseAssistantAiActionKey
import com.github.garynasser.correction_notebook.ui.screens.yanhe.courseAssistantResultNoteKey
import com.github.garynasser.correction_notebook.ui.screens.yanhe.courseAssistantResultTodoKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun assistantActionsUseStableScopedKeys() {
        assertEquals("ai:action-1", courseAssistantAiActionKey("action-1"))
        assertEquals("result-note:12:34", courseAssistantResultNoteKey(12, 34))
        assertEquals("result-todo:12:34", courseAssistantResultTodoKey(12, 34))
    }

    @Test
    fun assistantActionCannotRestartWhileBusyOrAfterCompletion() {
        val key = courseAssistantAiActionKey("action-1")

        assertTrue(canStartCourseAssistantAction(key, isActionBusy = false, appliedActionKeys = emptySet()))
        assertFalse(canStartCourseAssistantAction(key, isActionBusy = true, appliedActionKeys = emptySet()))
        assertFalse(canStartCourseAssistantAction(key, isActionBusy = false, appliedActionKeys = setOf(key)))
        assertFalse(canStartCourseAssistantAction("", isActionBusy = false, appliedActionKeys = emptySet()))
    }
}
