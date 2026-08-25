package com.github.garynasser.correction_notebook.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScheduleTextTest {
    @Test
    fun gridLocationTextKeepsOnlyTheFirstAddressLine() {
        val rawLocation = """
            文萃楼 F602
            良乡校区 备用教室
        """.trimIndent()

        assertEquals("文萃楼 F602", rawLocation.toGridLocationText())
    }

    @Test
    fun gridLocationTextKeepsSpacesForNaturalWrapping() {
        val rawLocation = "  游泳馆   浅水区   南侧  "

        assertEquals("游泳馆 浅水区 南侧", rawLocation.toGridLocationText())
    }
}
