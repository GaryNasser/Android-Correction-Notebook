package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.data.remote.school.SchoolScheduleRemoteDataSource
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SchoolScheduleRemoteDataSourceTest {
    private val dataSource = SchoolScheduleRemoteDataSource(
        bitCasClient = BitCasClient(OkHttpClient()),
        okHttpClient = OkHttpClient()
    )

    @Test
    fun parseCoursesSkipsStructuredFieldsAndUsesFallbackKeys() {
        val root = JsonParser.parseString(
            """
            {
              "cxxszhxqkb": [
                {
                  "KCMC": "矩阵分析",
                  "XQ": { "label": "broken" },
                  "SKXQ": "周三",
                  "JC": { "range": "broken" },
                  "SKJC": "6-7节",
                  "ZC": { "weeks": "broken" },
                  "SKZC": "1-3周",
                  "JASMC": "综教 A101"
                }
              ]
            }
            """.trimIndent()
        ).asJsonObject

        val courses = dataSource.parseCourses(root)

        assertEquals(1, courses.size)
        assertEquals("矩阵分析", courses.first().courseName)
        assertEquals(3, courses.first().weekday)
        assertEquals(6, courses.first().startSection)
        assertEquals(7, courses.first().endSection)
        assertEquals(listOf(1, 2, 3), courses.first().weeks)
        assertEquals("综教 A101", courses.first().location)
    }

    @Test
    fun parseCoursesHandlesCompactSectionCodeFromSchoolSystem() {
        val root = JsonParser.parseString(
            """
            {
              "cxxszhxqkb": [
                {
                  "KCMC": "机器学习",
                  "SKXQ": "4",
                  "JCDM": "1112",
                  "SKZC": "1-16周",
                  "JASMC": "文萃楼 F702"
                }
              ]
            }
            """.trimIndent()
        ).asJsonObject

        val courses = dataSource.parseCourses(root)

        assertEquals(1, courses.size)
        assertEquals(11, courses.first().startSection)
        assertEquals(12, courses.first().endSection)
    }

    @Test
    fun parseCurrentTermKeepsDateWhenSchoolReturnsTimeSuffix() {
        val root = JsonParser.parseString(
            """
            {
              "dqxnxq": {
                "XNXQDM": "2025-2026-1",
                "XNXQMC": "2025-2026 学年秋季学期",
                "KSRQ": "2025-09-01 00:00:00",
                "JSRQ": "2026-01-18T23:59:59"
              }
            }
            """.trimIndent()
        ).asJsonObject

        val term = dataSource.parseCurrentTerm(root)

        assertEquals(LocalDate.of(2025, 9, 1), term.startDate)
        assertEquals(LocalDate.of(2026, 1, 18), term.endDate)
    }

    @Test
    fun parseCurrentTermKeepsChineseFormattedDate() {
        val root = JsonParser.parseString(
            """
            {
              "dqxnxq": {
                "XNXQDM": "2025-2026-2",
                "XNXQMC": "2025-2026 学年春季学期",
                "KSRQ": "2026年2月23日",
                "JSRQ": "2026年7月5日"
              }
            }
            """.trimIndent()
        ).asJsonObject

        val term = dataSource.parseCurrentTerm(root)

        assertEquals(LocalDate.of(2026, 2, 23), term.startDate)
        assertEquals(LocalDate.of(2026, 7, 5), term.endDate)
    }

    @Test
    fun parseCurrentTermAcceptsUnpaddedAndDottedDates() {
        val root = JsonParser.parseString(
            """
            {
              "dqxnxq": {
                "XNXQDM": "2025-2026-1",
                "XNXQMC": "2025-2026 学年秋季学期",
                "KSRQ": "2025-9-1",
                "JSRQ": "2026.1.18 23:59:59"
              }
            }
            """.trimIndent()
        ).asJsonObject

        val term = dataSource.parseCurrentTerm(root)

        assertEquals(LocalDate.of(2025, 9, 1), term.startDate)
        assertEquals(LocalDate.of(2026, 1, 18), term.endDate)
    }
}
