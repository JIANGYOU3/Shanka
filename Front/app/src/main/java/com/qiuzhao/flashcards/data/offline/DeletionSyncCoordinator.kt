package com.qiuzhao.flashcards.data.offline

import com.qiuzhao.flashcards.data.local.DeletionKind
import com.qiuzhao.flashcards.data.local.V25CacheStore
import com.qiuzhao.flashcards.domain.v25.V25ErrorCodes
import com.qiuzhao.flashcards.domain.v25.V25Repository
import com.qiuzhao.flashcards.domain.v25.V25Result
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Outcome of one [DeletionSyncCoordinator.syncOnce] pass. */
enum class DeletionSyncOutcome {
    /** Every due tombstone was drained (or none was due); the merged refresh ran once. */
    DRAINED,
    /** A retriable failure kept tombstones PENDING with a scheduled next attempt. */
    RETRY_SCHEDULED,
    /** A 401 paused the pass; tombstones stay PENDING until a fresh session resumes. */
    PAUSED_AUTH,
    /** No session user: nothing can be sent. */
    NO_SESSION,
}

/** A tombstone the server permanently rejected — the UI surfaces it and restores server truth. */
data class DeletionSyncFailure(val kind: String, val errorCode: String)

/**
 * Drains `deletion_outbox` strictly in createdAt order for the signed-in user, mirroring the
 * review outbox strategy:
 * - 2xx (or a NOT_FOUND replay — the target is already server-gone, the goal state holds)
 *   drops the tombstone; the merged projects/decks refresh folds the server truth;
 * - network / 429 / 5xx keep the tombstone PENDING with exponential backoff;
 * - a session-death 401 pauses the whole pass, tombstones untouched;
 * - a permanent 4xx drops the tombstone and triggers exactly ONE authoritative refresh so the
 *   server truth (the still-existing project/material) reappears locally, and publishes a
 *   [DeletionSyncFailure] for the UI to surface;
 * - when a pass deleted at least one scope, projects / decks / today plan / dashboard are
 *   revalidated once (merged refresh), never once per tombstone.
 *
 * Process-level state on purpose: the WorkManager worker and the in-process trigger converge
 * on [syncOnce] behind one mutex, so an optimistic delete and a connectivity wake can never
 * double-send the same tombstone (the fixed Idempotency-Key is the wire-level backstop).
 */
class DeletionSyncCoordinator(
    private val remote: V25Repository,
    private val cache: V25CacheStore,
    private val sessionUser: () -> String?,
    private val clock: Clock,
    private val lanes: RequestLanes,
    private val scope: CoroutineScope,
    private val onAuthoritativeRefreshNeeded: suspend () -> Unit = {},
) {

    private val mutex = Mutex()

    private val _drainedPasses = MutableStateFlow(0)

    /**
     * Increments once per drained pass after its merged refresh rewrote the Room projections.
     * Presenters re-read the (now fresh) projections on this signal.
     */
    val drainedPasses: StateFlow<Int> = _drainedPasses.asStateFlow()

    private val _lastPermanentFailure = MutableStateFlow<DeletionSyncFailure?>(null)

    /** The most recent permanent rejection, cleared by the presenter after surfacing it. */
    val lastPermanentFailure: StateFlow<DeletionSyncFailure?> = _lastPermanentFailure.asStateFlow()

    fun clearPermanentFailure() {
        _lastPermanentFailure.value = null
    }

    @Volatile
    private var pausedForAuth = false

    /** True once a session-death 401 paused syncing; a fresh login resumes via [resume]. */
    fun isPausedForAuth(): Boolean = pausedForAuth

    fun resume() {
        pausedForAuth = false
    }

    /** In-process trigger: the optimistic delete just landed, try to drain immediately. */
    fun requestSync() {
        scope.launch { runCatching { syncOnce() } }
    }

    suspend fun syncOnce(): DeletionSyncOutcome = mutex.withLock {
        val userId = sessionUser() ?: return DeletionSyncOutcome.NO_SESSION
        if (pausedForAuth) return DeletionSyncOutcome.PAUSED_AUTH

        var sent = 0
        var retryScheduled = false
        var permanentFailure = false
        while (true) {
            val next = cache.nextDueDeletion(userId, clock.millis()) ?: break
            val result = try {
                when (next.kind) {
                    DeletionKind.PROJECT ->
                        remote.deleteProject(next.projectId, next.retain, next.idempotencyKey)
                    DeletionKind.MATERIAL -> remote.deleteProjectMaterial(
                        next.projectId,
                        next.materialId ?: "",
                        next.retain,
                        next.idempotencyKey,
                    )
                    else -> V25Result.Failure(V25ErrorCodes.INVALID_RESPONSE, message = "未知删除类型")
                }
            } catch (failure: Throwable) {
                V25Result.Failure(V25ErrorCodes.NETWORK_UNAVAILABLE)
            }
            when (result) {
                is V25Result.Success -> {
                    cache.completeDeletion(userId, next.operationId)
                    sent++
                }
                is V25Result.Failure -> when {
                    isAlreadyGone(result.code) -> {
                        // The deletion already happened server-side (retry after a lost
                        // response, another device): the goal state holds — drop the tombstone.
                        cache.completeDeletion(userId, next.operationId)
                        sent++
                    }
                    classify(result.code) == FailureClass.TRANSIENT -> {
                        val attempt = next.attemptCount + 1
                        cache.retryDeletion(userId, next.operationId, attempt, clock.millis() + backoffMs(attempt), result.code)
                        retryScheduled = true
                    }
                    classify(result.code) == FailureClass.AUTH -> {
                        // Session death pauses the whole pass. The tombstone stays PENDING with
                        // the diagnostic code and no attempt penalty.
                        cache.retryDeletion(userId, next.operationId, next.attemptCount, clock.millis(), result.code)
                        pausedForAuth = true
                        return DeletionSyncOutcome.PAUSED_AUTH
                    }
                    else -> {
                        // Permanent rejection: drop the tombstone; the authoritative refresh
                        // restores the still-existing scope and the UI explains what happened.
                        cache.failDeletion(userId, next.operationId, result.code ?: "UNKNOWN")
                        _lastPermanentFailure.value = DeletionSyncFailure(next.kind, result.code ?: "UNKNOWN")
                        permanentFailure = true
                    }
                }
            }
        }
        if (permanentFailure) onAuthoritativeRefreshNeeded()
        if (sent > 0) mergedRefresh(userId)
        return when {
            retryScheduled -> DeletionSyncOutcome.RETRY_SCHEDULED
            else -> DeletionSyncOutcome.DRAINED
        }
    }

    // --- failure classification -----------------------------------------------------------------

    private enum class FailureClass { TRANSIENT, AUTH, PERMANENT }

    private fun isAlreadyGone(code: String?): Boolean =
        code == V25ErrorCodes.PROJECT_NOT_FOUND ||
            code == V25ErrorCodes.MATERIAL_NOT_FOUND ||
            code == "HTTP_404"

    private fun classify(code: String?): FailureClass = when {
        code == V25ErrorCodes.AUTH_REQUIRED || code == V25ErrorCodes.AUTH_INVALID -> FailureClass.AUTH
        code == V25ErrorCodes.NETWORK_UNAVAILABLE || code == "RATE_LIMITED" || code == "TIMEOUT" ->
            FailureClass.TRANSIENT
        code != null && code.startsWith("HTTP_5") -> FailureClass.TRANSIENT
        // 400/403/409/422 and every coded contract violation are permanent.
        else -> FailureClass.PERMANENT
    }

    /** Exponential backoff with a cap: 1s, 2s, 4s … 10min. */
    internal fun backoffMs(attempt: Int): Long =
        (1_000L shl (attempt - 1).coerceIn(0, 20)).coerceAtMost(600_000L)

    /** One merged revalidation after a drained pass: projects, decks, today plan, dashboard. */
    private suspend fun mergedRefresh(userId: String) {
        // Lane keys are user-scoped like every other flight in the data tier.
        val prefix = "$userId:"
        val jobs = listOf(
            lanes.launchBackground("$prefix${V25CacheStore.KEY_PROJECTS}") {
                val fresh = remote.listProjects()
                if (fresh is V25Result.Success) cache.replaceProjects(userId, fresh.value, clock.millis())
            },
            lanes.launchBackground("$prefix${V25CacheStore.KEY_DECKS}") {
                val fresh = remote.listDecks()
                if (fresh is V25Result.Success) cache.replaceDecks(userId, fresh.value, clock.millis())
            },
            lanes.launchBackground("$prefix${V25CacheStore.KEY_TODAY_PLAN}") {
                val fresh = remote.todayPlan()
                if (fresh is V25Result.Success) cache.replaceTodayPlan(userId, fresh.value, clock.millis())
            },
            lanes.launchBackground("$prefix${V25CacheStore.KEY_DASHBOARD}") {
                val fresh = remote.statsDashboard()
                if (fresh is V25Result.Success) cache.replaceDashboard(userId, fresh.value, clock.millis())
            },
        )
        // Signal only after every projection rewrite has landed in Room.
        jobs.forEach { it.join() }
        _drainedPasses.value += 1
    }
}
