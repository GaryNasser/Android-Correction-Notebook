package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.repository.unescapeIcsText
import com.github.garynasser.correction_notebook.data.repository.readIcsText
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IcsTextParserTest {
    @Test
    fun unescapeIcsTextRestoresEscapedLineBreaksAndPunctuation() {
        val text = unescapeIcsText("高数\\n地点：教室 A\\, B\\; C\\\\D")

        assertEquals("高数\n地点：教室 A, B; C\\D", text)
    }

    @Test
    fun unescapeIcsTextSupportsUppercaseLineBreakEscape() {
        val text = unescapeIcsText("第一行\\N第二行")

        assertEquals("第一行\n第二行", text)
    }

    @Test
    fun readIcsTextRemovesUtf8BomAndHonorsLimit() {
        val content = "\uFEFFBEGIN:VCALENDAR\nEND:VCALENDAR"

        assertEquals(
            "BEGIN:VCALENDAR\nEND:VCALENDAR",
            readIcsText(ByteArrayInputStream(content.toByteArray()), maxBytes = 64)
        )
        assertThrows(IllegalArgumentException::class.java) {
            readIcsText(ByteArrayInputStream(ByteArray(5)), maxBytes = 4)
        }
    }
}
