package com.qiuzhao.flashcards.ui

/**
 * Accumulates foreground study seconds per deck for one study session, Anki-style: the clock
 * runs while the study screen is resumed, and each elapsed segment is attributed to the deck
 * of the card on screen. Today-mode sessions span decks, so [resume] with a different deck
 * settles the previous segment first; resuming the same deck is a no-op.
 *
 * [pause] settles the running segment and returns the whole-second deltas exactly once;
 * sub-second remainders stay queued so nothing accumulates rounding loss across pauses.
 * Pure and clock-free: every timestamp comes from the caller, which keeps the class testable
 * on the JVM.
 */
internal class StudyTimeAccumulator {
    private var activeDeckId: String? = null
    private var lastResumeMs: Long? = null
    private val pendingMs = LinkedHashMap<String, Long>()

    /** Starts (or continues) timing for [deckId]; a deck change settles the open segment. */
    fun resume(deckId: String, nowMs: Long) {
        val active = activeDeckId
        val last = lastResumeMs
        if (active == deckId && last != null) return
        if (active != null && last != null) settle(active, nowMs - last)
        activeDeckId = deckId
        lastResumeMs = nowMs
    }

    /** Stops timing and returns the whole-second deltas per deck; safe to call repeatedly. */
    fun pause(nowMs: Long): Map<String, Long> {
        val active = activeDeckId
        val last = lastResumeMs
        if (active != null && last != null) {
            settle(active, nowMs - last)
            activeDeckId = null
            lastResumeMs = null
        }
        val deltas = LinkedHashMap<String, Long>()
        for ((deckId, ms) in pendingMs) {
            val seconds = ms / 1_000
            if (seconds > 0) deltas[deckId] = seconds
            pendingMs[deckId] = ms - seconds * 1_000
        }
        return deltas
    }

    private fun settle(deckId: String, elapsedMs: Long) {
        if (elapsedMs <= 0) return
        pendingMs[deckId] = (pendingMs[deckId] ?: 0L) + elapsedMs
    }
}
