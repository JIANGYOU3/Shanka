package com.qiuzhao.flashcards.data

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSchedulerTest {
    private val now = 1_000_000L

    @Test fun `new card good follows one day interval`() {
        val next = ReviewScheduler.next(ReviewStateEntity(cardId = 1), Rating.GOOD, now)
        assertEquals(1, next.intervalStep)
        assertEquals(now + TimeUnit.DAYS.toMillis(1), next.nextReviewAt)
    }

    @Test fun `again resets to ten minutes`() {
        val state = ReviewStateEntity(cardId = 1, intervalStep = 3, masteredCount = 4)
        val next = ReviewScheduler.next(state, Rating.AGAIN, now)
        assertEquals(0, next.intervalStep)
        assertEquals(0, next.masteredCount)
        assertEquals(now + TimeUnit.MINUTES.toMillis(10), next.nextReviewAt)
    }
}
