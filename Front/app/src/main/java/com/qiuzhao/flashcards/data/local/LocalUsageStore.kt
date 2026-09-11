package com.qiuzhao.flashcards.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Device-local usage facts in `shanka-v25.db`: the study screen's foreground seconds per deck
 * and the per-deck difficulty mix read off the card projection. Unlike every other table these
 * rows have no server counterpart — V2.5 collects neither timing nor opens — so nothing here
 * ever syncs; values are this device's measurement and live as long as the cache does.
 *
 * The session user resolves through the same seam the offline repository uses; a signed-out
 * call simply drops the delta (there is no user row to attribute it to).
 */
class LocalUsageStore(
    private val database: ShankaV25Database,
    private val sessionUser: () -> String?,
) {

    /** Adds one settled session delta ([perDeckSeconds] deckId → whole seconds). */
    suspend fun addStudySeconds(perDeckSeconds: Map<String, Long>, nowMs: Long) {
        val user = sessionUser() ?: return
        val dao = database.localUsageDao()
        perDeckSeconds.forEach { (deckId, seconds) -> dao.addStudySeconds(user, deckId, seconds, nowMs) }
    }

    /** deckId → accumulated seconds. The user resolves once at collection start. */
    fun observeStudySeconds(): Flow<Map<String, Long>> = flow {
        val user = sessionUser() ?: run {
            emit(emptyMap())
            return@flow
        }
        database.localUsageDao().observeStudySeconds(user)
            .map { rows -> rows.associate { it.deckId to it.totalSeconds } }
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
}
