package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.ui.screens.home.canStartAiAction
import com.github.garynasser.correction_notebook.ui.screens.home.canStartAiGeneration
import com.github.garynasser.correction_notebook.ui.screens.home.canSaveAdviceTodo
import com.github.garynasser.correction_notebook.ui.screens.home.normalizeAdviceTodoText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAiActionStateTest {
    @Test
    fun actionCanOnlyStartWhenItIsNeitherRunningNorCompleted() {
        assertTrue(canStartAiAction("action-1", emptySet(), emptySet()))
        assertFalse(canStartAiAction("action-1", setOf("action-1"), emptySet()))
        assertFalse(canStartAiAction("action-1", emptySet(), setOf("action-1")))
    }

    @Test
    fun adviceTodoNormalizesListMarkersAndRejectsRepeatedSaves() {
        val text = "  • 复习高等数学  "
        val key = "复习高等数学"

        assertEquals(key, normalizeAdviceTodoText(text))
        assertTrue(canSaveAdviceTodo(text, emptySet(), emptySet()))
        assertFalse(canSaveAdviceTodo(text, setOf(key), emptySet()))
        assertFalse(canSaveAdviceTodo(text, emptySet(), setOf(key)))
        assertFalse(canSaveAdviceTodo("   ", emptySet(), emptySet()))
    }

    @Test
    fun aiGenerationCannotStartWhileAnotherRequestIsRunning() {
        assertTrue(canStartAiGeneration(isLoading = false))
        assertFalse(canStartAiGeneration(isLoading = true))
    }
}
