package com.qiuzhao.flashcards.data.local

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.qiuzhao.flashcards.domain.v25.V25LearningProject
import com.qiuzhao.flashcards.domain.v25.V25Material
import com.qiuzhao.flashcards.domain.v25.V25MaterialStatus
import com.qiuzhao.flashcards.domain.v25.V25MaterialType
import com.qiuzhao.flashcards.domain.v25.V25ProjectStatus
import com.qiuzhao.flashcards.domain.v25.V25CoverageMode
import com.qiuzhao.flashcards.domain.v25.V25DifficultyRatio
import com.qiuzhao.flashcards.domain.v25.V25GenerationConfig
import com.qiuzhao.flashcards.domain.v25.V25GenerationTask
import com.qiuzhao.flashcards.domain.v25.V25InternalStage
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime migration gate for the multi-material projection change (contract V25-D-29~32). A
 * real v1 `shanka-v25.db` file (old `project_files`/`project_chapters` schema) is opened through
 * [ShankaV25Database.buildOnFile] so the explicit MIGRATIONS run against real SQLite: the
 * destructive rebuild of the two project tables must leave every other cached fact (projects,
 * decks) intact, and the reopened database must serve the new `project_materials` projection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShankaV25MigrationTest {

    @Test
    fun test_migration_v1_to_v2_rebuilds_project_tables_and_preserves_other_facts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration-v1-v2-${System.nanoTime()}.db")

        // Build the v1 database by hand: the exact v1 schema plus one project, its single PDF
        // file row, one chapter and one unrelated cached deck.
        createV1Database(dbFile)
        insertV1Facts(dbFile)

        // Reopen through the production builder: Room applies MIGRATIONS(1→2) and validates the
        // resulting schema against the exported entities — a mismatch would throw here.
        val migrated = ShankaV25Database.buildOnFile(context, dbFile.absolutePath)
        val cache = V25CacheStore(migrated)

        // The old project_files/chapters rows are gone (rebuildable projections), but the
        // project itself and the unrelated deck survive the rebuild.
        assertEquals(1, cache.readProjects("u-1").size)
        assertEquals("d-old", cache.readDecks("u-1").single().deckId)

        // The new projection accepts multi-material writes and reads them back typed.
        val project = migratedProject()
        cache.replaceProject("u-1", project, now = 1_000L)
        val readBack = cache.readProject("u-1", "p-1")!!
        assertEquals(V25ProjectStatus.AWAITING_CHAPTER_CONFIRMATION, readBack.status)
        assertEquals(2, readBack.materials.size)
        val text = readBack.materials.single { it.type == V25MaterialType.TEXT }
        assertEquals(V25MaterialStatus.READY, text.status)
        assertEquals(42, text.charCount)
        assertEquals("ch-text", readBack.chapters.single { it.materialId == "m-text" }.id)
        assertNullPages(readBack)
        migrated.close()
    }

    @Test
    fun test_migration_v2_to_v3_creates_task_projection_and_preserves_facts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration-v2-v3-${System.nanoTime()}.db")

        // A v2 database with one cached fact: opening it through the production builder must
        // run MIGRATIONS(2→3) and validate the resulting schema against the exported entities.
        createV2Database(dbFile)
        insertV2Fact(dbFile)

        val migrated = ShankaV25Database.buildOnFile(context, dbFile.absolutePath)
        val cache = V25CacheStore(migrated)
        assertEquals("the pre-existing project survives the migration", 1, cache.readProjects("u-1").size)

        // The brand-new task projection accepts writes and reads them back typed.
        cache.upsertTask(
            "u-1",
            V25GenerationTask(
                taskId = "t-1",
                projectId = "p-1",
                fileId = null,
                deckId = null,
                retryOfTaskId = null,
                status = V25TaskStatus.GENERATING,
                internalStage = V25InternalStage.SCORING,
                selectedChapters = emptyList(),
                generationConfig = V25GenerationConfig(V25CoverageMode.BALANCED, V25DifficultyRatio(40, 40, 20), ""),
                sampleCards = emptyList(),
                sampleConfigHash = null,
                sampleConfirmedAt = null,
                generatedCardCount = 0,
                errorCode = null,
                failureStage = null,
                createdAt = Instant.ofEpochMilli(1_000L),
                startedAt = null,
                endedAt = null,
                updatedAt = Instant.ofEpochMilli(2_000L),
            ),
            now = 3_000L,
        )
        val observed = cache.observeTask("u-1", "t-1").firstOrNull()
        assertEquals(V25TaskStatus.GENERATING, observed?.status)
        assertEquals(V25InternalStage.SCORING, observed?.internalStage)
        assertEquals("p-1", observed?.projectId)
        migrated.close()
    }

    /** The v2 schema is exactly the v2 projection minus `generation_tasks`: rebuilt via no-op rows. */
    private fun createV2Database(dbFile: File) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Reuse Room itself: build a v2-typed database by opening the v2 exported schema through
        // a raw helper at version 2, matching the shape `2.json` declares for existing tables.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // The v2 schema subset that matters for the preservation assertion.
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `projects` (`user_id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `name` TEXT NOT NULL, `status` TEXT NOT NULL, `chapter_count` INTEGER NOT NULL, `deck_count` INTEGER NOT NULL, `task_count` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `project_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `project_materials` (`user_id` TEXT NOT NULL, `material_id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `status` TEXT NOT NULL, `error_code` TEXT, `size_bytes` INTEGER, `char_count` INTEGER, `created_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `material_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `project_chapters` (`user_id` TEXT NOT NULL, `chapter_id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `material_id` TEXT NOT NULL, `name` TEXT NOT NULL, `start_page` INTEGER, `end_page` INTEGER, `position` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `chapter_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `decks` (`user_id` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `name` TEXT NOT NULL, `project_id` TEXT, `card_count` INTEGER NOT NULL, `due_count` INTEGER NOT NULL, `mastered_card_count` INTEGER NOT NULL, `review_count` INTEGER NOT NULL, `mastery_ratio` REAL, `not_started_count` INTEGER NOT NULL, `learning_count` INTEGER NOT NULL, `relearning_count` INTEGER NOT NULL, `consolidating_count` INTEGER NOT NULL, `mastered_count` INTEGER NOT NULL, `review_event_count` INTEGER NOT NULL, `last_studied_at` INTEGER, PRIMARY KEY(`user_id`, `deck_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `cards` (`user_id` TEXT NOT NULL, `card_id` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `front` TEXT NOT NULL, `back` TEXT NOT NULL, `card_type` TEXT NOT NULL, `position` INTEGER NOT NULL, `target_difficulty` TEXT, `chapter_id` TEXT, `source_task_id` TEXT, `publication_state` TEXT, `version` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `card_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `review_states` (`user_id` TEXT NOT NULL, `card_id` TEXT NOT NULL, `state` TEXT NOT NULL, `due` INTEGER, `synced_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `card_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `review_queue` (`user_id` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `card_id` TEXT NOT NULL, PRIMARY KEY(`user_id`, `deck_id`, `position`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `study_plan` (`user_id` TEXT NOT NULL, `configured` INTEGER NOT NULL, `current_project_id` TEXT, `selected_deck_ids` TEXT NOT NULL, `daily_new_goal` INTEGER NOT NULL, `daily_review_goal` INTEGER NOT NULL, `updated_at` INTEGER, PRIMARY KEY(`user_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `today_plan` (`user_id` TEXT NOT NULL, `study_date` TEXT NOT NULL, `timezone` TEXT NOT NULL, `current_project_id` TEXT, `current_project_name` TEXT, `daily_goal` INTEGER NOT NULL, `today_completed_count` INTEGER NOT NULL, `due_count` INTEGER NOT NULL, `main_plan_remaining` INTEGER NOT NULL, `backlog_count` INTEGER NOT NULL, `daily_new_goal` INTEGER NOT NULL, `daily_review_goal` INTEGER NOT NULL, `new_completed_count` INTEGER NOT NULL, `review_completed_count` INTEGER NOT NULL, `new_remaining_count` INTEGER NOT NULL, `review_remaining_count` INTEGER NOT NULL, `core_target_count` INTEGER NOT NULL, `plan_configured` INTEGER NOT NULL, `selected_deck_ids` TEXT NOT NULL, PRIMARY KEY(`user_id`, `study_date`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `today_plan_cards` (`user_id` TEXT NOT NULL, `study_date` TEXT NOT NULL, `position` INTEGER NOT NULL, `card_id` TEXT NOT NULL, `plan_kind` TEXT, `is_new` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `study_date`, `position`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `project_progress` (`user_id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `card_count` INTEGER NOT NULL, `not_started_count` INTEGER NOT NULL, `learning_count` INTEGER NOT NULL, `relearning_count` INTEGER NOT NULL, `consolidating_count` INTEGER NOT NULL, `mastered_count` INTEGER NOT NULL, `due_count` INTEGER NOT NULL, `review_event_count` INTEGER NOT NULL, `last_studied_at` INTEGER, PRIMARY KEY(`user_id`, `project_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `dashboard_snapshot` (`user_id` TEXT NOT NULL, `has_data` INTEGER NOT NULL, `week_start_date` TEXT NOT NULL, `weekly_activity` TEXT NOT NULL, `weekly_total` INTEGER NOT NULL, `weekly_change_rate` REAL, `weekly_goal` INTEGER NOT NULL, `weekly_completed_count` INTEGER NOT NULL, `weekly_goal_progress` REAL, `recall_accuracy` REAL, `first_answer_accuracy` REAL, `retention_rate` REAL, `streak_days` INTEGER NOT NULL, `mastered_card_count` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `cache_metadata` (`user_id` TEXT NOT NULL, `resource_key` TEXT NOT NULL, `server_version` TEXT, `server_updated_at` INTEGER, `fetched_at` INTEGER NOT NULL, `schema_version` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `resource_key`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `review_outbox` (`user_id` TEXT NOT NULL, `client_event_id` TEXT NOT NULL, `card_id` TEXT NOT NULL, `rating` TEXT NOT NULL, `idempotency_key` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `status` TEXT NOT NULL, `attempt_count` INTEGER NOT NULL, `next_attempt_at` INTEGER NOT NULL, `last_error_code` TEXT, PRIMARY KEY(`user_id`, `client_event_id`))"
                        )
                        db.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS `index_review_outbox_user_id_idempotency_key` ON `review_outbox` (`user_id`, `idempotency_key`)"
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_project_materials_user_id_project_id` ON `project_materials` (`user_id`, `project_id`)"
                        )
                    }

                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.version = 2
        helper.writableDatabase.close()
    }

    private fun insertV2Fact(dbFile: File) {
        val db = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext(),
            )
                .name(dbFile.absolutePath)
                .callback(NoOpCallback(2))
                .build(),
        ).writableDatabase
        db.execSQL(
            "INSERT INTO projects VALUES ('u-1', 'p-1', 'v2 项目', 'READY', 0, 0, 0, 100, 200, 7)",
        )
        db.close()
    }

    private fun assertNullPages(project: V25LearningProject) {
        val textChapter = project.chapters.single { it.id == "ch-text" }
        assertEquals(null, textChapter.startPage)
        assertEquals(null, textChapter.endPage)
    }

    /** The full v1 schema verbatim from `app/schemas/.../1.json`. */
    private fun createV1Database(dbFile: File) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext(),
            )
                .name(dbFile.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // The v1 schema verbatim from `app/schemas/.../1.json`.
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `projects` (`user_id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `name` TEXT NOT NULL, `status` TEXT NOT NULL, `chapter_count` INTEGER NOT NULL, `deck_count` INTEGER NOT NULL, `task_count` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `project_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `project_files` (`user_id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `file_id` TEXT NOT NULL, `filename` TEXT NOT NULL, `size_bytes` INTEGER, `status` TEXT, `error_code` TEXT, `created_at` INTEGER, PRIMARY KEY(`user_id`, `project_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `project_chapters` (`user_id` TEXT NOT NULL, `chapter_id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `name` TEXT NOT NULL, `start_page` INTEGER NOT NULL, `end_page` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `chapter_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `decks` (`user_id` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `name` TEXT NOT NULL, `project_id` TEXT, `card_count` INTEGER NOT NULL, `due_count` INTEGER NOT NULL, `mastered_card_count` INTEGER NOT NULL, `review_count` INTEGER NOT NULL, `mastery_ratio` REAL, `not_started_count` INTEGER NOT NULL, `learning_count` INTEGER NOT NULL, `relearning_count` INTEGER NOT NULL, `consolidating_count` INTEGER NOT NULL, `mastered_count` INTEGER NOT NULL, `review_event_count` INTEGER NOT NULL, `last_studied_at` INTEGER, PRIMARY KEY(`user_id`, `deck_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `cards` (`user_id` TEXT NOT NULL, `card_id` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `front` TEXT NOT NULL, `back` TEXT NOT NULL, `card_type` TEXT NOT NULL, `position` INTEGER NOT NULL, `target_difficulty` TEXT, `chapter_id` TEXT, `source_task_id` TEXT, `publication_state` TEXT, `version` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `card_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `review_states` (`user_id` TEXT NOT NULL, `card_id` TEXT NOT NULL, `state` TEXT NOT NULL, `due` INTEGER, `synced_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `card_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `review_queue` (`user_id` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `card_id` TEXT NOT NULL, PRIMARY KEY(`user_id`, `deck_id`, `position`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `study_plan` (`user_id` TEXT NOT NULL, `configured` INTEGER NOT NULL, `current_project_id` TEXT, `selected_deck_ids` TEXT NOT NULL, `daily_new_goal` INTEGER NOT NULL, `daily_review_goal` INTEGER NOT NULL, `updated_at` INTEGER, PRIMARY KEY(`user_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `today_plan` (`user_id` TEXT NOT NULL, `study_date` TEXT NOT NULL, `timezone` TEXT NOT NULL, `current_project_id` TEXT, `current_project_name` TEXT, `daily_goal` INTEGER NOT NULL, `today_completed_count` INTEGER NOT NULL, `due_count` INTEGER NOT NULL, `main_plan_remaining` INTEGER NOT NULL, `backlog_count` INTEGER NOT NULL, `daily_new_goal` INTEGER NOT NULL, `daily_review_goal` INTEGER NOT NULL, `new_completed_count` INTEGER NOT NULL, `review_completed_count` INTEGER NOT NULL, `new_remaining_count` INTEGER NOT NULL, `review_remaining_count` INTEGER NOT NULL, `core_target_count` INTEGER NOT NULL, `plan_configured` INTEGER NOT NULL, `selected_deck_ids` TEXT NOT NULL, PRIMARY KEY(`user_id`, `study_date`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `today_plan_cards` (`user_id` TEXT NOT NULL, `study_date` TEXT NOT NULL, `position` INTEGER NOT NULL, `card_id` TEXT NOT NULL, `plan_kind` TEXT, `is_new` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `study_date`, `position`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `project_progress` (`user_id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `card_count` INTEGER NOT NULL, `not_started_count` INTEGER NOT NULL, `learning_count` INTEGER NOT NULL, `relearning_count` INTEGER NOT NULL, `consolidating_count` INTEGER NOT NULL, `mastered_count` INTEGER NOT NULL, `due_count` INTEGER NOT NULL, `review_event_count` INTEGER NOT NULL, `last_studied_at` INTEGER, PRIMARY KEY(`user_id`, `project_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `dashboard_snapshot` (`user_id` TEXT NOT NULL, `has_data` INTEGER NOT NULL, `week_start_date` TEXT NOT NULL, `weekly_activity` TEXT NOT NULL, `weekly_total` INTEGER NOT NULL, `weekly_change_rate` REAL, `weekly_goal` INTEGER NOT NULL, `weekly_completed_count` INTEGER NOT NULL, `weekly_goal_progress` REAL, `recall_accuracy` REAL, `first_answer_accuracy` REAL, `retention_rate` REAL, `streak_days` INTEGER NOT NULL, `mastered_card_count` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `cache_metadata` (`user_id` TEXT NOT NULL, `resource_key` TEXT NOT NULL, `server_version` TEXT, `server_updated_at` INTEGER, `fetched_at` INTEGER NOT NULL, `schema_version` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `resource_key`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `review_outbox` (`user_id` TEXT NOT NULL, `client_event_id` TEXT NOT NULL, `card_id` TEXT NOT NULL, `rating` TEXT NOT NULL, `idempotency_key` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `status` TEXT NOT NULL, `attempt_count` INTEGER NOT NULL, `next_attempt_at` INTEGER NOT NULL, `last_error_code` TEXT, PRIMARY KEY(`user_id`, `client_event_id`))"
                        )
                        db.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS `index_review_outbox_user_id_idempotency_key` ON `review_outbox` (`user_id`, `idempotency_key`)"
                        )
                    }

                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase
        db.version = 1
        db.close()
    }

    private fun insertV1Facts(dbFile: File) {
        val db = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext(),
            )
                .name(dbFile.absolutePath)
                .callback(NoOpCallback(1))
                .build(),
        ).writableDatabase
        db.execSQL(
            "INSERT INTO projects VALUES ('u-1', 'p-1', '旧项目', 'READY', 1, 0, 0, 100, 100, 3)",
        )
        db.execSQL(
            "INSERT INTO project_files VALUES ('u-1', 'p-1', 'f-1', 'old.pdf', 10, 'PARSED', NULL, 100)",
        )
        db.execSQL(
            "INSERT INTO project_chapters VALUES ('u-1', 'ch-1', 'p-1', '第一章', 1, 20, 0)",
        )
        db.execSQL(
            "INSERT INTO decks VALUES ('u-1', 'd-old', '旧卡组', 'p-1', 3, 1, 0, 5, NULL, 3, 0, 0, 0, 0, 0, NULL)",
        )
        db.close()
    }

    private fun migratedProject() = V25LearningProject(
        projectId = "p-1",
        name = "多资料项目",
        materials = listOf(
            V25Material(
                materialId = "m-pdf",
                projectId = "p-1",
                type = V25MaterialType.PDF,
                name = "book.pdf",
                status = V25MaterialStatus.PARSED,
                sizeBytes = 1024L,
                createdAt = Instant.ofEpochMilli(1_000L),
            ),
            V25Material(
                materialId = "m-text",
                projectId = "p-1",
                type = V25MaterialType.TEXT,
                name = "课堂笔记",
                status = V25MaterialStatus.READY,
                charCount = 42,
                createdAt = Instant.ofEpochMilli(2_000L),
            ),
        ),
        status = V25ProjectStatus.AWAITING_CHAPTER_CONFIRMATION,
        chapterCount = 2,
        deckCount = 0,
        taskCount = 0,
        createdAt = Instant.ofEpochMilli(1_000L),
        updatedAt = Instant.ofEpochMilli(2_000L),
        version = 5,
        chapters = listOf(
            com.qiuzhao.flashcards.domain.v25.V25Chapter("ch-pdf", "m-pdf", "第一章", 1, 20),
            com.qiuzhao.flashcards.domain.v25.V25Chapter("ch-text", "m-text", "课堂笔记", null, null),
        ),
    )

    /** Opens an existing SQLite file without touching its schema. */
    private class NoOpCallback(version: Int) : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(version) {
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
        override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
