package com.github.garynasser.correction_notebook.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePlanRefreshTest {
    @Test
    fun localPlanTracksSourceChangesUntilAiPlanIsGenerated() {
        assertTrue(shouldRefreshLocalPlan(aiAdvice = null))
        assertFalse(shouldRefreshLocalPlan(aiAdvice = "今天先完成高等数学复习"))
    }
}
