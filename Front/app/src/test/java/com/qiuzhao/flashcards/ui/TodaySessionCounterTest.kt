package com.qiuzhao.flashcards.ui

import com.qiuzhao.flashcards.data.remote.Rating
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Today's study screen re-derives its queue from the server on every visit, so the queue only
 * holds the cards still remaining for the day. The top-bar counter must continue the day-wide
 * progress (offset by what was completed before this visit) instead of restarting at 1/N.
 */
class TodaySessionCounterTest {

    private fun queue(vararg cardIds: String) = cardIds.map { StudyQueueEntry(it) }

    @Test
    fun `first visit of the day counts the session from one`() {
        val queue = queue("c-1", "c-2", "c-3")
        val counter = todaySessionCounter(0, queue, queue.first(), emptyMap())

        assertEquals(TodaySessionCounter(position = 1, total = 3, completed = 0), counter)
    }

    @Test
    fun `re-entering mid-day continues the day-wide count`() {
        // 30 of 90 studied: the refreshed queue holds the 60 remaining cards only.
        val queue = (1..60).map { StudyQueueEntry("c-$it") }
        val counter = todaySessionCounter(30, queue, queue.first(), emptyMap())

        assertEquals(31, counter.position)
        assertEquals(90, counter.total)
    }

    @Test
    fun `relearn visits do not inflate the total`() {
        val queue = listOf(
            StudyQueueEntry("c-1"),
            StudyQueueEntry("c-2"),
            StudyQueueEntry("c-1", relearn = true),
        )
        val counter = todaySessionCounter(10, queue, queue.last(), mapOf("c-1" to Rating.AGAIN))

        // The AGAIN card's return visit shows its original slot, and the total skips relearn entries.
        assertEquals(11, counter.position)
        assertEquals(12, counter.total)
        assertEquals(10, counter.completed)
    }

    @Test
    fun `completed offsets the session ratings for the progress bar`() {
        val queue = queue("c-1", "c-2", "c-3")
        val ratings = mapOf("c-1" to Rating.GOOD, "c-2" to Rating.AGAIN)
        val counter = todaySessionCounter(30, queue, queue.last(), ratings)

        // Only the settled (latest non-AGAIN) card counts toward the bar, on top of the day baseline.
        assertEquals(31, counter.completed)
        assertEquals(33, counter.total)
    }
}
