package com.github.garynasser.correction_notebook.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test fun lightSemanticTextPairsMeetMinimumContrast() = checkTextPairs(LightColorScheme)
    @Test fun darkSemanticTextPairsMeetMinimumContrast() = checkTextPairs(DarkColorScheme)
    @Test fun lightAccentTextIsReadableOnTheSurfacesItUses() = checkAccentText(LightColorScheme)
    @Test fun darkAccentTextIsReadableOnTheSurfacesItUses() = checkAccentText(DarkColorScheme)
    @Test fun selectedNavigationRemainsReadableInBothThemes() {
        listOf(LightColorScheme, DarkColorScheme).forEach { colors ->
            checkContrast("selected navigation label", colors.primary, colors.surface)
            checkContrast("selected navigation icon", colors.primary,
                colors.primaryContainer.copy(alpha = 0.66f).compositeOver(colors.surface))
        }
    }

    private fun checkTextPairs(c: ColorScheme) {
        listOf(
            Triple("primary", c.onPrimary, c.primary),
            Triple("secondary", c.onSecondary, c.secondary),
            Triple("tertiary", c.onTertiary, c.tertiary),
            Triple("primary container", c.onPrimaryContainer, c.primaryContainer),
            Triple("secondary container", c.onSecondaryContainer, c.secondaryContainer),
            Triple("tertiary container", c.onTertiaryContainer, c.tertiaryContainer),
            Triple("background", c.onBackground, c.background),
            Triple("surface", c.onSurface, c.surface),
            Triple("surface variant", c.onSurfaceVariant, c.surfaceVariant),
            Triple("error", c.onError, c.error),
            Triple("error container", c.onErrorContainer, c.errorContainer),
            Triple("inverse surface", c.inverseOnSurface, c.inverseSurface),
            Triple("inverse action", c.inversePrimary, c.inverseSurface)
        ).forEach { (name, foreground, background) -> checkContrast(name, foreground, background) }
    }

    private fun checkAccentText(c: ColorScheme) {
        listOf("primary" to c.primary, "secondary" to c.secondary, "tertiary" to c.tertiary).forEach { (name, color) ->
            listOf(c.surface, c.background, c.surfaceVariant).forEach { checkContrast("$name text", color, it) }
        }
    }

    private fun checkContrast(name: String, foreground: Color, background: Color) {
        val first = foreground.luminance().toDouble()
        val second = background.luminance().toDouble()
        val ratio = (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
        assertTrue("$name contrast is $ratio, expected at least 4.5", ratio >= 4.5)
    }
}
