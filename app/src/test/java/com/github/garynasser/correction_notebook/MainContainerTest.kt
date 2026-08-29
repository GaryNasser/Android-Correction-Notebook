package com.github.garynasser.correction_notebook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainContainerTest {
    @Test
    fun contentInsetsAreZeroWhenBottomBarIsVisible() {
        assertTrue(
            usesZeroContentInsets(
                hideBottomBar = false,
                shouldShowBottomBar = true
            )
        )
    }

    @Test
    fun contentInsetsAreZeroWhenImmersiveModeHidesBottomBar() {
        assertTrue(
            usesZeroContentInsets(
                hideBottomBar = true,
                shouldShowBottomBar = true
            )
        )
    }

    @Test
    fun contentInsetsUseDefaultsForScreensWithoutBottomBar() {
        assertFalse(
            usesZeroContentInsets(
                hideBottomBar = false,
                shouldShowBottomBar = false
            )
        )
    }
}
