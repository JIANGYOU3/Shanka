package com.qiuzhao.flashcards.data.remote.v25

import com.qiuzhao.flashcards.domain.v25.V25ApiKeyState
import com.qiuzhao.flashcards.domain.v25.V25ApiKeyStatus
import com.qiuzhao.flashcards.domain.v25.V25AuthUser
import com.qiuzhao.flashcards.domain.v25.V25AvatarKey
import com.qiuzhao.flashcards.domain.v25.V25Card
import com.qiuzhao.flashcards.domain.v25.V25CardDeletionBatch
import com.qiuzhao.flashcards.domain.v25.V25CardRewritePreview
import com.qiuzhao.flashcards.domain.v25.V25CardType
import com.qiuzhao.flashcards.domain.v25.V25Chapter
import com.qiuzhao.flashcards.domain.v25.V25CoverageMode
import com.qiuzhao.flashcards.domain.v25.V25CurrentProject
import com.qiuzhao.flashcards.domain.v25.V25DailyActivity
import com.qiuzhao.flashcards.domain.v25.V25Deck
import com.qiuzhao.flashcards.domain.v25.V25DeletionBatchStatus
import com.qiuzhao.flashcards.domain.v25.V25DeletionImpact
import com.qiuzhao.flashcards.domain.v25.V25DeletionPreflight
import com.qiuzhao.flashcards.domain.v25.V25DeletionTaskBlocker
import com.qiuzhao.flashcards.domain.v25.V25Difficulty
import com.qiuzhao.flashcards.domain.v25.V25DifficultyRatio
import com.qiuzhao.flashcards.domain.v25.V25GenerationConfig
import com.qiuzhao.flashcards.domain.v25.V25GenerationTask
import com.qiuzhao.flashcards.domain.v25.V25ImportResult
import com.qiuzhao.flashcards.domain.v25.V25ImportStatus
import com.qiuzhao.flashcards.domain.v25.V25InternalStage
import com.qiuzhao.flashcards.domain.v25.V25LearningProject
import com.qiuzhao.flashcards.domain.v25.V25Material
import com.qiuzhao.flashcards.domain.v25.V25MaterialStatus
import com.qiuzhao.flashcards.domain.v25.V25MaterialType
import com.qiuzhao.flashcards.domain.v25.V25PlanCard
import com.qiuzhao.flashcards.domain.v25.V25PreferencesPatch
import com.qiuzhao.flashcards.domain.v25.V25ProgressSummary
import com.qiuzhao.flashcards.domain.v25.V25ProjectStatus
import com.qiuzhao.flashcards.domain.v25.V25ProjectStudySettings
import com.qiuzhao.flashcards.domain.v25.V25PublicationState
import com.qiuzhao.flashcards.domain.v25.V25Rating
import com.qiuzhao.flashcards.domain.v25.V25RatingResult
import com.qiuzhao.flashcards.domain.v25.V25ReviewCard
import com.qiuzhao.flashcards.domain.v25.V25ReviewState
import com.qiuzhao.flashcards.domain.v25.V25RewriteStatus
import com.qiuzhao.flashcards.domain.v25.V25SampleCard
import com.qiuzhao.flashcards.domain.v25.V25StatsDashboard
import com.qiuzhao.flashcards.domain.v25.V25StudyPlan
import com.qiuzhao.flashcards.domain.v25.V25StudyPlanUpdate
import com.qiuzhao.flashcards.domain.v25.V25TaskConfigPatch
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import com.qiuzhao.flashcards.domain.v25.V25TodayPlan
import com.qiuzhao.flashcards.domain.v25.V25UserPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire layer of the V2.5 remote data tier (Architecture §8 `data/remote/v25`). Every DTO maps
 * one OpenAPI payload — or builds one request body — against the committed backend contract
 * (`openapi.yaml` + `structure-contract.md` section 3). Nothing in this file is visible to the
 * visual lane: it consumes only the typed domain models.
 *
 * Contract rules enforced here:
 * - Required fields (per the OpenAPI `required` sets) are non-nullable without defaults, so a
 *   missing, null or mistyped value fails decoding and surfaces as `INVALID_RESPONSE` in the
 *   repository.
 * - Optional/nullable fields carry `= null` defaults and map to null; the wire `""` (e.g. an
 *   unset `masked_key`) maps to null.
 * - Unknown added fields are tolerated (`ignoreUnknownKeys`); unknown enum values fail decode
 *   (contract violation).
 * - Timestamps are ISO-8601; `period.start` may carry a non-UTC offset (account learning
 *   timezone), so week-day derivation goes through [OffsetDateTime].
 * - Wire `version` is a string (`v\d+` from the rewrite CAS or an ISO timestamp from
 *   `version=now`); the boundary models it as an Int marker that only serves cache-refresh
 *   change detection — `updated_at` / `base_card_version` stay authoritative.
 */

// --- shared wire types --------------------------------------------------------------------------

/** The list envelope used by every collection endpoint (`{"items": [...]}`). */
@Serializable
internal data class ItemsResponse<T>(@SerialName("items") val items: List<T>)

@Serializable
internal data class DifficultyRatioDto(
    @SerialName("basic") val basic: Int,
    @SerialName("understanding") val understanding: Int,
    @SerialName("deep_question") val deepQuestion: Int,
)

@Serializable
internal data class GenerationConfigDto(
    @SerialName("coverage_mode") val coverageMode: String,
    @SerialName("difficulty_ratio") val difficultyRatio: DifficultyRatioDto,
    @SerialName("custom_requirements") val customRequirements: String? = null,
)

@Serializable
internal data class ChapterDto(
    @SerialName("chapter_id") val chapterId: String,
    @SerialName("material_id") val materialId: String,
    @SerialName("name") val name: String,
    @SerialName("start_page") val startPage: Int? = null,
    @SerialName("end_page") val endPage: Int? = null,
)

@Serializable
internal data class ReviewStateDto(
    @SerialName("state") val state: String,
    @SerialName("due") val due: String? = null,
)

/**
 * One card wire shape shared by list-cards items, review-queue items and today-plan cards:
 * the card fields are flattened next to the optional `review_state` (and plan metadata).
 */
@Serializable
internal data class CardDto(
    @SerialName("card_id") val cardId: String,
    @SerialName("deck_id") val deckId: String,
    @SerialName("front") val front: String,
    @SerialName("back") val back: String,
    @SerialName("card_type") val cardType: String,
    @SerialName("position") val position: Int,
    @SerialName("target_difficulty") val targetDifficulty: String? = null,
    @SerialName("chapter_id") val chapterId: String? = null,
    @SerialName("source_task_id") val sourceTaskId: String? = null,
    @SerialName("publication_state") val publicationState: String? = null,
    @SerialName("version") val version: String? = null,
    @SerialName("review_state") val reviewState: ReviewStateDto? = null,
    @SerialName("plan_kind") val planKind: String? = null,
)

// --- account and preferences ---------------------------------------------------------------------

@Serializable
internal data class AuthUserDto(
    @SerialName("user_id") val userId: String,
    @SerialName("username") val username: String,
    @SerialName("email") val email: String,
    @SerialName("avatar_key") val avatarKey: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
internal data class AuthUserResponse(@SerialName("user") val user: AuthUserDto)

@Serializable
internal data class PreferencesDto(
    @SerialName("default_coverage_mode") val defaultCoverageMode: String,
    @SerialName("default_difficulty_ratio") val difficultyRatio: DifficultyRatioDto,
    @SerialName("daily_learning_goal") val dailyLearningGoal: Int,
    @SerialName("learning_timezone") val learningTimezone: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("current_project_id") val currentProjectId: String? = null,
)

// --- projects -------------------------------------------------------------------------------------

/**
 * One learning material wire shape (openapi Material, structure-contract 3.2a). `status` is
 * required on the wire (PDF rows always carry a pdf_files status; TEXT is always READY).
 */
@Serializable
internal data class MaterialDto(
    @SerialName("material_id") val materialId: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("type") val type: String,
    @SerialName("name") val name: String,
    @SerialName("status") val status: String,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("char_count") val charCount: Int? = null,
    @SerialName("chapter") val chapter: ChapterDto? = null,
    @SerialName("created_at") val createdAt: String,
)

/**
 * Project detail responses add a cross-material `chapters` summary (backend project_view)
 * that the openapi LearningProject schema leaves implicit; list responses omit it.
 */
@Serializable
internal data class ProjectDto(
    @SerialName("project_id") val projectId: String,
    @SerialName("name") val name: String,
    @SerialName("materials") val materials: List<MaterialDto>,
    @SerialName("status") val status: String,
    @SerialName("chapter_count") val chapterCount: Int,
    @SerialName("deck_count") val deckCount: Int,
    @SerialName("task_count") val taskCount: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("version") val version: String? = null,
    @SerialName("tasks") val tasks: List<TaskDto>? = null,
    @SerialName("chapters") val chapters: List<ChapterDto>? = null,
)

@Serializable
internal data class StudySettingsDto(
    @SerialName("selected_new_card_chapter_ids") val selectedNewCardChapterIds: List<String>,
    @SerialName("include_unassigned") val includeUnassigned: Boolean,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("selected_deck_ids") val selectedDeckIds: List<String>? = null,
    @SerialName("daily_new_goal") val dailyNewGoal: Int? = null,
    @SerialName("daily_review_goal") val dailyReviewGoal: Int? = null,
)

@Serializable
internal data class StudyPlanDto(
    @SerialName("configured") val configured: Boolean,
    @SerialName("daily_new_goal") val dailyNewGoal: Int,
    @SerialName("daily_review_goal") val dailyReviewGoal: Int,
    @SerialName("selected_deck_ids") val selectedDeckIds: List<String>,
    @SerialName("current_project_id") val currentProjectId: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

// --- generation tasks ------------------------------------------------------------------------------

@Serializable
internal data class SampleCardDto(
    @SerialName("front") val front: String,
    @SerialName("back") val back: String,
    @SerialName("card_type") val cardType: String,
    // openapi SampleCard.target_difficulty 为 nullable 且非 required；非空假设会让
    // 服务端缺省/为 null 时整个 task 载荷降级为 INVALID_RESPONSE。
    @SerialName("target_difficulty") val targetDifficulty: String? = null,
)

@Serializable
internal data class TaskDto(
    @SerialName("task_id") val taskId: String,
    @SerialName("status") val status: String,
    @SerialName("selected_chapters") val selectedChapters: List<ChapterDto>,
    @SerialName("generation_config") val generationConfig: GenerationConfigDto,
    @SerialName("generated_card_count") val generatedCardCount: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("file_id") val fileId: String? = null,
    @SerialName("deck_id") val deckId: String? = null,
    @SerialName("retry_of_task_id") val retryOfTaskId: String? = null,
    @SerialName("internal_stage") val internalStage: String? = null,
    @SerialName("sample_cards") val sampleCards: List<SampleCardDto>? = null,
    @SerialName("sample_config_hash") val sampleConfigHash: String? = null,
    @SerialName("sample_confirmed_at") val sampleConfirmedAt: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("failure_stage") val failureStage: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
)

@Serializable
internal data class TaskBlockerDto(
    @SerialName("task_id") val taskId: String,
    @SerialName("status") val status: String,
    @SerialName("can_abandon") val canAbandon: Boolean,
    @SerialName("allowed_actions") val allowedActions: List<String>,
    @SerialName("internal_stage") val internalStage: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("deck_id") val deckId: String? = null,
    @SerialName("can_cancel") val canCancel: Boolean? = null,
)

@Serializable
internal data class DeletionImpactDto(
    @SerialName("retain_decks") val retainDecks: Boolean? = null,
    @SerialName("deck_count") val deckCount: Int? = null,
    @SerialName("card_count") val cardCount: Int? = null,
    @SerialName("task_count") val taskCount: Int? = null,
    @SerialName("project_status") val projectStatus: String? = null,
    @SerialName("deck_name") val deckName: String? = null,
)

@Serializable
internal data class DeletionPreflightDto(
    @SerialName("resource_type") val resourceType: String,
    @SerialName("resource_id") val resourceId: String,
    @SerialName("can_delete") val canDelete: Boolean,
    @SerialName("blockers") val blockers: List<TaskBlockerDto>,
    @SerialName("abandonable_task_ids") val abandonableTaskIds: List<String>,
    @SerialName("has_uncancellable_tasks") val hasUncancellableTasks: Boolean,
    @SerialName("actions") val actions: List<String>,
    @SerialName("impact") val impact: DeletionImpactDto,
    @SerialName("cancelable_task_ids") val cancelableTaskIds: List<String>? = null,
    @SerialName("can_cancel") val canCancel: Boolean? = null,
)

// --- decks / cards / deletion batches / rewrites ----------------------------------------------------

@Serializable
internal data class DeckDto(
    @SerialName("deck_id") val deckId: String,
    @SerialName("name") val name: String,
    @SerialName("card_count") val cardCount: Int,
    @SerialName("due_count") val dueCount: Int,
    @SerialName("mastered_card_count") val masteredCardCount: Int,
    @SerialName("review_count") val reviewCount: Int,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("mastery_ratio") val masteryRatio: Double? = null,
    @SerialName("not_started_count") val notStartedCount: Int? = null,
    @SerialName("learning_count") val learningCount: Int? = null,
    @SerialName("relearning_count") val relearningCount: Int? = null,
    @SerialName("consolidating_count") val consolidatingCount: Int? = null,
    @SerialName("mastered_count") val masteredCount: Int? = null,
    @SerialName("review_event_count") val reviewEventCount: Int? = null,
    @SerialName("last_studied_at") val lastStudiedAt: String? = null,
)

@Serializable
internal data class ImportResultDto(
    @SerialName("index") val index: Int,
    @SerialName("status") val status: String,
    @SerialName("card_id") val cardId: String? = null,
)

@Serializable
internal data class ImportResponse(@SerialName("results") val results: List<ImportResultDto>)

@Serializable
internal data class DeletionBatchDto(
    @SerialName("delete_batch_id") val deleteBatchId: String,
    @SerialName("undo_until") val undoUntil: String,
    @SerialName("status") val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("card_ids") val cardIds: List<String>,
)

@Serializable
internal data class RewritePreviewDto(
    @SerialName("rewrite_id") val rewriteId: String,
    @SerialName("card_id") val cardId: String,
    @SerialName("base_card_version") val baseCardVersion: String,
    @SerialName("front") val front: String,
    @SerialName("back") val back: String,
    @SerialName("card_type") val cardType: String,
    @SerialName("status") val status: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("target_difficulty") val targetDifficulty: String? = null,
    @SerialName("custom_requirements") val customRequirements: String? = null,
)

// --- today plan / progress / stats -------------------------------------------------------------------

@Serializable
internal data class CurrentProjectDto(
    @SerialName("project_id") val projectId: String,
    @SerialName("name") val name: String,
)

@Serializable
internal data class TodayPlanDto(
    @SerialName("timezone") val timezone: String,
    @SerialName("study_date") val studyDate: String,
    @SerialName("daily_goal") val dailyGoal: Int,
    @SerialName("today_completed_count") val completedCount: Int,
    @SerialName("due_count") val dueCount: Int,
    @SerialName("main_plan_remaining") val planRemaining: Int,
    @SerialName("backlog_count") val backlogCount: Int,
    @SerialName("cards") val cards: List<CardDto>,
    @SerialName("current_project") val currentProject: CurrentProjectDto? = null,
    @SerialName("daily_new_goal") val dailyNewGoal: Int? = null,
    @SerialName("daily_review_goal") val dailyReviewGoal: Int? = null,
    @SerialName("new_completed_count") val newCompletedCount: Int? = null,
    @SerialName("review_completed_count") val reviewCompletedCount: Int? = null,
    @SerialName("new_remaining_count") val newRemainingCount: Int? = null,
    @SerialName("review_remaining_count") val reviewRemainingCount: Int? = null,
    @SerialName("core_target_count") val coreTargetCount: Int? = null,
    @SerialName("plan_configured") val planConfigured: Boolean? = null,
    @SerialName("selected_deck_ids") val selectedDeckIds: List<String>? = null,
)

@Serializable
internal data class ProgressDto(
    @SerialName("card_count") val cardCount: Int,
    @SerialName("due_count") val dueCount: Int,
    @SerialName("not_started_count") val notStartedCount: Int? = null,
    @SerialName("new_count") val newCount: Int? = null,
    @SerialName("mastered_count") val masteredCount: Int? = null,
    @SerialName("mastered_card_count") val masteredCardCount: Int? = null,
    @SerialName("learning_count") val learningCount: Int? = null,
    @SerialName("relearning_count") val relearningCount: Int? = null,
    @SerialName("consolidating_count") val consolidatingCount: Int? = null,
    @SerialName("review_event_count") val reviewEventCount: Int? = null,
    @SerialName("last_studied_at") val lastStudiedAt: String? = null,
)

@Serializable
internal data class DashboardPeriodDto(
    @SerialName("start") val start: String,
)

@Serializable
internal data class DashboardDto(
    @SerialName("period") val period: DashboardPeriodDto,
    @SerialName("timezone") val timezone: String,
    @SerialName("weekly_activity") val weeklyActivity: List<Int>,
    @SerialName("weekly_total") val weeklyTotal: Int,
    @SerialName("weekly_completed_count") val weeklyCompletedCount: Int,
    @SerialName("weekly_goal") val weeklyGoal: Int,
    @SerialName("streak_days") val streakDays: Int,
    @SerialName("mastered_card_count") val masteredCardCount: Int,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("has_data") val hasData: Boolean,
    @SerialName("week_change_rate") val weekChangeRate: Double? = null,
    @SerialName("weekly_goal_progress") val weeklyGoalProgress: Double? = null,
    @SerialName("recall_accuracy") val recallAccuracy: Double? = null,
    @SerialName("first_answer_accuracy") val firstAnswerAccuracy: Double? = null,
    @SerialName("retention_rate") val retentionRate: Double? = null,
)

@Serializable
internal data class RatingResultDto(
    @SerialName("review_state") val reviewState: ReviewStateDto,
    @SerialName("study_date") val studyDate: String,
)

@Serializable
internal data class ApiKeyStatusDto(
    @SerialName("status") val status: String,
    @SerialName("masked_key") val maskedKey: String? = null,
)

// --- request bodies ---------------------------------------------------------------------------------
// Fields that mean "omit when unset" carry `= null` defaults (encodeDefaults=false omits them);
// `SetCurrentProjectRequest` / `RewritePreviewRequest` have no default so an explicit JSON null
// is encoded (the wire field is `[string, 'null']`).

@Serializable
internal data class AuthMeUpdateRequest(
    @SerialName("username") val username: String? = null,
    @SerialName("avatar_key") val avatarKey: String? = null,
)

@Serializable
internal data class PreferencesPatchRequest(
    @SerialName("default_coverage_mode") val defaultCoverageMode: String? = null,
    @SerialName("default_difficulty_ratio") val difficultyRatio: DifficultyRatioDto? = null,
    @SerialName("daily_learning_goal") val dailyLearningGoal: Int? = null,
    @SerialName("learning_timezone") val learningTimezone: String? = null,
)

@Serializable
internal data class SetCurrentProjectRequest(@SerialName("current_project_id") val currentProjectId: String?)

/** POST /projects two-step creation, step one: the named EMPTY project (openapi inline body). */
@Serializable
internal data class CreateProjectRequest(@SerialName("name") val name: String)

/** POST /projects/{project_id}/materials/text: pasted text material (openapi TextMaterialCreateRequest). */
@Serializable
internal data class TextMaterialCreateRequest(
    @SerialName("name") val name: String,
    @SerialName("content") val content: String,
)

@Serializable
internal data class ChapterEditRequest(
    @SerialName("name") val name: String,
    @SerialName("start_page") val startPage: Int,
    @SerialName("end_page") val endPage: Int,
)

@Serializable
internal data class StudySettingsPatchRequest(
    @SerialName("selected_new_card_chapter_ids") val selectedNewCardChapterIds: List<String>? = null,
    @SerialName("include_unassigned") val includeUnassigned: Boolean? = null,
)

@Serializable
internal data class StudyPlanRequest(
    @SerialName("project_id") val projectId: String,
    @SerialName("selected_deck_ids") val selectedDeckIds: List<String>,
    @SerialName("daily_new_goal") val dailyNewGoal: Int,
    @SerialName("daily_review_goal") val dailyReviewGoal: Int,
)

@Serializable
internal data class GenerationConfigRequest(
    @SerialName("coverage_mode") val coverageMode: String,
    @SerialName("difficulty_ratio") val difficultyRatio: DifficultyRatioDto,
    @SerialName("custom_requirements") val customRequirements: String? = null,
)

@Serializable
internal data class TaskCreateRequest(
    @SerialName("deck_id") val deckId: String,
    @SerialName("chapter_ids") val chapterIds: List<String>,
    @SerialName("generation_config") val generationConfig: GenerationConfigRequest,
)

@Serializable
internal data class TaskConfigPatchRequest(
    @SerialName("deck_id") val deckId: String? = null,
    @SerialName("chapter_ids") val chapterIds: List<String>? = null,
    @SerialName("generation_config") val generationConfig: GenerationConfigRequest? = null,
)

@Serializable
internal data class CreateDeckRequest(
    @SerialName("name") val name: String,
    @SerialName("project_id") val projectId: String? = null,
)

@Serializable
internal data class RenameRequest(@SerialName("name") val name: String)

@Serializable
internal data class CardPatchRequest(
    @SerialName("front") val front: String,
    @SerialName("back") val back: String,
)

@Serializable
internal data class CardDraftDto(
    @SerialName("front") val front: String,
    @SerialName("back") val back: String,
)

@Serializable
internal data class CardsImportRequest(@SerialName("cards") val cards: List<CardDraftDto>)

@Serializable
internal data class ReviewEventRequest(
    @SerialName("card_id") val cardId: String,
    @SerialName("rating") val rating: String,
    @SerialName("client_event_id") val clientEventId: String,
)

@Serializable
internal data class ApiKeyRequest(@SerialName("api_key") val apiKey: String)

@Serializable
internal data class RewritePreviewRequest(@SerialName("custom_requirements") val customRequirements: String?)

// --- wire → domain mappers --------------------------------------------------------------------------

internal fun DifficultyRatioDto.toDomain(): V25DifficultyRatio =
    V25DifficultyRatio(basic = basic, understanding = understanding, deepQuestion = deepQuestion)

internal fun GenerationConfigDto.toDomain(): V25GenerationConfig = V25GenerationConfig(
    coverageMode = enumValueOf<V25CoverageMode>(coverageMode),
    difficultyRatio = difficultyRatio.toDomain(),
    customRequirements = customRequirements.orEmpty(),
)

internal fun ChapterDto.toDomain(): V25Chapter = V25Chapter(
    id = chapterId,
    materialId = materialId,
    name = name,
    startPage = startPage,
    endPage = endPage,
)

internal fun MaterialDto.toDomain(): V25Material = V25Material(
    materialId = materialId,
    projectId = projectId,
    type = enumValueOf<V25MaterialType>(type),
    name = name,
    status = enumValueOf<V25MaterialStatus>(status),
    errorCode = errorCode,
    sizeBytes = sizeBytes,
    charCount = charCount,
    chapter = chapter?.toDomain(),
    createdAt = parseIsoInstant(createdAt, "created_at"),
)

internal fun ProjectDto.toDomain(): V25LearningProject = V25LearningProject(
    projectId = projectId,
    name = name,
    materials = materials.map { it.toDomain() },
    status = enumValueOf<V25ProjectStatus>(status),
    chapterCount = chapterCount,
    deckCount = deckCount,
    taskCount = taskCount,
    createdAt = parseIsoInstant(createdAt, "created_at"),
    updatedAt = parseIsoInstant(updatedAt, "updated_at"),
    version = parseVersion(version),
    tasks = tasks.orEmpty().map { it.toDomain() },
    chapters = chapters.orEmpty().map { it.toDomain() },
)

internal fun AuthUserDto.toDomain(): V25AuthUser = V25AuthUser(
    userId = userId,
    username = username,
    email = email,
    avatarKey = enumValueOf<V25AvatarKey>(avatarKey),
    createdAt = parseIsoInstant(createdAt, "created_at"),
)

internal fun PreferencesDto.toDomain(): V25UserPreferences = V25UserPreferences(
    defaultCoverageMode = enumValueOf<V25CoverageMode>(defaultCoverageMode),
    difficultyRatio = difficultyRatio.toDomain(),
    dailyLearningGoal = dailyLearningGoal,
    learningTimezone = learningTimezone,
    currentProjectId = currentProjectId,
    updatedAt = parseIsoInstant(updatedAt, "updated_at"),
)

internal fun StudySettingsDto.toDomain(projectId: String): V25ProjectStudySettings =
    V25ProjectStudySettings(
        projectId = projectId,
        selectedNewCardChapterIds = selectedNewCardChapterIds,
        includeUnassigned = includeUnassigned,
        updatedAt = parseIsoInstant(updatedAt, "updated_at"),
        selectedDeckIds = selectedDeckIds.orEmpty(),
        dailyNewGoal = dailyNewGoal ?: 10,
        dailyReviewGoal = dailyReviewGoal ?: 40,
    )

internal fun StudyPlanDto.toDomain(): V25StudyPlan = V25StudyPlan(
    configured = configured,
    currentProjectId = currentProjectId,
    selectedDeckIds = selectedDeckIds,
    dailyNewGoal = dailyNewGoal,
    dailyReviewGoal = dailyReviewGoal,
    updatedAt = updatedAt?.let { parseIsoInstant(it, "updated_at") },
)

internal fun SampleCardDto.toDomain(): V25SampleCard = V25SampleCard(
    front = front,
    back = back,
    cardType = enumValueOf<V25CardType>(cardType),
    targetDifficulty = targetDifficulty?.let { enumValueOf<V25Difficulty>(it) },
)

internal fun TaskDto.toDomain(): V25GenerationTask = V25GenerationTask(
    taskId = taskId,
    projectId = projectId,
    fileId = fileId,
    deckId = deckId,
    retryOfTaskId = retryOfTaskId,
    status = enumValueOf<V25TaskStatus>(status),
    internalStage = internalStage?.let { enumValueOf<V25InternalStage>(it) },
    selectedChapters = selectedChapters.map { it.toDomain() },
    generationConfig = generationConfig.toDomain(),
    sampleCards = sampleCards.orEmpty().map { it.toDomain() },
    sampleConfigHash = sampleConfigHash,
    sampleConfirmedAt = sampleConfirmedAt?.let { parseIsoInstant(it, "sample_confirmed_at") },
    generatedCardCount = generatedCardCount,
    errorCode = errorCode,
    failureStage = failureStage,
    createdAt = parseIsoInstant(createdAt, "created_at"),
    startedAt = startedAt?.let { parseIsoInstant(it, "started_at") },
    endedAt = endedAt?.let { parseIsoInstant(it, "ended_at") },
    updatedAt = parseIsoInstant(updatedAt, "updated_at"),
)

internal fun TaskBlockerDto.toDomain(): V25DeletionTaskBlocker = V25DeletionTaskBlocker(
    taskId = taskId,
    status = enumValueOf<V25TaskStatus>(status),
    internalStage = internalStage?.let { enumValueOf<V25InternalStage>(it) },
    projectId = projectId,
    deckId = deckId,
    canAbandon = canAbandon,
    allowedActions = allowedActions,
    canCancel = canCancel ?: false,
)

internal fun DeletionPreflightDto.toDomain(): V25DeletionPreflight = V25DeletionPreflight(
    resourceType = resourceType,
    resourceId = resourceId,
    canDelete = canDelete,
    blockers = blockers.map { it.toDomain() },
    abandonableTaskIds = abandonableTaskIds,
    hasUncancellableTasks = hasUncancellableTasks,
    actions = actions,
    impact = V25DeletionImpact(
        retainDecks = impact.retainDecks,
        deckCount = impact.deckCount ?: 0,
        cardCount = impact.cardCount ?: 0,
        taskCount = impact.taskCount ?: 0,
        projectStatus = impact.projectStatus?.let { enumValueOf<V25ProjectStatus>(it) },
        deckName = impact.deckName,
    ),
    cancelableTaskIds = cancelableTaskIds.orEmpty(),
    canCancel = canCancel ?: false,
)

internal fun DeckDto.toDomain(): V25Deck = V25Deck(
    deckId = deckId,
    name = name,
    projectId = projectId,
    cardCount = cardCount,
    dueCount = dueCount,
    masteredCards = masteredCardCount,
    reviewCount = reviewCount,
    masteryRatio = masteryRatio?.toFloat(),
    notStartedCount = notStartedCount ?: 0,
    learningCount = learningCount ?: 0,
    relearningCount = relearningCount ?: 0,
    consolidatingCount = consolidatingCount ?: 0,
    masteredCount = masteredCount ?: 0,
    reviewEventCount = reviewEventCount ?: 0,
    lastStudiedAt = lastStudiedAt?.let { parseIsoInstant(it, "last_studied_at") },
)

internal fun ReviewStateDto.toDomain(): V25ReviewState = V25ReviewState(
    state = state,
    due = due?.let { parseIsoInstant(it, "due") },
)

/** ReviewQueueItem / TodayPlanCard: the card fields are flattened next to review_state. */
internal fun CardDto.toCard(): V25Card = V25Card(
    cardId = cardId,
    deckId = deckId,
    front = front,
    back = back,
    cardType = enumValueOf<V25CardType>(cardType),
    targetDifficulty = targetDifficulty?.let { enumValueOf<V25Difficulty>(it) },
    position = position,
    chapterId = chapterId,
    sourceTaskId = sourceTaskId,
    publicationState = publicationState?.let { enumValueOf<V25PublicationState>(it) }
        ?: V25PublicationState.PUBLISHED,
    version = parseVersion(version),
)

internal fun CardDto.toReviewCard(): V25ReviewCard =
    V25ReviewCard(card = toCard(), reviewState = reviewState?.toDomain())

internal fun CardDto.toPlanCard(): V25PlanCard = V25PlanCard(
    card = toCard(),
    // plan_kind 是服务端的权威判定；仅当 wire 未携带（null）时回落到本地状态推断。
    isNew = planKind?.let { it == "NEW" } ?: (reviewState?.state == "NEW"),
    reviewState = reviewState?.toDomain(),
    planKind = planKind,
)

internal fun ImportResultDto.toDomain(): V25ImportResult = V25ImportResult(
    index = index,
    status = enumValueOf<V25ImportStatus>(status),
    cardId = cardId,
)

internal fun DeletionBatchDto.toDomain(): V25CardDeletionBatch = V25CardDeletionBatch(
    deleteBatchId = deleteBatchId,
    cardIds = cardIds,
    undoUntil = parseIsoInstant(undoUntil, "undo_until"),
    status = enumValueOf<V25DeletionBatchStatus>(status),
    createdAt = parseIsoInstant(createdAt, "created_at"),
    updatedAt = parseIsoInstant(updatedAt, "updated_at"),
)

internal fun RewritePreviewDto.toDomain(): V25CardRewritePreview = V25CardRewritePreview(
    rewriteId = rewriteId,
    cardId = cardId,
    baseCardVersion = baseCardVersion,
    front = front,
    back = back,
    cardType = enumValueOf<V25CardType>(cardType),
    targetDifficulty = targetDifficulty?.let { enumValueOf<V25Difficulty>(it) },
    customRequirements = customRequirements,
    status = enumValueOf<V25RewriteStatus>(status),
    expiresAt = parseIsoInstant(expiresAt, "expires_at"),
)

internal fun TodayPlanDto.toDomain(): V25TodayPlan = V25TodayPlan(
    learningTimezone = timezone,
    studyDate = LocalDate.parse(studyDate),
    currentProject = currentProject?.let { V25CurrentProject(it.projectId, it.name) },
    dailyGoal = dailyGoal,
    completedCount = completedCount,
    dueCount = dueCount,
    planRemaining = planRemaining,
    backlogCount = backlogCount,
    cards = cards.map { it.toPlanCard() },
    dailyNewGoal = dailyNewGoal ?: 0,
    dailyReviewGoal = dailyReviewGoal ?: 0,
    newCompletedCount = newCompletedCount ?: 0,
    reviewCompletedCount = reviewCompletedCount ?: 0,
    newRemainingCount = newRemainingCount ?: 0,
    reviewRemainingCount = reviewRemainingCount ?: 0,
    coreTargetCount = coreTargetCount ?: 0,
    planConfigured = planConfigured ?: false,
    selectedDeckIds = selectedDeckIds.orEmpty(),
)

internal fun ProgressDto.toDomain(scopeId: String, scopeName: String, isProject: Boolean): V25ProgressSummary {
    val notStarted = notStartedCount ?: newCount ?: 0
    val mastered = masteredCount ?: masteredCardCount ?: 0
    return V25ProgressSummary(
        scopeId = scopeId,
        scopeName = scopeName,
        isProject = isProject,
        cardCount = cardCount,
        newCount = notStarted,
        learnedCount = (cardCount - notStarted).coerceAtLeast(0),
        dueCount = dueCount,
        masteredCount = mastered,
        masteryRatio = if (cardCount > 0) mastered.toFloat() / cardCount else null,
        notStartedCount = notStarted,
        learningCount = learningCount ?: 0,
        relearningCount = relearningCount ?: 0,
        consolidatingCount = consolidatingCount ?: 0,
        reviewEventCount = reviewEventCount ?: 0,
        lastStudiedAt = lastStudiedAt?.let { parseIsoInstant(it, "last_studied_at") },
    )
}

internal fun DashboardDto.toDomain(): V25StatsDashboard {
    // Backend format_utc always emits period bounds in UTC ("...T16:00:00.000Z"); the
    // dashboard's required timezone field names the actual bucketing zone (openapi: 实际分桶
    // 时区 = 账号学习时区). period.start is Monday midnight in that zone, so project the
    // instant through it — reading the raw UTC instant would shift every studyDate back a day
    // for UTC+ zones.
    val zoneId = ZoneId.of(timezone)
    val weekStart = parseIsoInstant(period.start, "period.start").atZone(zoneId).toLocalDate()
    return V25StatsDashboard(
        hasData = hasData,
        weeklyActivity = weeklyActivity.mapIndexed { index, ratingCount ->
            V25DailyActivity(studyDate = weekStart.plusDays(index.toLong()), ratingCount = ratingCount)
        },
        weeklyTotalRatings = weeklyTotal,
        weeklyChangeRate = weekChangeRate?.toFloat(),
        weeklyGoal = weeklyGoal,
        weeklyGoalCompleted = weeklyCompletedCount,
        weeklyGoalRate = weeklyGoalProgress?.toFloat(),
        recallAccuracy = recallAccuracy?.toFloat(),
        firstAttemptAccuracy = firstAnswerAccuracy?.toFloat(),
        retentionRate = retentionRate?.toFloat(),
        streakDays = streakDays,
        masteredCards = masteredCardCount,
        // The dashboard wire carries no per-scope progress summaries (V25-STATS-FR-05 is not
        // part of the StatsDashboard resource yet); keep the honest empty list instead of
        // fabricating numbers. Project/deck progress is derivable from decks + cards instead.
        progress = emptyList(),
        updatedAt = parseIsoInstant(updatedAt, "updated_at"),
    )
}

internal fun RatingResultDto.toDomain(): V25RatingResult = V25RatingResult(
    reviewState = reviewState.toDomain(),
    studyDate = LocalDate.parse(studyDate),
)

internal fun ApiKeyStatusDto.toDomain(): V25ApiKeyStatus = V25ApiKeyStatus(
    state = apiKeyState(status),
    maskedKey = maskedKey?.takeIf { it.isNotBlank() },
)

internal fun apiKeyState(wire: String): V25ApiKeyState = when (wire) {
    "AVAILABLE" -> V25ApiKeyState.AVAILABLE
    "INVALID" -> V25ApiKeyState.INVALID
    "INSUFFICIENT_BALANCE" -> V25ApiKeyState.INSUFFICIENT_BALANCE
    // structure-contract 3.1: UNKNOWN = 未保存 Key（masked_key 为空串）→ 域模型 UNSET。
    "UNKNOWN" -> V25ApiKeyState.UNSET
    else -> throw IllegalArgumentException("unknown api key status '$wire'")
}

// --- domain → request mappers -------------------------------------------------------------------------

internal fun V25DifficultyRatio.toWire(): DifficultyRatioDto =
    DifficultyRatioDto(basic = basic, understanding = understanding, deepQuestion = deepQuestion)

internal fun V25GenerationConfig.toWire(): GenerationConfigRequest = GenerationConfigRequest(
    coverageMode = coverageMode.name,
    difficultyRatio = difficultyRatio.toWire(),
    customRequirements = customRequirements.takeIf { it.isNotBlank() },
)

internal fun V25PreferencesPatch.toWire(): PreferencesPatchRequest = PreferencesPatchRequest(
    defaultCoverageMode = defaultCoverageMode?.name,
    difficultyRatio = difficultyRatio?.toWire(),
    dailyLearningGoal = dailyLearningGoal,
    learningTimezone = learningTimezone,
)

internal fun V25StudyPlanUpdate.toWire(): StudyPlanRequest = StudyPlanRequest(
    projectId = currentProjectId,
    selectedDeckIds = selectedDeckIds,
    dailyNewGoal = dailyNewGoal,
    dailyReviewGoal = dailyReviewGoal,
)

internal fun V25TaskConfigPatch.toWire(): TaskConfigPatchRequest = TaskConfigPatchRequest(
    deckId = deckId,
    chapterIds = chapterIds,
    generationConfig = generationConfig?.toWire(),
)

// --- wire primitives -----------------------------------------------------------------------------------

/** The backend serializes UTC datetimes with a `+00:00` offset; tolerate `Z` and sub-second precision. */
internal fun parseIsoInstant(wire: String, key: String): Instant =
    runCatching { Instant.parse(wire) }
        .getOrElse {
            runCatching { OffsetDateTime.parse(wire).toInstant() }
                .getOrElse { throw IllegalArgumentException("invalid $key timestamp '$wire'") }
        }

/** Wire `version` is a string: `v\d+` (rewrite CAS) or an ISO timestamp (`version=now`). */
internal fun parseVersion(wire: String?): Int = when {
    wire == null -> 0
    wire.startsWith("v") -> wire.drop(1).toIntOrNull() ?: 0
    else -> runCatching { Instant.parse(wire).epochSecond.toInt() }.getOrDefault(0)
}
