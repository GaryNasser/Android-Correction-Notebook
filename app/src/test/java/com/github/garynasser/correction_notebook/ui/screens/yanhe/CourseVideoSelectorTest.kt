package com.github.garynasser.correction_notebook.ui.screens.yanhe

import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.model.yanhe.Video
import org.junit.Assert.*
import org.junit.Test

class CourseVideoSelectorTest {
    @Test fun retainsBothViewsInsteadOfSelectingOnlyOne() {
        val sources = selectCourseVideoSources(CourseSection(videos = listOf(
            Video(mainUrl = "camera.m3u8", vgaUrl = "screen.m3u8"))))
        assertEquals("camera.m3u8", sources.cameraUrl)
        assertEquals("screen.m3u8", sources.screenUrl)
    }

    @Test fun missingScreenDoesNotDuplicateCameraAsPpt() {
        val sources = selectCourseVideoSources(CourseSection(videos = listOf(Video(mainUrl = "camera.mp4"))))
        assertEquals("camera.mp4", sources.cameraUrl)
        assertNull(sources.screenUrl)
    }

    @Test fun screenOnlyRecordingDoesNotInventACamera() {
        val sources = selectCourseVideoSources(CourseSection(videos = listOf(Video(vgaUrl = "screen.mp4"))))
        assertNull(sources.cameraUrl)
        assertEquals("screen.mp4", sources.screenUrl)
    }

    @Test fun returnsEmptySourcesWhenNoRecordingExists() {
        assertEquals(CourseVideoSources(null, null), selectCourseVideoSources(CourseSection()))
    }

    @Test fun specificCameraWinsOverEarlierGenericPathAndViewsCanBeInDifferentRecords() {
        val sources = selectCourseVideoSources(CourseSection(videos = listOf(
            Video(path = "generic.mp4", vgaUrl = "screen.mp4"), Video(mainUrl = "camera.mp4"))))
        assertEquals("camera.mp4", sources.cameraUrl)
        assertEquals("screen.mp4", sources.screenUrl)
    }

    @Test fun trimsUrlsAndKeepsTheirQueries() {
        assertEquals("screen.m3u8?token=a+b", selectCourseVideoSources(CourseSection(
            videos = listOf(Video(vgaUrl = "  screen.m3u8?token=a+b  ")))).screenUrl)
    }
    @Test fun generatedPptCanFallBackToTheSameRecordingOriginalVgaStream() {
        val camera = "https://aita.yanhekt.cn/play/2/vod3/2026/09/22/67016037/0/Video1/Video1.m3u8?t=old"
        val generated = "https://aita.yanhekt.cn/play/frim_files/ppt2026/2026-09-22/output.mp4"
        assertEquals("https://aita.yanhekt.cn/play/2/vod3/2026/09/22/67016037/0/VGA/VGA.m3u8",
            originalScreenVideoUrl(camera, generated))
    }

    @Test fun fallbackDoesNotRewriteDirectVgaUrlsOrCrossPlatformDestinations() {
        val camera = "https://aita.yanhekt.cn/play/2/vod3/2026/09/22/67016037/0/Video1/Video1.m3u8"
        val ppt = "https://aita.yanhekt.cn/play/frim_files/ppt2026/output.mp4"
        assertNull(originalScreenVideoUrl(camera, camera.replace("Video1", "VGA")))
        assertNull(originalScreenVideoUrl(camera.replace("aita.yanhekt.cn", "example.com"), ppt))
        assertNull(originalScreenVideoUrl(camera, ppt.replace("https:", "http:")))
        assertNull(originalScreenVideoUrl(camera.replace("/Video1/Video1", "/other/other"), ppt))
    }

}
