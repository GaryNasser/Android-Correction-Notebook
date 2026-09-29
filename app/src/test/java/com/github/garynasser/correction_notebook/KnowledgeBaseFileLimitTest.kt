package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.local.knowledgebase.copyKnowledgeBaseFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KnowledgeBaseFileLimitTest {
    @Test
    fun fileAtTheLimitIsCopiedCompletely() {
        val source = byteArrayOf(1, 2, 3, 4)
        val output = ByteArrayOutputStream()

        val copied = copyKnowledgeBaseFile(ByteArrayInputStream(source), output, maxBytes = 4)

        assertEquals(4L, copied)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test
    fun fileOverTheLimitIsRejected() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            copyKnowledgeBaseFile(
                input = ByteArrayInputStream(ByteArray(5)),
                output = ByteArrayOutputStream(),
                maxBytes = 4
            )
        }

        assertEquals("文件不能超过 4 字节", error.message)
    }
}
