package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseVideoMimeTypeTest {
    @Test
    fun detectsSupportedVideoManifestAndFileTypes() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            inferCourseVideoMimeType("https://video.example/live/playlist.M3U8?token=abc")
        )
        assertEquals(
            MimeTypes.VIDEO_MP4,
            inferCourseVideoMimeType("https://video.example/recording.mp4?download=0")
        )
        assertEquals(
            MimeTypes.VIDEO_MP4,
            inferCourseVideoMimeType("https://video.example/recording.m4v")
        )
    }

    @Test
    fun leavesUnknownSourcesForMedia3ToInspect() {
        assertNull(inferCourseVideoMimeType("https://video.example/playback/12345"))
        assertNull(inferCourseVideoMimeType("https://video.example/stream.mpd#start"))
        assertNull(inferCourseVideoMimeType(""))
    }
}
