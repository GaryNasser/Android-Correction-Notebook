package com.github.garynasser.correction_notebook.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.components.BottomBarTab
import com.github.garynasser.correction_notebook.ui.navigation.AITutor
import com.github.garynasser.correction_notebook.ui.navigation.Home
import com.github.garynasser.correction_notebook.ui.navigation.bottomNavList
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CourseWeekPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightWeekKeepsCourseTextCompleteAndOpensTheOriginalAddress() = checkWeek(false, 1f)

    @Test fun darkWeekKeepsCourseTextCompleteAtLargeFontScale() = checkWeek(true, 1.3f)

    private fun checkWeek(dark: Boolean, fontScale: Float) = runBlocking {
        val f = HomeFormSaveFailureTest.Fixture()
        val monday = LocalDate.of(2026, 10, 5)
        val events = listOf(
            ScheduleEvent(id = "english", title = "Signals and Systems", location = "文萃楼 F702\n良乡校区",
                startAt = monday.atTime(15, 15), endAt = monday.atTime(16, 50), sourceType = ScheduleSourceType.ICS_IMPORT),
            ScheduleEvent(id = "long", title = "毛泽东思想和中国特色社会主义理论体系概论", location = "文萃楼 M134\n良乡校区备用教室",
                startAt = monday.plusDays(1).atTime(9, 55), endAt = monday.plusDays(1).atTime(12, 20), sourceType = ScheduleSourceType.SCHOOL_IMPORT),
            ScheduleEvent(id = "short", title = "导入链路验证", location = "文萃楼 M134\n本地验证第二行",
                startAt = monday.plusDays(2).atTime(9, 55), endAt = monday.plusDays(2).atTime(11, 30), sourceType = ScheduleSourceType.ICS_IMPORT),
            ScheduleEvent(id = "single", title = "数据结构与算法", location = "综教 A101\n良乡校区备用教室",
                startAt = monday.plusDays(3).atTime(8, 0), endAt = monday.plusDays(3).atTime(8, 45)),
            ScheduleEvent(id = "matrix", title = "矩阵分析", location = "综教 A101\n良乡校区",
                startAt = monday.plusDays(4).atTime(13, 20), endAt = monday.plusDays(4).atTime(14, 55), sourceType = ScheduleSourceType.SCHOOL_IMPORT)
        )
        try {
            withContext(Dispatchers.Main) { f.create() }
            f.await { f.home.uiState.value.scheduleSections.size == 7 }
            events.forEach { f.schedules.addEvent(it) }
            withContext(Dispatchers.Main) { f.home.setSelectedWeek(monday) }
            f.await { f.home.uiState.value.scheduleSections.flatMap { it.items }.size == events.size }
            compose.setContent {
                CompositionLocalProvider(LocalAiEnabled provides false,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                        // Reserve the same bottom-bar and system-inset space as MainContainer.
                        Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
                            Surface(Modifier.fillMaxWidth().navigationBarsPadding()) {
                                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    bottomNavList.filter { it.route !is AITutor }.forEach { item ->
                                        BottomBarTab(Modifier.weight(1f), item.title, item.icon,
                                            isSelected = item.route is Home, onClick = {})
                                    }
                                }
                            }
                        }) { padding ->
                            Box(Modifier.fillMaxSize().padding(padding)) { HomeScreen(f.home, f.statistics) }
                        }
                    }
                }
            }
            assertWeek(monday, events)
            capture(if (dark) "dark" else "light")

            val selected = events[2]
            compose.onNodeWithText(gridText(selected)).performClick()
            compose.onNodeWithText(selected.title).assertIsDisplayed()
            compose.onNodeWithText(selected.location).assertIsDisplayed()
            compose.onNodeWithText("关闭").performClick()
            compose.onNodeWithContentDescription("下一周").performClick()
            f.await { f.home.uiState.value.selectedDate == monday.plusWeeks(1) &&
                f.home.uiState.value.scheduleSections.flatMap { it.items }.isEmpty() }
            compose.onNodeWithText(gridText(selected)).assertDoesNotExist()
            compose.onNodeWithContentDescription("上一周").performClick()
            f.await { f.home.uiState.value.selectedDate == monday &&
                f.home.uiState.value.scheduleSections.flatMap { it.items }.size == events.size }
            assertWeek(monday, events)
        } finally {
            withContext(NonCancellable) {
                withContext(Dispatchers.Main) { f.store.clear() }
                f.scope.cancel()
                f.scope.coroutineContext[Job]?.join()
                f.files.forEach { it.delete() }
                f.database.close()
                f.knowledge.close()
                f.network.dispatcher.executorService.shutdownNow()
                f.network.connectionPool.evictAll()
            }
        }
    }

    private fun assertWeek(monday: LocalDate, events: List<ScheduleEvent>) {
        val format = DateTimeFormatter.ofPattern("MM/dd")
        repeat(7) { compose.onNodeWithText(monday.plusDays(it.toLong()).format(format)).assertIsDisplayed() }
        val times = listOf("08:00", "08:50", "09:55", "10:45", "11:35", "13:20", "14:10",
            "15:15", "16:05", "16:55", "18:30", "19:20", "20:10")
        times.forEachIndexed { index, time ->
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("${index + 1}\n$time").assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
            assertTrue(results.isNotEmpty())
            results.forEach { assertFalse("Period ${index + 1} and $time must both fit", it.hasVisualOverflow) }
        }
        events.forEach { event ->
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(gridText(event)).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
            assertTrue(results.isNotEmpty())
            results.forEach { layout ->
                assertFalse("${event.title} must fit in its course block", layout.hasVisualOverflow)
                assertEquals(gridText(event).length, layout.getLineEnd(layout.lineCount - 1))
                assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
            }
        }
    }

    private fun gridText(event: ScheduleEvent): String {
        val location = when (event.id) {
            "english" -> "文萃楼\nF702"
            "single" -> "综教 A101"
            "matrix" -> "综教\nA101"
            else -> "文萃楼\nM134"
        }
        return "${event.title}\n$location"
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-course-week-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
