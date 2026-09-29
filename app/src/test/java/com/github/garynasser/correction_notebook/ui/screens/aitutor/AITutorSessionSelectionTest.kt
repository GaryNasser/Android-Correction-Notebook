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

    @Test
    fun openingTutorSelectsExistingSessionWithoutCreatingAnEmptyOne() {
        val selected = session(id = 1L, providerId = 10L)
        val latest = session(id = 2L, providerId = 10L)

        assertEquals(1L, chooseSessionIdForProvider(10L, selected, latest))
        assertEquals(2L, chooseSessionIdForProvider(10L, session(3L, 20L), latest))
        assertEquals(null, chooseSessionIdForProvider(10L, null, null))
    }

    @Test
    fun emptyDefaultSessionUsesTheFirstMessageAsItsTitle() {
        val emptySession = session(id = 1L, providerId = 10L, title = DEFAULT_CHAT_SESSION_TITLE)

        assertTrue(shouldAutoTitleSession(emptySession, hasMessages = false))
        assertFalse(shouldAutoTitleSession(emptySession, hasMessages = true))
        assertFalse(shouldAutoTitleSession(emptySession.copy(title = "自定义标题"), hasMessages = false))
        assertEquals("请帮我复习高等数学第一章", chatSessionTitleFrom("  请帮我复习高等数学第一章  "))
        assertEquals(18, chatSessionTitleFrom("这是一段超过十八个字符且用于生成会话标题的学习问题").length)
    }

    private fun session(
        id: Long,
        providerId: Long,
        title: String = "测试对话"
    ) = ChatSessionEntity(
        id = id,
        title = title,
        providerId = providerId,
        model = "test-model"
    )
}
