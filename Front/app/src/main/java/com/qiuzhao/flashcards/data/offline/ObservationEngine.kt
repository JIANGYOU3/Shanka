package com.qiuzhao.flashcards.data.offline

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.qiuzhao.flashcards.domain.v25.isTerminal
import com.qiuzhao.flashcards.domain.v25.V25ProjectStatus
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The app's single polling mechanism (contract V25-D-34). While a resource is in flight — a
 * `PARSING` project or a non-terminal generation task — exactly one poll job per resource pulls
 * server truth at a backed-off cadence through the offline repository, which lands every answer
 * in Room; surfaces render from the projection flows and never poll themselves.
 *
 * There is no wait timeout: a poll outlives any screen (the "解析中 forever" defect this engine
 * replaces came from per-screen loops dying or never starting), stops the moment the projection
 * shows a terminal state, and re-arms from the next foreground reconcile.
 */
class ObservationEngine(
    private val repository: OfflineFirstV25Repository,
    private val sessionUser: () -> String?,
    private val scope: CoroutineScope,
    /** Fired once per observed active→terminal task transition (decks/today/dashboard refresh). */
    private val onTaskTerminal: suspend (taskId: String) -> Unit = {},
) {
    private val pollJobs = ConcurrentHashMap<String, Job>()
    private val polledTasks = ConcurrentHashMap<String, Job>()
    private var monitorJob: Job? = null

    /** Begin watching the projection flows; poll jobs follow whatever is in flight. Idempotent. */
    fun start() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            launch {
                repository.observeProjects().collect { projects ->
                    projects.forEach { project ->
                        val key = "project:${project.projectId}"
                        if (project.status == V25ProjectStatus.PARSING) {
                            ensurePoll(key) { pollParse(project.projectId) }
                        } else {
                            pollJobs.remove(key)?.cancel()
                        }
                    }
                }
            }
            launch {
                repository.observeAllTasks().collect { tasks ->
                    val active = tasks.filterNot { it.status.isTerminal }
                    val seen = active.mapTo(mutableSetOf()) { "task:${it.taskId}" }
                    active.forEach { task ->
                        ensurePoll("task:${task.taskId}") { pollTask(task.taskId) }
                    }
                    // A polled task that turned terminal: stop its poller and fire the hook once.
                    polledTasks.keys.toList().forEach { key ->
                        if (key !in seen) {
                            polledTasks.remove(key)
                            pollJobs.remove(key)?.cancel()
                            onTaskTerminal(key.removePrefix("task:"))
                        }
                    }
                }
            }
        }
    }

    /** Stop everything (sign-out); the next [start] re-arms from the then-current projections. */
    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        pollJobs.values.forEach { it.cancel() }
        pollJobs.clear()
        polledTasks.clear()
    }

    /** Foreground: pull fresh statuses once; the monitors re-arm per-resource pollers. */
    fun reconcile() {
        if (sessionUser() == null) return
        scope.launch { runCatching { repository.refreshProcessing() } }
    }

    /**
     * Background: pollers stop; the monitors keep watching so [reconcile] can re-arm them. The
     * polled-task key set survives the pause so a task that reached its terminal state while
     * backgrounded still fires its hook after the foreground reconcile lands the status.
     */
    fun pause() {
        pollJobs.values.forEach { it.cancel() }
        pollJobs.clear()
    }

    /** Drive [pause]/[reconcile] from the host activity's lifecycle. */
    fun attach(lifecycle: Lifecycle) {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) = reconcile()

                override fun onStop(owner: LifecycleOwner) = pause()
            },
        )
    }

    private fun ensurePoll(key: String, poll: suspend () -> Unit) {
        if (pollJobs[key]?.isActive == true) return
        val job = scope.launch { poll() }
        pollJobs[key] = job
        if (key.startsWith("task:")) polledTasks[key] = job
        // A poll loop only finishes without cancellation when the session user vanished;
        // either way the registration is stale once the job is done.
        job.invokeOnCompletion {
            pollJobs.remove(key, job)
            polledTasks.remove(key, job)
        }
    }

    /** Backed-off forced project reads until the monitor cancels on a terminal projection. */
    private suspend fun pollParse(projectId: String) {
        var delayMs = PARSE_POLL_INITIAL_MS
        while (true) {
            delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(PARSE_POLL_MAX_MS)
            if (sessionUser() == null) return
            runCatching { repository.getProject(projectId, forceRefresh = true) }
        }
    }

    /** Fixed-cadence forced task reads until the monitor cancels on a terminal projection. */
    private suspend fun pollTask(taskId: String) {
        while (true) {
            delay(TASK_POLL_INTERVAL_MS)
            if (sessionUser() == null) return
            runCatching { repository.getTask(taskId) }
        }
    }

    private companion object {
        const val PARSE_POLL_INITIAL_MS = 1_000L
        const val PARSE_POLL_MAX_MS = 5_000L
        const val TASK_POLL_INTERVAL_MS = 2_500L
    }
}
