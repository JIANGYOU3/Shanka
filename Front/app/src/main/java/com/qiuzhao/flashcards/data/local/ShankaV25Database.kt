package com.qiuzhao.flashcards.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * `shanka-v25.db` — the local-fact projection of server V2.5 state. Schema is exported
 * (`app/schemas`, kapt `room.schemaLocation`) and version bumps must ship an explicit
 * [MIGRATIONS] entry; [fallbackToDestructiveMigration] is deliberately never called, so a
 * missing migration crashes loudly instead of silently wiping the user's cached facts.
 */
@Database(
    entities = [
        ProjectEntity::class,
        ProjectMaterialEntity::class,
        ProjectChapterEntity::class,
        GenerationTaskEntity::class,
        DeckEntity::class,
        CardEntity::class,
        ReviewStateEntity::class,
        ReviewQueueItemEntity::class,
        StudyPlanEntity::class,
        TodayPlanEntity::class,
        TodayPlanCardEntity::class,
        ProjectProgressEntity::class,
        DashboardEntity::class,
        CacheMetadataEntity::class,
        ReviewOutboxEntity::class,
        DeletionOutboxEntity::class,
        DeckDailyActivityEntity::class,
    ],
    version = ShankaV25Database.VERSION,
    exportSchema = true,
)
abstract class ShankaV25Database : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun reviewQueueDao(): ReviewQueueDao
    abstract fun studyPlanDao(): StudyPlanDao
    abstract fun todayPlanDao(): TodayPlanDao
    abstract fun progressDao(): ProgressDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun cacheMetadataDao(): CacheMetadataDao
    abstract fun reviewOutboxDao(): ReviewOutboxDao
    abstract fun deletionOutboxDao(): DeletionOutboxDao
    abstract fun localUsageDao(): LocalUsageDao

    companion object {
        const val NAME = "shanka-v25.db"
        const val VERSION = 8

        /** Projection schema version written into cache metadata rows. */
        const val CACHE_SCHEMA_VERSION = 3

        /**
         * Explicit migrations only. Bump [VERSION] → add the Migration here → the exported
         * schema lands in `app/schemas` in the same change. Destructive fallback is banned.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // v1 → v2 (multi-material projects, contract V25-D-29~32): `project_files` becomes
            // the per-material `project_materials`, and chapters gain material ownership plus
            // nullable page spans. Both are rebuildable projections of the server payload, so
            // the migration drops and recreates exactly those two tables; every other cached
            // fact (decks, cards, review states, outbox rows) is preserved.
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS project_files")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `project_materials` (" +
                            "`user_id` TEXT NOT NULL, `material_id` TEXT NOT NULL, " +
                            "`project_id` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                            "`status` TEXT NOT NULL, `error_code` TEXT, `size_bytes` INTEGER, " +
                            "`char_count` INTEGER, `created_at` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`user_id`, `material_id`))",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_project_materials_user_id_project_id` " +
                            "ON `project_materials` (`user_id`, `project_id`)",
                    )
                    db.execSQL("DROP TABLE IF EXISTS project_chapters")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `project_chapters` (" +
                            "`user_id` TEXT NOT NULL, `chapter_id` TEXT NOT NULL, " +
                            "`project_id` TEXT NOT NULL, `material_id` TEXT NOT NULL, " +
                            "`name` TEXT NOT NULL, `start_page` INTEGER, `end_page` INTEGER, " +
                            "`position` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `chapter_id`))",
                    )
                }
            },
            // v2 → v3 (observation layer, contract V25-D-34): the light `generation_tasks`
            // projection lands. A brand-new rebuildable table — existing cached facts keep
            // their rows; the next task-returning refresh repopulates statuses.
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `generation_tasks` (" +
                            "`user_id` TEXT NOT NULL, `task_id` TEXT NOT NULL, " +
                            "`project_id` TEXT, `deck_id` TEXT, `retry_of_task_id` TEXT, " +
                            "`status` TEXT NOT NULL, `internal_stage` TEXT, " +
                            "`generated_card_count` INTEGER NOT NULL, `error_code` TEXT, " +
                            "`failure_stage` TEXT, `created_at` INTEGER NOT NULL, " +
                            "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`, `task_id`))",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_generation_tasks_user_id_project_id` " +
                            "ON `generation_tasks` (`user_id`, `project_id`)",
                    )
                }
            },
            // v3 → v4 (optimistic deletions): the `deletion_outbox` tombstone table lands.
            // A brand-new rebuildable queue — existing cached facts keep their rows.
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
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
                }
            },
            // v4 → v5 (device-local usage stats): `deck_study_seconds` accumulates the study
            // screen's foreground seconds per deck. A brand-new device-owned table with no
            // server counterpart — nothing to rebuild; every other cached fact keeps its rows.
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `deck_study_seconds` (" +
                            "`user_id` TEXT NOT NULL, `deck_id` TEXT NOT NULL, " +
                            "`total_seconds` INTEGER NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`user_id`, `deck_id`))",
                    )
                }
            },
            // v5 → v6 (per-day device-local usage): `deck_daily_activity` buckets rated cards
            // and foreground seconds per deck and device-local study date — the 学习数据 "今日"
            // tab's source. Another brand-new device-owned table; existing rows are untouched.
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `deck_daily_activity` (" +
                            "`user_id` TEXT NOT NULL, `deck_id` TEXT NOT NULL, `study_date` TEXT NOT NULL, " +
                            "`reviewed_count` INTEGER NOT NULL, `study_seconds` INTEGER NOT NULL, " +
                            "`updated_at_epoch_ms` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`user_id`, `deck_id`, `study_date`))",
                    )
                }
            },
            // v6 → v7 (AI chapter planning, contract V25-D-36): chapters gain the origin column
            // `source` (TOC/AI/FALLBACK/TEXT/ZIP/MANUAL). Existing rows are all pre-V25-D-36
            // projections of the same server chapters, so they seed with TOC; the next project
            // refresh rewrites the projection from the server payload.
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE `project_chapters` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'TOC'",
                    )
                }
            },
            // v7 → v8 (study sessions & rating origin, contract V25-D-37): `review_outbox` gains
            // the nullable `origin` column — rows pending at upgrade replay as origin-less and
            // the server stores NULL = 未分类; `dashboard_snapshot` gains the server-aggregated
            // study-duration columns (0/'[]' until the next dashboard refresh rewrites them).
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `review_outbox` ADD COLUMN `origin` TEXT")
                    // The device-local lifetime-seconds table is superseded by server-side
                    // study-session aggregation (V25-D-37); the data cannot be migrated into
                    // sessions (no per-day split was kept) and would otherwise never be read.
                    db.execSQL("DROP TABLE IF EXISTS `deck_study_seconds`")
                    db.execSQL(
                        "ALTER TABLE `dashboard_snapshot` ADD COLUMN `weekly_study_seconds` " +
                            "INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "ALTER TABLE `dashboard_snapshot` ADD COLUMN `daily_study_seconds` " +
                            "TEXT NOT NULL DEFAULT '[]'",
                    )
                    db.execSQL(
                        "ALTER TABLE `dashboard_snapshot` ADD COLUMN `plan_study_seconds` " +
                            "INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "ALTER TABLE `dashboard_snapshot` ADD COLUMN `backlog_study_seconds` " +
                            "INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "ALTER TABLE `dashboard_snapshot` ADD COLUMN `adhoc_study_seconds` " +
                            "INTEGER NOT NULL DEFAULT 0",
                    )
                }
            },
        )

        fun build(context: Context): ShankaV25Database =
            Room.databaseBuilder(context.applicationContext, ShankaV25Database::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        /**
         * Robolectric/JVM file-backed builder: a real SQLite file at [path], so a test can
         * close the process-level objects, rebuild them and still observe persisted rows.
         */
        fun buildOnFile(context: Context, path: String): ShankaV25Database =
            Room.databaseBuilder(context, ShankaV25Database::class.java, path)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
