package com.qiuzhao.flashcards.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the study-time accounting on the JVM: every timestamp comes from the caller, so the
 * attribution rules (per-deck segments, deck switches, idempotent pauses, sub-second
 * remainders) are exact and deterministic.
 */
class StudyTimeAccumulatorTest {

    @Test
    fun `a single deck segment settles as whole seconds`() {
        val timer = StudyTimeAccumulator()
        timer.resume("deck-a", nowMs = 0)
        assertEquals(mapOf("deck-a" to 65L), timer.pause(nowMs = 65_400))
    }

    @Test
    fun `a deck switch settles the open segment to the previous deck`() {
        val timer = StudyTimeAccumulator()
        timer.resume("deck-a", nowMs = 0)
        timer.resume("deck-b", nowMs = 30_000)
        assertEquals(
            mapOf("deck-a" to 30L, "deck-b" to 45L),
            timer.pause(nowMs = 75_000),
        )
    }

    @Test
    fun `pausing twice returns the delta exactly once`() {
        val timer = StudyTimeAccumulator()
        timer.resume("deck-a", nowMs = 0)
        assertEquals(mapOf("deck-a" to 10L), timer.pause(nowMs = 10_500))
        assertTrue(timer.pause(nowMs = 20_000).isEmpty())
    }

    @Test
    fun `sub-second remainders survive a pause and settle with the next segment`() {
        val timer = StudyTimeAccumulator()
        timer.resume("deck-a", nowMs = 0)
        assertTrue(timer.pause(nowMs = 500).isEmpty()) // 0.5s pending: 0 whole seconds
        timer.resume("deck-a", nowMs = 500)
        assertEquals(mapOf("deck-a" to 11L), timer.pause(nowMs = 11_000)) // 0.5 + 10.5 = 11 more
    }

    @Test
    fun `resuming the same deck never restarts its segment`() {
        val timer = StudyTimeAccumulator()
        timer.resume("deck-a", nowMs = 0)
        timer.resume("deck-a", nowMs = 5_000) // no-op, not a re-anchor
        assertEquals(mapOf("deck-a" to 10L), timer.pause(nowMs = 10_000))
    }

    @Test
    fun `segments accumulate across pause resume cycles`() {
        val timer = StudyTimeAccumulator()
        timer.resume("deck-a", nowMs = 0)
        assertEquals(mapOf("deck-a" to 10L), timer.pause(nowMs = 10_000))
        timer.resume("deck-a", nowMs = 10_000)
        assertEquals(mapOf("deck-a" to 10L), timer.pause(nowMs = 20_000))
    }

    @Test
    fun `a paused timer returns nothing until resumed again`() {
        val timer = StudyTimeAccumulator()
        assertTrue(timer.pause(nowMs = 1_000).isEmpty())
        timer.resume("deck-a", nowMs = 1_000)
        timer.pause(nowMs = 2_000)
        assertTrue(timer.pause(nowMs = 3_000).isEmpty())
    }
}
