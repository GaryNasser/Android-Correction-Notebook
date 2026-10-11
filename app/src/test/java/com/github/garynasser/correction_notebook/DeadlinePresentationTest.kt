package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.home.parseDeadline
import com.github.garynasser.correction_notebook.data.model.home.parseDeadlineTime
import com.github.garynasser.correction_notebook.ui.screens.home.deadlineRows
import com.github.garynasser.correction_notebook.ui.screens.home.deadlineStatus
import com.google.gson.JsonParser
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class DeadlinePresentationTest {
    private fun row(json: String, kind: String = "homework") = parseDeadline(JsonParser.parseString(json).asJsonObject, 12, "Course", kind)
    @Test fun submittedHomeworkIsNotMarkedPendingBeforeTheDeadline() {
        val item = row("""{"id":1,"title":"Assignment","user_submit_count":1,"is_closed":false,"end_time":"2026-10-13T15:59:00Z"}""")
        val now = Instant.parse("2026-10-11T00:00:00Z").toEpochMilli()
        assertEquals("已提交", deadlineStatus(item, now))
        assertTrue(deadlineRows(listOf(item), "pending", now).isEmpty())
        assertEquals(listOf(item), deadlineRows(listOf(item), "submitted", now))
    }
    @Test fun utcAndOffsetDatesResolveToSameDeadline() {
        assertEquals(parseDeadlineTime("2026-10-13T15:59:00Z"), parseDeadlineTime("2026-10-13T23:59:00+08:00"))
        assertEquals(parseDeadlineTime("2026-10-13T15:59:00Z"), parseDeadlineTime("2026-10-13 23:59:00"))
    }
    @Test fun missingDueDateDoesNotBecomeMidnightOrToday() {
        assertNull(row("""{"id":1,"title":"Assignment","end_time":null}""").deadline)
        assertTrue(runCatching { parseDeadlineTime("invalid") }.isFailure)
    }
    @Test fun unknownExamSubmissionIsNotClaimedUnsubmitted() {
        val item = row("""{"id":1,"title":"Exam","end_time":"2026-10-13T15:59:00Z"}""", "exam")
        assertNull(item.submitted)
        assertEquals("未查询考试提交状态", deadlineStatus(item, 0))
    }
    @Test fun upcomingItemsComeBeforeOverdueAndUndatedItems() {
        val item = row("""{"id":1,"title":"Assignment","user_submit_count":0}""")
        val future = item.copy(id = "future", deadline = 200)
        val overdue = item.copy(id = "overdue", deadline = 50)
        assertEquals(listOf(future, overdue, item), deadlineRows(listOf(item, overdue, future), "pending", 100))
        assertEquals("未提交 · 已截止", deadlineStatus(overdue, 100))
    }
}
