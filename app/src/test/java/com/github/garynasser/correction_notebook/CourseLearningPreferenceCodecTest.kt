package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.yanhe.CourseNote
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseProgress
import com.github.garynasser.correction_notebook.data.repository.CourseLearningPreferenceCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseLearningPreferenceCodecTest {
    @Test
    fun progressJsonRoundTripPreservesDelimiterLikeText() {
        val progress = CourseProgress(
            courseId = 42,
            courseName = "机器学习:::实验|||复盘",
            lastSectionId = 7,
            lastSectionTitle = "支持向量机:::核函数|||习题",
            lastVideoUrl = "https://yanhe.example/video?id=42:::7|||part=1",
            completedSectionIds = setOf(1, 3, 7),
            totalSections = 12,
            watchedMinutes = 90,
            lastAccessedAt = 123_456L
        )

        val encoded = CourseLearningPreferenceCodec.serializeProgressItems(listOf(progress))
        val decoded = CourseLearningPreferenceCodec.parseProgressItems(encoded)

        assertEquals(listOf(progress), decoded)
    }

    @Test
    fun notesJsonRoundTripPreservesAiContentWithSeparators() {
        val note = CourseNote(
            id = "note-1",
            courseId = 42,
            courseName = "矩阵分析:::A",
            sectionId = 5,
            sectionTitle = "特征值|||特征向量",
            content = "重点:::先看定义\n例题|||要重新推一遍",
            aiGenerated = true,
            createdAt = 987_654L
        )

        val encoded = CourseLearningPreferenceCodec.serializeNotes(listOf(note))
        val decoded = CourseLearningPreferenceCodec.parseNotes(encoded)

        assertEquals(listOf(note), decoded)
    }

    @Test
    fun parsersStillReadLegacyRecords() {
        val legacyProgress = listOf(
            "7",
            "高等数学",
            "3",
            "极限与连续",
            "https://example.com/video.mp4",
            "1,3",
            "10",
            "35",
            "1000"
        ).joinToString(":::")
        val legacyNote = listOf(
            "legacy-note",
            "7",
            "高等数学",
            "3",
            "极限与连续",
            "先复习洛必达",
            "false",
            "2000"
        ).joinToString(":::")

        val progress = CourseLearningPreferenceCodec.parseProgressItems(legacyProgress).single()
        val note = CourseLearningPreferenceCodec.parseNotes(legacyNote).single()

        assertEquals("高等数学", progress.courseName)
        assertEquals(setOf(1, 3), progress.completedSectionIds)
        assertEquals("legacy-note", note.id)
        assertEquals("先复习洛必达", note.content)
    }

    @Test
    fun parsersSkipOnlyCorruptJsonRecords() {
        val rawProgress = """
            [
              {"courseName":"坏记录"},
              {
                "courseId":8,
                "courseName":"有效课程",
                "lastSectionId":2,
                "lastSectionTitle":"有效章节",
                "lastVideoUrl":"",
                "completedSectionIds":[2],
                "totalSections":6,
                "watchedMinutes":20,
                "lastAccessedAt":3000
              }
            ]
        """.trimIndent()
        val rawNotes = """
            [
              {"id":"bad-note","content":"缺少课程"},
              {
                "id":"valid-note",
                "courseId":8,
                "courseName":"有效课程",
                "sectionId":2,
                "sectionTitle":"有效章节",
                "content":"正常内容",
                "aiGenerated":false,
                "createdAt":4000
              }
            ]
        """.trimIndent()

        val progress = CourseLearningPreferenceCodec.parseProgressItems(rawProgress)
        val notes = CourseLearningPreferenceCodec.parseNotes(rawNotes)

        assertEquals(1, progress.size)
        assertEquals("有效课程", progress.single().courseName)
        assertEquals(1, notes.size)
        assertEquals("valid-note", notes.single().id)
    }
}
