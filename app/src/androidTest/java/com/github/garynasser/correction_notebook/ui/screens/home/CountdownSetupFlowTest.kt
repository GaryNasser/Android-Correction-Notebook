package com.github.garynasser.correction_notebook.ui.screens.home

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Parcel
import android.view.accessibility.AccessibilityWindowInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CountdownSetupFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightRecoveredDraftStartsTheChosenCountdown() = checkRestoredStart(false)
    @Test fun darkRecoveredDraftStartsTheChosenCountdown() = checkRestoredStart(true)
    @Test fun lightCompactEditorKeepsPresetsAndKeyboardActionsReachable() = checkLayout(false)
    @Test fun darkCompactEditorKeepsPresetsAndKeyboardActionsReachable() = checkLayout(true)

    @Test fun anEmptyRestoredDraftStaysEmptyAndCannotStart() {
        var confirmed: Int? = null
        val restoration = showDialog { confirmed = it }
        field("小时").performTextReplacement("")
        field("分钟").performTextReplacement("")
        restoration.emulateSavedInstanceStateRestore()
        assertField("小时", "")
        assertField("分钟", "")
        compose.onNodeWithText("至少设置 1 分钟").performScrollTo().assertIsDisplayed()
        action("开始").assertIsNotEnabled()
        field("分钟").performImeAction()
        compose.runOnIdle { assertNull(confirmed) }
        field("分钟").performTextReplacement("1")
        field("分钟").performImeAction()
        compose.runOnIdle { assertEquals(1, confirmed) }
        field("分钟").performTextClearance()
        field("分钟").performTextInput("1")
        field("分钟").performTextInput("7")
        assertField("分钟", "17")
    }

    @Test fun invalidPastesAndOverlongDurationsCannotStart() {
        var confirmed: Int? = null
        showDialog { confirmed = it }
        listOf("60", "-1", "1.5", "99999999999999").forEach {
            field("分钟").performTextReplacement(it)
            assertField("分钟", "25")
        }
        field("小时").performTextReplacement("99999999999999")
        assertField("小时", "0")
        field("小时").performTextInput("1")
        field("小时").performTextInput("2")
        assertField("小时", "12")
        field("分钟").performTextReplacement("1")
        compose.onNodeWithText("最长支持 12 小时").performScrollTo().assertIsDisplayed()
        action("开始").assertIsNotEnabled()
        field("分钟").performImeAction()
        compose.runOnIdle { assertNull(confirmed) }
        field("分钟").performTextReplacement("0")
        field("分钟").performImeAction()
        compose.runOnIdle { assertEquals(720, confirmed) }
    }

    @Test fun presetsSelectTheCorrectHoursAndMinutes() {
        var confirmed: Int? = null
        showDialog { confirmed = it }
        listOf(15, 25, 45, 60).forEach { preset ->
            compose.onNodeWithText("$preset 分钟").performScrollTo().performClick().assertIsSelected()
            assertField("小时", (preset / 60).toString())
            assertField("分钟", (preset % 60).toString())
            action("开始").performClick()
            compose.runOnIdle { assertEquals(preset, confirmed) }
        }
    }

    @Test fun cancellingARecoveredDraftDoesNotStartAndReopeningUsesDefaults() = withFixture { f ->
        var model = f.home
        val restoration = render(f, false) { model }
        openFromHome()
        editValues()
        recreate(f)
        model = f.home
        restoration.emulateSavedInstanceStateRestore()
        assertField("小时", "2")
        assertField("分钟", "17")
        action("取消").performClick()
        compose.onNodeWithText("自定义计时器").assertDoesNotExist()
        assertEquals(TimerState.Idle, f.home.timerManager.timerState.value)
        compose.onNodeWithContentDescription("学习模式").performClick()
        compose.onNodeWithText("倒计时").performScrollTo().performClick()
        assertField("小时", "0")
        assertField("分钟", "25")
        action("取消").performClick()
    }

    @Test fun theRecoveredModeChooserDoesNotStartATimerUntilAnOptionIsChosen() = withFixture { f ->
        var model = f.home
        val restoration = render(f, false) { model }
        compose.onNodeWithText("Study").performClick()
        compose.onNodeWithContentDescription("学习模式").performClick()
        recreate(f)
        model = f.home
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("选择学习模式").assertIsDisplayed()
        listOf("番茄钟", "倒计时", "正计时", "上传背景").forEach {
            compose.onNode(hasText(it) and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
        }
        assertEquals(TimerState.Idle, f.home.timerManager.timerState.value)
        action("取消").performClick()
        assertFalse(f.home.uiState.value.showModeSelector)
    }

    private fun checkRestoredStart(dark: Boolean) = withFixture { f ->
        var model = f.home
        val restoration = render(f, dark) { model }
        openFromHome()
        editValues()
        recreate(f)
        model = f.home
        restoration.emulateSavedInstanceStateRestore()
        assertField("小时", "2")
        assertField("分钟", "17")
        assertEquals(TimerState.Idle, f.home.timerManager.timerState.value)
        action("开始").assertIsEnabled().performClick()
        withContext(Dispatchers.Main) { f.home.timerManager.pause() }
        val timer = f.home.timerManager.timerState.value as TimerState.Countdown
        assertEquals(137 * 60, timer.totalSeconds)
        assertFalse(timer.isRunning)
        assertEquals(StudyMode.IMMERSIVE, f.home.uiState.value.selectedMode)
        assertEquals(ActiveTimerMode.COUNTDOWN, f.home.uiState.value.activeTimerMode)
        compose.onNodeWithText("自定义计时器").assertDoesNotExist()
        compose.onNodeWithContentDescription("继续").assertIsDisplayed()
    }

    private fun checkLayout(dark: Boolean) {
        showDialog(dark) {}
        capture(if (dark) "dark-presets" else "light-presets")
        listOf("15 分钟", "25 分钟", "45 分钟", "60 分钟").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            val layout = layouts.single()
            assertEquals("$label must stay on one line", 1, layout.lineCount)
            assertFalse("$label must not be ellipsized", layout.isLineEllipsized(0))
            assertFalse("$label must fit vertically", layout.didOverflowHeight)
            assertTrue("$label glyphs must fit: ${layout.getLineLeft(0)}..${layout.getLineRight(0)}, size=${layout.size}, paragraph=${layout.multiParagraph.width}",
                layout.getLineLeft(0) >= 0 && layout.getLineRight(0) <= layout.size.width)
        }
        capture(if (dark) "dark-presets" else "light-presets")
        field("分钟").performScrollTo().performClick().performTextReplacement("17")
        capture(if (dark) "dark-keyboard" else "light-keyboard")
        assertActionsAboveKeyboard()
        action("开始").assertIsDisplayed()
        action("取消").assertIsDisplayed()
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun action(label: String) = compose.onNode(hasText(label) or hasContentDescription(label))

    private fun assertField(label: String, value: String) = field(label)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)))

    private fun editValues() {
        field("小时").performTextReplacement("2")
        field("分钟").performTextReplacement("17")
    }

    private fun openFromHome() {
        compose.onNodeWithText("Study").performClick()
        compose.onNodeWithContentDescription("学习模式").performClick()
        compose.onNodeWithText("倒计时").performScrollTo().performClick()
        compose.onNodeWithText("自定义计时器").assertIsDisplayed()
    }

    private fun showDialog(dark: Boolean = false, onConfirm: (Int) -> Unit) =
        StateRestorationTester(compose).also { restoration ->
            restoration.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                        CustomTimerDialog(onDismiss = {}, onConfirm = onConfirm)
                    }
                }
            }
        }

    private fun render(f: HomeFormSaveFailureTest.Fixture, dark: Boolean, model: () -> HomeViewModel) =
        StateRestorationTester(compose).also { restoration ->
            restoration.setContent {
                CompositionLocalProvider(LocalAiEnabled provides false,
                    LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                        Surface(Modifier.fillMaxSize().safeDrawingPadding()) { HomeScreen(model(), f.statistics) }
                    }
                }
            }
        }

    @SuppressLint("RestrictedApi")
    private suspend fun recreate(f: HomeFormSaveFailureTest.Fixture) {
        val original = f.home
        val handle = withContext(Dispatchers.Main) {
            val parcel = Parcel.obtain()
            try {
                parcel.writeBundle(f.homeSavedState.savedStateProvider().saveState())
                parcel.setDataPosition(0)
                SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
            } finally { parcel.recycle() }
        }
        withContext(Dispatchers.Main) { f.store.clear() }
        original.viewModelScope.coroutineContext[Job]?.join()
        withContext(Dispatchers.Main) { f.create(handle) }
        f.await { !f.home.uiState.value.isRestoringStudySession && f.home.uiState.value.scheduleSections.size == 7 }
    }

    private fun withFixture(test: suspend (HomeFormSaveFailureTest.Fixture) -> Unit) = runBlocking {
        val f = HomeFormSaveFailureTest.Fixture()
        try {
            withContext(Dispatchers.Main) { f.create() }
            f.await { f.home.uiState.value.scheduleSections.size == 7 }
            withTimeout(30_000) { test(f) }
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

    private fun assertActionsAboveKeyboard() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        val flags = info.flags
        info.flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        try {
            val keyboard = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            assertNotNull("A native keyboard must be visible", keyboard)
            val keyboardBounds = Rect().also { keyboard!!.getBoundsInScreen(it) }
            val root = automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root }.firstOrNull { findText(it, "自定义计时器") != null }
            assertNotNull(root)
            listOf("自定义计时器", "开始", "取消").forEach { label ->
                val node = findText(root!!, label)
                assertNotNull("$label must be exposed in the native dialog", node)
                val bounds = Rect().also { node!!.getBoundsInScreen(it) }
                assertTrue("$label must fit above the keyboard: $bounds, keyboard=$keyboardBounds",
                    !bounds.isEmpty && bounds.top >= 0 && bounds.bottom <= keyboardBounds.top)
            }
        } finally {
            info.flags = flags
            automation.serviceInfo = info
        }
    }

    private fun findText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
        repeat(node.childCount) { index ->
            node.getChild(index)?.let { child -> findText(child, text)?.let { return it } }
        }
        return null
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-countdown-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
