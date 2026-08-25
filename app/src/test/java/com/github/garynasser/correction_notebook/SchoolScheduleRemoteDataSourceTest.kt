package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.data.remote.school.SchoolScheduleRemoteDataSource
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
