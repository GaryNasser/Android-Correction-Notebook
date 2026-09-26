package com.github.garynasser.correction_notebook.ui.screens.aitutor

import com.github.garynasser.correction_notebook.data.local.ai.ChatSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AITutorSessionSelectionTest {
    @Test
    fun sessionListOnlyContainsTheActiveProvidersSessions() {
        val sessions = listOf(
            session(id = 1L, providerId = 10L),
            session(id = 2L, providerId = 20L),
            session(id = 3L, providerId = 10L)
        )

        assertEquals(listOf(1L, 3L), sessions.forProvider(10L).map { it.id })
        assertTrue(sessions.forProvider(null).isEmpty())
    }

    @Test
    fun selectedSessionMustBelongToTheActiveProvider() {
        val session = session(id = 1L, providerId = 10L)

        assertTrue(session.belongsToProvider(10L))
        assertFalse(session.belongsToProvider(20L))
        assertFalse((null as ChatSessionEntity?).belongsToProvider(10L))
    }

    private fun session(id: Long, providerId: Long) = ChatSessionEntity(
        id = id,
        title = "测试对话",
        providerId = providerId,
        model = "test-model"
    )
}
