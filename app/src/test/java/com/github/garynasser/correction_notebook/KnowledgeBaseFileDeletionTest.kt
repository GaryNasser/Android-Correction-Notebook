package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.local.knowledgebase.commitStagedKnowledgeBaseFileDeletion
import com.github.garynasser.correction_notebook.data.local.knowledgebase.restoreStagedKnowledgeBaseFile
import com.github.garynasser.correction_notebook.data.local.knowledgebase.stageKnowledgeBaseFileForDeletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class KnowledgeBaseFileDeletionTest {
    @Test
    fun stagedDeletionCanBeRolledBack() {
        val directory = Files.createTempDirectory("kb-delete-test").toFile()
        try {
            val source = directory.resolve("notes.txt").apply { writeText("content") }

            val deletion = requireNotNull(stageKnowledgeBaseFileForDeletion(source))
            assertFalse(source.exists())

            restoreStagedKnowledgeBaseFile(deletion)

            assertTrue(source.exists())
            assertEquals("content", source.readText())
            assertFalse(File(deletion.pendingPath).exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun committedDeletionRemovesTheStagedFile() {
        val directory = Files.createTempDirectory("kb-delete-test").toFile()
        try {
            val source = directory.resolve("notes.txt").apply { writeText("content") }
            val deletion = requireNotNull(stageKnowledgeBaseFileForDeletion(source))

            commitStagedKnowledgeBaseFileDeletion(deletion)

            assertFalse(source.exists())
            assertFalse(File(deletion.pendingPath).exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
