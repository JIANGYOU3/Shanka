package com.qiuzhao.flashcards.ui

import com.qiuzhao.flashcards.data.remote.DeckSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the project aggregation on the JVM: every project metric is the real sum of its decks
 * (there is no project-statistics endpoint), and the page never invents today's review, learning
 * time, question-type distribution, streak or open count.
 */
class ProjectAggregateTest {

    private fun deck(
        id: String,
        cards: Int,
        mastered: Int,
        due: Int,
        reviews: Int,
    ) = DeckSummary(
        id = id,
        name = "卡组$id",
        cardCount = cards,
        masteredCards = mastered,
        dueCount = due,
        reviewCount = reviews,
    )

    @Test
    fun `the project aggregate sums every deck's real counts`() {
        val aggregate = projectDeckAggregate(
            listOf(
                deck("d1", cards = 30, mastered = 10, due = 5, reviews = 40),
                deck("d2", cards = 12, mastered = 5, due = 3, reviews = 22),
            ),
        )

        assertEquals(42, aggregate.cardCount)
        assertEquals(15, aggregate.masteredCount)
        assertEquals(8, aggregate.dueCount)
        assertEquals(62, aggregate.reviewCount)
    }

    @Test
    fun `an empty project aggregates to honest zeros`() {
        val aggregate = projectDeckAggregate(emptyList())
        assertEquals(0, aggregate.cardCount)
        assertEquals(0, aggregate.masteredCount)
        assertEquals(0, aggregate.dueCount)
        assertEquals(0, aggregate.reviewCount)
    }
}
