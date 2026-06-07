package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.repository.unescapeIcsText
import org.junit.Assert.assertEquals
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
}
