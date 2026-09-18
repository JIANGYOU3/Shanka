package com.qiuzhao.flashcards.data.local

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** One deck's device-measured activity of a single study date (the 今日 tab's row). */
data class DeckDailyActivity(
    val deckId: String,
    val studyDate: String,
    val reviewedCount: Int,
    val studySeconds: Long,
)

/**
 * Device-local usage facts in `shanka-v25.db`: the per-day review counts and seconds and the
 * per-deck difficulty mix read off the card projection — the 学习数据 "今日" tab's source.
 * Lifetime study duration is server truth since V25-D-37 (study sessions aggregate there), so
 * these rows are this device's per-day measurement only and never sync.
 *
 * The session user resolves through the same seam the offline repository uses; a signed-out
 * call simply drops the delta (there is no user row to attribute it to).
 */
class LocalUsageStore(
    private val database: ShankaV25Database,
    private val sessionUser: () -> String?,
) {

    /**
     * Adds one settled session delta ([perDeckSeconds] deckId → whole seconds) to today's
     * per-day rows. Lifetime accumulation is server truth (study sessions, V25-D-37); these
     * rows only keep the 今日 tab's device-local measurement.
     */
    suspend fun addTodayStudySeconds(perDeckSeconds: Map<String, Long>, nowMs: Long) {
        val user = sessionUser() ?: return
        val dao = database.localUsageDao()
        perDeckSeconds.forEach { (deckId, seconds) ->
            dao.addDailyActivity(user, deckId, studyDate(nowMs), reviewedCount = 0, seconds = seconds, nowMs = nowMs)
        }
    }

    /** Counts one rated card toward [deckId]'s device-local measurement of [nowMs]'s date. */
    suspend fun addDeckReview(deckId: String, nowMs: Long) {
        val user = sessionUser() ?: return
        database.localUsageDao().addDailyActivity(
            user, deckId, studyDate(nowMs), reviewedCount = 1, seconds = 0L, nowMs = nowMs,
        )
    }

    /**
     * deckId → today's device-local activity. The study date resolves once at collection start
     * (the same seam-level snapshot as the session user), so the map re-emits on every write
     * but a session that crosses midnight keeps showing the day it started in.
     */
    fun observeDeckDailyActivity(nowMs: Long = System.currentTimeMillis()): Flow<Map<String, DeckDailyActivity>> = flow {
        val user = sessionUser() ?: run {
            emit(emptyMap())
            return@flow
        }
        val today = studyDate(nowMs)
        database.localUsageDao().observeDailyActivity(user, today)
            .map { rows ->
                rows.associate {
                    it.deckId to DeckDailyActivity(it.deckId, it.studyDate, it.reviewedCount, it.studySeconds)
                }
            }
            .collect { emit(it) }
    }

    /** Difficulty-tier name (`BASIC`/`UNDERSTANDING`/`DEEP_QUESTION`) → card count of [deckId]. */
    fun observeDeckDifficultyCounts(deckId: String): Flow<Map<String, Int>> = flow {
        val user = sessionUser() ?: run {
            emit(emptyMap())
            return@flow
        }
        database.localUsageDao().observeDeckDifficultyCounts(user, deckId)
            .map { rows ->
                rows.mapNotNull { row -> row.targetDifficulty?.let { it to row.cardCount } }.toMap()
            }
            .collect { emit(it) }
    }

    private fun studyDate(nowMs: Long): String =
        Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
