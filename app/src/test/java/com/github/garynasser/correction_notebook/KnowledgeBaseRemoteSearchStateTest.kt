package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.ui.screens.knowledgebase.isLatestRemoteSearch
import com.github.garynasser.correction_notebook.ui.screens.knowledgebase.shouldCancelRemoteSearch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeBaseRemoteSearchStateTest {
    @Test
    fun editingAnActiveQueryCancelsItsSearch() {
        assertFalse(shouldCancelRemoteSearch(activeQuery = null, editedQuery = "高数"))
        assertFalse(shouldCancelRemoteSearch(activeQuery = "高数", editedQuery = " 高数 "))
        assertTrue(shouldCancelRemoteSearch(activeQuery = "高数", editedQuery = "线代"))
        assertTrue(shouldCancelRemoteSearch(activeQuery = "高数", editedQuery = ""))
    }

    @Test
    fun onlyLatestSearchCanPublishState() {
        assertTrue(isLatestRemoteSearch(requestId = 3L, latestRequestId = 3L))
        assertFalse(isLatestRemoteSearch(requestId = 2L, latestRequestId = 3L))
    }
}
