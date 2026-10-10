package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.data.model.home.ScheduleOccurrence
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CourseGridBlockLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun longCourseNameAndFirstAddressLineFitAtLargeFontScale() {
        assertCourseFits(
            title = "毛泽东思想和中国特色社会主义理论体系概论",
            location = "文萃楼 M134\n良乡校区备用教室",
            expectedLocation = "文萃楼 M134",
            span = 3
        )
    }

    @Test
    fun multiPartFirstAddressLineFitsWithoutIncludingTheSecondLine() {
        assertCourseFits(
            title = "体育/游泳（初级）",
            location = "游泳馆 浅水区 南侧\n良乡校区",
            expectedLocation = "游泳馆 浅水区 南侧",
            span = 2
        )
    }

    @Test
    fun aShortCourseTitleDoesNotLeaveOneCharacterOnItsLastLine() {
        val title = "导入链路验证"
        val layouts = assertCourseFits(
            title = title,
            location = "文萃楼 M134\n本地验证第二行",
            expectedLocation = "文萃楼 M134",
            span = 2,
            width = 54.dp,
            fontScale = 1f
        )
        layouts.forEach { layout ->
            val lengths = (0 until layout.lineCount)
                .filter { layout.getLineStart(it) < title.length }
                .map { layout.getLineEnd(it, visibleEnd = true).coerceAtMost(title.length) - layout.getLineStart(it) }
            assertEquals(2, lengths.size)
            assertTrue("Title lines should be balanced, not $lengths", lengths.max() - lengths.min() <= 1)
        }
    }

    @Test
    fun aSinglePeriodCourseKeepsItsCompleteNameAndFirstAddressLine() {
        assertCourseFits(
            title = "数据结构与算法",
            location = "综教 A101\n良乡校区备用教室",
            expectedLocation = "综教 A101",
            span = 1
        )
    }

    @Test
    fun anEnglishTitleKeepsItsCompleteTextAndFirstAddressLine() {
        assertCourseFits(
            title = "Signals and Systems",
            location = "文萃楼 F702\n良乡校区备用教室",
            expectedLocation = "文萃楼 F702",
            span = 3
        )
    }

    @Test
    fun twoShortPeriodsKeepTheRoomAtLargeFontScale() {
        assertCourseFits(
            title = "本地调整后的计算理论与算法分析设计",
            location = "文萃楼 F702\n良乡校区",
            expectedLocation = "文萃楼 F702",
            span = 2,
            height = 35.dp
        )
    }

    private fun assertCourseFits(
        title: String,
        location: String,
        expectedLocation: String,
        span: Int,
        width: Dp = 38.dp,
        fontScale: Float = 1.3f,
        height: Dp = (span * 35).dp
    ): List<TextLayoutResult> {
        val course = ScheduleOccurrence(
            occurrenceId = "course",
            eventId = "event",
            title = title,
            description = "",
            location = location,
            startAt = LocalDateTime.of(2026, 10, 2, 9, 55),
            endAt = LocalDateTime.of(2026, 10, 2, 12, 20),
            allDay = false,
            sourceType = ScheduleSourceType.ICS_IMPORT
        )
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = fontScale)) {
                CorrectionNotebookTheme {
                    CourseGridBlock(
                        item = course,
                        span = span,
                        modifier = Modifier.width(width).height(height),
                        onClick = {}
                    )
                }
            }
        }

        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("$title\n$expectedLocation")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue("The rendered course must expose its text layout", results.isNotEmpty())
        results.forEach {
            assertFalse("Course name and address must not be clipped", it.hasVisualOverflow)
            assertEquals(TextAlign.Center, it.layoutInput.style.textAlign)
        }
        return results
    }
}
