package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.ai.AiActionType
import com.github.garynasser.correction_notebook.data.remote.ai.AiActionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiActionParserTest {
    @Test
    fun parsesStandardJson() {
        val result = AiActionParser.parse(
            """
            {
              "summary": "今天先复习数学",
              "planBlocks": [
                {"title": "高数例题", "reason": "明天上课", "estimatedMinutes": 30, "priority": "HIGH"}
              ],
              "actions": [
                {"type": "CREATE_TODO", "title": "做高数题", "description": "完成 3 道题", "payload": {"content": "完成 3 道题"}}
              ],
              "referencedMemories": ["薄弱课程: 高数"]
            }
            """.trimIndent()
        )

        assertTrue(result.parsed)
        assertEquals("今天先复习数学", result.summary)
        assertEquals(1, result.planBlocks.size)
        assertEquals(30, result.planBlocks.first().estimatedMinutes)
        assertEquals(AiActionType.CREATE_TODO, result.actions.first().type)
        assertEquals("薄弱课程: 高数", result.referencedMemories.first())
    }

    @Test
    fun parsesMarkdownFencedJson() {
        val result = AiActionParser.parse(
            """
            ```json
            {"summary":"ok","actions":[{"type":"SAVE_MEMORY","title":"记住偏好","payload":{"category":"学习偏好","content":"晚上学习"}}]}
            ```
            """.trimIndent()
        )

        assertTrue(result.parsed)
        assertEquals(AiActionType.SAVE_MEMORY, result.actions.first().type)
        assertEquals("晚上学习", result.actions.first().payload["content"])
    }

    @Test
    fun fallsBackForPlainText() {
        val result = AiActionParser.parse("先完成今天的课表，再复习资料。")

        assertFalse(result.parsed)
        assertEquals("先完成今天的课表，再复习资料。", result.rawText)
        assertTrue(result.actions.isEmpty())
    }
}
