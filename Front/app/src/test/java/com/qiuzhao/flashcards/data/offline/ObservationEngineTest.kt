package com.qiuzhao.flashcards.data.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qiuzhao.flashcards.data.local.ShankaV25Database
import com.qiuzhao.flashcards.data.local.V25CacheStore
import com.qiuzhao.flashcards.data.remote.http.NetworkStack
import com.qiuzhao.flashcards.data.remote.v25.RemoteV25Repository
import com.qiuzhao.flashcards.data.session.InMemorySessionStore
import com.qiuzhao.flashcards.data.session.SessionUser
import com.qiuzhao.flashcards.domain.v25.V25GenerationTask
import com.qiuzhao.flashcards.domain.v25.V25GenerationConfig
import com.qiuzhao.flashcards.domain.v25.V25DifficultyRatio
import com.qiuzhao.flashcards.domain.v25.V25CoverageMode
import com.qiuzhao.flashcards.domain.v25.V25ProjectStatus
import com.qiuzhao.flashcards.domain.v25.V25Result
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The observation engine suite (V25-D-34) — the regression gate for the "解析中 forever" defect:
 * a project uploaded seconds ago stayed PARSING on screen because no observer ran. Here the
 * engine must (1) poll a PARSING project on its own and land the finished parse in Room,
 * (2) stop polling once terminal, (3) poll a GENERATING task and fire its terminal hook exactly
 * once, and (4) never poll without a session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ObservationEngineTest {

    class TestClock(val nowMs: Long = Instant.parse("2026-08-31T00:00:00Z").toEpochMilli()) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    }

    /** Fake backend: project detail/list whose parse status the test flips between requests. */
    class ProcessingBackend : Dispatcher() {
        @Volatile var detailParsed = false
        @Volatile var taskCompleted = false
        var detailRequestCount = 0
        var taskRequestCount = 0

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path ?: return notFound()
            return when {
                path == "/projects" -> ok("""{"items": [${projectBody(parsed = detailParsed)}]}""")
                path.startsWith("/projects/") -> {
                    detailRequestCount++
                    ok(projectBody(parsed = detailParsed))
                }
                path.startsWith("/tasks") -> {
                    taskRequestCount++
                    ok(taskBody(completed = taskCompleted))
                }
                else -> notFound()
            }
        }

        private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
        private fun notFound() = MockResponse().setResponseCode(404).setBody("""{"error":{"code":"NOT_FOUND"}}""")
    }

    private lateinit var server: MockWebServer
    private lateinit var backend: ProcessingBackend
    private lateinit var store: InMemorySessionStore
    private lateinit var dbFile: File
    private lateinit var scope: CoroutineScope

    private val user1 = SessionUser(userId = "u-1", username = "alice", createdAt = "2026-08-01T00:00:00Z")

    @Before
    fun setUp() {
        server = MockWebServer()
        backend = ProcessingBackend()
        server.dispatcher = backend
        server.start()
        store = InMemorySessionStore()
        store.save("token-u1", user1)
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbFile = File(context.cacheDir, "observation-engine-${System.nanoTime()}.db")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        server.shutdown()
        scope.cancel()
    }

    private fun openDatabase(): ShankaV25Database {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return ShankaV25Database.buildOnFile(context, dbFile.absolutePath)
    }

    private fun buildStack(db: ShankaV25Database): OfflineFirstV25Repository {
        val stack = NetworkStack(store, baseUrlOverride = server.url("/").toString())
        val remote = RemoteV25Repository.create(stack)
        val cache = V25CacheStore(db)
        val lanes = RequestLanes(scope)
        val sync = ReviewSyncCoordinator(
            remote = remote,
            cache = cache,
            sessionUser = { store.load()?.user?.userId },
            clock = TestClock(),
            lanes = lanes,
            scope = scope,
            onAuthoritativeRefreshNeeded = {},
        )
        return OfflineFirstV25Repository(
            remote = remote,
            cache = cache,
            sessionStore = store,
            lanes = lanes,
            reviewSync = sync,
            clock = TestClock(),
        )
    }

    private suspend fun seedParsingProject(db: ShankaV25Database, repo: OfflineFirstV25Repository) {
        // A priming read against the fake backend lands the PARSING snapshot — exactly the state
        // right after the upload returned on the 添加卡片组 screen.
        val priming = repo.getProject("p-1")
        assertTrue(priming is V25Result.Success)
        assertEquals(V25ProjectStatus.PARSING, (priming as V25Result.Success).value.status)
    }

    // --- 1. the regression: a PARSING project is polled to its terminal state without any screen ----

    @Test
    fun test_engine_polls_parsing_project_to_terminal_and_lands_it_in_room() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db)
        val cache = V25CacheStore(db)
        seedParsingProject(db, repo)
        backend.detailParsed = true

        val terminalHooks = CopyOnWriteArrayList<String>()
        val engine = ObservationEngine(
            repository = repo,
            sessionUser = { store.load()?.user?.userId },
            scope = scope,
        )
        engine.start()
        try {
            withTimeout(15_000) {
                cache.observeProjects("u-1").first { it.single().status != V25ProjectStatus.PARSING }
            }
            assertEquals(V25ProjectStatus.AWAITING_CHAPTER_CONFIRMATION, cache.readProject("u-1", "p-1")?.status)
            assertTrue("the engine must have polled the detail read itself", backend.detailRequestCount >= 2)
            assertTrue(terminalHooks.isEmpty()) // parse projects never fire the task hook
        } finally {
            engine.stop()
        }
        db.close()
    }

    // --- 2. a polling project stops once the projection shows terminal -------------------------------

    @Test
    fun test_engine_stops_polling_after_terminal_state_lands() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db)
        seedParsingProject(db, repo)
        backend.detailParsed = true

        val engine = ObservationEngine(repo, { store.load()?.user?.userId }, scope)
        engine.start()
        try {
            withTimeout(15_000) {
                while (backend.detailRequestCount < 2) delay(50)
            }
        } finally {
            engine.stop()
        }
        // Terminal landed; give a would-be poller every chance to fire again and assert silence.
        val countAtTerminal = backend.detailRequestCount
        delay(1_200)
        assertEquals("a terminal project must not be polled again", countAtTerminal, backend.detailRequestCount)
        db.close()
    }

    // --- 3. a GENERATING task is polled to COMPLETED and fires its terminal hook exactly once ---------

    @Test
    fun test_engine_polls_active_task_and_fires_terminal_hook_once() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db)
        val cache = V25CacheStore(db)
        val task = V25GenerationTask(
            taskId = "t-1",
            projectId = "p-1",
            fileId = "f-1",
            deckId = "d-1",
            retryOfTaskId = null,
            status = V25TaskStatus.GENERATING,
            internalStage = null,
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
            updatedAt = Instant.ofEpochMilli(1_000L),
        )
        cache.upsertTask("u-1", task, now = 1_000L)
        backend.taskCompleted = true

        val terminalHooks = CopyOnWriteArrayList<String>()
        val engine = ObservationEngine(
            repository = repo,
            sessionUser = { store.load()?.user?.userId },
            scope = scope,
            onTaskTerminal = { taskId -> terminalHooks.add(taskId) },
        )
        engine.start()
        try {
            withTimeout(15_000) {
                cache.observeTask("u-1", "t-1").first { it?.status == V25TaskStatus.COMPLETED }
            }
            assertTrue("the engine must have polled the task itself", backend.taskRequestCount >= 1)
            withTimeout(5_000) {
                while (terminalHooks.isEmpty()) delay(50)
            }
            delay(200)
            assertEquals(listOf("t-1"), terminalHooks)
        } finally {
            engine.stop()
        }
        db.close()
    }

    // --- 4. no session, no polls: a signed-out engine is inert ----------------------------------------

    @Test
    fun test_engine_without_session_never_polls() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db)
        val cache = V25CacheStore(db)
        seedParsingProject(db, repo)
        store.clear()

        val engine = ObservationEngine(repo, { store.load()?.user?.userId }, scope)
        engine.start()
        delay(1_500)
        engine.stop()
        assertEquals("signed-out engines must not touch the network", 1, backend.detailRequestCount)
        assertTrue(cache.readProject("u-1", "p-1") != null)
        db.close()
    }

    companion object {
        private fun projectBody(parsed: Boolean): String {
            val materialStatus = if (parsed) "PARSED" else "PARSING"
            val chapters = if (parsed) {
                """[{"chapter_id": "ch-1", "material_id": "m-1", "name": "第一章", "start_page": 1, "end_page": 10}]"""
            } else {
                "[]"
            }
            val status = if (parsed) "AWAITING_CHAPTER_CONFIRMATION" else "PARSING"
            val chapterCount = if (parsed) 1 else 0
            return """
                {"project_id": "p-1", "name": "测试项目",
                 "materials": [{"material_id": "m-1", "project_id": "p-1", "type": "PDF",
                                "name": "book.pdf", "status": "$materialStatus", "error_code": null,
                                "size_bytes": 1024, "char_count": null, "chapter": null,
                                "created_at": "2026-08-31T00:00:00Z"}],
                 "chapters": $chapters,
                 "status": "$status", "chapter_count": $chapterCount,
                 "deck_count": 0, "task_count": 0,
                 "created_at": "2026-08-31T00:00:00Z", "updated_at": "2026-08-31T00:00:00Z"}
            """.trimIndent()
        }

        private fun taskBody(completed: Boolean): String {
            val status = if (completed) "COMPLETED" else "GENERATING"
            val generated = if (completed) 12 else 0
            return """
                {"task_id": "t-1", "project_id": "p-1", "file_id": "f-1", "deck_id": "d-1",
                 "retry_of_task_id": null, "status": "$status", "internal_stage": null,
                 "selected_chapters": [],
                 "generation_config": {"coverage_mode": "BALANCED",
                                        "difficulty_ratio": {"basic": 40, "understanding": 40, "deep_question": 20},
                                        "requirement": ""},
                 "sample_cards": [], "sample_config_hash": null, "sample_confirmed_at": null,
                 "generated_card_count": $generated, "error_code": null, "failure_stage": null,
                 "created_at": "2026-08-31T00:00:00Z", "started_at": "2026-08-31T00:00:00Z",
                 "ended_at": null, "updated_at": "2026-08-31T00:00:00Z"}
            """.trimIndent()
        }
    }
}
