package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.home.Priority
import com.github.garynasser.correction_notebook.data.model.home.TodoHistoryItem
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import com.github.garynasser.correction_notebook.data.model.home.TodoSource
import com.github.garynasser.correction_notebook.data.model.home.TodoTaskType
import com.github.garynasser.correction_notebook.data.repository.TodoPreferenceCodec
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TodoPreferenceCodecTest {
    @Test
    fun todoJsonRoundTripPreservesDelimiterLikeText() {
        val item = TodoItem(
            id = "todo-1",
            title = "复习:::线代|||概率",
            description = "第一步:::看错题\n第二步|||整理公式",
            priority = Priority.HIGH,
            dueDate = LocalDate.of(2026, 9, 1),
            isCompleted = true,
            createdAt = 100L,
            completedAt = 200L,
            source = TodoSource.COURSE_ASSISTANT,
            sourceRefId = "course:::42",
            taskType = TodoTaskType.REVIEW,
            courseId = 42,
            estimatedMinutes = 30,
            weight = 1.5f
        )

        val encoded = TodoPreferenceCodec.serializeTodoItems(listOf(item))
        val decoded = TodoPreferenceCodec.parseTodoItems(encoded)

        assertEquals(listOf(item), decoded)
    }

    @Test
    fun todoParserStillReadsLegacyRecords() {
        val legacy = listOf(
            "legacy-id",
            "高数复习",
            "整理错题",
            "HIGH",
            "2026-09-01",
            "false",
            "100",
            "",
            "AI_TODAY_ADVICE",
            "advice-1",
            "REVIEW",
            "7",
            "25",
            "1.0"
        ).joinToString(":::")

        val decoded = TodoPreferenceCodec.parseTodoItems(legacy).single()

        assertEquals("legacy-id", decoded.id)
        assertEquals("高数复习", decoded.title)
        assertEquals(TodoSource.AI_TODAY_ADVICE, decoded.source)
        assertEquals(TodoTaskType.REVIEW, decoded.taskType)
        assertEquals(7, decoded.courseId)
    }

    @Test
    fun historyJsonRoundTripPreservesDelimiterLikeText() {
        val item = TodoHistoryItem(
            id = "history-1",
            title = "完成:::任务|||A",
            description = "复盘:::收获|||问题",
            priority = Priority.MEDIUM,
            dueDate = LocalDate.of(2026, 10, 2),
            createdAt = 300L,
            completedAt = 400L,
            completedDate = LocalDate.of(2026, 10, 3)
        )

        val encoded = TodoPreferenceCodec.serializeHistoryItems(listOf(item))
        val decoded = TodoPreferenceCodec.parseHistoryItems(encoded)

        assertEquals(listOf(item), decoded)
    }
}
