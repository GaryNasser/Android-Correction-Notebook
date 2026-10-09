package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.github.garynasser.correction_notebook.MainActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class WeekDatePickerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun aCourseCreatedFromTheWeekKeepsItsOriginalAddressAndCanBeDeleted() {
        val title = "课程排版验证"
        val location = "文萃楼 M134\n本地验证第二行"
        val gridText = "$title\n文萃楼\nM134"
        compose.onNodeWithContentDescription("添加日程").performClick()
        compose.onNodeWithText("活动标题").performTextReplacement(title)
        compose.onNodeWithText("地点").performTextReplacement(location)
        compose.onNodeWithContentDescription("开始小时").performScrollTo().performTextReplacement("08")
        compose.onNodeWithContentDescription("开始分钟").performTextReplacement("00")
        compose.onNodeWithContentDescription("结束小时").performScrollTo().performTextReplacement("09")
        compose.onNodeWithContentDescription("结束分钟").performTextReplacement("35")
        compose.onNodeWithContentDescription("保存").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(gridText).fetchSemanticsNodes().size == 1 }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(gridText).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { assertFalse(it.hasVisualOverflow) }
        compose.onNodeWithText("13\n20:10").assertIsDisplayed()
        compose.onNodeWithText("首页").assertIsDisplayed()
        capturePlannerPicker("real-course-week")
        compose.onNodeWithText(gridText).performClick()
        compose.onNodeWithText(location).assertIsDisplayed()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("确定删除“$title”吗？").assertIsDisplayed()
        compose.onNodeWithText("删除").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(gridText).fetchSemanticsNodes().isEmpty() }
    }

    @Test fun theOpenDatePickerSurvivesActivityRecreationAndSelectsAMondayToSundayWeek() {
        val selected = LocalDate.now().withDayOfMonth(20)
        compose.onNodeWithContentDescription("课表操作").performClick()
        compose.onNodeWithText("选择日期").performClick()
        compose.onNode(hasText("确定") or hasContentDescription("确定")).assertExists()
        compose.onNodeWithText(pickerDayDescription(selected)).performClick()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText("确定") or hasContentDescription("确定")).assertExists()
        compose.waitForIdle()
        capturePlannerPicker("week-restored")
        assertPlannerPickerActionsFitScreen()
        compose.onNodeWithText(selected.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))).assertIsDisplayed()
        compose.onNode(hasText("确定") or hasContentDescription("确定")).performClick()
        val monday = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val format = DateTimeFormatter.ofPattern("MM/dd")
        repeat(7) { offset -> compose.onNodeWithText(monday.plusDays(offset.toLong()).format(format)).assertIsDisplayed() }
        compose.onNodeWithContentDescription("课表操作").performClick()
        compose.onNodeWithText("回到本周").performClick()
    }
}
