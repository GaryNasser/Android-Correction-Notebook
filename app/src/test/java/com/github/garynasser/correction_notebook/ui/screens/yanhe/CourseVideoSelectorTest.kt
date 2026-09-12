package com.github.garynasser.correction_notebook.ui.screens.yanhe

import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.model.yanhe.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseVideoSelectorTest {
    @Test
    fun selectsRequestedVideoSource() {
        val section = sectionWith(main = "camera.m3u8", screen = "screen.m3u8")

        assertEquals("camera.m3u8", selectCourseVideoUrl(section, preferScreen = false))
        assertEquals("screen.m3u8", selectCourseVideoUrl(section, preferScreen = true))
    }

    @Test
    fun fallsBackToAvailableCameraSource() {
        val section = sectionWith(main = "camera.m3u8", screen = "")

        assertEquals("camera.m3u8", selectCourseVideoUrl(section, preferScreen = true))
    }

    @Test
    fun returnsNullWhenNoSourceExists() {
        assertNull(selectCourseVideoUrl(sectionWith(main = "", screen = ""), preferScreen = false))
    }

    private fun sectionWith(main: String, screen: String) = CourseSection(
        videos = listOf(Video(mainUrl = main, vgaUrl = screen))
    )
}
