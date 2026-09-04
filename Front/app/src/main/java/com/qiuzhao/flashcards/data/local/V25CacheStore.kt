package com.qiuzhao.flashcards.data.local

import androidx.room.withTransaction
import com.qiuzhao.flashcards.domain.v25.V25Card
import com.qiuzhao.flashcards.domain.v25.V25Chapter
import com.qiuzhao.flashcards.domain.v25.V25DailyActivity
import com.qiuzhao.flashcards.domain.v25.V25Deck
import com.qiuzhao.flashcards.domain.v25.V25GenerationTask
import com.qiuzhao.flashcards.domain.v25.V25InternalStage
import com.qiuzhao.flashcards.domain.v25.V25LearningProject
import com.qiuzhao.flashcards.domain.v25.V25Material
import com.qiuzhao.flashcards.domain.v25.V25ObservedTask
import com.qiuzhao.flashcards.domain.v25.V25PlanCard
import com.qiuzhao.flashcards.domain.v25.V25ProgressSummary
import com.qiuzhao.flashcards.domain.v25.V25Rating
import com.qiuzhao.flashcards.domain.v25.V25ReviewCard
import com.qiuzhao.flashcards.domain.v25.V25ReviewState
import com.qiuzhao.flashcards.domain.v25.V25StatsDashboard
import com.qiuzhao.flashcards.domain.v25.V25StudyPlan
import com.qiuzhao.flashcards.domain.v25.V25TodayPlan
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Transactional read/write surface over `shanka-v25.db`. Scope rules:
 * - A list refresh replaces exactly the scope it owns (the user's projects, one deck's cards,
 *   one deck's queue, one study date's plan) inside a single Room transaction.
 * - A network failure never calls a writer here, so the last successful cache is never cleared.
 * - `cache_metadata` rows are written in the same transaction as the data they describe.
 */
/** Shared JSON codec for the two list-typed cache columns (typed projection, not raw payloads). */
private val cacheJson = Json

class V25CacheStore(private val db: ShankaV25Database) {
    private val projectDao = db.projectDao()
    private val taskDao = db.taskDao()
    private val deckDao = db.deckDao()
    private val cardDao = db.cardDao()
    private val queueDao = db.reviewQueueDao()
    private val studyPlanDao = db.studyPlanDao()
    private val todayPlanDao = db.todayPlanDao()
    private val progressDao = db.progressDao()
    private val dashboardDao = db.dashboardDao()
    private val metadataDao = db.cacheMetadataDao()
    private val outboxDao = db.reviewOutboxDao()

    // --- projects -------------------------------------------------------------------------------

    suspend fun replaceProjects(userId: String, projects: List<V25LearningProject>, now: Long) {
        db.withTransaction {
            projectDao.deleteProjects(userId)
            projectDao.deleteMaterials(userId)
            projectDao.deleteChapters(userId)
            projectDao.insertProjects(projects.map { it.toEntity(userId) })
            projectDao.insertMaterials(projects.flatMap { p -> p.materials.toEntities(userId) })
            projectDao.insertChapters(
                projects.flatMap { p -> p.chapters.toEntities(userId, p.projectId) },
            )
            metadataDao.upsert(
                CacheMetadataEntity(
                    userId = userId,
                    resourceKey = KEY_PROJECTS,
                    serverVersion = null,
                    serverUpdatedAt = projects.maxOfOrNull { it.updatedAt.toEpochMilli() },
                    fetchedAt = now,
                    schemaVersion = ShankaV25Database.CACHE_SCHEMA_VERSION,
                ),
            )
        }
    }

    /** Single-project scope: detail refreshes replace only that project's projection. */
    suspend fun replaceProject(userId: String, project: V25LearningProject, now: Long) {
        db.withTransaction {
            projectDao.deleteProject(userId, project.projectId)
            projectDao.deleteMaterialsOf(userId, project.projectId)
            projectDao.deleteChaptersOf(userId, project.projectId)
            projectDao.insertProjects(listOf(project.toEntity(userId)))
            projectDao.insertMaterials(project.materials.toEntities(userId))
            projectDao.insertChapters(project.chapters.toEntities(userId, project.projectId))
        }
    }

    // --- generation tasks (V25-D-34 observation projection) --------------------------------------

    /** Full-user scope replace: the task list payload is the authority for every project's rows. */
    suspend fun replaceTasks(userId: String, tasks: List<V25GenerationTask>, now: Long) {
        db.withTransaction {
            taskDao.deleteTasks(userId)
            taskDao.insertTasks(tasks.map { it.toEntity(userId) })
        }
    }

    /** Landing spot for single-task payloads (create/get/start/abandon/retry/config). */
    suspend fun upsertTask(userId: String, task: V25GenerationTask, now: Long) {
        taskDao.insertTasks(listOf(task.toEntity(userId)))
    }

    suspend fun deleteTasksOf(userId: String, projectId: String) {
        taskDao.deleteTasksOf(userId, projectId)
    }

    suspend fun readTask(userId: String, taskId: String): V25ObservedTask? =
        taskDao.getTask(userId, taskId)?.toObservedDomain()

    fun observeAllTasks(userId: String): Flow<List<V25ObservedTask>> =
        taskDao.observeAllTasks(userId).map { rows -> rows.map { it.toObservedDomain() } }

    fun observeProjectTasks(userId: String, projectId: String): Flow<List<V25ObservedTask>> =
        taskDao.observeProjectTasks(userId, projectId).map { rows -> rows.map { it.toObservedDomain() } }

    fun observeTask(userId: String, taskId: String): Flow<V25ObservedTask?> =
        taskDao.observeTask(userId, taskId).map { it?.toObservedDomain() }

    suspend fun readProjects(userId: String): List<V25LearningProject> =
        projectDao.getProjectList(userId).map { row -> readProjectParts(userId, row) }

    suspend fun readProject(userId: String, projectId: String): V25LearningProject? {
        val row = projectDao.getProject(userId, projectId) ?: return null
        return readProjectParts(userId, row)
    }

    /** Assembles the domain project from its three projection tables in one consistent read. */
    private suspend fun readProjectParts(userId: String, row: ProjectEntity): V25LearningProject {
        val materials = projectDao.getMaterials(userId, row.projectId).map { it.toDomain() }
        val chapters = projectDao.getChapters(userId, row.projectId)
        return row.toDomain(materials, chapters)
    }

    suspend fun metadata(userId: String, resourceKey: String): CacheMetadataEntity? =
        metadataDao.get(userId, resourceKey)

    suspend fun markFetched(userId: String, resourceKey: String, now: Long, serverUpdatedAt: Long? = null) {
        metadataDao.upsert(
            CacheMetadataEntity(
                userId = userId,
                resourceKey = resourceKey,
                serverVersion = null,
                serverUpdatedAt = serverUpdatedAt,
                fetchedAt = now,
                schemaVersion = ShankaV25Database.CACHE_SCHEMA_VERSION,
            ),
        )
    }

    suspend fun invalidate(userId: String, resourceKey: String) = metadataDao.invalidate(userId, resourceKey)

    // --- decks ----------------------------------------------------------------------------------

    suspend fun replaceDecks(userId: String, decks: List<V25Deck>, now: Long) {
        db.withTransaction {
            deckDao.deleteDecks(userId)
            deckDao.insertDecks(decks.map { it.toEntity(userId) })
            markFetched(userId, KEY_DECKS, now)
        }
    }

    suspend fun readDecks(userId: String): List<V25Deck> = deckDao.getDecks(userId).map { it.toDomain() }

    // --- cards (one deck's scope) ------------------------------------------------------------------

    suspend fun replaceDeckCards(userId: String, deckId: String, cards: List<V25Card>, now: Long) {
        db.withTransaction {
            cardDao.deleteDeckCards(userId, deckId)
            cardDao.insertCards(cards.map { it.toEntity(userId) })
            markFetched(userId, "$KEY_CARDS:$deckId", now)
        }
    }

    suspend fun readDeckCards(userId: String, deckId: String): List<V25Card> =
        cardDao.getDeckCards(userId, deckId).map { it.toDomain() }

    // --- deck review queue -------------------------------------------------------------------------

    suspend fun replaceDeckReviewQueue(userId: String, deckId: String, queue: List<V25ReviewCard>, now: Long) {
        db.withTransaction {
            queueDao.deleteQueue(userId, deckId)
            cardDao.insertCards(queue.map { it.card.toEntity(userId) })
            cardDao.upsertReviewStates(
                queue.mapNotNull { item ->
                    item.reviewState?.toEntity(userId, item.card.cardId, now)
                },
            )
            queueDao.insertQueue(
                queue.mapIndexed { index, item ->
                    ReviewQueueItemEntity(userId, deckId, index, item.card.cardId)
                },
            )
            markFetched(userId, "$KEY_REVIEW_QUEUE:$deckId", now)
        }
    }

    suspend fun readDeckReviewQueue(userId: String, deckId: String): List<V25ReviewCard> =
        queueDao.getDeckQueueWithState(userId, deckId).mapNotNull { row ->
            row.card?.let { card ->
                V25ReviewCard(card = card.toDomain(), reviewState = row.reviewState?.toDomain())
            }
        }

    // --- study plan --------------------------------------------------------------------------------

    suspend fun replaceStudyPlan(userId: String, plan: V25StudyPlan, now: Long) {
        db.withTransaction {
            studyPlanDao.upsert(plan.toEntity(userId))
            markFetched(userId, KEY_STUDY_PLAN, now)
        }
    }

    suspend fun readStudyPlan(userId: String): V25StudyPlan? = studyPlanDao.getStudyPlan(userId)?.toDomain()

    // --- today plan (user_id + study_date scope) ----------------------------------------------------

    suspend fun replaceTodayPlan(userId: String, plan: V25TodayPlan, now: Long) {
        val studyDate = plan.studyDate.toString()
        db.withTransaction {
            todayPlanDao.deleteOtherDates(userId, studyDate)
            todayPlanDao.deleteOtherDateCards(userId, studyDate)
            todayPlanDao.insertPlan(plan.toEntity(userId))
            todayPlanDao.insertCards(plan.cards.mapIndexed { index, pc -> pc.toEntity(userId, studyDate, index) })
            cardDao.insertCards(plan.cards.map { it.card.toEntity(userId) })
            cardDao.upsertReviewStates(
                plan.cards.mapNotNull { pc -> pc.reviewState?.toEntity(userId, pc.card.cardId, now) },
            )
            markFetched(userId, KEY_TODAY_PLAN, now)
        }
    }

    /** Only the exact [studyDate] row is ever served; another date is a judged miss, not data. */
    suspend fun readTodayPlan(userId: String, studyDate: LocalDate): V25TodayPlan? {
        val date = studyDate.toString()
        val plan = todayPlanDao.getTodayPlan(userId, date) ?: return null
        val cards = todayPlanDao.getVisiblePlanCards(userId, date).mapNotNull { row ->
            row.card?.let { card -> row.item.toPlanCard(card.toDomain(), row.reviewState?.toDomain()) }
        }
        return plan.toDomain(cards)
    }

    suspend fun latestCachedStudyDate(userId: String): LocalDate? =
        todayPlanDao.latestStudyDate(userId)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    // --- project progress -----------------------------------------------------------------------------

    suspend fun upsertProjectProgress(userId: String, projectId: String, progress: V25ProgressSummary, now: Long) {
        db.withTransaction {
            progressDao.upsert(progress.toEntity(userId, projectId))
            markFetched(userId, "$KEY_PROGRESS:$projectId", now)
        }
    }

    suspend fun readProjectProgress(userId: String, projectId: String): V25ProgressSummary? =
        progressDao.get(userId, projectId)?.toDomain(projectId)

    // --- dashboard --------------------------------------------------------------------------------------

    suspend fun replaceDashboard(userId: String, dashboard: V25StatsDashboard, now: Long) {
        db.withTransaction {
            dashboardDao.upsert(dashboard.toEntity(userId, now))
            markFetched(userId, KEY_DASHBOARD, now, serverUpdatedAt = dashboard.updatedAt?.toEpochMilli())
        }
    }

    suspend fun readDashboard(userId: String): V25StatsDashboard? = dashboardDao.get(userId)?.toDomain()

    // --- review states / outbox -------------------------------------------------------------------------

    suspend fun upsertReviewStates(userId: String, states: List<Pair<String, V25ReviewState>>, now: Long) {
        cardDao.upsertReviewStates(states.mapNotNull { (cardId, state) -> state.toEntity(userId, cardId, now) })
    }

    suspend fun readReviewState(userId: String, cardId: String): V25ReviewState? =
        cardDao.getReviewState(userId, cardId)?.toDomain()

    /**
     * The swipe transaction: the outbox row lands first; only after that commit does the card
     * disappear from its queues. A transaction failure leaves both untouched.
     */
    suspend fun enqueueReview(
        userId: String,
        cardId: String,
        rating: V25Rating,
        clientEventId: String,
        idempotencyKey: String,
        now: Long,
    ) {
        db.withTransaction {
            outboxDao.insert(
                ReviewOutboxEntity(
                    userId = userId,
                    clientEventId = clientEventId,
                    cardId = cardId,
                    rating = rating.name,
                    idempotencyKey = idempotencyKey,
                    createdAt = now,
                    status = OutboxStatus.PENDING,
                    attemptCount = 0,
                    nextAttemptAt = now,
                    lastErrorCode = null,
                ),
            )
            queueDao.removeFromQueue(userId, cardId)
            queueDao.hideFromTodayPlan(userId, cardId)
        }
    }

    // --- outbox reads / transitions (used by the sync coordinator) ---------------------------------

    suspend fun nextDueOutbox(userId: String, now: Long): ReviewOutboxEntity? =
        outboxDao.nextDue(userId, now)

    suspend fun allOutbox(userId: String): List<ReviewOutboxEntity> = outboxDao.allForUser(userId)

    fun observeOutbox(userId: String) = outboxDao.observeAll(userId)

    fun observePendingCount(userId: String) = outboxDao.observePendingCount(userId)

    // --- Room Flow projections (immediate cached emission, re-emits on every scoped write) -----------

    fun observeDecks(userId: String): Flow<List<V25Deck>> =
        deckDao.observeDecks(userId).map { rows -> rows.map { it.toDomain() } }

    /**
     * Live learning projects: the row flow triggers a consistent re-read of files/chapters
     * (replaceProjects writes all three in one transaction), so a parse-status advance or a
     * background reconcile re-emits the assembled domain model without screen-driven polling.
     */
    fun observeProjects(userId: String): Flow<List<V25LearningProject>> =
        projectDao.observeProjectList(userId).map { readProjects(userId) }

    /** Live single project: the same assembled read, re-emitted on any scoped write. */
    fun observeProject(userId: String, projectId: String): Flow<V25LearningProject?> =
        projectDao.observeProjectRow(userId, projectId).map { row ->
            row?.let { readProjectParts(userId, it) }
        }

    /**
     * Today's visible (not yet rated away) plan queue; a write re-emits the new order. The JOIN
     * projection also watches `cards`/`review_states`, so a completed sync re-emits here too.
     */
    fun observeTodayPlanCards(userId: String, studyDate: LocalDate): Flow<List<V25Card>> =
        todayPlanDao.observeVisiblePlanCards(userId, studyDate.toString()).map { rows ->
            rows.mapNotNull { it.card?.toDomain() }
        }

    /** 2xx or idempotent replay: done, and the server review state becomes the local fact. */
    suspend fun completeOutbox(
        userId: String,
        clientEventId: String,
        cardId: String,
        reviewState: V25ReviewState,
        now: Long,
    ) {
        db.withTransaction {
            outboxDao.markCompleted(userId, clientEventId)
            cardDao.upsertReviewStates(listOf(reviewState.toEntity(userId, cardId, now)))
        }
    }

    suspend fun retryOutbox(
        userId: String,
        clientEventId: String,
        attemptCount: Int,
        nextAttemptAt: Long,
        errorCode: String?,
    ) {
        outboxDao.scheduleRetry(userId, clientEventId, attemptCount, nextAttemptAt, errorCode)
    }

    suspend fun failOutbox(userId: String, clientEventId: String, errorCode: String) {
        outboxDao.markFailed(userId, clientEventId, errorCode)
    }

    companion object {
        const val KEY_PROJECTS = "projects"
        const val KEY_PROJECT = "project"
        const val KEY_DECKS = "decks"
        const val KEY_CARDS = "cards"
        const val KEY_REVIEW_QUEUE = "review_queue"
        const val KEY_STUDY_PLAN = "study_plan"
        const val KEY_TODAY_PLAN = "today_plan"
        const val KEY_PROGRESS = "progress"
        const val KEY_DASHBOARD = "dashboard"
    }
}

// --- entity ↔ domain mappers -----------------------------------------------------------------------

private fun V25LearningProject.toEntity(userId: String) = ProjectEntity(
    userId = userId,
    projectId = projectId,
    name = name,
    status = status.name,
    chapterCount = chapterCount,
    deckCount = deckCount,
    taskCount = taskCount,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    version = version,
)

private fun List<V25Material>.toEntities(userId: String) = map { material ->
    ProjectMaterialEntity(
        userId = userId,
        materialId = material.materialId,
        projectId = material.projectId,
        type = material.type.name,
        name = material.name,
        status = material.status.name,
        errorCode = material.errorCode,
        sizeBytes = material.sizeBytes,
        charCount = material.charCount,
        createdAt = material.createdAt.toEpochMilli(),
    )
}

private fun List<V25Chapter>.toEntities(userId: String, projectId: String) =
    mapIndexed { index, chapter ->
        ProjectChapterEntity(
            userId = userId,
            chapterId = chapter.id,
            projectId = projectId,
            materialId = chapter.materialId,
            name = chapter.name,
            startPage = chapter.startPage,
            endPage = chapter.endPage,
            position = index,
        )
    }

private fun ProjectMaterialEntity.toDomain() = V25Material(
    materialId = materialId,
    projectId = projectId,
    type = enumValueOf(type),
    name = name,
    status = enumValueOf(status),
    errorCode = errorCode,
    sizeBytes = sizeBytes,
    charCount = charCount,
    chapter = null,
    createdAt = Instant.ofEpochMilli(createdAt),
)

private fun ProjectEntity.toDomain(materials: List<V25Material>, chapters: List<ProjectChapterEntity>) =
    V25LearningProject(
        projectId = projectId,
        name = name,
        materials = materials,
        status = enumValueOf(status),
        chapterCount = chapterCount,
        deckCount = deckCount,
        taskCount = taskCount,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        version = version,
        chapters = chapters.map {
            V25Chapter(it.chapterId, it.materialId, it.name, it.startPage, it.endPage)
        },
    )

private fun V25GenerationTask.toEntity(userId: String) = GenerationTaskEntity(
    userId = userId,
    taskId = taskId,
    projectId = projectId,
    deckId = deckId,
    retryOfTaskId = retryOfTaskId,
    status = status.name,
    internalStage = internalStage?.name,
    generatedCardCount = generatedCardCount,
    errorCode = errorCode,
    failureStage = failureStage,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

private fun GenerationTaskEntity.toObservedDomain() = V25ObservedTask(
    taskId = taskId,
    projectId = projectId,
    deckId = deckId,
    retryOfTaskId = retryOfTaskId,
    status = enumValueOf(status),
    internalStage = internalStage?.let { enumValueOf<V25InternalStage>(it) },
    generatedCardCount = generatedCardCount,
    errorCode = errorCode,
    failureStage = failureStage,
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

private fun V25Deck.toEntity(userId: String) = DeckEntity(
    userId = userId,
    deckId = deckId,
    name = name,
    projectId = projectId,
    cardCount = cardCount,
    dueCount = dueCount,
    masteredCards = masteredCards,
    reviewCount = reviewCount,
    masteryRatio = masteryRatio?.toDouble(),
    notStartedCount = notStartedCount,
    learningCount = learningCount,
    relearningCount = relearningCount,
    consolidatingCount = consolidatingCount,
    masteredCount = masteredCount,
    reviewEventCount = reviewEventCount,
    lastStudiedAt = lastStudiedAt?.toEpochMilli(),
)

private fun DeckEntity.toDomain() = V25Deck(
    deckId = deckId,
    name = name,
    projectId = projectId,
    cardCount = cardCount,
    dueCount = dueCount,
    masteredCards = masteredCards,
    reviewCount = reviewCount,
    masteryRatio = masteryRatio?.toFloat(),
    notStartedCount = notStartedCount,
    learningCount = learningCount,
    relearningCount = relearningCount,
    consolidatingCount = consolidatingCount,
    masteredCount = masteredCount,
    reviewEventCount = reviewEventCount,
    lastStudiedAt = lastStudiedAt?.let(Instant::ofEpochMilli),
)

private fun V25Card.toEntity(userId: String) = CardEntity(
    userId = userId,
    cardId = cardId,
    deckId = deckId,
    front = front,
    back = back,
    cardType = cardType.name,
    position = position,
    targetDifficulty = targetDifficulty?.name,
    chapterId = chapterId,
    sourceTaskId = sourceTaskId,
    publicationState = publicationState?.name,
    version = version,
)

private fun CardEntity.toDomain() = V25Card(
    cardId = cardId,
    deckId = deckId,
    front = front,
    back = back,
    cardType = enumValueOf(cardType),
    targetDifficulty = targetDifficulty?.let { enumValueOf<com.qiuzhao.flashcards.domain.v25.V25Difficulty>(it) },
    position = position,
    chapterId = chapterId,
    sourceTaskId = sourceTaskId,
    publicationState = publicationState?.let { enumValueOf<com.qiuzhao.flashcards.domain.v25.V25PublicationState>(it) }
        ?: com.qiuzhao.flashcards.domain.v25.V25PublicationState.PUBLISHED,
    version = version,
)

private fun V25ReviewState.toEntity(userId: String, cardId: String, now: Long) = ReviewStateEntity(
    userId = userId,
    cardId = cardId,
    state = state,
    due = due?.toEpochMilli(),
    syncedAt = now,
)

private fun ReviewStateEntity.toDomain() = V25ReviewState(state = state, due = due?.let(Instant::ofEpochMilli))

private fun V25StudyPlan.toEntity(userId: String) = StudyPlanEntity(
    userId = userId,
    configured = configured,
    currentProjectId = currentProjectId,
    selectedDeckIds = selectedDeckIds.joinToString(","),
    dailyNewGoal = dailyNewGoal,
    dailyReviewGoal = dailyReviewGoal,
    updatedAt = updatedAt?.toEpochMilli(),
)

private fun StudyPlanEntity.toDomain() = V25StudyPlan(
    configured = configured,
    currentProjectId = currentProjectId,
    selectedDeckIds = selectedDeckIds.split(',').filter { it.isNotBlank() },
    dailyNewGoal = dailyNewGoal,
    dailyReviewGoal = dailyReviewGoal,
    updatedAt = updatedAt?.let(Instant::ofEpochMilli),
)

private fun V25TodayPlan.toEntity(userId: String) = TodayPlanEntity(
    userId = userId,
    studyDate = studyDate.toString(),
    timezone = learningTimezone,
    currentProjectId = currentProject?.projectId,
    currentProjectName = currentProject?.name,
    dailyGoal = dailyGoal,
    completedCount = completedCount,
    dueCount = dueCount,
    planRemaining = planRemaining,
    backlogCount = backlogCount,
    dailyNewGoal = dailyNewGoal,
    dailyReviewGoal = dailyReviewGoal,
    newCompletedCount = newCompletedCount,
    reviewCompletedCount = reviewCompletedCount,
    newRemainingCount = newRemainingCount,
    reviewRemainingCount = reviewRemainingCount,
    coreTargetCount = coreTargetCount,
    planConfigured = planConfigured,
    selectedDeckIds = selectedDeckIds.joinToString(","),
)

private fun TodayPlanEntity.toDomain(cards: List<V25PlanCard>) = V25TodayPlan(
    learningTimezone = timezone,
    studyDate = LocalDate.parse(studyDate),
    currentProject = currentProjectId?.let { id ->
        com.qiuzhao.flashcards.domain.v25.V25CurrentProject(id, currentProjectName.orEmpty())
    },
    dailyGoal = dailyGoal,
    completedCount = completedCount,
    dueCount = dueCount,
    planRemaining = planRemaining,
    backlogCount = backlogCount,
    cards = cards,
    dailyNewGoal = dailyNewGoal,
    dailyReviewGoal = dailyReviewGoal,
    newCompletedCount = newCompletedCount,
    reviewCompletedCount = reviewCompletedCount,
    newRemainingCount = newRemainingCount,
    reviewRemainingCount = reviewRemainingCount,
    coreTargetCount = coreTargetCount,
    planConfigured = planConfigured,
    selectedDeckIds = selectedDeckIds.split(',').filter { it.isNotBlank() },
)

private fun V25PlanCard.toEntity(userId: String, studyDate: String, position: Int) = TodayPlanCardEntity(
    userId = userId,
    studyDate = studyDate,
    position = position,
    cardId = card.cardId,
    planKind = planKind,
    isNew = isNew,
)

private fun TodayPlanCardEntity.toPlanCard(card: V25Card, reviewState: V25ReviewState?) = V25PlanCard(
    card = card,
    isNew = isNew,
    reviewState = reviewState,
    planKind = planKind,
)

private fun V25ProgressSummary.toEntity(userId: String, projectId: String) = ProjectProgressEntity(
    userId = userId,
    projectId = projectId,
    cardCount = cardCount,
    notStartedCount = notStartedCount,
    learningCount = learningCount,
    relearningCount = relearningCount,
    consolidatingCount = consolidatingCount,
    masteredCount = masteredCount,
    dueCount = dueCount,
    reviewEventCount = reviewEventCount,
    lastStudiedAt = lastStudiedAt?.toEpochMilli(),
)

private fun ProjectProgressEntity.toDomain(projectId: String) = V25ProgressSummary(
    scopeId = projectId,
    scopeName = "",
    isProject = true,
    cardCount = cardCount,
    newCount = notStartedCount,
    learnedCount = (cardCount - notStartedCount).coerceAtLeast(0),
    dueCount = dueCount,
    masteredCount = masteredCount,
    masteryRatio = if (cardCount > 0) masteredCount.toFloat() / cardCount else null,
    notStartedCount = notStartedCount,
    learningCount = learningCount,
    relearningCount = relearningCount,
    consolidatingCount = consolidatingCount,
    reviewEventCount = reviewEventCount,
    lastStudiedAt = lastStudiedAt?.let(Instant::ofEpochMilli),
)

private fun V25StatsDashboard.toEntity(userId: String, now: Long) = DashboardEntity(
    userId = userId,
    hasData = hasData,
    weekStartDate = weeklyActivity.firstOrNull()?.studyDate?.toString() ?: "",
    weeklyActivity = cacheJson.encodeToString(ListSerializer(Int.serializer()), weeklyActivity.map { it.ratingCount }),
    weeklyTotal = weeklyTotalRatings,
    weeklyChangeRate = weeklyChangeRate?.toDouble(),
    weeklyGoal = weeklyGoal,
    weeklyCompletedCount = weeklyGoalCompleted,
    weeklyGoalProgress = weeklyGoalRate?.toDouble(),
    recallAccuracy = recallAccuracy?.toDouble(),
    firstAttemptAccuracy = firstAttemptAccuracy?.toDouble(),
    retentionRate = retentionRate?.toDouble(),
    streakDays = streakDays,
    masteredCards = masteredCards,
    updatedAt = now,
)

private fun DashboardEntity.toDomain(): V25StatsDashboard {
    val weekStart = runCatching { LocalDate.parse(weekStartDate) }.getOrNull() ?: LocalDate.now()
    val counts = runCatching {
        cacheJson.decodeFromString(ListSerializer(Int.serializer()), weeklyActivity)
    }.getOrDefault(List(7) { 0 })
    return V25StatsDashboard(
        hasData = hasData,
        weeklyActivity = counts.mapIndexed { index, ratingCount ->
            V25DailyActivity(studyDate = weekStart.plusDays(index.toLong()), ratingCount = ratingCount)
        },
        weeklyTotalRatings = weeklyTotal,
        weeklyChangeRate = weeklyChangeRate?.toFloat(),
        weeklyGoal = weeklyGoal,
        weeklyGoalCompleted = weeklyCompletedCount,
        weeklyGoalRate = weeklyGoalProgress?.toFloat(),
        recallAccuracy = recallAccuracy?.toFloat(),
        firstAttemptAccuracy = firstAttemptAccuracy?.toFloat(),
        retentionRate = retentionRate?.toFloat(),
        streakDays = streakDays,
        masteredCards = masteredCards,
        progress = emptyList(),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )
}
