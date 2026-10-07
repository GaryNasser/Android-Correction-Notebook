package com.github.garynasser.correction_notebook.ui.screens.home

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import java.time.LocalDate
import java.time.chrono.Chronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScheduleDatePickerFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightPickerKeepsActionsOnScreenAndRestoresTheSelectedDate() = checkPicker(false)
    @Test fun darkPickerKeepsActionsOnScreenAndRestoresTheSelectedDate() = checkPicker(true)

    @Test fun aSixRowMonthCanReachItsLastDayWithoutHidingActions() {
        val month = LocalDate.of(2026, 8, 1)
        val last = month.withDayOfMonth(31)
        var selected: LocalDate? = null
        compactContent {
            PlannerDatePickerDialog(initialDate = month.withDayOfMonth(13), onDismiss = {}, onDateSelected = { selected = it })
        }
        compose.waitForIdle()
        capturePlannerPicker("six-rows-first")
        assertPlannerPickerDatesFit(*(1..7).map(month::withDayOfMonth).toTypedArray())
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performTouchInput { swipeUp() }
        compose.onNodeWithText(pickerDayDescription(last)).assertIsDisplayed()
        compose.waitForIdle()
        capturePlannerPicker("six-rows-last")
        assertPlannerPickerDatesFit(last)
        assertPlannerPickerActionsFitScreen()
        compose.onNodeWithText(pickerDayDescription(last)).performClick()
        confirm().performClick()
        compose.runOnIdle { assertEquals(last, selected) }
    }

    @Test fun monthAndYearNavigationStillSelectTheExactDate() {
        var selected: LocalDate? = null
        compactContent {
            PlannerDatePickerDialog(initialDate = LocalDate.of(2026, 11, 13), onDismiss = {}, onDateSelected = { selected = it })
        }
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        compose.onNodeWithContentDescription(resources.getString(androidx.compose.material3.R.string.m3c_date_picker_switch_to_next_month))
            .performClick()
        compose.onNodeWithText(pickerDayDescription(LocalDate.of(2026, 12, 1))).assertIsDisplayed()
        compose.onNodeWithContentDescription(resources.getString(androidx.compose.material3.R.string.m3c_date_picker_switch_to_previous_month))
            .performClick()
        compose.onNodeWithText(pickerDayDescription(LocalDate.of(2026, 11, 1))).assertIsDisplayed()
        compose.onNodeWithContentDescription(resources.getString(androidx.compose.material3.R.string.m3c_date_picker_switch_to_year_selection))
            .performClick()
        compose.onNodeWithText(resources.getString(androidx.compose.material3.R.string.m3c_date_picker_navigate_to_year_description, "2027"))
            .performScrollTo().performClick()
        val target = LocalDate.of(2027, 11, 20)
        compose.onNodeWithText(pickerDayDescription(target)).performScrollTo().performClick()
        confirm().performClick()
        compose.runOnIdle { assertEquals(target, selected) }
    }

    @Test fun manualInputRestoresWithTheKeyboardAndDoesNotConfirmAnInvalidDate() {
        val restoration = StateRestorationTester(compose)
        var selected: LocalDate? = null
        restoration.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = true) {
                    PlannerDatePickerDialog(initialDate = LocalDate.of(2026, 11, 13), onDismiss = {}, onDateSelected = { selected = it })
                }
            }
        }
        compose.onNodeWithContentDescription("输入日期").performClick()
        val input = compose.onNode(hasSetTextAction())
        input.performScrollTo().performTextReplacement("0")
        confirm().assertIsNotEnabled()
        compose.runOnIdle { assertNull(selected) }
        val target = LocalDate.of(2026, 11, 20)
        val locale = Locale.getDefault()
        val localizedPattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.SHORT, null, Chronology.ofLocale(locale), locale
        )
        val inputPattern = Regex("[dMy]+").findAll(localizedPattern).joinToString("") {
            when (it.value.first()) { 'd' -> "dd"; 'M' -> "MM"; else -> "yyyy" }
        }
        input.performTextReplacement(target.format(DateTimeFormatter.ofPattern(inputPattern, locale)))
        input.performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("日历选择").assertIsDisplayed()
        confirm().assertIsEnabled()
        compose.onNode(hasSetTextAction()).performClick()
        compose.waitForIdle()
        capturePlannerPicker("manual-restored")
        assertPlannerPickerActionsFitScreen(requireKeyboard = true)
        confirm().performClick()
        compose.runOnIdle { assertEquals(target, selected) }
    }

    @Test fun cancellingAChangedDateKeepsTheScheduleDraftDate() {
        compactContent {
            AddScheduleDialog(initialDate = LocalDate.of(2026, 11, 13), onDismiss = {}, onAdd = {})
        }
        compose.onNodeWithText("活动标题").performTextReplacement("复习矩阵分析")
        compose.onNodeWithText("2026/11/13").performScrollTo().performClick()
        compose.onNodeWithText(pickerDayDescription(LocalDate.of(2026, 11, 20))).performClick()
        compose.onNode(hasContentDescription("取消") and hasAnyAncestor(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "选择日期")
        )).performClick()
        compose.onNodeWithText("2026/11/13").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("复习矩阵分析").performScrollTo().assertIsDisplayed()
    }

    private fun compactContent(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme { content() }
            }
        }
    }

    private fun checkPicker(dark: Boolean) {
        val restoration = StateRestorationTester(compose)
        var added: ScheduleEvent? = null
        restoration.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark) {
                    AddScheduleDialog(initialDate = LocalDate.of(2026, 11, 13), onDismiss = {}, onAdd = { added = it })
                }
            }
        }
        compose.onNodeWithText("活动标题").performTextReplacement("复习矩阵分析")
        compose.onNodeWithText("地点").performTextReplacement("文萃楼 M134")
        compose.onNodeWithText("备注").performTextReplacement("带上课堂笔记\n核对第三章例题")
        compose.onNodeWithText("备注").performClick()
        compose.onNodeWithText("2026/11/13").performScrollTo().performClick()
        compose.waitForIdle()
        capturePlannerPicker(if (dark) "dark-open" else "light-open")
        assertPlannerPickerActionsFitScreen()
        assertPlannerPickerMonthFits(LocalDate.of(2026, 11, 1))
        compose.onNodeWithText(pickerDayDescription(LocalDate.of(2026, 11, 20))).performClick()
        restoration.emulateSavedInstanceStateRestore()
        confirm().assertIsDisplayed().performClick()
        compose.onNodeWithText("2026/11/20").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("保存").performClick()
        compose.runOnIdle {
            val event = checkNotNull(added)
            assertEquals("复习矩阵分析", event.title)
            assertEquals("文萃楼 M134", event.location)
            assertEquals("带上课堂笔记\n核对第三章例题", event.description)
            assertEquals(LocalDate.of(2026, 11, 20).atTime(9, 0), event.startAt)
            assertEquals(LocalDate.of(2026, 11, 20).atTime(10, 0), event.endAt)
        }
    }

    private fun confirm() = compose.onNode(hasText("确定") or hasContentDescription("确定"))
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun pickerDayDescription(date: LocalDate): String = checkNotNull(
    DatePickerDefaults.dateFormatter().formatDate(date.toDatePickerUtcMillis(), Locale.getDefault(), forContentDescription = true)
)

internal fun assertPlannerPickerMonthFits(month: LocalDate) {
    assertPlannerPickerDatesFit(*(1..7).map(month::withDayOfMonth)
        .plus(month.withDayOfMonth(month.lengthOfMonth())).toTypedArray())
}

internal fun assertPlannerPickerDatesFit(vararg dates: LocalDate) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val info = automation.serviceInfo
    val previousFlags = info.flags
    info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
    automation.serviceInfo = info
    try {
        val root = automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root }.first { findPickerText(it, "确定") != null }
        val weekday = DateFormatSymbols.getInstance().weekdays[Calendar.SUNDAY]
        val header = checkNotNull(findPickerText(root, weekday))
        val headerBounds = Rect().also { header.getBoundsInScreen(it) }
        val confirm = Rect().also { checkNotNull(findPickerText(root, "确定")).getBoundsInScreen(it) }
        val panel = Rect().also { checkNotNull(findPickerText(root, "选择日期")).getBoundsInScreen(it) }
        dates.forEach { date ->
            val day = findPickerText(root, pickerDayDescription(date))
            assertNotNull("$date must be available in the native calendar", day)
            val bounds = Rect().also { day!!.getBoundsInScreen(it) }
            assertTrue("$date must fit below weekday labels without overlapping actions: $bounds, header=$headerBounds, confirm=$confirm",
                !bounds.isEmpty && bounds.top + 1 >= headerBounds.bottom &&
                    bounds.left >= panel.left && bounds.right <= panel.right && bounds.bottom <= panel.bottom &&
                    (bounds.bottom <= confirm.top + 1 || bounds.top + 1 >= confirm.bottom))
        }
    } finally {
        info.flags = previousFlags
        automation.serviceInfo = info
    }
}

internal fun assertPlannerPickerActionsFitScreen(requireKeyboard: Boolean = false) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val automation = instrumentation.uiAutomation
    val info = automation.serviceInfo
    val previousFlags = info.flags
    info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
    automation.serviceInfo = info
    try {
        val root = automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root }.firstOrNull { findPickerText(it, "确定") != null }
        assertNotNull("The native date picker must be open", root)
        val screen = instrumentation.targetContext.resources.displayMetrics
        val keyboard = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        if (requireKeyboard) assertNotNull("The manual input test requires a visible native keyboard", keyboard)
        val keyboardBounds = keyboard?.let { window -> Rect().also { window.getBoundsInScreen(it) } }
        listOf("确定", "取消").forEach { label ->
            val node = findPickerText(root!!, label)
            assertNotNull("$label must be present", node)
            val bounds = Rect().also { node!!.getBoundsInScreen(it) }
            assertTrue("$label must fit on screen: $bounds, screen=${screen.widthPixels}x${screen.heightPixels}",
                !bounds.isEmpty && bounds.left >= 0 && bounds.top >= 0 &&
                    bounds.right <= screen.widthPixels && bounds.bottom <= screen.heightPixels &&
                    (keyboardBounds == null || bounds.bottom <= keyboardBounds.top))
        }
    } finally {
        info.flags = previousFlags
        automation.serviceInfo = info
    }
}

private fun findPickerText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
    if (node.text?.toString() == text || node.contentDescription?.toString() == text ||
        AccessibilityNodeInfoCompat.wrap(node).paneTitle?.toString() == text) return node
    repeat(node.childCount) { index ->
        node.getChild(index)?.let { child -> findPickerText(child, text)?.let { return it } }
    }
    return null
}

internal fun capturePlannerPicker(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    Thread.sleep(300) // Wait for the native dialog and keyboard transition.
    val bitmap = instrumentation.uiAutomation.takeScreenshot()
    try {
        File(instrumentation.targetContext.getExternalFilesDir(null), "qa-planner-picker-$name.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } finally { bitmap.recycle() }
}
