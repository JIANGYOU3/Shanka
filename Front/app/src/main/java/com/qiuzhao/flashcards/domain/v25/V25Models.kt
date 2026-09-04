package com.qiuzhao.flashcards.domain.v25

import java.time.Instant
import java.time.LocalDate

/**
 * UI-agnostic V2.5 models for the typed bridge (Architecture §8 `domain/v25`). Every type here
 * mirrors the target Architecture resource model (sections 3.1–3.9) so the visual lane never
 * touches DTOs, HTTP statuses or JSON. Rules of this file:
 *
 * - IDs are opaque UUID strings and must never be parsed as numbers.
 * - Timestamps cross the boundary as ISO-8601 [Instant]; dates as [LocalDate].
 * - Difficulty ratios are integer ten-percent steps (0..100 per tier, always summing to 100).
 * - Enum entry names are the exact wire values from the target Architecture — including the
 *   lowercase query-parameter values `position`/`random` and `all`/`mastered`/`unmastered`,
 *   and the preset avatar ids `mood_01`..`mood_12`.
 *
 * This file must not import Android UI, `android.*`, `org.json`, or `java.net` types.
 */

// --- enums (exact V2.5 values) ----------------------------------------------------------------

/** Preset system avatar ids, `mood_01`..`mood_12` (Architecture 3.1, V25-ACC-FR-02). */
enum class V25AvatarKey {
    mood_01, mood_02, mood_03, mood_04, mood_05, mood_06,
    mood_07, mood_08, mood_09, mood_10, mood_11, mood_12,
}

/** Default knowledge coverage depth (Architecture 3.1, V25-SET-FR-01). */
enum class V25CoverageMode { COMPACT, BALANCED, EXTENSIVE }

/** Content difficulty tiers; legacy wire value `APPLICATION` maps to `DEEP_QUESTION` (Architecture 3.5). */
enum class V25Difficulty { BASIC, UNDERSTANDING, DEEP_QUESTION }

/** Card shapes rendered through the shared `front/back` view (Architecture 3.5, V25-STUDY-FR-08). */
enum class V25CardType { QUESTION, TRUE_FALSE }

/**
 * User-visible learning project lifecycle (Architecture 3.2, structure-contract 3.16). The
 * server aggregates the value from every material's status plus the chapter confirmation:
 * no materials = EMPTY; any PDF pending/parsing = PARSING; all PDFs failed with no usable
 * chapter source = PARSE_FAILED; usable chapters not confirmed = AWAITING_CHAPTER_CONFIRMATION;
 * confirmed = READY. The client never re-derives it.
 */
enum class V25ProjectStatus { EMPTY, PARSING, PARSE_FAILED, AWAITING_CHAPTER_CONFIRMATION, READY }

/** Learning material kind (structure-contract 3.2a); LINK is reserved and not implemented. */
enum class V25MaterialType { PDF, TEXT }

/** Material lifecycle: PDF uses PENDING/PARSING/PARSED/FAILED; TEXT is always READY. */
enum class V25MaterialStatus { PENDING, PARSING, PARSED, FAILED, READY }

/** User-visible generation task lifecycle (Architecture 3.4). */
enum class V25TaskStatus {
    DRAFT, SAMPLE_GENERATING, AWAITING_SAMPLE_CONFIRMATION, GENERATING, COMPLETED, FAILED, ABANDONED,
}

/** Internal worker stage; never exposed to the user as a status (Architecture 3.4). */
enum class V25InternalStage { PLANNING, GENERATING, SCORING, PUBLISHING }

/** Card visibility; ordinary queries only return `PUBLISHED` (Architecture 3.6). */
enum class V25PublicationState { STAGED, PUBLISHED }

/** Deletion batch lifecycle (Architecture 3.7). */
enum class V25DeletionBatchStatus { PENDING, UNDONE, FINALIZED }

/** Card rewrite preview lifecycle (Architecture 3.8). */
enum class V25RewriteStatus { PENDING, APPLIED, CANCELLED, EXPIRED }

/** The four self-rated review outcomes (V25-STUDY-FR-07). */
enum class V25Rating { AGAIN, HARD, GOOD, EASY }

/** Free-browse ordering; `random` is fixed per client session seed (Architecture 4.4). */
enum class V25BrowseOrder { position, random }

/** Free-browse content-difficulty filter, including unlabeled cards (Architecture 4.4). */
enum class V25ContentDifficulty { BASIC, UNDERSTANDING, DEEP_QUESTION, UNLABELED }

/** Free-browse mastery filter (Architecture 4.4). */
enum class V25MasteryFilter { all, mastered, unmastered }

/** DeepSeek API key states shown in settings (V25-SET-FR-05). */
enum class V25ApiKeyState { AVAILABLE, INVALID, INSUFFICIENT_BALANCE, VERIFICATION_UNAVAILABLE, UNSET }

// --- result types -----------------------------------------------------------------------------

/**
 * Outcome of every [V25Repository] call. Loading is implied by the suspended call; empty data is
 * a [Success] carrying an empty collection or a null field (e.g. `currentProjectId == null`),
 * never a [Failure]; recoverable failures carry a stable [Failure.code] (see [V25ErrorCodes]).
 */
sealed interface V25Result<out T> {
    data class Success<T>(val value: T) : V25Result<T>
    data class Failure(
        val code: String,
        val localizationKey: String? = null,
        val message: String? = null,
        /** Server-provided next actions for conflicts (e.g. VIEW_TASKS or WAIT_FOR_TERMINAL). */
        val actions: List<String> = emptyList(),
    ) : V25Result<Nothing>
}

/** Exact error codes from Architecture section 6 plus the shared transport-level codes. */
object V25ErrorCodes {
    const val PROJECT_NOT_FOUND = "PROJECT_NOT_FOUND"
    const val MATERIAL_NOT_FOUND = "MATERIAL_NOT_FOUND"
    const val PROJECT_STATE_CONFLICT = "PROJECT_STATE_CONFLICT"
    const val PROJECT_HAS_ACTIVE_TASK = "PROJECT_HAS_ACTIVE_TASK"
    const val TASK_STATE_CONFLICT = "TASK_STATE_CONFLICT"
    const val TASK_ZERO_CARDS = "TASK_ZERO_CARDS"
    const val SAMPLE_STALE = "SAMPLE_STALE"
    const val GENERATION_FAILED = "GENERATION_FAILED"
    const val INVALID_LEARNING_TIMEZONE = "INVALID_LEARNING_TIMEZONE"
    const val INVALID_PREFERENCES = "INVALID_PREFERENCES"
    const val CARD_DELETE_WINDOW_EXPIRED = "CARD_DELETE_WINDOW_EXPIRED"
    const val CARD_REWRITE_UNAVAILABLE = "CARD_REWRITE_UNAVAILABLE"
    const val CARD_VERSION_CONFLICT = "CARD_VERSION_CONFLICT"
    const val AUTH_REQUIRED = "AUTH_REQUIRED"
    const val AUTH_INVALID = "AUTH_INVALID"
    const val NETWORK_UNAVAILABLE = "NETWORK_UNAVAILABLE"
    const val INVALID_RESPONSE = "INVALID_RESPONSE"
}

/** A 401 whose code means the stored session is dead; credential and network failures never qualify. */
val V25Result.Failure.isAuthFailure: Boolean
    get() = code == V25ErrorCodes.AUTH_REQUIRED || code == V25ErrorCodes.AUTH_INVALID

// --- account and preferences (Architecture 3.1) -----------------------------------------------

/** The signed-in user profile: read-only email plus editable username and preset avatar. */
data class V25AuthUser(
    val userId: String,
    val username: String,
    val email: String,
    val avatarKey: V25AvatarKey,
    val createdAt: Instant,
)

/** Three difficulty tiers as integer percents, each a 10% step, always summing to 100. */
data class V25DifficultyRatio(
    val basic: Int,
    val understanding: Int,
    val deepQuestion: Int,
) {
    init {
        val tiers = listOf(basic, understanding, deepQuestion)
        require(tiers.all { it in 0..100 && it % 10 == 0 }) {
            "difficulty ratios must be 10% steps between 0 and 100, got $tiers"
        }
        require(basic + understanding + deepQuestion == 100) {
            "difficulty ratios must sum to 100, got ${basic + understanding + deepQuestion}"
        }
    }
}

/** Account-level learning and generation preferences (Architecture 3.1). */
data class V25UserPreferences(
    val defaultCoverageMode: V25CoverageMode,
    val difficultyRatio: V25DifficultyRatio,
    val dailyLearningGoal: Int,
    val learningTimezone: String,
    val currentProjectId: String?,
    val updatedAt: Instant,
) {
    init {
        require(dailyLearningGoal in 10..200 && dailyLearningGoal % 10 == 0) {
            "daily learning goal must be a multiple of 10 between 10 and 200, got $dailyLearningGoal"
        }
        require(learningTimezone.isNotBlank()) { "learning timezone must be an IANA timezone id" }
    }
}

/** Partial preferences update (PATCH /preferences); at least one field is required. */
data class V25PreferencesPatch(
    val defaultCoverageMode: V25CoverageMode? = null,
    val difficultyRatio: V25DifficultyRatio? = null,
    val dailyLearningGoal: Int? = null,
    val learningTimezone: String? = null,
) {
    init {
        require(defaultCoverageMode != null || difficultyRatio != null || dailyLearningGoal != null || learningTimezone != null) {
            "preferences patch requires at least one field"
        }
    }
}

// --- learning projects and chapters (Architecture 3.2) -----------------------------------------

/**
 * A chapter derived from one learning material. PDF chapters carry an editable page span;
 * TEXT material chapters are the material's single whole-content chapter with null pages.
 */
data class V25Chapter(
    val id: String,
    /** Owning material (structure-contract 3.2a); chapters always belong to one material. */
    val materialId: String,
    val name: String,
    val startPage: Int?,
    val endPage: Int?,
) {
    /** Page-span label for the chapter list; TEXT chapters have no pages. */
    val pageSpanLabel: String?
        get() = if (startPage == null || endPage == null) null else "$startPage-$endPage 页"
}

/** Chapter name/page-span edit (PATCH /projects/{project_id}/chapters/{chapter_id}). */
data class V25ChapterEdit(
    val name: String,
    val startPage: Int,
    val endPage: Int,
)

/**
 * One learning material inside a project (structure-contract 3.2a): a project is a collection
 * of materials, and adding/removing any material resets the chapter confirmation server-side.
 */
data class V25Material(
    val materialId: String,
    val projectId: String,
    val type: V25MaterialType,
    /** PDF = uploaded file name; TEXT = the user-titled source (1..60 characters). */
    val name: String,
    val status: V25MaterialStatus,
    /** Parse failure code; PDF materials only. */
    val errorCode: String? = null,
    /** PDF file size in bytes; TEXT is null. */
    val sizeBytes: Long? = null,
    /** TEXT character count (1..30000); PDF is null. */
    val charCount: Int? = null,
    /** The material's own chapter; TEXT carries exactly one with null pages. */
    val chapter: V25Chapter? = null,
    val createdAt: Instant,
)

/** Aggregate root of materials, chapters, decks and tasks (Architecture 3.2, contract 3.16). */
data class V25LearningProject(
    val projectId: String,
    val name: String,
    /** Material summaries; an empty list is the explicit EMPTY project. */
    val materials: List<V25Material>,
    val status: V25ProjectStatus,
    val chapterCount: Int,
    val deckCount: Int,
    val taskCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int,
    /** Project detail may include the persisted task snapshot; list responses may omit it. */
    val tasks: List<V25GenerationTask> = emptyList(),
    /**
     * Chapters across every material (detail responses only; list responses omit them and this
     * stays empty — the chapter screen always re-reads the detail).
     */
    val chapters: List<V25Chapter> = emptyList(),
)

/** New-card chapter scope for a project (Architecture 3.3). */
data class V25ProjectStudySettings(
    val projectId: String,
    val selectedNewCardChapterIds: List<String>,
    val includeUnassigned: Boolean,
    val updatedAt: Instant,
    /** Deck-scoped daily plan fields; optional on legacy responses during rollout. */
    val selectedDeckIds: List<String> = emptyList(),
    val dailyNewGoal: Int = 10,
    val dailyReviewGoal: Int = 40,
)

/** Atomic, current-project study plan returned by GET/PUT /study/plan. */
data class V25StudyPlan(
    val configured: Boolean,
    val currentProjectId: String?,
    val selectedDeckIds: List<String>,
    val dailyNewGoal: Int,
    val dailyReviewGoal: Int,
    val updatedAt: Instant? = null,
)

/** Request payload for the single-save study-plan form. */
data class V25StudyPlanUpdate(
    val currentProjectId: String,
    val selectedDeckIds: List<String>,
    val dailyNewGoal: Int,
    val dailyReviewGoal: Int,
)

/** Partial study-settings update; at least one field is required. */
data class V25StudySettingsPatch(
    val selectedNewCardChapterIds: List<String>? = null,
    val includeUnassigned: Boolean? = null,
) {
    init {
        require(selectedNewCardChapterIds != null || includeUnassigned != null) {
            "study settings patch requires at least one field"
        }
    }
}

// --- generation tasks (Architecture 3.4–3.5) ---------------------------------------------------

/** Per-task generation configuration; `custom_requirements` is free text, never a full prompt. */
data class V25GenerationConfig(
    val coverageMode: V25CoverageMode,
    val difficultyRatio: V25DifficultyRatio,
    val customRequirements: String = "",
)

/** A persisted sample card; one per enabled difficulty tier, 1–3 total (V25-GEN-FR-05). */
data class V25SampleCard(
    val front: String,
    val back: String,
    val cardType: V25CardType,
    /** openapi：nullable 且非 required——服务端可能不发送。 */
    val targetDifficulty: V25Difficulty? = null,
)

/** A generation task with its frozen chapter snapshot and persisted samples (Architecture 3.4). */
data class V25GenerationTask(
    val taskId: String,
    val projectId: String?,
    val fileId: String?,
    val deckId: String?,
    val retryOfTaskId: String?,
    val status: V25TaskStatus,
    val internalStage: V25InternalStage?,
    val selectedChapters: List<V25Chapter>,
    val generationConfig: V25GenerationConfig,
    val sampleCards: List<V25SampleCard>,
    val sampleConfigHash: String?,
    val sampleConfirmedAt: Instant?,
    val generatedCardCount: Int,
    val errorCode: String?,
    val failureStage: String?,
    val createdAt: Instant,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val updatedAt: Instant,
)

/** One active task that currently blocks a project/deck deletion. */
data class V25DeletionTaskBlocker(
    val taskId: String,
    val status: V25TaskStatus,
    val internalStage: V25InternalStage?,
    val projectId: String?,
    val deckId: String?,
    val canAbandon: Boolean,
    val allowedActions: List<String>,
    val canCancel: Boolean = false,
)

/**
 * Light status-carrying projection of one generation task (V25-D-34): everything an observing
 * surface needs to render progress and terminal outcomes, without the bulky sample/chapter
 * payloads. Surfaces that need samples or the frozen configuration read the full
 * [V25GenerationTask] through `getTask` on demand.
 */
data class V25ObservedTask(
    val taskId: String,
    val projectId: String?,
    val deckId: String?,
    val retryOfTaskId: String?,
    val status: V25TaskStatus,
    val internalStage: V25InternalStage?,
    val generatedCardCount: Int,
    val errorCode: String?,
    val failureStage: String?,
    val updatedAt: Instant,
)

fun V25GenerationTask.toObserved() = V25ObservedTask(
    taskId = taskId,
    projectId = projectId,
    deckId = deckId,
    retryOfTaskId = retryOfTaskId,
    status = status,
    internalStage = internalStage,
    generatedCardCount = generatedCardCount,
    errorCode = errorCode,
    failureStage = failureStage,
    updatedAt = updatedAt,
)

/** Task states an observation engine must keep polling; everything else is terminal. */
val V25TaskStatus.isTerminal: Boolean
    get() = this == V25TaskStatus.COMPLETED || this == V25TaskStatus.FAILED || this == V25TaskStatus.ABANDONED

/** Stable, typed impact summary returned by the deletion preflight endpoints. */
data class V25DeletionImpact(
    val retainDecks: Boolean? = null,
    val deckCount: Int = 0,
    val cardCount: Int = 0,
    val taskCount: Int = 0,
    val projectStatus: V25ProjectStatus? = null,
    val deckName: String? = null,
)

/** Read-only deletion preview; it is advisory and never reserves the resource. */
data class V25DeletionPreflight(
    val resourceType: String,
    val resourceId: String,
    val canDelete: Boolean,
    val blockers: List<V25DeletionTaskBlocker>,
    val abandonableTaskIds: List<String>,
    val hasUncancellableTasks: Boolean,
    val actions: List<String>,
    val impact: V25DeletionImpact,
    val cancelableTaskIds: List<String> = emptyList(),
    val canCancel: Boolean = false,
)

/** Partial task-config update (PATCH /tasks/{task_id}); at least one field is required. */
data class V25TaskConfigPatch(
    val deckId: String? = null,
    val chapterIds: List<String>? = null,
    val generationConfig: V25GenerationConfig? = null,
) {
    init {
        require(deckId != null || chapterIds != null || generationConfig != null) {
            "task config patch requires at least one field"
        }
    }
}

// --- decks and cards (Architecture 3.6) ---------------------------------------------------------

/** A deck; `projectId == null` means an independent deck outside any project. */
data class V25Deck(
    val deckId: String,
    val name: String,
    val projectId: String?,
    val cardCount: Int,
    val dueCount: Int,
    val masteredCards: Int,
    val reviewCount: Int,
    val masteryRatio: Float?,
    val notStartedCount: Int = 0,
    val learningCount: Int = 0,
    val relearningCount: Int = 0,
    val consolidatingCount: Int = 0,
    val masteredCount: Int = 0,
    val reviewEventCount: Int = 0,
    val lastStudiedAt: Instant? = null,
)

/** A visible (PUBLISHED) card; `chapterId == null` is the unassigned group, not a fake chapter. */
data class V25Card(
    val cardId: String,
    val deckId: String,
    val front: String,
    val back: String,
    val cardType: V25CardType,
    val targetDifficulty: V25Difficulty?,
    val position: Int,
    val chapterId: String?,
    val sourceTaskId: String?,
    val publicationState: V25PublicationState,
    val version: Int,
)

/** Manual card addition / text import input; both sides must be non-empty. */
data class V25CardDraft(
    val front: String,
    val back: String,
)

/** Per-card outcome of the atomic bulk import (`POST /decks/{deck_id}/cards/import`). */
enum class V25ImportStatus { CREATED, FAILED }

/** One import result row; `cardId` is present only for a created card. */
data class V25ImportResult(
    val index: Int,
    val status: V25ImportStatus,
    val cardId: String?,
)

/** Free-browse filters (Architecture 4.4): order, content difficulty, mastery. */
data class V25BrowseFilter(
    val order: V25BrowseOrder,
    val contentDifficulty: V25ContentDifficulty? = null,
    val mastery: V25MasteryFilter = V25MasteryFilter.all,
)

/** FSRS review state attached to a card. */
data class V25ReviewState(
    val state: String,
    val due: Instant? = null,
)

/** A review-queue item for a deck. */
data class V25ReviewCard(
    val card: V25Card,
    val reviewState: V25ReviewState?,
)

// --- deletion batches (Architecture 3.7) -------------------------------------------------------

/** Server-authoritative 10-second undo batch; cards stay visible only while PENDING. */
data class V25CardDeletionBatch(
    val deleteBatchId: String,
    val cardIds: List<String>,
    val undoUntil: Instant,
    val status: V25DeletionBatchStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

// --- rewrite previews (Architecture 3.8) -------------------------------------------------------

/** Two-stage AI rewrite preview; applying requires an unchanged [baseCardVersion]. */
data class V25CardRewritePreview(
    val rewriteId: String,
    val cardId: String,
    val baseCardVersion: String,
    val front: String,
    val back: String,
    val cardType: V25CardType,
    val targetDifficulty: V25Difficulty?,
    val customRequirements: String?,
    val status: V25RewriteStatus,
    val expiresAt: Instant,
)

// --- today plan and stats (Architecture 3.9) ---------------------------------------------------

/** Minimal current-project summary inside the today plan. */
data class V25CurrentProject(
    val projectId: String,
    val name: String,
)

/** An ordered today-plan item; `isNew` distinguishes due review from new-card fill. */
data class V25PlanCard(
    val card: V25Card,
    val isNew: Boolean,
    val reviewState: V25ReviewState?,
    val planKind: String? = null,
)

/**
 * Server-computed today plan (Architecture 3.9): due-first queue up to the daily goal, filled
 * with in-scope new cards. `currentProject == null` with zero cards is the no-project empty state.
 */
data class V25TodayPlan(
    val learningTimezone: String,
    val studyDate: LocalDate,
    val currentProject: V25CurrentProject?,
    val dailyGoal: Int,
    val completedCount: Int,
    val dueCount: Int,
    val planRemaining: Int,
    val backlogCount: Int,
    val cards: List<V25PlanCard>,
    val dailyNewGoal: Int = 0,
    val dailyReviewGoal: Int = 0,
    val newCompletedCount: Int = 0,
    val reviewCompletedCount: Int = 0,
    val newRemainingCount: Int = 0,
    val reviewRemainingCount: Int = 0,
    val coreTargetCount: Int = 0,
    val planConfigured: Boolean = false,
    val selectedDeckIds: List<String> = emptyList(),
)

/** Review submission outcome: the updated FSRS state and the account-timezone study date. */
data class V25RatingResult(
    val reviewState: V25ReviewState,
    val studyDate: LocalDate,
)

/** One day of weekly activity (successful rating events). */
data class V25DailyActivity(
    val studyDate: LocalDate,
    val ratingCount: Int,
)

/** Project or deck progress summary (V25-STATS-FR-05). */
data class V25ProgressSummary(
    val scopeId: String,
    val scopeName: String,
    val isProject: Boolean,
    val cardCount: Int,
    val newCount: Int = 0,
    val learnedCount: Int = 0,
    val dueCount: Int,
    val masteredCount: Int,
    val masteryRatio: Float?,
    /** Lifecycle projection from the server; no client-side state inference is allowed. */
    val notStartedCount: Int = 0,
    val learningCount: Int = 0,
    val relearningCount: Int = 0,
    val consolidatingCount: Int = 0,
    val reviewEventCount: Int = 0,
    val lastStudiedAt: Instant? = null,
)

/**
 * Stats dashboard (Architecture 3.9, V25-STATS-FR-01..05). Rates are null when their denominator
 * is zero — an honest empty state, never a fabricated `0%`. Timezone and weekly goal are derived
 * server-side from the account preferences; this model carries no client-supplied values.
 */
data class V25StatsDashboard(
    val hasData: Boolean,
    val weeklyActivity: List<V25DailyActivity>,
    val weeklyTotalRatings: Int,
    val weeklyChangeRate: Float?,
    val weeklyGoal: Int,
    val weeklyGoalCompleted: Int,
    val weeklyGoalRate: Float?,
    val recallAccuracy: Float?,
    val firstAttemptAccuracy: Float?,
    val retentionRate: Float?,
    val streakDays: Int,
    val masteredCards: Int,
    val progress: List<V25ProgressSummary>,
    val updatedAt: Instant?,
)

// --- API key status (V25-SET-FR-05) ------------------------------------------------------------

/** DeepSeek API key state; the client only ever sees the masked key. */
data class V25ApiKeyStatus(
    val state: V25ApiKeyState,
    val maskedKey: String?,
)
