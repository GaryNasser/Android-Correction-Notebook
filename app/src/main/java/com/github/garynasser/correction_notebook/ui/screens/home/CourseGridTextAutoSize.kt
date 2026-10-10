package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val courseWordPattern = Regex("[A-Za-z0-9]+")

internal data class CourseGridTextAutoSize(
    val minFontSize: TextUnit,
    val maxFontSize: TextUnit
) : TextAutoSize {
    private val boundsAutoSize = TextAutoSize.StepBased(minFontSize, maxFontSize, 0.5.sp)

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val fittingSize = with(boundsAutoSize) { getFontSize(constraints, text) }
        val words = courseWordPattern.findAll(text.text).map { it.range }.toList()
        if (words.isEmpty()) return fittingSize

        // Bounds-only sizing can fit the text while splitting an English word across lines.
        var candidate = fittingSize
        while (true) {
            val layout = performLayout(constraints, text, candidate)
            val splitsWord = (1 until layout.lineCount).any { line ->
                val start = layout.getLineStart(line)
                words.any { start > it.first && start <= it.last }
            }
            if (!layout.hasVisualOverflow && !splitsWord) return candidate
            // Keep the readable bounds fit if even the minimum size cannot keep a word intact.
            if (candidate <= minFontSize) return fittingSize
            candidate = (candidate.value - 0.5f).coerceAtLeast(minFontSize.value).sp
        }
    }
}
