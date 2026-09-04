package com.qiuzhao.flashcards.data.offline

import com.qiuzhao.flashcards.data.local.V25CacheStore
import com.qiuzhao.flashcards.domain.v25.V25ErrorCodes
import com.qiuzhao.flashcards.domain.v25.V25Rating
import com.qiuzhao.flashcards.domain.v25.V25Repository
import com.qiuzhao.flashcards.domain.v25.V25Result
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Outcome of one [ReviewSyncCoordinator.syncOnce] pass. */
enum class SyncOutcome {
    /** Every due outbox row was drained (or none was due); the merged refresh ran once. */
    DRAINED,
    /** A retriable failure kept rows PENDING with a scheduled next attempt. */
    RETRY_SCHEDULED,
    /** A 401 paused the pass; rows stay PENDING until a fresh session resumes. */
    PAUSED_AUTH,
    /** No session user: nothing can be sent. */
    NO_SESSION,
}

/**
 * Drains `review_outbox` strictly in createdAt order (the reliable global-serial strategy the
 * contract allows) for the signed-in user:
 * - 2xx (including idempotent replays) marks the row COMPLETED and stores the server's
 *   authoritative FSRS review state locally;
 * - network / 429 / 5xx keep the row PENDING with exponential backoff (`next_attempt_at`);
 * - a session-death 401 pauses the whole pass, rows untouched;
 * - a permanent 4xx marks the row FAILED with its code and triggers exactly ONE authoritative
 *   refresh — evidence stays in the table for diagnosis, no hot loop;
 * - when a pass sent at least one event, decks / today plan / dashboard are revalidated once
 *   (merged refresh), never once per card.
 *
 * The coordinator is process-level state on purpose: the WorkManager worker and the
 * in-process "online now" trigger both converge on [syncOnce] behind one mutex, so a swipe
 * and a connectivity wake can never double-send.
 */
class ReviewSyncCoordinator(
    private val remote: V25Repository,
    private val cache: V25CacheStore,
    private val sessionUser: () -> String?,
    private val clock: Clock,
    private val lanes: RequestLanes,
    private val scope: CoroutineScope,
    private val onAuthoritativeRefreshNeeded: suspend () -> Unit = {},
) {

    private val mutex = Mutex()

    @Volatile
    private var pausedForAuth = false

    /** True once a session-death 401 paused syncing; a fresh login resumes via [resume]. */
    fun isPausedForAuth(): Boolean = pausedForAuth

    fun resume() {
        pausedForAuth = false
    }

    /** In-process trigger: online right now, try to drain immediately (fire-and-forget). */
    fun requestSync() {
        scope.launch { runCatching { syncOnce() } }
    }

    suspend fun syncOnce(): SyncOutcome = mutex.withLock {
        val userId = sessionUser() ?: return SyncOutcome.NO_SESSION
        if (pausedForAuth) return SyncOutcome.PAUSED_AUTH

        var sent = 0
        var retryScheduled = false
        var permanentFailure = false
        while (true) {
            val next = cache.nextDueOutbox(userId, clock.millis()) ?: break
            val rating = runCatching { V25Rating.valueOf(next.rating) }.getOrNull()
            if (rating == null) {
                cache.failOutbox(userId, next.clientEventId, "INVALID_RATING")
                permanentFailure = true
                continue
            }
            val result = try {
                remote.rateCard(next.cardId, rating, next.clientEventId, next.idempotencyKey)
            } catch (failure: Throwable) {
                V25Result.Failure(V25ErrorCodes.NETWORK_UNAVAILABLE)
            }
            when (result) {
                is V25Result.Success -> {
                    cache.completeOutbox(userId, next.clientEventId, next.cardId, result.value.reviewState, clock.millis())
                    sent++
                }
                is V25Result.Failure -> when (classify(result.code)) {
                    FailureClass.TRANSIENT -> {
                        val attempt = next.attemptCount + 1
                        cache.retryOutbox(userId, next.clientEventId, attempt, clock.millis() + backoffMs(attempt), result.code)
                        retryScheduled = true
                    }
                    FailureClass.AUTH -> {
                        // Session death pauses the whole pass. The row stays PENDING with the
                        // diagnostic code and no attempt penalty, so a fresh session resumes it
                        // immediately instead of after a backoff.
                        cache.retryOutbox(userId, next.clientEventId, next.attemptCount, clock.millis(), result.code)
                        pausedForAuth = true
                        return SyncOutcome.PAUSED_AUTH
                    }
                    FailureClass.PERMANENT -> {
                        // Evidence stays in the row for diagnosis; it never re-enters nextDue().
                        cache.failOutbox(userId, next.clientEventId, result.code)
                        permanentFailure = true
                    }
                }
            }
        }
        if (permanentFailure) onAuthoritativeRefreshNeeded()
        if (sent > 0) mergedRefresh(userId)
        return when {
            retryScheduled -> SyncOutcome.RETRY_SCHEDULED
            else -> SyncOutcome.DRAINED
        }
    }

    // --- failure classification -----------------------------------------------------------------

    private enum class FailureClass { TRANSIENT, AUTH, PERMANENT }

    private fun classify(code: String?): FailureClass = when {
        code == V25ErrorCodes.AUTH_REQUIRED || code == V25ErrorCodes.AUTH_INVALID -> FailureClass.AUTH
        code == V25ErrorCodes.NETWORK_UNAVAILABLE || code == "RATE_LIMITED" || code == "TIMEOUT" ->
            FailureClass.TRANSIENT
        code != null && code.startsWith("HTTP_5") -> FailureClass.TRANSIENT
        // 400/403/404/409/422 and every coded contract violation are permanent.
        else -> FailureClass.PERMANENT
    }

    /** Exponential backoff with a cap: 1s, 2s, 4s … 10min. */
    internal fun backoffMs(attempt: Int): Long =
        (1_000L shl (attempt - 1).coerceIn(0, 20)).coerceAtMost(600_000L)

    /** One merged revalidation after a drained pass: decks, today plan, dashboard. */
    private suspend fun mergedRefresh(userId: String) {
        // Lane keys are user-scoped like every other flight in the data tier.
        val prefix = "$userId:"
        lanes.launchBackground("$prefix${V25CacheStore.KEY_DECKS}") {
            val fresh = remote.listDecks()
            if (fresh is V25Result.Success) cache.replaceDecks(userId, fresh.value, clock.millis())
        }
        lanes.launchBackground("$prefix${V25CacheStore.KEY_TODAY_PLAN}") {
            val fresh = remote.todayPlan()
            if (fresh is V25Result.Success) cache.replaceTodayPlan(userId, fresh.value, clock.millis())
        }
        lanes.launchBackground("$prefix${V25CacheStore.KEY_DASHBOARD}") {
            val fresh = remote.statsDashboard()
            if (fresh is V25Result.Success) cache.replaceDashboard(userId, fresh.value, clock.millis())
        }
    }
}
