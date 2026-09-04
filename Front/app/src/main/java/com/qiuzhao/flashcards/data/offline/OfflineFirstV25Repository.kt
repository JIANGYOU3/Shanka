package com.qiuzhao.flashcards.data.offline

import com.qiuzhao.flashcards.data.local.V25CacheStore
import com.qiuzhao.flashcards.data.session.SessionStore
import com.qiuzhao.flashcards.data.session.loadQuietly
import com.qiuzhao.flashcards.domain.v25.V25ApiKeyStatus
import com.qiuzhao.flashcards.domain.v25.V25AuthUser
import com.qiuzhao.flashcards.domain.v25.V25AvatarKey
import com.qiuzhao.flashcards.domain.v25.V25BrowseFilter
import com.qiuzhao.flashcards.domain.v25.V25Card
import com.qiuzhao.flashcards.domain.v25.V25CardDeletionBatch
import com.qiuzhao.flashcards.domain.v25.V25CardDraft
import com.qiuzhao.flashcards.domain.v25.V25CardRewritePreview
import com.qiuzhao.flashcards.domain.v25.V25Chapter
import com.qiuzhao.flashcards.domain.v25.V25ChapterEdit
import com.qiuzhao.flashcards.domain.v25.V25DeletionPreflight
import com.qiuzhao.flashcards.domain.v25.V25ErrorCodes
import com.qiuzhao.flashcards.domain.v25.V25GenerationConfig
import com.qiuzhao.flashcards.domain.v25.V25GenerationTask
import com.qiuzhao.flashcards.domain.v25.V25ImportResult
import com.qiuzhao.flashcards.domain.v25.V25LearningProject
import com.qiuzhao.flashcards.domain.v25.V25PlanCard
import com.qiuzhao.flashcards.domain.v25.V25PreferencesPatch
import com.qiuzhao.flashcards.domain.v25.V25ProjectStudySettings
import com.qiuzhao.flashcards.domain.v25.V25ProgressSummary
import com.qiuzhao.flashcards.domain.v25.V25Rating
import com.qiuzhao.flashcards.domain.v25.V25RatingResult
import com.qiuzhao.flashcards.domain.v25.V25Repository
import com.qiuzhao.flashcards.domain.v25.V25Result
import com.qiuzhao.flashcards.domain.v25.V25ReviewCard
import com.qiuzhao.flashcards.domain.v25.V25ReviewState
import com.qiuzhao.flashcards.domain.v25.V25SampleCard
import com.qiuzhao.flashcards.domain.v25.V25StatsDashboard
import com.qiuzhao.flashcards.domain.v25.V25StudyPlan
import com.qiuzhao.flashcards.domain.v25.V25StudyPlanUpdate
import com.qiuzhao.flashcards.domain.v25.V25StudySettingsPatch
import com.qiuzhao.flashcards.domain.v25.V25TaskConfigPatch
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import com.qiuzhao.flashcards.domain.v25.V25TodayPlan
import com.qiuzhao.flashcards.domain.v25.V25UserPreferences
import java.io.InputStream
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/** Refresh policy for a cache-backed read. */
enum class RefreshPolicy {
    /** Serve cache when fresh; revalidate in the background when the soft TTL expired. */
    SOFT_TTL,
    /** User-initiated refresh: fetch synchronously, then rewrite the cache scope. */
    FORCE,
}

/** The judged state of the today-plan projection the UI can render honestly. */
sealed interface TodayPlanState {
    data class Fresh(val plan: V25TodayPlan) : TodayPlanState
    /** Cached rows exist only for older study dates (or the last fetch failed): today is unknown. */
    data class StaleNoData(val latestCachedStudyDate: LocalDate?) : TodayPlanState
    /** Nothing cached for this user yet. */
    data object Empty : TodayPlanState
}

/**
 * The offline-first V25Repository the AppViewModel consumes. Server stays authoritative for
 * every business decision (FSRS scheduling, plan computation, statistics); this class only
 * decides where a read is served from and when the server is asked again:
 *
 * - Reads are stale-while-revalidate against `shanka-v25.db`: the cache serves immediately,
 *   a background lane (max 1 concurrent, single-flight per resource) refreshes when the soft
 *   TTL expired. Default TTLs: projects/decks/cards 5min; today plan/progress/stats 60s.
 * - The today plan is keyed `user_id + study_date`; a cached plan from another study date is
 *   never served as today's queue — offline cross-day reads surface as a judged
 *   [TodayPlanState.StaleNoData], not fabricated data.
 * - A network failure never writes the cache, so the last successful snapshot survives.
 * - Every write except rating is still server-synchronous (online-only by contract); a
 *   successful write invalidates its resource so the next read refetches.
 * - [rateCard] is the offline path: the event lands in `review_outbox` inside one Room
 *   transaction that also hides the card from the local queue, then the in-process sync
 *   coordinator replays it with its FIXED client_event_id + Idempotency-Key.
 */
class OfflineFirstV25Repository(
    private val remote: V25Repository,
    private val cache: V25CacheStore,
    private val sessionStore: SessionStore,
    private val lanes: RequestLanes,
    val reviewSync: ReviewSyncCoordinator,
    private val clock: Clock,
) : V25Repository {

    private fun userId(): String? = sessionStore.loadQuietly()?.user?.userId

    private fun requireUserId(): V25Result.Failure =
        V25Result.Failure(V25ErrorCodes.AUTH_REQUIRED, message = "未登录，无法读取本地数据")

    // --- stale-while-revalidate core ------------------------------------------------------------

    private suspend fun <T : Any> cachedRead(
        resourceKey: String,
        ttlMs: Long,
        policy: RefreshPolicy,
        readCache: suspend () -> T?,
        fetch: suspend () -> V25Result<T>,
        writeCache: suspend (T) -> Unit,
    ): V25Result<T> {
        val user = userId() ?: return requireUserId()
        val now = clock.millis()
        // Lane/single-flight keys are user-scoped: two accounts must never share one flight.
        val laneKey = "$user:$resourceKey"
        val cached = readCache()
        val metadata = cache.metadata(user, resourceKey)

        // The cached lane requires BOTH data and metadata: an empty-but-written projection is
        // a legitimate cached value, while a row-less table is a first load.
        if (cached != null && metadata != null) {
            val fresh = now - metadata.fetchedAt < ttlMs
            if (policy == RefreshPolicy.SOFT_TTL) {
                if (!fresh) {
                    // Stale-while-revalidate: serve the cached value, refresh in the background.
                    lanes.launchBackground(laneKey) {
                        val refreshed = fetch()
                        if (refreshed is V25Result.Success) writeCache(refreshed.value)
                    }
                }
                return V25Result.Success(cached)
            }
            // FORCE: fetch synchronously so pull-to-refresh shows the real result.
            val forced = fetch()
            if (forced is V25Result.Success) writeCache(forced.value)
            return forced
        }
        // First load (no cache): the caller's foreground lane fetches synchronously.
        val first = lanes.background(laneKey) { fetch() }
        if (first is V25Result.Success) writeCache(first.value)
        return first
    }

    // --- reads (cached) ---------------------------------------------------------------------------

    override suspend fun listProjects(forceRefresh: Boolean): V25Result<List<V25LearningProject>> {
        if (forceRefresh) {
            val user = userId() ?: return requireUserId()
            val forced = remote.listProjects()
            if (forced is V25Result.Success) cache.replaceProjects(user, forced.value, clock.millis())
            return forced
        }
        return cachedRead(
            V25CacheStore.KEY_PROJECTS,
            TTL_LIST,
            currentPolicy,
            readCache = { userId()?.let { user -> cache.readProjects(user) } },
            fetch = { remote.listProjects() },
            writeCache = { value -> userId()?.let { user -> cache.replaceProjects(user, value, clock.millis()) } },
        )
    }

    override suspend fun getProject(projectId: String, forceRefresh: Boolean): V25Result<V25LearningProject> {
        val user = userId() ?: return requireUserId()
        val laneKey = "$user:${V25CacheStore.KEY_PROJECT}:$projectId"
        val cached = cache.readProject(user, projectId)
        // Parse polling and first loads must observe server truth synchronously: a cached
        // PARSING snapshot served for five minutes is exactly the "forever waiting" defect.
        // A failed forced fetch still falls back to the snapshot so offline re-entry renders.
        if (forceRefresh || cached == null) {
            val result = remote.getProject(projectId)
            if (result is V25Result.Success) {
                cache.replaceProject(user, result.value, clock.millis())
                return result
            }
            return cached?.let { V25Result.Success(it) } ?: result
        }
        val metadata = cache.metadata(user, V25CacheStore.KEY_PROJECTS)
        if (metadata != null && clock.millis() - metadata.fetchedAt < TTL_LIST) {
            return V25Result.Success(cached)
        }
        lanes.launchBackground(laneKey) { refreshProject(user, projectId) }
        return V25Result.Success(cached)
    }

    private suspend fun refreshProject(user: String, projectId: String) {
        val fresh = remote.getProject(projectId)
        if (fresh is V25Result.Success) cache.replaceProject(user, fresh.value, clock.millis())
    }

    override suspend fun listDecks(projectId: String?): V25Result<List<com.qiuzhao.flashcards.domain.v25.V25Deck>> =
        cachedRead(
            V25CacheStore.KEY_DECKS,
            TTL_LIST,
            currentPolicy,
            readCache = { userId()?.let { cache.readDecks(it) } },
            fetch = { remote.listDecks() },
            writeCache = { value -> userId()?.let { user -> cache.replaceDecks(user, value, clock.millis()) } },
        )

    override suspend fun listCards(deckId: String, filter: V25BrowseFilter): V25Result<List<V25Card>> =
        cachedRead(
            "${V25CacheStore.KEY_CARDS}:$deckId",
            TTL_LIST,
            currentPolicy,
            readCache = { userId()?.let { user -> cache.readDeckCards(user, deckId) } },
            fetch = { remote.listCards(deckId, filter) },
            writeCache = { value -> userId()?.let { user -> cache.replaceDeckCards(user, deckId, value, clock.millis()) } },
        )

    override suspend fun deckReviewQueue(deckId: String): V25Result<List<V25ReviewCard>> =
        cachedRead(
            "${V25CacheStore.KEY_REVIEW_QUEUE}:$deckId",
            TTL_FAST,
            currentPolicy,
            readCache = { userId()?.let { user -> cache.readDeckReviewQueue(user, deckId) } },
            fetch = { remote.deckReviewQueue(deckId) },
            writeCache = { value -> userId()?.let { user -> cache.replaceDeckReviewQueue(user, deckId, value, clock.millis()) } },
        )

    override suspend fun getStudyPlan(): V25Result<V25StudyPlan> =
        cachedRead(
            V25CacheStore.KEY_STUDY_PLAN,
            TTL_LIST,
            currentPolicy,
            readCache = { userId()?.let { cache.readStudyPlan(it) } },
            fetch = { remote.getStudyPlan() },
            writeCache = { value -> userId()?.let { user -> cache.replaceStudyPlan(user, value, clock.millis()) } },
        )

    override suspend fun todayPlan(): V25Result<V25TodayPlan> {
        val user = userId() ?: return requireUserId()
        val today = LocalDate.now(clock)
        val laneKey = "$user:${V25CacheStore.KEY_TODAY_PLAN}"
        val cached = cache.readTodayPlan(user, today)
        val metadata = cache.metadata(user, V25CacheStore.KEY_TODAY_PLAN)
        val fresh = metadata != null && clock.millis() - metadata.fetchedAt < TTL_FAST

        if (cached != null) {
            if (currentPolicy == RefreshPolicy.FORCE) {
                val forced = remote.todayPlan()
                if (forced is V25Result.Success) cache.replaceTodayPlan(user, forced.value, clock.millis())
                return forced
            }
            if (!fresh) {
                lanes.launchBackground(laneKey) {
                    val fetched = remote.todayPlan()
                    if (fetched is V25Result.Success) cache.replaceTodayPlan(user, fetched.value, clock.millis())
                }
            }
            return V25Result.Success(cached)
        }
        val fetched = lanes.background(laneKey) { remote.todayPlan() }
        return when (fetched) {
            is V25Result.Success -> {
                cache.replaceTodayPlan(user, fetched.value, clock.millis())
                fetched
            }
            is V25Result.Failure ->
                // Offline with only an old-date cache (or none): the judged empty state —
                // zero cards, not configured — instead of a fabricated today queue.
                V25Result.Success(emptyTodayPlan(today))
        }
    }

    /** The judged today-plan state for honest offline UI; never fabricates a queue. */
    suspend fun todayPlanState(): TodayPlanState {
        val user = userId() ?: return TodayPlanState.Empty
        val today = LocalDate.now(clock)
        cache.readTodayPlan(user, today)?.let { return TodayPlanState.Fresh(it) }
        return TodayPlanState.StaleNoData(cache.latestCachedStudyDate(user))
    }

    // --- Room Flow projections (cached data immediately; every scoped write re-emits) ----------------

    /** Live deck list: the merged refresh after an outbox drain re-emits new due counts here. */
    fun observeDecks(): Flow<List<com.qiuzhao.flashcards.domain.v25.V25Deck>> = flow {
        val user = userId() ?: return@flow
        cache.observeDecks(user).collect { emit(it) }
    }

    /** Live learning projects: parse-status advances and reconciles re-emit here. */
    fun observeProjects(): Flow<List<V25LearningProject>> = flow {
        val user = userId() ?: return@flow
        cache.observeProjects(user).collect { emit(it) }
    }

    /** Live single project (assembled materials+chapters); any scoped write re-emits. */
    fun observeProject(projectId: String): Flow<V25LearningProject?> = flow {
        val user = userId() ?: return@flow
        cache.observeProject(user, projectId).collect { emit(it) }
    }

    /** Live task statuses (V25-D-34): engine polls and task writes re-emit here. */
    fun observeAllTasks(): Flow<List<com.qiuzhao.flashcards.domain.v25.V25ObservedTask>> = flow {
        val user = userId() ?: return@flow
        cache.observeAllTasks(user).collect { emit(it) }
    }

    fun observeProjectTasks(projectId: String): Flow<List<com.qiuzhao.flashcards.domain.v25.V25ObservedTask>> =
        flow {
            val user = userId() ?: return@flow
            cache.observeProjectTasks(user, projectId).collect { emit(it) }
        }

    fun observeTask(taskId: String): Flow<com.qiuzhao.flashcards.domain.v25.V25ObservedTask?> = flow {
        val user = userId() ?: return@flow
        cache.observeTask(user, taskId).collect { emit(it) }
    }

    /**
     * Foreground reconcile for every in-flight processing resource (V25-D-34): one forced
     * projects pull, one forced detail pull per still-PARSING project (the list can lag the
     * finished parse) and one forced task-list pull — all landing fresh statuses in Room. The
     * observation engine's pollers re-arm from the re-emitted flows; the WorkManager
     * [com.qiuzhao.flashcards.work.ProcessingSyncWorker] backstop calls this too.
     */
    suspend fun refreshProcessing() {
        val fresh = listProjects(forceRefresh = true)
        if (fresh is V25Result.Success) {
            fresh.value.filter { it.status == com.qiuzhao.flashcards.domain.v25.V25ProjectStatus.PARSING }
                .forEach { project -> getProject(project.projectId, forceRefresh = true) }
        }
        remote.listTasks(projectId = null, status = null).alsoOnSuccess { tasks ->
            userId()?.let { user -> cache.replaceTasks(user, tasks, clock.millis()) }
        }
    }

    /** Today's still-visible plan queue (a rated card disappears the moment its transaction commits). */
    fun observeTodayPlanQueue(): Flow<List<V25Card>> = flow {
        val user = userId() ?: return@flow
        cache.observeTodayPlanCards(user, LocalDate.now(clock)).collect { emit(it) }
    }

    /** Pending outbox rows (diagnostics UI / tests). */
    fun observeOutbox(): Flow<List<com.qiuzhao.flashcards.data.local.ReviewOutboxEntity>> = flow {
        val user = userId() ?: return@flow
        cache.observeOutbox(user).collect { emit(it) }
    }

    override suspend fun projectProgress(projectId: String): V25Result<V25ProgressSummary> =
        cachedRead(
            "${V25CacheStore.KEY_PROGRESS}:$projectId",
            TTL_FAST,
            currentPolicy,
            readCache = { userId()?.let { user -> cache.readProjectProgress(user, projectId) } },
            fetch = { remote.projectProgress(projectId) },
            writeCache = { value ->
                userId()?.let { user -> cache.upsertProjectProgress(user, projectId, value, clock.millis()) }
            },
        )

    override suspend fun statsDashboard(): V25Result<V25StatsDashboard> =
        cachedRead(
            V25CacheStore.KEY_DASHBOARD,
            TTL_FAST,
            currentPolicy,
            readCache = { userId()?.let { cache.readDashboard(it) } },
            fetch = { remote.statsDashboard() },
            writeCache = { value -> userId()?.let { user -> cache.replaceDashboard(user, value, clock.millis()) } },
        )

    // --- rating: the offline outbox path -------------------------------------------------------------

    override suspend fun rateCard(
        cardId: String,
        rating: V25Rating,
        clientEventId: String?,
        idempotencyKey: String?,
    ): V25Result<V25RatingResult> {
        val user = userId() ?: return requireUserId()
        val eventId = clientEventId ?: UUID.randomUUID().toString()
        val key = idempotencyKey ?: UUID.randomUUID().toString()
        val now = clock.millis()
        return try {
            // One transaction: outbox row first, then hide the card from its local queues.
            // Failure here keeps the card on screen and reports an error — never a lost swipe.
            cache.enqueueReview(user, cardId, rating, eventId, key, now)
            reviewSync.requestSync()
            V25Result.Success(optimisticRatingResult(user, cardId))
        } catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            V25Result.Failure(
                V25ErrorCodes.INVALID_RESPONSE,
                message = "本地写入失败，评分未记录：${failure.javaClass.simpleName}",
            )
        }
    }

    private suspend fun optimisticRatingResult(user: String, cardId: String): V25RatingResult {
        val lastKnown = cache.readReviewState(user, cardId)
        return V25RatingResult(
            reviewState = lastKnown ?: V25ReviewState(state = "NEW", due = null),
            studyDate = LocalDate.now(clock),
        )
    }

    // --- session-scoped policy knob ------------------------------------------------------------------

    /**
     * The AppViewModel sets FORCE around explicit user refreshes (pull-to-refresh, post-write
     * consistency) and SOFT_TTL otherwise.
     */
    @Volatile
    var currentPolicy: RefreshPolicy = RefreshPolicy.SOFT_TTL

    /** Sign-out: stop background revalidations and pause the outbox sync, keep the isolated cache. */
    suspend fun onSignedOut() {
        lanes.cancelBackgroundWork()
    }

    /** Sign-in: a fresh session may resume outbox syncing paused by a 401. */
    fun onSignedIn() {
        reviewSync.resume()
        reviewSync.requestSync()
    }

    // --- everything else stays server-synchronous ----------------------------------------------------

    override suspend fun getAuthUser(): V25Result<V25AuthUser> = remote.getAuthUser()
    override suspend fun updateAuthUser(username: String?, avatarKey: V25AvatarKey?): V25Result<V25AuthUser> =
        remote.updateAuthUser(username, avatarKey)

    override suspend fun logout(): V25Result<Unit> {
        val result = remote.logout()
        onSignedOut()
        return result
    }

    override suspend fun getPreferences(): V25Result<V25UserPreferences> = remote.getPreferences()
    override suspend fun updatePreferences(patch: V25PreferencesPatch): V25Result<V25UserPreferences> =
        remote.updatePreferences(patch).alsoOnSuccess { userId()?.let { cache.invalidate(it, V25CacheStore.KEY_STUDY_PLAN) } }

    override suspend fun setCurrentProject(projectId: String?): V25Result<V25UserPreferences> =
        remote.setCurrentProject(projectId).alsoOnSuccess {
            userId()?.let { user ->
                cache.invalidate(user, V25CacheStore.KEY_STUDY_PLAN)
                cache.invalidate(user, V25CacheStore.KEY_TODAY_PLAN)
            }
        }

    /**
     * Two-step creation, step one (contract V25-D-29): the JSON call only creates the named
     * EMPTY project; materials attach afterwards through the materials endpoints.
     */
    override suspend fun createProject(name: String, idempotencyKey: String?): V25Result<V25LearningProject> =
        remote.createProject(name, idempotencyKey).alsoOnSuccess { project ->
            userId()?.let { user -> cache.replaceProject(user, project, clock.millis()) }
        }

    override suspend fun addProjectMaterialPdf(
        projectId: String,
        fileName: String,
        content: InputStream,
        idempotencyKey: String?,
    ): V25Result<com.qiuzhao.flashcards.domain.v25.V25Material> =
        remote.addProjectMaterialPdf(projectId, fileName, content, idempotencyKey).alsoOnSuccess {
            // Any material add resets the chapter confirmation server-side; drop the cached
            // project so the next read observes the re-aggregated status.
            userId()?.let { user -> cache.invalidate(user, V25CacheStore.KEY_PROJECTS) }
        }

    override suspend fun addProjectMaterialText(
        projectId: String,
        name: String,
        content: String,
        idempotencyKey: String?,
    ): V25Result<com.qiuzhao.flashcards.domain.v25.V25Material> =
        remote.addProjectMaterialText(projectId, name, content, idempotencyKey).alsoOnSuccess {
            userId()?.let { user -> cache.invalidate(user, V25CacheStore.KEY_PROJECTS) }
        }

    override suspend fun listProjectMaterials(projectId: String): V25Result<List<com.qiuzhao.flashcards.domain.v25.V25Material>> =
        remote.listProjectMaterials(projectId)

    override suspend fun deleteProjectMaterial(
        projectId: String,
        materialId: String,
        retainCards: Boolean,
        idempotencyKey: String?,
    ): V25Result<V25LearningProject> =
        remote.deleteProjectMaterial(projectId, materialId, retainCards, idempotencyKey).alsoOnSuccess {
            // The response carries the re-aggregated project (EMPTY when the last material
            // went away); fold it in and refresh the derived lists.
            userId()?.let { user -> cache.replaceProject(user, it, clock.millis()) }
        }.alsoOnSuccess {
            userId()?.let { user ->
                cache.invalidate(user, V25CacheStore.KEY_DECKS)
                cache.invalidate(user, V25CacheStore.KEY_TODAY_PLAN)
            }
        }

    override suspend fun replaceProjectMaterialPdf(
        projectId: String,
        materialId: String,
        fileName: String,
        content: InputStream,
        idempotencyKey: String?,
    ): V25Result<com.qiuzhao.flashcards.domain.v25.V25Material> =
        remote.replaceProjectMaterialPdf(projectId, materialId, fileName, content, idempotencyKey).alsoOnSuccess {
            userId()?.let { user -> cache.invalidate(user, V25CacheStore.KEY_PROJECTS) }
        }

    override suspend fun renameProject(projectId: String, name: String): V25Result<V25LearningProject> =
        remote.renameProject(projectId, name).alsoOnSuccess {
            userId()?.let { user -> cache.replaceProject(user, it, clock.millis()) }
        }

    override suspend fun deleteProject(
        projectId: String,
        retainDecks: Boolean,
        idempotencyKey: String?,
    ): V25Result<Unit> =
        remote.deleteProject(projectId, retainDecks, idempotencyKey).alsoOnSuccess {
            userId()?.let { user ->
                cache.invalidate(user, V25CacheStore.KEY_PROJECTS)
                cache.invalidate(user, V25CacheStore.KEY_DECKS)
                cache.invalidate(user, V25CacheStore.KEY_TODAY_PLAN)
                cache.deleteTasksOf(user, projectId)
            }
        }

    override suspend fun getProjectDeletionPreflight(
        projectId: String,
        retainDecks: Boolean,
        allowCancel: Boolean,
    ): V25Result<V25DeletionPreflight> = remote.getProjectDeletionPreflight(projectId, retainDecks, allowCancel)

    override suspend fun updateChapter(
        projectId: String,
        chapterId: String,
        edit: V25ChapterEdit,
    ): V25Result<V25Chapter> =
        remote.updateChapter(projectId, chapterId, edit).alsoOnSuccess {
            userId()?.let { user -> cache.invalidate(user, V25CacheStore.KEY_PROJECTS) }
        }

    override suspend fun deleteChapter(projectId: String, chapterId: String, deleteCards: Boolean): V25Result<Unit> =
        remote.deleteChapter(projectId, chapterId, deleteCards).alsoOnSuccess {
            userId()?.let { user -> cache.invalidate(user, V25CacheStore.KEY_PROJECTS) }
        }

    override suspend fun confirmChapters(projectId: String): V25Result<V25LearningProject> =
        remote.confirmChapters(projectId).alsoOnSuccess {
            userId()?.let { user -> cache.replaceProject(user, it, clock.millis()) }
        }

    override suspend fun getStudySettings(projectId: String): V25Result<V25ProjectStudySettings> =
        remote.getStudySettings(projectId)

    override suspend fun updateStudySettings(
        projectId: String,
        patch: V25StudySettingsPatch,
    ): V25Result<V25ProjectStudySettings> = remote.updateStudySettings(projectId, patch)

    // Every task-returning call lands the light status projection (V25-D-34) so the Room flows
    // — not the caller — are the single place a status advance becomes observable.

    override suspend fun createTask(
        projectId: String,
        deckId: String,
        chapterIds: List<String>,
        config: V25GenerationConfig,
    ): V25Result<V25GenerationTask> = remote.createTask(projectId, deckId, chapterIds, config)
        .alsoOnSuccess { task -> userId()?.let { user -> cache.upsertTask(user, task, clock.millis()) } }

    override suspend fun listTasks(projectId: String?, status: V25TaskStatus?): V25Result<List<V25GenerationTask>> =
        remote.listTasks(projectId, status).alsoOnSuccess { tasks ->
            // The list payload is the authority: full-user scope replace keeps deleted
            // projects' rows from lingering.
            userId()?.let { user -> cache.replaceTasks(user, tasks, clock.millis()) }
        }

    override suspend fun getTask(taskId: String): V25Result<V25GenerationTask> =
        remote.getTask(taskId).alsoOnSuccess { task ->
            userId()?.let { user -> cache.upsertTask(user, task, clock.millis()) }
        }

    override suspend fun updateTaskConfig(taskId: String, patch: V25TaskConfigPatch): V25Result<V25GenerationTask> =
        remote.updateTaskConfig(taskId, patch).alsoOnSuccess { task ->
            userId()?.let { user -> cache.upsertTask(user, task, clock.millis()) }
        }

    override suspend fun generateSamples(taskId: String): V25Result<List<V25SampleCard>> =
        remote.generateSamples(taskId)

    override suspend fun startTask(taskId: String): V25Result<V25GenerationTask> = remote
        .startTask(taskId)
        .alsoOnSuccess { task ->
            userId()?.let { user ->
                cache.upsertTask(user, task, clock.millis())
                cache.invalidate(user, V25CacheStore.KEY_DECKS)
            }
        }

    override suspend fun abandonTask(taskId: String): V25Result<V25GenerationTask> =
        remote.abandonTask(taskId).alsoOnSuccess { task ->
            userId()?.let { user -> cache.upsertTask(user, task, clock.millis()) }
        }

    override suspend fun retryTask(taskId: String): V25Result<V25GenerationTask> =
        remote.retryTask(taskId).alsoOnSuccess { task ->
            userId()?.let { user -> cache.upsertTask(user, task, clock.millis()) }
        }

    override suspend fun deleteTask(taskId: String, deleteGeneratedCards: Boolean): V25Result<Unit> =
        remote.deleteTask(taskId, deleteGeneratedCards).alsoOnSuccess {
            userId()?.let { user -> cache.invalidate(user, V25CacheStore.KEY_DECKS) }
        }

    override suspend fun createDeck(
        name: String,
        projectId: String?,
        idempotencyKey: String?,
    ): V25Result<com.qiuzhao.flashcards.domain.v25.V25Deck> =
        remote.createDeck(name, projectId, idempotencyKey).alsoOnSuccess {
            userId()?.let { user -> cache.invalidate(user, V25CacheStore.KEY_DECKS) }
        }

    override suspend fun getDeck(deckId: String): V25Result<com.qiuzhao.flashcards.domain.v25.V25Deck> =
        remote.getDeck(deckId)

    override suspend fun attachDeckToProject(
        projectId: String,
        deckId: String,
        idempotencyKey: String?,
    ): V25Result<com.qiuzhao.flashcards.domain.v25.V25Deck> =
        remote.attachDeckToProject(projectId, deckId, idempotencyKey).alsoOnSuccess {
            userId()?.let { user ->
                cache.invalidate(user, V25CacheStore.KEY_DECKS)
                cache.invalidate(user, V25CacheStore.KEY_STUDY_PLAN)
            }
        }

    override suspend fun renameDeck(deckId: String, name: String): V25Result<com.qiuzhao.flashcards.domain.v25.V25Deck> =
        remote.renameDeck(deckId, name).alsoOnSuccess {
            userId()?.let { user -> cache.invalidate(user, V25CacheStore.KEY_DECKS) }
        }

    override suspend fun deleteDeck(deckId: String, idempotencyKey: String?): V25Result<Unit> =
        remote.deleteDeck(deckId, idempotencyKey)
            .alsoOnSuccess { userId()?.let { user -> cache.invalidate(user, V25CacheStore.KEY_DECKS) } }

    override suspend fun getDeckDeletionPreflight(deckId: String, allowCancel: Boolean): V25Result<V25DeletionPreflight> =
        remote.getDeckDeletionPreflight(deckId, allowCancel)

    override suspend fun importCards(
        deckId: String,
        drafts: List<V25CardDraft>,
        idempotencyKey: String?,
    ): V25Result<List<V25ImportResult>> =
        remote.importCards(deckId, drafts, idempotencyKey).alsoOnSuccess {
            userId()?.let { user -> cache.invalidate(user, "${V25CacheStore.KEY_CARDS}:$deckId") }
        }

    override suspend fun updateCard(cardId: String, front: String, back: String): V25Result<V25Card> =
        remote.updateCard(cardId, front, back)

    override suspend fun deleteCard(cardId: String): V25Result<V25CardDeletionBatch> = remote.deleteCard(cardId)

    override suspend fun pendingDeletionBatches(): V25Result<List<V25CardDeletionBatch>> =
        remote.pendingDeletionBatches()

    override suspend fun undoDeletionBatch(deleteBatchId: String): V25Result<Unit> = remote.undoDeletionBatch(deleteBatchId)

    override suspend fun createRewritePreview(cardId: String, customRequirements: String?): V25Result<V25CardRewritePreview> =
        remote.createRewritePreview(cardId, customRequirements)

    override suspend fun applyRewritePreview(cardId: String, rewriteId: String): V25Result<V25Card> =
        remote.applyRewritePreview(cardId, rewriteId)

    override suspend fun cancelRewritePreview(cardId: String, rewriteId: String): V25Result<Unit> =
        remote.cancelRewritePreview(cardId, rewriteId)

    override suspend fun updateStudyPlan(
        plan: V25StudyPlanUpdate,
        idempotencyKey: String?,
    ): V25Result<V25StudyPlan> =
        remote.updateStudyPlan(plan, idempotencyKey).alsoOnSuccess {
            userId()?.let { user ->
                cache.replaceStudyPlan(user, it, clock.millis())
                cache.invalidate(user, V25CacheStore.KEY_TODAY_PLAN)
                cache.invalidate(user, V25CacheStore.KEY_DECKS)
            }
        }

    override suspend fun studyPlanBacklog(offset: Int, limit: Int): V25Result<List<V25PlanCard>> =
        remote.studyPlanBacklog(offset, limit)

    override suspend fun apiKeyStatus(): V25Result<V25ApiKeyStatus> = remote.apiKeyStatus()
    override suspend fun saveApiKey(apiKey: String): V25Result<V25ApiKeyStatus> = remote.saveApiKey(apiKey)

    // --- helpers ---------------------------------------------------------------------------------------

    private fun emptyTodayPlan(today: LocalDate) = V25TodayPlan(
        learningTimezone = clock.zone.id,
        studyDate = today,
        currentProject = null,
        dailyGoal = 0,
        completedCount = 0,
        dueCount = 0,
        planRemaining = 0,
        backlogCount = 0,
        cards = emptyList(),
        dailyNewGoal = 0,
        dailyReviewGoal = 0,
        newCompletedCount = 0,
        reviewCompletedCount = 0,
        newRemainingCount = 0,
        reviewRemainingCount = 0,
        coreTargetCount = 0,
        planConfigured = false,
        selectedDeckIds = emptyList(),
    )

    private inline fun <T> V25Result<T>.alsoOnSuccess(block: (T) -> Unit): V25Result<T> {
        if (this is V25Result.Success) block(value)
        return this
    }

    private companion object {
        /** Soft TTLs: project/deck/card lists 5min; today/progress/stats 60s. */
        const val TTL_LIST = 5 * 60 * 1_000L
        const val TTL_FAST = 60 * 1_000L
    }
}
