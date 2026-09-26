package com.github.garynasser.correction_notebook.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleUrlTest {
    @Test
    fun allowsOnlyValidWebUrls() {
        assertEquals("https://www.bit.edu.cn/article?id=1", safeArticleWebUrl(" https://www.bit.edu.cn/article?id=1 "))
        assertEquals("http://example.com/path", safeArticleWebUrl("http://example.com/path"))
        assertNull(safeArticleWebUrl("javascript:alert(1)"))
        assertNull(safeArticleWebUrl("intent://example.com"))
        assertNull(safeArticleWebUrl("https:///missing-host"))
        assertNull(safeArticleWebUrl("https://@"))
        assertNull(safeArticleWebUrl("  "))
    }
}
