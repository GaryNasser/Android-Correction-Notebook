package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.ui.screens.knowledgebase.parseKnowledgeTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeBaseTagInputTest {
    @Test fun bothCommaStylesTrimAndDeduplicateWithoutChangingTagOrder() {
        assertEquals(listOf("期末", "重点", "线性代数"), parseKnowledgeTags(" 期末，重点, 重点，线性代数,期末 "))
    }

    @Test fun emptyInputAndEmptySeparatorsDoNotCreateTags() {
        assertTrue(parseKnowledgeTags(" \t，, ， ").isEmpty())
    }
}
