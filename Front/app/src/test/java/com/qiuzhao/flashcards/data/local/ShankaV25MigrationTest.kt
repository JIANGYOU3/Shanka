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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun test_migration_v3_to_v4_creates_deletion_outbox_and_preserves_facts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration-v3-v4-${System.nanoTime()}.db")

        // A v3 database with one cached fact: opening it through the production builder must
        // run MIGRATIONS(3→4) and validate the resulting schema against the exported entities.
        createV3Database(dbFile)
        insertV3Fact(dbFile)

        val migrated = ShankaV25Database.buildOnFile(context, dbFile.absolutePath)
        val cache = V25CacheStore(migrated)
        assertEquals("the pre-existing project survives the migration", 1, cache.readProjects("u-1").size)

        // The brand-new tombstone table accepts writes, keeps the scope hidden from rewrites
        // and reads back through the coordinator-facing API.
        cache.enqueueProjectDeletion(
            userId = "u-1",
            projectId = "p-1",
            retainDecks = true,
            idempotencyKey = "migration-key",
            now = 5_000L,
        )
        assertTrue(cache.readProjects("u-1").isEmpty())
        val pending = cache.allPendingDeletions("u-1").single()
        assertEquals(V25CacheStore.projectDeletionOperationId("p-1"), pending.operationId)
        assertEquals("migration-key", pending.idempotencyKey)
        cache.completeDeletion("u-1", pending.operationId)
        assertTrue(cache.allPendingDeletions("u-1").isEmpty())
        migrated.close()
    }

    @Test
    fun test_migration_v4_to_v8_preserves_facts_and_drops_legacy_seconds() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration-v4-v8-${System.nanoTime()}.db")

        // A v4 database with one cached deck: opening through the production builder runs the
        // whole chain to head (v8). The v5-era `deck_study_seconds` table is dropped again at
        // v7→v8 (lifetime duration became server truth, V25-D-37), and every other cached
        // fact survives.
        createV4Database(dbFile)
        insertV4Fact(dbFile)

        val migrated = ShankaV25Database.buildOnFile(context, dbFile.absolutePath)
        val cache = V25CacheStore(migrated)
        assertEquals("the pre-existing deck survives the migration", "d-old", cache.readDecks("u-1").single().deckId)

        val usage = LocalUsageStore(migrated) { "u-1" }
        usage.addTodayStudySeconds(mapOf("d-old" to 45L), nowMs = 2_000)
        val today = usage.observeDeckDailyActivity(nowMs = 2_000).first()["d-old"]!!
        assertEquals(45L, today.studySeconds)
        val cursor = migrated.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE name = 'deck_study_seconds'")
        assertEquals("legacy lifetime-seconds table is gone at head", 0, cursor.count)
        cursor.close()
        migrated.close()
    }

    @Test
    fun test_migration_v5_to_v6_creates_deck_daily_activity_and_preserves_facts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration-v5-v6-${System.nanoTime()}.db")

        // A v5 database with one cached deck: opening through the production builder must run
        // MIGRATIONS(5→6) and validate the resulting schema against the exported entities.
        createV5Database(dbFile)
        insertV5Fact(dbFile)

        val migrated = ShankaV25Database.buildOnFile(context, dbFile.absolutePath)
        val cache = V25CacheStore(migrated)
        assertEquals("the pre-existing deck survives the migration", "d-old", cache.readDecks("u-1").single().deckId)

        // The brand-new per-day device-owned table accepts reviews and seconds and reads
        // them back as today's activity.
        val usage = LocalUsageStore(migrated) { "u-1" }
        usage.addDeckReview("d-old", nowMs = 1_000)
        usage.addTodayStudySeconds(mapOf("d-old" to 45L), nowMs = 2_000)
        val today = usage.observeDeckDailyActivity(nowMs = 2_000).first()["d-old"]!!
        assertEquals(1, today.reviewedCount)
        assertEquals(45L, today.studySeconds)
        migrated.close()
    }

    @Test
    fun test_migration_v6_to_v7_adds_chapter_source_and_preserves_facts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration-v6-v7-${System.nanoTime()}.db")

        // A v6 database with one cached chapter (pre-V25-D-36 shape, no `source` column):
        // opening through the production builder must run MIGRATIONS(6→7), validate the
        // schema against the exported entities and seed the origin column with TOC.
        createV6Database(dbFile)
        insertV6ChapterFact(dbFile)

        val migrated = ShankaV25Database.buildOnFile(context, dbFile.absolutePath)
        val cache = V25CacheStore(migrated)
        val chapters = cache.readProjects("u-1").single().chapters
        assertEquals("the pre-existing chapter survives the migration", "ch-old", chapters.single().id)
        assertEquals(
            "existing rows seed with TOC until the next project refresh rewrites the projection",
            "TOC",
            chapters.single().source,
        )
        migrated.close()
    }

    @Test
    fun test_migration_v7_to_v8_adds_origin_and_duration_and_preserves_facts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration-v7-v8-${System.nanoTime()}.db")

        // A v7 database (v6 + chapters.source) carrying one pending outbox row and one
        // dashboard snapshot, both in their pre-V25-D-37 shape (no origin / duration columns):
        // MIGRATIONS(7→8) must add the columns without touching either fact.
        createV7Database(dbFile)
        insertV7Facts(dbFile)

        val migrated = ShankaV25Database.buildOnFile(context, dbFile.absolutePath)
        val cache = V25CacheStore(migrated)

        // The pending row survives with a null origin — it replays as origin-less and the
        // server stores NULL = 未分类.
        val pending = cache.nextDueOutbox("u-1", now = 1_000L)
        assertEquals("ev-1", pending?.clientEventId)
        assertNull("rows pending at upgrade keep a null origin", pending?.origin)

        // The old dashboard snapshot reads back with the honest duration defaults until the
        // next dashboard refresh rewrites the projection.
        val dashboard = cache.readDashboard("u-1")
        assertEquals(0, dashboard?.weeklyStudySeconds)
        assertEquals(emptyList<Int>(), dashboard?.dailyStudySeconds)

        // The new origin write path works after migration.
        cache.enqueueReview(
            userId = "u-1",
            cardId = "c-9",
            rating = com.qiuzhao.flashcards.domain.v25.V25Rating.GOOD,
            clientEventId = "ev-2",
            idempotencyKey = "key-2",
            origin = com.qiuzhao.flashcards.domain.v25.V25StudyOrigin.PLAN,
            now = 2_000L,
        )
        val fresh = cache.allOutbox("u-1").first { it.clientEventId == "ev-2" }
        assertEquals("PLAN", fresh.origin)
        migrated.close()
    }

    /** The v7 schema is the v6 projection plus `project_chapters.source` (V25-D-36). */
    private fun createV7Database(dbFile: File) {
        createV6Database(dbFile)
        val db = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext(),
            )
                .name(dbFile.absolutePath)
                .callback(NoOpCallback(7))
                .build(),
        ).writableDatabase
        db.execSQL("ALTER TABLE `project_chapters` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'TOC'")
        db.version = 7
        db.close()
    }

    /** One pending review row + one dashboard snapshot, both in their v7 (pre-V25-D-37) shape. */
    private fun insertV7Facts(dbFile: File) {
        val db = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext(),
            )
                .name(dbFile.absolutePath)
                .callback(NoOpCallback(7))
                .build(),
        ).writableDatabase
        db.execSQL(
            "INSERT INTO review_outbox (user_id, client_event_id, card_id, rating, idempotency_key, " +
                "created_at, status, attempt_count, next_attempt_at, last_error_code) VALUES " +
                "('u-1', 'ev-1', 'c-1', 'GOOD', 'key-1', 1, 'PENDING', 0, 1, NULL)",
        )
        db.execSQL(
            "INSERT INTO dashboard_snapshot (user_id, has_data, week_start_date, weekly_activity, " +
                "weekly_total, weekly_change_rate, weekly_goal, weekly_completed_count, " +
                "weekly_goal_progress, recall_accuracy, first_answer_accuracy, retention_rate, " +
                "streak_days, mastered_card_count, updated_at) VALUES " +
                "('u-1', 0, '2026-09-14', '[0,0,0,0,0,0,0]', 0, NULL, 100, 0, NULL, NULL, NULL, NULL, 0, 0, 1)",
        )
        db.close()
    }

    /** The v6 schema is the v5 projection plus the device-owned `deck_daily_activity` table. */
    private fun createV6Database(dbFile: File) {
        createV5Database(dbFile)
        val db = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext(),
            )
                .name(dbFile.absolutePath)
                .callback(NoOpCallback(6))
                .build(),
        ).writableDatabase
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deck_daily_activity` (" +
                "`user_id` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `study_date` TEXT NOT NULL, " +
                "`reviewed_count` INTEGER NOT NULL, `study_seconds` INTEGER NOT NULL, " +
                "`updated_at_epoch_ms` INTEGER NOT NULL, " +
                "PRIMARY KEY(`user_id`, `deck_id`, `study_date`))",
        )
        db.version = 6
        db.close()
    }

    /** One v6-shaped chapter fact: projects + project_materials + project_chapters rows. */
    private fun insertV6ChapterFact(dbFile: File) {
        val db = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext(),
            )
                .name(dbFile.absolutePath)
                .callback(NoOpCallback(6))
                .build(),
        ).writableDatabase
        db.execSQL(
            "INSERT INTO projects VALUES ('u-1', 'p-1', 'v6 项目', 'READY', 1, 0, 0, 100, 200, 7)",
        )
        db.execSQL(
            "INSERT INTO project_materials VALUES ('u-1', 'm-1', 'p-1', 'PDF', 'book.pdf', 'PARSED', NULL, 100, NULL, 1000)",
        )
        db.execSQL(
            "INSERT INTO project_chapters VALUES ('u-1', 'ch-old', 'p-1', 'm-1', '第一章', 1, 20, 0)",
        )
        db.close()
    }

    /** The v5 schema is the v4 projection plus the cumulative `deck_study_seconds` table. */
    private fun createV5Database(dbFile: File) {
        createV4Database(dbFile)
        val db = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext(),
            )
                .name(dbFile.absolutePath)
                .callback(NoOpCallback(5))
                .build(),
        ).writableDatabase
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deck_study_seconds` (" +
                "`user_id` TEXT NOT NULL, `deck_id` TEXT NOT NULL, " +
                "`total_seconds` INTEGER NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, " +
                "PRIMARY KEY(`user_id`, `deck_id`))",
        )
        db.version = 5
        db.close()
    }

    private fun insertV5Fact(dbFile: File) {
        val db = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext(),
            )
                .name(dbFile.absolutePath)
                .callback(NoOpCallback(5))
                .build(),
        ).writableDatabase
        db.execSQL(
            "INSERT INTO decks VALUES ('u-1', 'd-old', 'v5 卡组', 'p-1', 3, 1, 0, 5, NULL, 3, 0, 0, 0, 0, 0, NULL)",
        )
        db.close()
    }

    /** The v4 schema is the v3 projection plus the `deletion_outbox` tombstone table. */
    private fun createV4Database(dbFile: File) {
        createV3Database(dbFile)
        val db = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext(),
            )
                .name(dbFile.absolutePath)
                .callback(NoOpCallback(4))
                .build(),
        ).writableDatabase
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deletion_outbox` (" +
                "`user_id` TEXT NOT NULL, `operation_id` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, `project_id` TEXT NOT NULL, " +
                "`material_id` TEXT, `retain` INTEGER NOT NULL, " +
                "`idempotency_key` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, `attempt_count` INTEGER NOT NULL, " +
                "`next_attempt_at` INTEGER NOT NULL, `last_error_code` TEXT, " +
                "PRIMARY KEY(`user_id`, `operation_id`))",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_deletion_outbox_user_id_idempotency_key` " +
                "ON `deletion_outbox` (`user_id`, `idempotency_key`)",
        )
        db.version = 4
        db.close()
    }

    private fun insertV4Fact(dbFile: File) {
        val db = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext(),
            )
                .name(dbFile.absolutePath)
                .callback(NoOpCallback(4))
                .build(),
        ).writableDatabase
        db.execSQL(
            "INSERT INTO decks VALUES ('u-1', 'd-old', 'v4 卡组', 'p-1', 3, 1, 0, 5, NULL, 3, 0, 0, 0, 0, 0, NULL)",
        )
        db.close()
    }

    /**
     * The v3 schema is the current projection minus `deletion_outbox`: every v2 table plus the
     * `generation_tasks` projection (v3's own addition), verbatim from `3.json`.
     */
    private fun createV3Database(dbFile: File) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(NoOpCallback(3))
                .build(),
        )
        val db = helper.writableDatabase
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `projects` (`user_id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `name` TEXT NOT NULL, `status` TEXT NOT NULL, `chapter_count` INTEGER NOT NULL, `deck_count` INTEGER NOT NULL, `task_count` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `project_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `project_materials` (`user_id` TEXT NOT NULL, `material_id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `status` TEXT NOT NULL, `error_code` TEXT, `size_bytes` INTEGER, `char_count` INTEGER, `created_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `material_id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_project_materials_user_id_project_id` ON `project_materials` (`user_id`, `project_id`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `project_chapters` (`user_id` TEXT NOT NULL, `chapter_id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `material_id` TEXT NOT NULL, `name` TEXT NOT NULL, `start_page` INTEGER, `end_page` INTEGER, `position` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `chapter_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `generation_tasks` (`user_id` TEXT NOT NULL, `task_id` TEXT NOT NULL, `project_id` TEXT, `deck_id` TEXT, `retry_of_task_id` TEXT, `status` TEXT NOT NULL, `internal_stage` TEXT, `generated_card_count` INTEGER NOT NULL, `error_code` TEXT, `failure_stage` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `task_id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_generation_tasks_user_id_project_id` ON `generation_tasks` (`user_id`, `project_id`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `decks` (`user_id` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `name` TEXT NOT NULL, `project_id` TEXT, `card_count` INTEGER NOT NULL, `due_count` INTEGER NOT NULL, `mastered_card_count` INTEGER NOT NULL, `review_count` INTEGER NOT NULL, `mastery_ratio` REAL, `not_started_count` INTEGER NOT NULL, `learning_count` INTEGER NOT NULL, `relearning_count` INTEGER NOT NULL, `consolidating_count` INTEGER NOT NULL, `mastered_count` INTEGER NOT NULL, `review_event_count` INTEGER NOT NULL, `last_studied_at` INTEGER, PRIMARY KEY(`user_id`, `deck_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cards` (`user_id` TEXT NOT NULL, `card_id` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `front` TEXT NOT NULL, `back` TEXT NOT NULL, `card_type` TEXT NOT NULL, `position` INTEGER NOT NULL, `target_difficulty` TEXT, `chapter_id` TEXT, `source_task_id` TEXT, `publication_state` TEXT, `version` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `card_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `review_states` (`user_id` TEXT NOT NULL, `card_id` TEXT NOT NULL, `state` TEXT NOT NULL, `due` INTEGER, `synced_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `card_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `review_queue` (`user_id` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `card_id` TEXT NOT NULL, PRIMARY KEY(`user_id`, `deck_id`, `position`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `study_plan` (`user_id` TEXT NOT NULL, `configured` INTEGER NOT NULL, `current_project_id` TEXT, `selected_deck_ids` TEXT NOT NULL, `daily_new_goal` INTEGER NOT NULL, `daily_review_goal` INTEGER NOT NULL, `updated_at` INTEGER, PRIMARY KEY(`user_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `today_plan` (`user_id` TEXT NOT NULL, `study_date` TEXT NOT NULL, `timezone` TEXT NOT NULL, `current_project_id` TEXT, `current_project_name` TEXT, `daily_goal` INTEGER NOT NULL, `today_completed_count` INTEGER NOT NULL, `due_count` INTEGER NOT NULL, `main_plan_remaining` INTEGER NOT NULL, `backlog_count` INTEGER NOT NULL, `daily_new_goal` INTEGER NOT NULL, `daily_review_goal` INTEGER NOT NULL, `new_completed_count` INTEGER NOT NULL, `review_completed_count` INTEGER NOT NULL, `new_remaining_count` INTEGER NOT NULL, `review_remaining_count` INTEGER NOT NULL, `core_target_count` INTEGER NOT NULL, `plan_configured` INTEGER NOT NULL, `selected_deck_ids` TEXT NOT NULL, PRIMARY KEY(`user_id`, `study_date`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `today_plan_cards` (`user_id` TEXT NOT NULL, `study_date` TEXT NOT NULL, `position` INTEGER NOT NULL, `card_id` TEXT NOT NULL, `plan_kind` TEXT, `is_new` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `study_date`, `position`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `project_progress` (`user_id` TEXT NOT NULL, `project_id` TEXT NOT NULL, `card_count` INTEGER NOT NULL, `not_started_count` INTEGER NOT NULL, `learning_count` INTEGER NOT NULL, `relearning_count` INTEGER NOT NULL, `consolidating_count` INTEGER NOT NULL, `mastered_count` INTEGER NOT NULL, `due_count` INTEGER NOT NULL, `review_event_count` INTEGER NOT NULL, `last_studied_at` INTEGER, PRIMARY KEY(`user_id`, `project_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `dashboard_snapshot` (`user_id` TEXT NOT NULL, `has_data` INTEGER NOT NULL, `week_start_date` TEXT NOT NULL, `weekly_activity` TEXT NOT NULL, `weekly_total` INTEGER NOT NULL, `weekly_change_rate` REAL, `weekly_goal` INTEGER NOT NULL, `weekly_completed_count` INTEGER NOT NULL, `weekly_goal_progress` REAL, `recall_accuracy` REAL, `first_answer_accuracy` REAL, `retention_rate` REAL, `streak_days` INTEGER NOT NULL, `mastered_card_count` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cache_metadata` (`user_id` TEXT NOT NULL, `resource_key` TEXT NOT NULL, `server_version` TEXT, `server_updated_at` INTEGER, `fetched_at` INTEGER NOT NULL, `schema_version` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `resource_key`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `review_outbox` (`user_id` TEXT NOT NULL, `client_event_id` TEXT NOT NULL, `card_id` TEXT NOT NULL, `rating` TEXT NOT NULL, `idempotency_key` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `status` TEXT NOT NULL, `attempt_count` INTEGER NOT NULL, `next_attempt_at` INTEGER NOT NULL, `last_error_code` TEXT, PRIMARY KEY(`user_id`, `client_event_id`))",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_review_outbox_user_id_idempotency_key` ON `review_outbox` (`user_id`, `idempotency_key`)",
        )
        db.close()
    }

    private fun insertV3Fact(dbFile: File) {
        val db = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext(),
            )
                .name(dbFile.absolutePath)
                .callback(NoOpCallback(3))
                .build(),
        ).writableDatabase
        db.execSQL(
            "INSERT INTO projects VALUES ('u-1', 'p-1', 'v3 项目', 'READY', 0, 0, 0, 100, 200, 7)",
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
            com.qiuzhao.flashcards.domain.v25.V25Chapter("ch-pdf", "m-pdf", "第一章", "TOC", 1, 20),
            com.qiuzhao.flashcards.domain.v25.V25Chapter("ch-text", "m-text", "课堂笔记", "TEXT", null, null),
        ),
    )

    /** Opens an existing SQLite file without touching its schema. */
    private class NoOpCallback(version: Int) : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(version) {
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
        override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
