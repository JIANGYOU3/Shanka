package com.qiuzhao.flashcards.domain.v25

import java.io.InputStream

/**
 * The only V2.5 boundary the visual lane may consume (Architecture §8). It exposes typed auth
 * profile/preferences, learning projects, generation tasks, decks/cards, today plan, review
 * rating, browse filters, deletion batches, rewrite previews and stats — as immutable models
 * (see [V25Models] / [V25Result]) — and no Android UI type, HTTP status or JSON object.
 *
 * Contract rules:
 * - Loading is implied by the suspended call; empty data is `V25Result.Success` with an empty
 *   collection or a null field; recoverable failure is `V25Result.Failure` with a [V25ErrorCodes] code.
 * - Timestamps cross the boundary as ISO-8601 `Instant`; dates as `LocalDate`.
 * - Difficulty ratios are integer ten-percent steps summing to 100.
 * - Write calls are idempotent; the implementation owns the Idempotency-Key and bearer session.
 *   A caller retrying one user operation after a lost response supplies its own explicit
 *   `idempotencyKey` so the replay carries the identical key instead of writing twice.
 * - `saveApiKey` must never log, persist or echo the plaintext key.
 *
 * Every method maps to an endpoint of the target Architecture HTTP contract (section 4); the
 * remote implementation (data/remote/v25) is responsible for the transport.
 */
interface V25Repository {

    // --- account profile (Architecture 4.1, V25-ACC) ------------------------------------------

    /** GET /auth/me — current profile with read-only email and preset avatar. */
    suspend fun getAuthUser(): V25Result<V25AuthUser>

    /** PATCH /auth/me — update username and/or avatar; at least one field is required. */
    suspend fun updateAuthUser(
        username: String? = null,
        avatarKey: V25AvatarKey? = null,
    ): V25Result<V25AuthUser>

    /** POST /auth/logout — end the current device session only; never clears server data. */
    suspend fun logout(): V25Result<Unit>

    // --- preferences (Architecture 4.1, V25-SET) ----------------------------------------------

    /** GET /preferences — account-level learning and generation preferences. */
    suspend fun getPreferences(): V25Result<V25UserPreferences>

    /** PATCH /preferences — partial update; server validates ratios, goal and IANA timezone. */
    suspend fun updatePreferences(patch: V25PreferencesPatch): V25Result<V25UserPreferences>

    /** PATCH /preferences with `current_project_id` — switch the current project; null clears it. */
    suspend fun setCurrentProject(projectId: String?): V25Result<V25UserPreferences>

    // --- learning projects and chapters (Architecture 4.2) -------------------------------------

    /**
     * POST /projects — two-step creation, step one: create the named EMPTY project (JSON body,
     * contract V25-D-29). Materials attach afterwards through [addProjectMaterialPdf] /
     * [addProjectMaterialText].
     */
    suspend fun createProject(name: String, idempotencyKey: String? = null): V25Result<V25LearningProject>

    /** GET /projects — the user's projects; empty list is the true empty state. `forceRefresh` bypasses the offline cache. */
    suspend fun listProjects(forceRefresh: Boolean = false): V25Result<List<V25LearningProject>>

    /** GET /projects/{project_id} — project detail. `forceRefresh` bypasses the offline cache. */
    suspend fun getProject(projectId: String, forceRefresh: Boolean = false): V25Result<V25LearningProject>

    /** GET /projects/{project_id}/progress — server-derived project lifecycle aggregate. */
    suspend fun projectProgress(projectId: String): V25Result<V25ProgressSummary>

    /** PATCH /projects/{project_id} — rename the project (1..60 trimmed characters). */
    suspend fun renameProject(projectId: String, name: String): V25Result<V25LearningProject>

    /** DELETE /projects/{project_id}?retain_decks= — server cancels active tasks; keep or delete decks. */
    suspend fun deleteProject(
        projectId: String,
        retainDecks: Boolean,
        idempotencyKey: String? = null,
    ): V25Result<Unit>

    /** GET /projects/{project_id}/deletion-preflight — read-only impact and task blockers. */
    suspend fun getProjectDeletionPreflight(
        projectId: String,
        retainDecks: Boolean = true,
        allowCancel: Boolean = false,
    ): V25Result<V25DeletionPreflight>

    // --- materials (Architecture 4.2, structure-contract 3.2a) -----------------------------------

    /** POST /projects/{project_id}/materials/pdf — attach a PDF; it parses asynchronously. */
    suspend fun addProjectMaterialPdf(
        projectId: String,
        fileName: String,
        content: InputStream,
        idempotencyKey: String? = null,
    ): V25Result<V25Material>

    /** POST /projects/{project_id}/materials/text — attach pasted text (≤30000 characters); READY immediately. */
    suspend fun addProjectMaterialText(
        projectId: String,
        name: String,
        content: String,
        idempotencyKey: String? = null,
    ): V25Result<V25Material>

    /** GET /projects/{project_id}/materials — every material with its own status. */
    suspend fun listProjectMaterials(projectId: String): V25Result<List<V25Material>>

    /**
     * DELETE /projects/{project_id}/materials/{material_id}?retain_cards= — three-tier delete;
     * the server silently cancels tasks referencing the material and returns the re-aggregated
     * project (deleting the last material turns it EMPTY and it stays alive).
     */
    suspend fun deleteProjectMaterial(
        projectId: String,
        materialId: String,
        retainCards: Boolean = true,
        idempotencyKey: String? = null,
    ): V25Result<V25LearningProject>

    /** POST /projects/{project_id}/materials/{material_id}/replace — in-place re-upload of a FAILED PDF material. */
    suspend fun replaceProjectMaterialPdf(
        projectId: String,
        materialId: String,
        fileName: String,
        content: InputStream,
        idempotencyKey: String? = null,
    ): V25Result<V25Material>

    /** PATCH /projects/{project_id}/chapters/{chapter_id} — edit chapter name and page span. */
    suspend fun updateChapter(
        projectId: String,
        chapterId: String,
        edit: V25ChapterEdit,
    ): V25Result<V25Chapter>

    /** DELETE /projects/{project_id}/chapters/{chapter_id}?delete_cards= — protected by active tasks. */
    suspend fun deleteChapter(
        projectId: String,
        chapterId: String,
        deleteCards: Boolean,
    ): V25Result<Unit>

    /** POST /projects/{project_id}/confirm-chapters — accept the table of contents; project → READY. */
    suspend fun confirmChapters(projectId: String): V25Result<V25LearningProject>

    /** GET /projects/{project_id}/study-settings — new-card chapter scope and unassigned group. */
    suspend fun getStudySettings(projectId: String): V25Result<V25ProjectStudySettings>

    /** PATCH /projects/{project_id}/study-settings — update the new-card chapter scope. */
    suspend fun updateStudySettings(
        projectId: String,
        patch: V25StudySettingsPatch,
    ): V25Result<V25ProjectStudySettings>

    // --- generation tasks (Architecture 4.3) ---------------------------------------------------

    /** POST /projects/{project_id}/tasks — create a DRAFT task with chapters, deck and config. */
    suspend fun createTask(
        projectId: String,
        deckId: String,
        chapterIds: List<String>,
        config: V25GenerationConfig,
    ): V25Result<V25GenerationTask>

    /** GET /tasks?project_id=&status= — task area and history for a project (or all). */
    suspend fun listTasks(
        projectId: String? = null,
        status: V25TaskStatus? = null,
    ): V25Result<List<V25GenerationTask>>

    /** GET /tasks/{task_id} — current task state; the source of truth for background progress. */
    suspend fun getTask(taskId: String): V25Result<V25GenerationTask>

    /** PATCH /tasks/{task_id} — reconfigure a DRAFT or sample-pending task; samples become stale. */
    suspend fun updateTaskConfig(
        taskId: String,
        patch: V25TaskConfigPatch,
    ): V25Result<V25GenerationTask>

    /** POST /tasks/{task_id}/samples — persist 1..3 samples, one per enabled difficulty tier. */
    suspend fun generateSamples(taskId: String): V25Result<List<V25SampleCard>>

    /** POST /tasks/{task_id}/start — validate the sample hash, then enter GENERATING. */
    suspend fun startTask(taskId: String): V25Result<V25GenerationTask>

    /** POST /tasks/{task_id}/abandon — end a pre-generation task; it stays in history. */
    suspend fun abandonTask(taskId: String): V25Result<V25GenerationTask>

    /** POST /tasks/{task_id}/retry — link a new task to the failed one, reusing confirmed samples. */
    suspend fun retryTask(taskId: String): V25Result<V25GenerationTask>

    /** DELETE /tasks/{task_id}?delete_generated_cards= — keep or delete the task's published cards. */
    suspend fun deleteTask(taskId: String, deleteGeneratedCards: Boolean): V25Result<Unit>

    // --- decks and cards (Architecture 4.4) -----------------------------------------------------

    /** GET /decks — all decks, optionally scoped to a project; independent decks have null project. */
    suspend fun listDecks(projectId: String? = null): V25Result<List<V25Deck>>

    /** POST /decks — create a deck; `projectId == null` creates an independent deck. */
    suspend fun createDeck(name: String, projectId: String? = null, idempotencyKey: String? = null): V25Result<V25Deck>

    /** GET /decks/{deck_id} — deck detail with counts. */
    suspend fun getDeck(deckId: String): V25Result<V25Deck>

    /** POST /projects/{project_id}/decks/{deck_id}/attach — move an independent deck into a project. */
    suspend fun attachDeckToProject(
        projectId: String,
        deckId: String,
        idempotencyKey: String? = null,
    ): V25Result<V25Deck>

    /** PATCH /decks/{deck_id} — rename the deck. */
    suspend fun renameDeck(deckId: String, name: String): V25Result<V25Deck>

    /** DELETE /decks/{deck_id} — server cancels active tasks before cascading the deck delete. */
    suspend fun deleteDeck(deckId: String, idempotencyKey: String? = null): V25Result<Unit>

    /** GET /decks/{deck_id}/deletion-preflight — read-only impact and task blockers. */
    suspend fun getDeckDeletionPreflight(
        deckId: String,
        allowCancel: Boolean = false,
    ): V25Result<V25DeletionPreflight>

    /** GET /decks/{deck_id}/cards — cards with free-browse order/difficulty/mastery filters. */
    suspend fun listCards(deckId: String, filter: V25BrowseFilter): V25Result<List<V25Card>>

    /**
     * POST /decks/{deck_id}/cards/import — atomic bulk import for manual add / text import. One
     * request carries every draft, so a retry with the same Idempotency-Key can never create a
     * second copy of some cards. Returns the per-index [V25ImportResult]s.
     */
    suspend fun importCards(deckId: String, drafts: List<V25CardDraft>, idempotencyKey: String? = null): V25Result<List<V25ImportResult>>

    /** PATCH /cards/{card_id} — edit front/back (both non-empty); review state resets. */
    suspend fun updateCard(cardId: String, front: String, back: String): V25Result<V25Card>

    /** DELETE /cards/{card_id} — hide now, hard-delete after the undo window; returns the batch. */
    suspend fun deleteCard(cardId: String): V25Result<V25CardDeletionBatch>

    // --- deletion undo (Architecture 4.4 / 3.7) --------------------------------------------------

    /** GET /card-deletion-batches/pending — recover still-valid undo batches after app restart. */
    suspend fun pendingDeletionBatches(): V25Result<List<V25CardDeletionBatch>>

    /** POST /card-deletion-batches/{delete_batch_id}/undo — restore the whole batch. */
    suspend fun undoDeletionBatch(deleteBatchId: String): V25Result<Unit>

    // --- AI rewrite previews (Architecture 4.4 / 3.8) --------------------------------------------

    /** POST /cards/{card_id}/rewrite-previews — persist a preview; original card stays untouched. */
    suspend fun createRewritePreview(
        cardId: String,
        customRequirements: String? = null,
    ): V25Result<V25CardRewritePreview>

    /** POST /cards/{card_id}/rewrite-previews/{rewrite_id}/apply — replace only if the version matches. */
    suspend fun applyRewritePreview(cardId: String, rewriteId: String): V25Result<V25Card>

    /** DELETE /cards/{card_id}/rewrite-previews/{rewrite_id} — cancel a preview, idempotently. */
    suspend fun cancelRewritePreview(cardId: String, rewriteId: String): V25Result<Unit>

    // --- study and review (Architecture 4.5) ------------------------------------------------------

    /** GET /study/plan — current project's atomic deck-scoped plan. */
    suspend fun getStudyPlan(): V25Result<V25StudyPlan>

    /** PUT /study/plan — atomically save the project, deck scope and two daily quotas. */
    suspend fun updateStudyPlan(
        plan: V25StudyPlanUpdate,
        idempotencyKey: String? = null,
    ): V25Result<V25StudyPlan>

    /** GET /study/today — server-computed today plan in the account learning timezone. */
    suspend fun todayPlan(): V25Result<V25TodayPlan>

    /** GET /study/today/backlog — due cards beyond the soft review target. */
    suspend fun studyPlanBacklog(
        offset: Int = 0,
        limit: Int = 50,
    ): V25Result<List<V25PlanCard>>

    /** GET /decks/{deck_id}/review — due review for an independent deck or a specific deck. */
    suspend fun deckReviewQueue(deckId: String): V25Result<List<V25ReviewCard>>

    /**
     * POST /review-events — submit AGAIN/HARD/GOOD/EASY; returns the updated state and study
     * date. A retry of one rating must reuse the same `clientEventId` (the server's fallback
     * dedupe key) and the same `idempotencyKey`; both default to fresh values for a first
     * submission.
     */
    suspend fun rateCard(
        cardId: String,
        rating: V25Rating,
        clientEventId: String? = null,
        idempotencyKey: String? = null,
    ): V25Result<V25RatingResult>

    // --- statistics (Architecture 4.5) ------------------------------------------------------------

    /** GET /stats/dashboard — timezone and weekly goal derived server-side; no client parameters. */
    suspend fun statsDashboard(): V25Result<V25StatsDashboard>

    // --- AI service key (V25-SET-FR-05) -------------------------------------------------------------

    /** GET /api-key/status — masked key and one of the five states; never the plaintext key. */
    suspend fun apiKeyStatus(): V25Result<V25ApiKeyStatus>

    /** PUT /api-key — validate and save a candidate key; a failed validation keeps the old key. */
    suspend fun saveApiKey(apiKey: String): V25Result<V25ApiKeyStatus>
}
