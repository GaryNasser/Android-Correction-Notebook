package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.ui.screens.home.canStartAiAction
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
}
