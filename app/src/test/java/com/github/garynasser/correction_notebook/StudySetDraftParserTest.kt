package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.ai.StudySetDraftParser
import com.github.garynasser.correction_notebook.data.model.studyset.KnowledgeCardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySetDraftParserTest {
    @Test
    fun parsesStructuredStudySetJson() {
        val draft = StudySetDraftParser.parse(
            """
            {
              "title": "高数复习集",
              "cards": [
                {
                  "type": "QA_FLASHCARD",
                  "title": "导数定义",
                  "front": "导数的定义是什么？",
                  "back": "导数是函数增量与自变量增量比值在自变量增量趋近于 0 时的极限。",
                  "hint": "极限",
                  "difficulty": "MEDIUM",
                  "sourceLocation": "第 1 页",
                  "sourceQuote": "函数增量与自变量增量比值的极限",
                  "confidence": 0.9
                },
                {
                  "type": "KNOWLEDGE_CARD",
                  "title": "导数的几何意义",
                  "explanation": "导数表示函数图像在某点处切线的斜率。",
                  "example": "位置函数的导数表示瞬时速度。",
                  "pitfall": "不要把平均变化率直接当成导数。",
                  "formula": "f'(x)=lim Δy/Δx"
                }
              ],
              "quizQuestions": [
                {"type": "SHORT_ANSWER", "question": "导数的几何意义是什么？", "answer": "切线斜率", "explanation": "导数描述函数局部变化率"}
              ]
            }
            """.trimIndent(),
            fallbackTitle = "默认复习集"
        )

        assertEquals("高数复习集", draft.title)
        assertEquals(2, draft.cards.size)
        assertEquals(KnowledgeCardType.QA_FLASHCARD, draft.cards.first().type)
        assertEquals("导数定义", draft.cards.first().title)
        assertEquals("导数的定义是什么？", draft.cards.first().front)
        assertEquals(KnowledgeCardType.KNOWLEDGE_CARD, draft.cards[1].type)
        assertEquals("导数表示函数图像在某点处切线的斜率。", draft.cards[1].explanation)
        assertEquals(1, draft.quizQuestions.size)
        assertEquals("切线斜率", draft.quizQuestions.first().answer)
    }

    @Test
    fun plainTextDoesNotCreateLowQualityCards() {
        val draft = StudySetDraftParser.parse(
            """
            极限是微积分的基础概念。
            导数可以表示函数在一点的变化率。
            积分可以理解为累积量。
            """.trimIndent(),
            fallbackTitle = "资料复习集"
        )

        assertEquals("资料复习集", draft.title)
        assertTrue(draft.cards.isEmpty())
        assertTrue(draft.quizQuestions.isEmpty())
    }

    @Test
    fun splitsLabeledQuestionAnswerHintText() {
        val draft = StudySetDraftParser.parse(
            """
            {
              "title": "计组复习集",
              "cards": [
                {
                  "type": "QA_FLASHCARD",
                  "title": "Cache 命中率",
                  "front": "问题：Cache 命中率是什么？\n答案：命中率是访问 Cache 成功的次数占总访问次数的比例。\n提示：访问比例",
                  "back": "",
                  "hint": ""
                }
              ]
            }
            """.trimIndent(),
            fallbackTitle = "默认复习集"
        )

        val card = draft.cards.first()
        assertEquals("Cache 命中率是什么？", card.front)
        assertEquals("命中率是访问 Cache 成功的次数占总访问次数的比例。", card.back)
        assertEquals("访问比例", card.hint)
    }
}
