package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.ui.screens.profile.canStartAiSettingsAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAiSettingsStateTest {
    @Test
    fun aiSettingsActionsAreMutuallyExclusive() {
        assertTrue(canStartAiSettingsAction(isAiToggleBusy = false, isProviderBusy = false))
        assertFalse(canStartAiSettingsAction(isAiToggleBusy = true, isProviderBusy = false))
        assertFalse(canStartAiSettingsAction(isAiToggleBusy = false, isProviderBusy = true))
        assertFalse(canStartAiSettingsAction(isAiToggleBusy = true, isProviderBusy = true))
    }
}
