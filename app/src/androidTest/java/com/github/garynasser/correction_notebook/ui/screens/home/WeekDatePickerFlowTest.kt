package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.garynasser.correction_notebook.MainActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import org.junit.Rule
import org.junit.Test

class WeekDatePickerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

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
