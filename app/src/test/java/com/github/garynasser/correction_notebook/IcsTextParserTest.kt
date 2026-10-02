package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.repository.unescapeIcsText
import com.github.garynasser.correction_notebook.data.repository.readIcsText
import com.github.garynasser.correction_notebook.data.repository.unfoldIcsLines
import com.github.garynasser.correction_notebook.data.repository.parseIcsEvents
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IcsTextParserTest {
    @Test
    fun unfoldingKeepsContentSpacesAroundFoldedBoundaries() {
        assertEquals(
            listOf("LOCATION:文萃楼 M134", "SUMMARY:课程  名称"),
            unfoldIcsLines("LOCATION:文萃楼 \r\n M134\r\nSUMMARY:课程\r\n   名称\r\n")
        )
        assertEquals(listOf("DESCRIPTION:Two  words"), unfoldIcsLines("DESCRIPTION:Two \n\t words"))
    }

    @Test
    fun foldedAddressIsParsedWithItsOriginalWordSeparators() {
        val event = parseIcsEvents(unfoldIcsLines(
            "BEGIN:VEVENT\r\nDTSTART:20261002T080000\r\n" +
                "LOCATION:文萃楼 \r\n M134\\n良乡校区\r\nEND:VEVENT\r\n"
        ), "test").single()
        assertEquals("文萃楼 M134\n良乡校区", event.location)
    }

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
