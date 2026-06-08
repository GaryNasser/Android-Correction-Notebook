package com.github.garynasser.correction_notebook.data.local.knowledgebase

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class FolderFileCountRow(
    val folderId: String?,
    val count: Int
)

@Dao
interface KnowledgeBaseDao {
    @Query(
        """
        SELECT * FROM kb_folder
        WHERE ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId)
          AND (:query = '' OR name LIKE '%' || :query || '%')
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun observeFolders(parentId: String?, query: String): Flow<List<KnowledgeBaseFolderEntity>>

    @Query(
        """
        SELECT * FROM kb_file
        WHERE ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId)
          AND (:query = '' OR displayName LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC, displayName COLLATE NOCASE ASC
        """
    )
    fun observeFiles(folderId: String?, query: String): Flow<List<KnowledgeBaseFileEntity>>

    @Query("SELECT * FROM kb_folder ORDER BY name COLLATE NOCASE ASC")
    fun observeAllFolders(): Flow<List<KnowledgeBaseFolderEntity>>

    @Query("SELECT * FROM kb_file ORDER BY downloadedAt DESC, updatedAt DESC LIMIT :limit")
    fun observeRecentFiles(limit: Int): Flow<List<KnowledgeBaseFileEntity>>

    @Query("SELECT folderId, COUNT(*) AS count FROM kb_file GROUP BY folderId")
    fun observeFolderFileCounts(): Flow<List<FolderFileCountRow>>

    @Query("SELECT * FROM kb_folder ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllFolders(): List<KnowledgeBaseFolderEntity>

    @Query("SELECT * FROM kb_folder WHERE id = :folderId LIMIT 1")
    suspend fun getFolderById(folderId: String): KnowledgeBaseFolderEntity?

    @Query("SELECT * FROM kb_file WHERE id = :fileId LIMIT 1")
    suspend fun getFileById(fileId: String): KnowledgeBaseFileEntity?

    @Query("SELECT * FROM kb_file ORDER BY updatedAt DESC, displayName COLLATE NOCASE ASC")
    suspend fun getAllFiles(): List<KnowledgeBaseFileEntity>

    @Query(
        """
        SELECT * FROM kb_file
        WHERE ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId)
        ORDER BY updatedAt DESC, displayName COLLATE NOCASE ASC
        """
    )
    suspend fun getFilesForFolder(folderId: String?): List<KnowledgeBaseFileEntity>

    @Query(
        """
        SELECT * FROM kb_file
        WHERE ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId)
        """
    )
    suspend fun getFilesByFolder(folderId: String?): List<KnowledgeBaseFileEntity>

    @Query(
        """
        SELECT COUNT(*) FROM kb_folder
        WHERE ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId)
        """
    )
    suspend fun countFoldersByParent(parentId: String?): Int

    @Query(
        """
        SELECT COUNT(*) FROM kb_file
        WHERE ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId)
        """
    )
    suspend fun countFilesByFolder(folderId: String?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: KnowledgeBaseFolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: KnowledgeBaseFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<KnowledgeBaseChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudySet(studySet: StudySetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(cards: List<FlashcardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizQuestions(questions: List<QuizQuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAiResultCache(cache: AiResultCacheEntity)

    @Update
    suspend fun updateFolder(folder: KnowledgeBaseFolderEntity)

    @Update
    suspend fun updateFile(file: KnowledgeBaseFileEntity)

    @Delete
    suspend fun deleteFolder(folder: KnowledgeBaseFolderEntity)

    @Delete
    suspend fun deleteFile(file: KnowledgeBaseFileEntity)

    @Query("DELETE FROM study_set WHERE id = :studySetId")
    suspend fun deleteStudySet(studySetId: String)

    @Query("UPDATE study_set SET title = :title, updatedAt = :updatedAt WHERE id = :studySetId")
    suspend fun updateStudySetTitle(studySetId: String, title: String, updatedAt: Long)

    @Query("UPDATE flashcard SET studySetId = :targetStudySetId, updatedAt = :updatedAt WHERE studySetId IN (:sourceStudySetIds)")
    suspend fun moveFlashcardsToStudySet(sourceStudySetIds: List<String>, targetStudySetId: String, updatedAt: Long)

    @Query("UPDATE flashcard SET studySetId = :targetStudySetId, updatedAt = :updatedAt WHERE id = :flashcardId")
    suspend fun moveFlashcardToStudySet(flashcardId: String, targetStudySetId: String, updatedAt: Long)

    @Query("UPDATE quiz_question SET studySetId = :targetStudySetId WHERE studySetId IN (:sourceStudySetIds)")
    suspend fun moveQuizQuestionsToStudySet(sourceStudySetIds: List<String>, targetStudySetId: String)

    @Query("UPDATE study_set SET updatedAt = :updatedAt WHERE id = :studySetId")
    suspend fun touchStudySet(studySetId: String, updatedAt: Long)

    @Query("DELETE FROM study_set WHERE id IN (:studySetIds)")
    suspend fun deleteStudySets(studySetIds: List<String>)

    @Query("DELETE FROM kb_chunk WHERE fileId = :fileId")
    suspend fun deleteChunksForFile(fileId: String)

    @Query("SELECT * FROM kb_chunk WHERE fileId = :fileId ORDER BY chunkIndex ASC")
    suspend fun getChunksForFile(fileId: String): List<KnowledgeBaseChunkEntity>

    @Query("SELECT COUNT(*) FROM kb_chunk WHERE fileId = :fileId")
    suspend fun countChunksForFile(fileId: String): Int

    @Query(
        """
        SELECT
            s.id,
            s.courseId,
            s.courseName,
            s.title,
            s.sourceType,
            s.sourceRefId,
            s.createdByAi,
            s.createdAt,
            s.updatedAt,
            (SELECT COUNT(*) FROM flashcard f WHERE f.studySetId = s.id) AS flashcardCount,
            (SELECT COUNT(*) FROM quiz_question q WHERE q.studySetId = s.id) AS quizCount,
            (SELECT COUNT(*) FROM flashcard f WHERE f.studySetId = s.id AND f.type = 'QA_FLASHCARD' AND (f.lastReviewedAt IS NULL OR f.nextReviewAt <= :now)) AS dueFlashcardCount
        FROM study_set s
        ORDER BY s.updatedAt DESC
        """
    )
    fun observeStudySetSummaries(now: Long): Flow<List<StudySetSummaryRow>>

    @Query(
        """
        SELECT
            f.id AS flashcardId,
            s.id AS studySetId,
            s.title AS studySetTitle,
            s.courseName AS courseName,
            f.type AS type,
            f.title AS title,
            f.front AS front,
            f.back AS back,
            f.explanation AS explanation,
            f.example AS example,
            f.pitfall AS pitfall,
            f.formula AS formula,
            f.tags AS tags,
            f.sourceLocation AS sourceLocation,
            f.sourceQuote AS sourceQuote,
            f.hint AS hint,
            f.difficulty AS difficulty,
            f.confidence AS confidence,
            f.createdByAi AS createdByAi,
            f.editedByUser AS editedByUser,
            f.nextReviewAt AS nextReviewAt,
            f.lastReviewedAt AS lastReviewedAt,
            f.reviewCount AS reviewCount,
            f.createdAt AS createdAt,
            f.updatedAt AS updatedAt
        FROM flashcard f
        INNER JOIN study_set s ON s.id = f.studySetId
        WHERE f.type = 'QA_FLASHCARD'
          AND (f.lastReviewedAt IS NULL OR f.nextReviewAt <= :now)
        ORDER BY f.nextReviewAt ASC, s.updatedAt DESC
        LIMIT :limit
        """
    )
    fun observeDueReviewItems(now: Long, limit: Int): Flow<List<DueReviewRow>>

    @Query(
        """
        SELECT
            f.id AS flashcardId,
            s.id AS studySetId,
            s.title AS studySetTitle,
            s.courseName AS courseName,
            f.type AS type,
            f.title AS title,
            f.front AS front,
            f.back AS back,
            f.explanation AS explanation,
            f.example AS example,
            f.pitfall AS pitfall,
            f.formula AS formula,
            f.tags AS tags,
            f.sourceLocation AS sourceLocation,
            f.sourceQuote AS sourceQuote,
            f.hint AS hint,
            f.difficulty AS difficulty,
            f.confidence AS confidence,
            f.createdByAi AS createdByAi,
            f.editedByUser AS editedByUser,
            f.nextReviewAt AS nextReviewAt,
            f.lastReviewedAt AS lastReviewedAt,
            f.reviewCount AS reviewCount,
            f.createdAt AS createdAt,
            f.updatedAt AS updatedAt
        FROM flashcard f
        INNER JOIN study_set s ON s.id = f.studySetId
        ORDER BY s.updatedAt DESC, f.reviewCount DESC, f.front COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    fun observeKnowledgeCards(limit: Int): Flow<List<DueReviewRow>>

    @Query(
        """
        SELECT
            f.id AS flashcardId,
            s.id AS studySetId,
            s.title AS studySetTitle,
            s.courseName AS courseName,
            f.type AS type,
            f.title AS title,
            f.front AS front,
            f.back AS back,
            f.explanation AS explanation,
            f.example AS example,
            f.pitfall AS pitfall,
            f.formula AS formula,
            f.tags AS tags,
            f.sourceLocation AS sourceLocation,
            f.sourceQuote AS sourceQuote,
            f.hint AS hint,
            f.difficulty AS difficulty,
            f.confidence AS confidence,
            f.createdByAi AS createdByAi,
            f.editedByUser AS editedByUser,
            f.nextReviewAt AS nextReviewAt,
            f.lastReviewedAt AS lastReviewedAt,
            f.reviewCount AS reviewCount,
            f.createdAt AS createdAt,
            f.updatedAt AS updatedAt
        FROM flashcard f
        INNER JOIN study_set s ON s.id = f.studySetId
        WHERE f.lastReviewedAt IS NOT NULL
        ORDER BY f.lastReviewedAt DESC
        LIMIT :limit
        """
    )
    fun observeReviewedCards(limit: Int): Flow<List<DueReviewRow>>

    @Query(
        """
        SELECT * FROM quiz_question
        ORDER BY studySetId ASC, question COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    fun observeQuizQuestions(limit: Int): Flow<List<QuizQuestionEntity>>

    @Query("SELECT * FROM ai_result_cache WHERE fileId = :fileId AND mode = :mode LIMIT 1")
    suspend fun getAiResultCache(fileId: String, mode: String): AiResultCacheEntity?

    @Query("SELECT * FROM flashcard WHERE id = :flashcardId LIMIT 1")
    suspend fun getFlashcardById(flashcardId: String): FlashcardEntity?

    @Update
    suspend fun updateFlashcard(card: FlashcardEntity)

    @Query("DELETE FROM flashcard WHERE id = :flashcardId")
    suspend fun deleteFlashcard(flashcardId: String)

    @Transaction
    suspend fun insertStudySetWithItems(
        studySet: StudySetEntity,
        cards: List<FlashcardEntity>,
        questions: List<QuizQuestionEntity>
    ) {
        insertStudySet(studySet)
        if (cards.isNotEmpty()) insertFlashcards(cards)
        if (questions.isNotEmpty()) insertQuizQuestions(questions)
    }

    @Transaction
    suspend fun mergeStudySets(sourceStudySetIds: List<String>, targetStudySetId: String, updatedAt: Long) {
        val sources = sourceStudySetIds.filter { it != targetStudySetId }
        if (sources.isEmpty()) return
        moveFlashcardsToStudySet(sources, targetStudySetId, updatedAt)
        moveQuizQuestionsToStudySet(sources, targetStudySetId)
        touchStudySet(targetStudySetId, updatedAt)
        deleteStudySets(sources)
    }

    @Transaction
    suspend fun insertCardsToStudySet(studySetId: String, cards: List<FlashcardEntity>, updatedAt: Long) {
        if (cards.isNotEmpty()) insertFlashcards(cards)
        touchStudySet(studySetId, updatedAt)
    }

    @Transaction
    suspend fun moveCardToStudySet(flashcardId: String, sourceStudySetId: String, targetStudySetId: String, updatedAt: Long) {
        moveFlashcardToStudySet(flashcardId, targetStudySetId, updatedAt)
        touchStudySet(sourceStudySetId, updatedAt)
        touchStudySet(targetStudySetId, updatedAt)
    }

    @Query(
        """
        SELECT * FROM kb_chunk
        WHERE (:folderIdFilter = 0 OR fileId IN (
            SELECT id FROM kb_file
            WHERE ((:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId)
        ))
          AND (:courseIdFilter = 0 OR fileId IN (
            SELECT id FROM kb_file WHERE courseId = :courseId
          ))
          AND (
            content LIKE '%' || :query || '%'
            OR keywords LIKE '%' || :query || '%'
            OR title LIKE '%' || :query || '%'
          )
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchChunks(
        query: String,
        folderId: String?,
        folderIdFilter: Boolean,
        courseId: Int?,
        courseIdFilter: Boolean,
        limit: Int
    ): List<KnowledgeBaseChunkEntity>
}
