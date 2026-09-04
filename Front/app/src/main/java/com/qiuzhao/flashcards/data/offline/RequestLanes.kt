package com.qiuzhao.flashcards.data.offline

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Foreground/background request lanes for the offline-first data tier (replaces the removed
 * fixed 220ms process-wide pacer):
 * - The foreground lane is what a user-visible action (a rating submission, a first load)
 *   uses. It dispatches immediately on the caller's dispatcher — it can never queue behind a
 *   background refresh.
 * - The background lane runs revalidations: at most ONE concurrent network refresh, and
 *   identical concurrent GETs for the same resource collapse into a single flight whose
 *   result (or failure) is shared with every follower.
 * - Background work is cancellable as a group (page leave / sign-out / FORCE refresh).
 *
 * Both lanes share the single OkHttp stack (connection pool + dispatcher) owned by
 * [com.qiuzhao.flashcards.data.remote.http.NetworkStack]; this class only sequences who may
 * call it, it never becomes a second transport.
 */
class RequestLanes(scope: CoroutineScope) {

    private val backgroundPermits = Semaphore(1)
    private val flightMutex = Mutex()
    private val flights = ConcurrentHashMap<String, CompletableDeferred<Any?>>()
    private val backgroundJobs = ConcurrentHashMap<String, Job>()

    private val scope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    /** Immediate dispatch: the lane a user-visible write must never queue behind. */
    suspend fun <T> foreground(block: suspend () -> T): T = block()

    /**
     * Background revalidation: max one concurrent refresh app-wide, same-resource GETs are
     * single-flighted. The returned value is the shared result of the one network call.
     */
    suspend fun <T> background(resourceKey: String, block: suspend () -> T): T {
        val deferred = CompletableDeferred<Any?>()
        val follower: CompletableDeferred<Any?>? = flightMutex.withLock {
            val existing = flights[resourceKey]
            if (existing == null) {
                flights[resourceKey] = deferred
                null
            } else {
                existing
            }
        }
        if (follower != null) {
            @Suppress("UNCHECKED_CAST")
            return follower.await() as T
        }
        try {
            val result = backgroundPermits.withPermit { block() }
            deferred.complete(result)
            return result
        } catch (failure: Throwable) {
            deferred.completeExceptionally(failure)
            throw failure
        } finally {
            flightMutex.withLock { flights.remove(resourceKey) }
        }
    }

    /**
     * Fire-and-forget background revalidation (the stale-while-revalidate kick-off). The job
     * joins any existing flight for the same resource (single-flight); it never cancels the
     * leader — a newer kick finds the shared flight and waits for or reuses it.
     */
    fun launchBackground(resourceKey: String, block: suspend () -> Unit): Job {
        backgroundJobs[resourceKey]?.let { existing ->
            if (existing.isActive) return existing
        }
        val job = scope.launch {
            runCatching { background(resourceKey) { block(); Unit } }
        }
        backgroundJobs[resourceKey] = job
        job.invokeOnCompletion { backgroundJobs.remove(resourceKey, job) }
        return job
    }

    /** Cancels every in-flight background revalidation (page exit, sign-out, FORCE refresh). */
    fun cancelBackgroundWork() {
        backgroundJobs.values.forEach(Job::cancel)
        backgroundJobs.clear()
    }

    /** Observability for tests: distinct resources that currently have a flight open. */
    fun activeFlightCount(): Int = flights.size
}
