package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.home.SessionType
import com.github.garynasser.correction_notebook.data.model.home.StudySession
import com.github.garynasser.correction_notebook.data.repository.StudySessionPreferenceCodec
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class StudySessionPreferenceCodecTest {
    @Test
    fun jsonRoundTripPreservesDelimiterLikeSubject() {
        val session = StudySession(
            id = "session-1",
            subject = "番茄钟:::线代|||概率",
            startTime = LocalDateTime.of(2026, 9, 1, 8, 30),
            endTime = LocalDateTime.of(2026, 9, 1, 9, 10),
            durationMinutes = 40,
            sessionType = SessionType.POMODORO,
            pomodoroCount = 2
        )

        val encoded = StudySessionPreferenceCodec.serializeSessions(listOf(session))
        val decoded = StudySessionPreferenceCodec.parseSessions(encoded)

        assertEquals(listOf(session), decoded)
    }

    @Test
    fun parserStillReadsLegacyRecords() {
        val legacy = listOf(
            "legacy-id",
            "正计时",
            "2026-09-01T08:30:00",
            "2026-09-01T09:10:00",
            "40",
            "STOPWATCH",
            "0"
        ).joinToString(":::")

        val decoded = StudySessionPreferenceCodec.parseSessions(legacy).single()

        assertEquals("legacy-id", decoded.id)
        assertEquals("正计时", decoded.subject)
        assertEquals(SessionType.STOPWATCH, decoded.sessionType)
        assertEquals(40, decoded.durationMinutes)
    }

    @Test
    fun parserSkipsOnlyCorruptLegacyRecord() {
        val valid = listOf(
            "valid-id",
            "倒计时",
            "2026-09-01T10:00:00",
            "",
            "25",
            "COUNTDOWN",
            "0"
        ).joinToString(":::")
        val corrupt = listOf(
            "bad-id",
            "坏记录",
            "not-a-date",
            "",
            "20",
            "COUNTDOWN",
            "0"
        ).joinToString(":::")

        val decoded = StudySessionPreferenceCodec.parseSessions("$corrupt|||$valid")

        assertEquals(1, decoded.size)
        assertEquals("valid-id", decoded.single().id)
    }

    @Test
    fun parserSkipsOnlyCorruptJsonRecord() {
        val raw = """
            [
              {
                "id": "bad-id",
                "subject": "坏记录",
                "startTime": "not-a-date",
                "durationMinutes": 30,
                "sessionType": "POMODORO",
                "pomodoroCount": 1
              },
              {
                "id": "json-id",
                "subject": "番茄钟",
                "startTime": "2026-09-01T12:00:00",
                "endTime": null,
                "durationMinutes": 25,
                "sessionType": "POMODORO",
                "pomodoroCount": 1
              }
            ]
        """.trimIndent()

        val decoded = StudySessionPreferenceCodec.parseSessions(raw)

        assertEquals(1, decoded.size)
        assertEquals("json-id", decoded.single().id)
    }
}
