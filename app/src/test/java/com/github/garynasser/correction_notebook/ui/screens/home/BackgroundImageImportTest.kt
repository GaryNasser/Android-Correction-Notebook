package com.github.garynasser.correction_notebook.ui.screens.home

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackgroundImageImportTest {
    @Test
    fun extensionPrefersImageMimeTypeAndFallsBackSafely() {
        assertEquals("png", backgroundImageExtension("image/png", "photo.jpg"))
        assertEquals("webp", backgroundImageExtension(null, "photo.WEBP"))
        assertEquals("img", backgroundImageExtension("application/octet-stream", "photo.invalid-extension"))
    }

    @Test
    fun copyKeepsImageBytesWithinLimit() {
        val source = byteArrayOf(1, 2, 3, 4)
        val output = ByteArrayOutputStream()

        assertEquals(4L, copyBackgroundImage(ByteArrayInputStream(source), output, maxBytes = 4L))
        assertArrayEquals(source, output.toByteArray())
    }

    @Test
    fun copyRejectsImagesOverLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            copyBackgroundImage(
                input = ByteArrayInputStream(ByteArray(5)),
                output = ByteArrayOutputStream(),
                maxBytes = 4L
            )
        }
    }
}
