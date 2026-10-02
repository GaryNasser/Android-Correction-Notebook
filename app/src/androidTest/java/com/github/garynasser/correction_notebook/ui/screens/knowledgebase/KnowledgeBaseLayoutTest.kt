package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KnowledgeBaseLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allTabLabelsFitOnOneLineAtNarrowWidthAndLargeFont() {
        val selected = mutableListOf<Int>()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) {
                CorrectionNotebookTheme {
                    Box(Modifier.width(320.dp)) {
                        KnowledgeBaseTabs(0) { selected += it }
                    }
                }
            }
        }
        listOf("文件管理", "知识空间", "BITShare").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach {
                assertEquals(1, it.lineCount)
                assertFalse("Tab label must not be clipped: $label", it.hasVisualOverflow)
            }
            compose.onNodeWithText(label).performClick()
        }
        compose.runOnIdle { assertEquals(listOf(0, 1, 2), selected) }
    }

    @Test
    fun emptyFolderHasCompactLayoutAndWorkingActions() {
        var imports = 0
        var folders = 0
        lateinit var density: Density
        compose.setContent {
            density = Density(LocalDensity.current.density, 1.3f)
            CompositionLocalProvider(LocalDensity provides density) {
                CorrectionNotebookTheme {
                    Box(Modifier.width(320.dp).testTag("empty")) {
                        EmptyStateCard(
                            title = "目录是空的",
                            description = "尚未添加文件。",
                            icon = Icons.Default.Folder,
                            primaryActionText = "导入资料",
                            onPrimaryAction = { imports++ },
                            secondaryActionText = "新建文件夹",
                            onSecondaryAction = { folders++ }
                        )
                    }
                }
            }
        }
        assertTrue(compose.onNodeWithTag("empty").fetchSemanticsNode().boundsInRoot.height <=
            with(density) { 156.dp.toPx() } + 1f)
        compose.onNodeWithText("导入资料").performClick()
        compose.onNodeWithText("新建文件夹").performClick()
        compose.runOnIdle {
            assertEquals(1, imports)
            assertEquals(1, folders)
        }
    }
}
