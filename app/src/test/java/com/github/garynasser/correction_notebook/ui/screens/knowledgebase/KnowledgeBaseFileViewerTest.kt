package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeBaseFileViewerTest {
    @Test
    fun pdfPreviewCapsRenderedPages() {
        assertEquals(0, pdfPreviewPageCount(0))
        assertEquals(4, pdfPreviewPageCount(4))
        assertEquals(MAX_PDF_PREVIEW_PAGES, pdfPreviewPageCount(120))
    }
}
