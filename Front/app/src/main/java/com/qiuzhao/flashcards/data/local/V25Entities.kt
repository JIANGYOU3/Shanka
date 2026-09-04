package com.qiuzhao.flashcards.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local-fact projection of the V2.5 server state (`shanka-v25.db`). Every business table is
 * isolated by `user_id` (multi-account device: no cross-account reads are possible because
 * every primary key and query carries the user), and every server UUID is stored as a String.
 * Timestamps are epoch millis; dates are ISO `LocalDate` strings.
 *
 * This is a read-model cache of server-authoritative data, never a second scheduler: FSRS
 * scheduling stays server-side and the client only stores what the server computed.
 */

@Entity(tableName = "projects", primaryKeys = ["user_id", "project_id"])
data class ProjectEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "chapter_count") val chapterCount: Int,
    @ColumnInfo(name = "deck_count") val deckCount: Int,
    @ColumnInfo(name = "task_count") val taskCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "version") val version: Int,
)

/**
 * One learning material of a project (structure-contract 3.2a). The material id is the cache
 * key because materials are added and deleted inside one living project; `project_id` scopes
 * the per-project reads.
 */
@Entity(
    tableName = "project_materials",
    primaryKeys = ["user_id", "material_id"],
    indices = [Index(value = ["user_id", "project_id"])],
)
data class ProjectMaterialEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "material_id") val materialId: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    /** Wire PDF/TEXT (structure-contract 3.2a). */
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "error_code") val errorCode: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    /** TEXT-only character count. */
    @ColumnInfo(name = "char_count") val charCount: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "project_chapters", primaryKeys = ["user_id", "chapter_id"])
data class ProjectChapterEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    /** Owning material (structure-contract 3.2a); TEXT chapters own their material's single chapter. */
    @ColumnInfo(name = "material_id") val materialId: String,
    @ColumnInfo(name = "name") val name: String,
    /** Page spans are PDF-only; TEXT chapters store null. */
    @ColumnInfo(name = "start_page") val startPage: Int?,
    @ColumnInfo(name = "end_page") val endPage: Int?,
    @ColumnInfo(name = "position") val position: Int,
)

/**
 * Light status-carrying projection of one generation task (V25-D-34): the fields an observing
 * surface renders, deliberately without sample/chapter/config payloads (those come from an
 * on-demand full `getTask`). Written by every task-returning repository call and by the
 * observation engine's polls; read through Room flows by every status surface.
 */
@Entity(
    tableName = "generation_tasks",
    primaryKeys = ["user_id", "task_id"],
    indices = [Index(value = ["user_id", "project_id"])],
)
data class GenerationTaskEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "project_id") val projectId: String?,
    @ColumnInfo(name = "deck_id") val deckId: String?,
    @ColumnInfo(name = "retry_of_task_id") val retryOfTaskId: String?,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "internal_stage") val internalStage: String?,
    @ColumnInfo(name = "generated_card_count") val generatedCardCount: Int,
    @ColumnInfo(name = "error_code") val errorCode: String?,
    @ColumnInfo(name = "failure_stage") val failureStage: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "decks", primaryKeys = ["user_id", "deck_id"])
data class DeckEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "deck_id") val deckId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "project_id") val projectId: String?,
    @ColumnInfo(name = "card_count") val cardCount: Int,
    @ColumnInfo(name = "due_count") val dueCount: Int,
    @ColumnInfo(name = "mastered_card_count") val masteredCards: Int,
    @ColumnInfo(name = "review_count") val reviewCount: Int,
    @ColumnInfo(name = "mastery_ratio") val masteryRatio: Double?,
    @ColumnInfo(name = "not_started_count") val notStartedCount: Int,
    @ColumnInfo(name = "learning_count") val learningCount: Int,
    @ColumnInfo(name = "relearning_count") val relearningCount: Int,
    @ColumnInfo(name = "consolidating_count") val consolidatingCount: Int,
    @ColumnInfo(name = "mastered_count") val masteredCount: Int,
    @ColumnInfo(name = "review_event_count") val reviewEventCount: Int,
    @ColumnInfo(name = "last_studied_at") val lastStudiedAt: Long?,
)

@Entity(tableName = "cards", primaryKeys = ["user_id", "card_id"])
data class CardEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "card_id") val cardId: String,
    @ColumnInfo(name = "deck_id") val deckId: String,
    @ColumnInfo(name = "front") val front: String,
    @ColumnInfo(name = "back") val back: String,
    @ColumnInfo(name = "card_type") val cardType: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "target_difficulty") val targetDifficulty: String?,
    @ColumnInfo(name = "chapter_id") val chapterId: String?,
    @ColumnInfo(name = "source_task_id") val sourceTaskId: String?,
    @ColumnInfo(name = "publication_state") val publicationState: String?,
    @ColumnInfo(name = "version") val version: Int,
)

/** The server-owned FSRS projection of one card; written from review payloads, never predicted. */
@Entity(tableName = "review_states", primaryKeys = ["user_id", "card_id"])
data class ReviewStateEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "card_id") val cardId: String,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "due") val due: Long?,
    @ColumnInfo(name = "synced_at") val syncedAt: Long,
)

/** Snapshot of GET /decks/{id}/review ordering; replaced per deck inside one transaction. */
@Entity(tableName = "review_queue", primaryKeys = ["user_id", "deck_id", "position"])
data class ReviewQueueItemEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "deck_id") val deckId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "card_id") val cardId: String,
)

@Entity(tableName = "study_plan", primaryKeys = ["user_id"])
data class StudyPlanEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "configured") val configured: Boolean,
    @ColumnInfo(name = "current_project_id") val currentProjectId: String?,
    @ColumnInfo(name = "selected_deck_ids") val selectedDeckIds: String,
    @ColumnInfo(name = "daily_new_goal") val dailyNewGoal: Int,
    @ColumnInfo(name = "daily_review_goal") val dailyReviewGoal: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
)

/** Keyed user_id + study_date: a plan from another study date is never today's authority. */
@Entity(tableName = "today_plan", primaryKeys = ["user_id", "study_date"])
data class TodayPlanEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "study_date") val studyDate: String,
    @ColumnInfo(name = "timezone") val timezone: String,
    @ColumnInfo(name = "current_project_id") val currentProjectId: String?,
    @ColumnInfo(name = "current_project_name") val currentProjectName: String?,
    @ColumnInfo(name = "daily_goal") val dailyGoal: Int,
    @ColumnInfo(name = "today_completed_count") val completedCount: Int,
    @ColumnInfo(name = "due_count") val dueCount: Int,
    @ColumnInfo(name = "main_plan_remaining") val planRemaining: Int,
    @ColumnInfo(name = "backlog_count") val backlogCount: Int,
    @ColumnInfo(name = "daily_new_goal") val dailyNewGoal: Int,
    @ColumnInfo(name = "daily_review_goal") val dailyReviewGoal: Int,
    @ColumnInfo(name = "new_completed_count") val newCompletedCount: Int,
    @ColumnInfo(name = "review_completed_count") val reviewCompletedCount: Int,
    @ColumnInfo(name = "new_remaining_count") val newRemainingCount: Int,
    @ColumnInfo(name = "review_remaining_count") val reviewRemainingCount: Int,
    @ColumnInfo(name = "core_target_count") val coreTargetCount: Int,
    @ColumnInfo(name = "plan_configured") val planConfigured: Boolean,
    @ColumnInfo(name = "selected_deck_ids") val selectedDeckIds: String,
)

@Entity(tableName = "today_plan_cards", primaryKeys = ["user_id", "study_date", "position"])
data class TodayPlanCardEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "study_date") val studyDate: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "card_id") val cardId: String,
    @ColumnInfo(name = "plan_kind") val planKind: String?,
    @ColumnInfo(name = "is_new") val isNew: Boolean,
    /** Marked when the swipe already landed in the outbox; the queue advance hides the card. */
    @ColumnInfo(name = "hidden") val hidden: Boolean = false,
)

@Entity(tableName = "project_progress", primaryKeys = ["user_id", "project_id"])
data class ProjectProgressEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "card_count") val cardCount: Int,
    @ColumnInfo(name = "not_started_count") val notStartedCount: Int,
    @ColumnInfo(name = "learning_count") val learningCount: Int,
    @ColumnInfo(name = "relearning_count") val relearningCount: Int,
    @ColumnInfo(name = "consolidating_count") val consolidatingCount: Int,
    @ColumnInfo(name = "mastered_count") val masteredCount: Int,
    @ColumnInfo(name = "due_count") val dueCount: Int,
    @ColumnInfo(name = "review_event_count") val reviewEventCount: Int,
    @ColumnInfo(name = "last_studied_at") val lastStudiedAt: Long?,
)

/** One snapshot per user of the server dashboard; nullable rates stay null when unset. */
@Entity(tableName = "dashboard_snapshot", primaryKeys = ["user_id"])
data class DashboardEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "has_data") val hasData: Boolean,
    @ColumnInfo(name = "week_start_date") val weekStartDate: String,
    @ColumnInfo(name = "weekly_activity") val weeklyActivity: String,
    @ColumnInfo(name = "weekly_total") val weeklyTotal: Int,
    @ColumnInfo(name = "weekly_change_rate") val weeklyChangeRate: Double?,
    @ColumnInfo(name = "weekly_goal") val weeklyGoal: Int,
    @ColumnInfo(name = "weekly_completed_count") val weeklyCompletedCount: Int,
    @ColumnInfo(name = "weekly_goal_progress") val weeklyGoalProgress: Double?,
    @ColumnInfo(name = "recall_accuracy") val recallAccuracy: Double?,
    @ColumnInfo(name = "first_answer_accuracy") val firstAttemptAccuracy: Double?,
    @ColumnInfo(name = "retention_rate") val retentionRate: Double?,
    @ColumnInfo(name = "streak_days") val streakDays: Int,
    @ColumnInfo(name = "mastered_card_count") val masteredCards: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * Typed cache metadata per resource key: server version/updated_at when the payload carries
 * one, fetch time and the schema version of the projection that wrote it. The soft TTLs of
 * the stale-while-revalidate reads are judged against `fetched_at`.
 */
@Entity(tableName = "cache_metadata", primaryKeys = ["user_id", "resource_key"])
data class CacheMetadataEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "resource_key") val resourceKey: String,
    @ColumnInfo(name = "server_version") val serverVersion: String?,
    @ColumnInfo(name = "server_updated_at") val serverUpdatedAt: Long?,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
)

/** Outbox row lifecycle: PENDING → (retry PENDING…) → COMPLETED | FAILED. */
object OutboxStatus {
    const val PENDING = "PENDING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
}

/**
 * One pending review event. The two dedupe identities are fixed at enqueue time and survive
 * every retry and process death: `client_event_id` (service-layer fallback dedupe) and
 * `idempotency_key` (transport Idempotency-Key), covered by its own unique constraint.
 */
@Entity(
    tableName = "review_outbox",
    primaryKeys = ["user_id", "client_event_id"],
    indices = [Index(value = ["user_id", "idempotency_key"], unique = true)],
)
data class ReviewOutboxEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "client_event_id") val clientEventId: String,
    @ColumnInfo(name = "card_id") val cardId: String,
    @ColumnInfo(name = "rating") val rating: String,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String?,
)
