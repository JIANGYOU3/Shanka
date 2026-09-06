package com.qiuzhao.flashcards.ui

import com.qiuzhao.flashcards.data.remote.DeckSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the home page's honesty projections on the JVM: the greeting carries the real account
 * nickname, the streak track is a bounded projection of the server streak (never an invented
 * per-day history), and a missing dashboard shows a dash instead of a fabricated zero.
 */
class HomeProjectionTest {

    @Test
    fun `the greeting uses the real account nickname`() {
        assertEquals("小明，快来学习", homeGreeting("小明"))
        assertEquals("同学，快来学习", homeGreeting(null))
        assertEquals("同学，快来学习", homeGreeting(""))
    }

    @Test
    fun `the streak number is the server value and stays a dash while the dashboard is missing`() {
        assertEquals("5", streakNumberText(5))
        assertEquals("0", streakNumberText(0))
        assertEquals("—", streakNumberText(null))
    }

    @Test
    fun `the five flame slots project the real streak without inventing history`() {
        assertEquals(0, streakTrackFillCount(null))
        assertEquals(0, streakTrackFillCount(0))
        assertEquals(1, streakTrackFillCount(1))
        assertEquals(5, streakTrackFillCount(5))
        assertEquals(5, streakTrackFillCount(30))
        assertEquals(0, streakTrackFillCount(-3))
    }

    @Test
    fun `deck progress comes from the server deck counters`() {
        val deck = DeckSummary(id = "d", name = "n", cardCount = 10, dueCount = 4)
        assertEquals(0.6f, deckLearnedProgress(deck))
        assertEquals(0f, deckLearnedProgress(deck.copy(cardCount = 0, dueCount = 0)))
        assertEquals(1f, deckLearnedProgress(deck.copy(cardCount = 10, dueCount = 0)))
    }
}
