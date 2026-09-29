package com.github.garynasser.correction_notebook.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeArticleRefreshTest {
    @Test
    fun articleRefreshStartsOnlyWhenNoRequestIsRunning() {
        assertTrue(canStartArticleRefresh(isLoading = false))
        assertFalse(canStartArticleRefresh(isLoading = true))
    }
}
