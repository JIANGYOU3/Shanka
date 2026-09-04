package com.qiuzhao.flashcards.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Every query names its `user_id`: account isolation is enforced by the query itself, not by
 * caller discipline. List refreshes delete-then-insert the owned scope inside one transaction
 * (see [V25CacheStore]); a failed network fetch therefore never touches these tables and the
 * last successful cache survives.
 */
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE user_id = :userId ORDER BY created_at")
    suspend fun getProjectList(userId: String): List<ProjectEntity>

    /** Live project rows: a parse-status advance re-emits without any screen-driven polling. */
    @Query("SELECT * FROM projects WHERE user_id = :userId ORDER BY created_at")
    fun observeProjectList(userId: String): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE user_id = :userId AND project_id = :projectId")
    suspend fun getProject(userId: String, projectId: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE user_id = :userId AND project_id = :projectId")
    fun observeProjectRow(userId: String, projectId: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM project_materials WHERE user_id = :userId AND project_id = :projectId ORDER BY created_at")
    suspend fun getMaterials(userId: String, projectId: String): List<ProjectMaterialEntity>

    @Query("SELECT * FROM project_chapters WHERE user_id = :userId AND project_id = :projectId ORDER BY position")
    suspend fun getChapters(userId: String, projectId: String): List<ProjectChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjects(projects: List<ProjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterials(materials: List<ProjectMaterialEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ProjectChapterEntity>)

    @Query("DELETE FROM projects WHERE user_id = :userId")
    suspend fun deleteProjects(userId: String)

    @Query("DELETE FROM project_materials WHERE user_id = :userId")
    suspend fun deleteMaterials(userId: String)

    @Query("DELETE FROM project_chapters WHERE user_id = :userId")
    suspend fun deleteChapters(userId: String)

    @Query("DELETE FROM projects WHERE user_id = :userId AND project_id = :projectId")
    suspend fun deleteProject(userId: String, projectId: String)

    @Query("DELETE FROM project_materials WHERE user_id = :userId AND project_id = :projectId")
    suspend fun deleteMaterialsOf(userId: String, projectId: String)

    @Query("DELETE FROM project_chapters WHERE user_id = :userId AND project_id = :projectId")
    suspend fun deleteChaptersOf(userId: String, projectId: String)
}

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks WHERE user_id = :userId ORDER BY name")
    fun observeDecks(userId: String): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE user_id = :userId")
    suspend fun getDecks(userId: String): List<DeckEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecks(decks: List<DeckEntity>)

    @Query("DELETE FROM decks WHERE user_id = :userId")
    suspend fun deleteDecks(userId: String)
}

/**
 * Task status projection (V25-D-34). Every read is reactive: the observation engine's polls and
 * each task-returning repository call write here, and every status surface re-emits without
 * running a poll loop of its own.
 */
@Dao
interface TaskDao {
    @Query("SELECT * FROM generation_tasks WHERE user_id = :userId ORDER BY created_at DESC")
    fun observeAllTasks(userId: String): Flow<List<GenerationTaskEntity>>

    @Query(
        "SELECT * FROM generation_tasks WHERE user_id = :userId AND project_id = :projectId " +
            "ORDER BY created_at DESC",
    )
    fun observeProjectTasks(userId: String, projectId: String): Flow<List<GenerationTaskEntity>>

    @Query("SELECT * FROM generation_tasks WHERE user_id = :userId AND task_id = :taskId")
    fun observeTask(userId: String, taskId: String): Flow<GenerationTaskEntity?>

    @Query("SELECT * FROM generation_tasks WHERE user_id = :userId AND task_id = :taskId")
    suspend fun getTask(userId: String, taskId: String): GenerationTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<GenerationTaskEntity>)

    @Query("DELETE FROM generation_tasks WHERE user_id = :userId AND project_id = :projectId")
    suspend fun deleteTasksOf(userId: String, projectId: String)

    @Query("DELETE FROM generation_tasks WHERE user_id = :userId")
    suspend fun deleteTasks(userId: String)
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE user_id = :userId AND deck_id = :deckId ORDER BY position")
    fun observeDeckCards(userId: String, deckId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE user_id = :userId AND deck_id = :deckId ORDER BY position")
    suspend fun getDeckCards(userId: String, deckId: String): List<CardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<CardEntity>)

    @Query("DELETE FROM cards WHERE user_id = :userId AND deck_id = :deckId")
    suspend fun deleteDeckCards(userId: String, deckId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReviewStates(states: List<ReviewStateEntity>)

    @Query("SELECT * FROM review_states WHERE user_id = :userId AND card_id = :cardId")
    suspend fun getReviewState(userId: String, cardId: String): ReviewStateEntity?
}

@Dao
interface ReviewQueueDao {
    @Query(
        "SELECT cards.user_id AS card_user_id, cards.card_id AS card_card_id, " +
            "cards.deck_id AS card_deck_id, cards.front AS card_front, cards.back AS card_back, " +
            "cards.card_type AS card_card_type, cards.position AS card_position, " +
            "cards.target_difficulty AS card_target_difficulty, cards.chapter_id AS card_chapter_id, " +
            "cards.source_task_id AS card_source_task_id, " +
            "cards.publication_state AS card_publication_state, cards.version AS card_version, " +
            "review_states.user_id AS state_user_id, review_states.card_id AS state_card_id, " +
            "review_states.state AS state_state, review_states.due AS state_due, " +
            "review_states.synced_at AS state_synced_at " +
            "FROM review_queue LEFT JOIN cards ON cards.user_id = review_queue.user_id " +
            "AND cards.card_id = review_queue.card_id " +
            "LEFT JOIN review_states ON review_states.user_id = review_queue.user_id " +
            "AND review_states.card_id = review_queue.card_id " +
            "WHERE review_queue.user_id = :userId AND review_queue.deck_id = :deckId " +
            "ORDER BY review_queue.position",
    )
    suspend fun getDeckQueueWithState(userId: String, deckId: String): List<DeckQueueCardProjection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(items: List<ReviewQueueItemEntity>)

    @Query("DELETE FROM review_queue WHERE user_id = :userId AND deck_id = :deckId")
    suspend fun deleteQueue(userId: String, deckId: String)

    /** The swipe transaction: hide the card from its deck queue before the card advances. */
    @Query("DELETE FROM review_queue WHERE user_id = :userId AND card_id = :cardId")
    suspend fun removeFromQueue(userId: String, cardId: String)

    @Query("UPDATE today_plan_cards SET hidden = 1 WHERE user_id = :userId AND card_id = :cardId")
    suspend fun hideFromTodayPlan(userId: String, cardId: String)
}

@Dao
interface StudyPlanDao {
    @Query("SELECT * FROM study_plan WHERE user_id = :userId")
    suspend fun getStudyPlan(userId: String): StudyPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: StudyPlanEntity)

}

@Dao
interface TodayPlanDao {
    @Query(
        "SELECT today_plan_cards.*, " +
            "cards.user_id AS card_user_id, cards.card_id AS card_card_id, " +
            "cards.deck_id AS card_deck_id, cards.front AS card_front, cards.back AS card_back, " +
            "cards.card_type AS card_card_type, cards.position AS card_position, " +
            "cards.target_difficulty AS card_target_difficulty, cards.chapter_id AS card_chapter_id, " +
            "cards.source_task_id AS card_source_task_id, " +
            "cards.publication_state AS card_publication_state, cards.version AS card_version, " +
            "review_states.user_id AS state_user_id, review_states.card_id AS state_card_id, " +
            "review_states.state AS state_state, review_states.due AS state_due, " +
            "review_states.synced_at AS state_synced_at " +
            "FROM today_plan_cards " +
            "LEFT JOIN cards ON cards.user_id = today_plan_cards.user_id " +
            "AND cards.card_id = today_plan_cards.card_id " +
            "LEFT JOIN review_states ON review_states.user_id = today_plan_cards.user_id " +
            "AND review_states.card_id = today_plan_cards.card_id " +
            "WHERE today_plan_cards.user_id = :userId AND today_plan_cards.study_date = :studyDate " +
            "AND today_plan_cards.hidden = 0 ORDER BY today_plan_cards.position",
    )
    suspend fun getVisiblePlanCards(userId: String, studyDate: String): List<TodayPlanCardProjection>

    @Query(
        "SELECT today_plan_cards.*, " +
            "cards.user_id AS card_user_id, cards.card_id AS card_card_id, " +
            "cards.deck_id AS card_deck_id, cards.front AS card_front, cards.back AS card_back, " +
            "cards.card_type AS card_card_type, cards.position AS card_position, " +
            "cards.target_difficulty AS card_target_difficulty, cards.chapter_id AS card_chapter_id, " +
            "cards.source_task_id AS card_source_task_id, " +
            "cards.publication_state AS card_publication_state, cards.version AS card_version, " +
            "review_states.user_id AS state_user_id, review_states.card_id AS state_card_id, " +
            "review_states.state AS state_state, review_states.due AS state_due, " +
            "review_states.synced_at AS state_synced_at " +
            "FROM today_plan_cards " +
            "LEFT JOIN cards ON cards.user_id = today_plan_cards.user_id " +
            "AND cards.card_id = today_plan_cards.card_id " +
            "LEFT JOIN review_states ON review_states.user_id = today_plan_cards.user_id " +
            "AND review_states.card_id = today_plan_cards.card_id " +
            "WHERE today_plan_cards.user_id = :userId AND today_plan_cards.study_date = :studyDate " +
            "AND today_plan_cards.hidden = 0 ORDER BY today_plan_cards.position",
    )
    fun observeVisiblePlanCards(userId: String, studyDate: String): Flow<List<TodayPlanCardProjection>>

    @Query("SELECT * FROM today_plan WHERE user_id = :userId AND study_date = :studyDate")
    suspend fun getTodayPlan(userId: String, studyDate: String): TodayPlanEntity?

    @Query("SELECT * FROM today_plan WHERE user_id = :userId AND study_date = :studyDate")
    fun observeTodayPlan(userId: String, studyDate: String): Flow<TodayPlanEntity?>

    @Query("SELECT study_date FROM today_plan WHERE user_id = :userId ORDER BY study_date DESC LIMIT 1")
    suspend fun latestStudyDate(userId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: TodayPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<TodayPlanCardEntity>)

    /** Old study dates are not today's authority; keep the table bounded to the latest date. */
    @Query("DELETE FROM today_plan WHERE user_id = :userId AND study_date != :studyDate")
    suspend fun deleteOtherDates(userId: String, studyDate: String)

    @Query("DELETE FROM today_plan_cards WHERE user_id = :userId AND study_date != :studyDate")
    suspend fun deleteOtherDateCards(userId: String, studyDate: String)

}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM project_progress WHERE user_id = :userId AND project_id = :projectId")
    suspend fun get(userId: String, projectId: String): ProjectProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProjectProgressEntity)

}

@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboard_snapshot WHERE user_id = :userId")
    suspend fun get(userId: String): DashboardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dashboard: DashboardEntity)
}

@Dao
interface CacheMetadataDao {
    @Query("SELECT * FROM cache_metadata WHERE user_id = :userId AND resource_key = :resourceKey")
    suspend fun get(userId: String, resourceKey: String): CacheMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: CacheMetadataEntity)

    @Query("DELETE FROM cache_metadata WHERE user_id = :userId AND resource_key = :resourceKey")
    suspend fun invalidate(userId: String, resourceKey: String)

}

@Dao
interface ReviewOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: ReviewOutboxEntity)

    /** Strict createdAt order (client_event_id as the deterministic tiebreaker). */
    @Query(
        "SELECT * FROM review_outbox WHERE user_id = :userId AND status = 'PENDING' " +
            "AND next_attempt_at <= :now ORDER BY created_at, client_event_id LIMIT 1",
    )
    suspend fun nextDue(userId: String, now: Long): ReviewOutboxEntity?

    @Query("SELECT * FROM review_outbox WHERE user_id = :userId ORDER BY created_at")
    suspend fun allForUser(userId: String): List<ReviewOutboxEntity>

    @Query("SELECT * FROM review_outbox WHERE user_id = :userId ORDER BY created_at")
    fun observeAll(userId: String): Flow<List<ReviewOutboxEntity>>

    @Query("SELECT COUNT(*) FROM review_outbox WHERE user_id = :userId AND status = 'PENDING'")
    fun observePendingCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM review_outbox WHERE user_id = :userId AND status = 'PENDING'")
    suspend fun pendingCount(userId: String): Int

    @Query(
        "UPDATE review_outbox SET status = 'COMPLETED' WHERE user_id = :userId " +
            "AND client_event_id = :clientEventId",
    )
    suspend fun markCompleted(userId: String, clientEventId: String)

    @Query(
        "UPDATE review_outbox SET status = 'FAILED', last_error_code = :errorCode " +
            "WHERE user_id = :userId AND client_event_id = :clientEventId",
    )
    suspend fun markFailed(userId: String, clientEventId: String, errorCode: String)

    @Query(
        "UPDATE review_outbox SET attempt_count = :attemptCount, next_attempt_at = :nextAttemptAt, " +
            "last_error_code = :errorCode WHERE user_id = :userId AND client_event_id = :clientEventId",
    )
    suspend fun scheduleRetry(
        userId: String,
        clientEventId: String,
        attemptCount: Int,
        nextAttemptAt: Long,
        errorCode: String?,
    )
}

// --- single-query JOIN projections -----------------------------------------------------------------

/**
 * One plan row joined with its card and server review state in a single SELECT: the plan read
 * path never issues per-card lookups (see `V25DaoQueryShapeTest` for the query-count gate).
 * Card/state stay null when the referenced row is absent (LEFT JOIN semantics).
 */
data class TodayPlanCardProjection(
    @Embedded val item: TodayPlanCardEntity,
    @Embedded(prefix = "card_") val card: CardEntity?,
    @Embedded(prefix = "state_") val reviewState: ReviewStateEntity?,
)

/** Deck-queue entry joined with its card and review state in a single SELECT. */
data class DeckQueueCardProjection(
    @Embedded(prefix = "card_") val card: CardEntity?,
    @Embedded(prefix = "state_") val reviewState: ReviewStateEntity?,
)
