package com.qiuzhao.flashcards.data.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.qiuzhao.flashcards.data.local.ShankaV25Database
import com.qiuzhao.flashcards.data.local.V25CacheStore
import com.qiuzhao.flashcards.data.remote.http.NetworkStack
import com.qiuzhao.flashcards.data.remote.v25.RemoteV25Repository
import com.qiuzhao.flashcards.data.session.InMemorySessionStore
import com.qiuzhao.flashcards.data.session.SessionUser
import com.qiuzhao.flashcards.domain.v25.V25Card
import com.qiuzhao.flashcards.domain.v25.V25CardType
import com.qiuzhao.flashcards.domain.v25.V25PublicationState
import com.qiuzhao.flashcards.domain.v25.V25Rating
import com.qiuzhao.flashcards.domain.v25.V25Result
import com.qiuzhao.flashcards.work.ReviewSyncWorker
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

/**
 * The contract's automated airplane-mode scenario, end to end on the JVM:
 * rate 10 cards offline → kill the process-level objects (database closed, repository /
 * coordinator / lanes rebuilt) → the persisted outbox still holds 10 events → network restored
 * → the fake server has received EXACTLY 10 unique events whose client_event_id and
 * Idempotency-Key are the original values → outbox drained, review states updated.
 *
 * Benchmarks at the bottom measure the local swipe path (p95 ≤ 100ms) and the Room cold start
 * of the first cached batch (p95 ≤ 300ms). They are JVM/Robolectric numbers on the development
 * machine (WSL2), not device evidence.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class OfflineRecoveryAndBenchmarkTest {

    private class FixedClock(nowMs: Long = Instant.parse("2026-08-30T00:00:00Z").toEpochMilli()) : Clock() {
        var nowMs = nowMs
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    }

    /** Records every review event the SERVER received, with both dedupe identities. */
    private class RecordingBackend(private val clock: Clock) : Dispatcher() {
        @Volatile var offline = false
        val reviewEvents = CopyOnWriteArrayList<Pair<String, String>>()
        val ratedCardIds = CopyOnWriteArrayList<String>()
        val todayRequests = CopyOnWriteArrayList<Unit>()
        val decksRequests = CopyOnWriteArrayList<Unit>()
        val dashboardRequests = CopyOnWriteArrayList<Unit>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            if (offline) throw IOException("airplane mode")
            val path = request.path ?: return MockResponse().setResponseCode(404)
            return when {
                path == "/study/today" -> {
                    todayRequests += Unit
                    ok(todayBody(LocalDate.now(clock)))
                }
                path.startsWith("/decks") -> {
                    decksRequests += Unit
                    ok("""{"items": []}""")
                }
                path == "/stats/dashboard" -> {
                    dashboardRequests += Unit
                    ok(dashboardBody())
                }
                path == "/review-events" -> {
                    val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                    reviewEvents += (
                        body["client_event_id"]!!.jsonPrimitive.content to request.getHeader("Idempotency-Key")!!
                        )
                    ratedCardIds += body["card_id"]!!.jsonPrimitive.content
                    ok(
                        """{"review_state": {"state": "REVIEW", "due": "2026-09-01T00:00:00Z"},
                            "study_date": "${LocalDate.now(clock)}"}""",
                    )
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

        private fun todayBody(studyDate: LocalDate): String {
            val cards = (1..10).joinToString(",") { index ->
                // A card the server has already rated reports its post-rating state, so a
                // post-drain today revalidation never resets local facts to stale NEW ones.
                val state = if ("c-$index" in ratedCardIds) "REVIEW" else "NEW"
                """{"card_id": "c-$index", "deck_id": "d-1", "position": $index,
                    "front": "问题$index", "back": "答案$index", "card_type": "QUESTION",
                    "review_state": {"state": "$state", "due": null}}"""
            }
            return """{"timezone": "UTC", "study_date": "$studyDate", "daily_goal": 10,
                "today_completed_count": 0, "due_count": 10, "main_plan_remaining": 10, "backlog_count": 0,
                "cards": [$cards]}"""
        }

        private fun dashboardBody(): String =
            """{"period": {"start": "2026-08-24T00:00:00Z"}, "timezone": "UTC",
                "weekly_activity": [0,0,0,0,0,0,0], "weekly_total": 0, "weekly_completed_count": 0,
                "weekly_goal": 100, "streak_days": 0, "mastered_card_count": 0,
                "updated_at": "2026-08-30T00:00:00Z", "has_data": false}"""
    }

    private lateinit var server: MockWebServer
    private lateinit var backend: RecordingBackend
    private lateinit var clock: FixedClock
    private lateinit var store: InMemorySessionStore
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        clock = FixedClock()
        server = MockWebServer()
        backend = RecordingBackend(clock)
        server.dispatcher = backend
        server.start()
        store = InMemorySessionStore()
        store.save(
            "token-1",
            SessionUser(userId = "u-1", username = "alice", createdAt = "2026-08-01T00:00:00Z"),
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbFile = File(context.cacheDir, "recovery-${System.nanoTime()}.db")
        // WorkManager on-demand initialization for the enqueue wiring assertions.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** A full process-level graph; a new instance per "process" in the scenario. */
    private class Stack(
        val database: ShankaV25Database,
        val cache: V25CacheStore,
        val repository: OfflineFirstV25Repository,
    )

    private fun buildProcess(dbFile: File): Stack {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = ShankaV25Database.buildOnFile(context, dbFile.absolutePath)
        val cache = V25CacheStore(db)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val network = NetworkStack(store, baseUrlOverride = server.url("/").toString())
        val remote = RemoteV25Repository.create(network)
        val lanes = RequestLanes(scope)
        val sync = ReviewSyncCoordinator(
            remote = remote,
            cache = cache,
            sessionUser = { store.load()?.user?.userId },
            clock = clock,
            lanes = lanes,
            scope = scope,
        )
        return Stack(db, cache, OfflineFirstV25Repository(remote, cache, store, lanes, sync, clock))
    }

    @Test
    fun `ten offline ratings survive a process rebuild and sync exactly once each`() = runBlocking {
        // --- Phase 1: online, hydrate today's queue. -----------------------------------------------
        val first = buildProcess(dbFile)
        val plan = first.repository.todayPlan()
        assertTrue(plan is V25Result.Success)
        assertEquals(10, (plan as V25Result.Success).value.cards.size)

        // --- Phase 2: airplane mode; rate all 10 cards. -------------------------------------------
        backend.offline = true
        repeat(10) { index ->
            val result = first.repository.rateCard("c-${index + 1}", V25Rating.GOOD)
            assertTrue("swipe $index must succeed locally", result is V25Result.Success)
        }
        // Give the in-process sync kick a moment to discover the offline state and schedule
        // its retry backoff (this is what the WorkManager backstop would inherit).
        Thread.sleep(300)
        val beforeKill = first.cache.allOutbox("u-1")
        assertEquals(10, beforeKill.size)
        val originalIdentities = beforeKill.map { it.clientEventId to it.idempotencyKey }

        // --- Phase 3: process death: close the DB, rebuild every process-level object. ------------
        first.database.close()
        val second = buildProcess(dbFile)

        // The persisted database still holds the 10 pending events with their fixed identities.
        val afterRebuild = second.cache.allOutbox("u-1")
        assertEquals(10, afterRebuild.size)
        assertEquals(
            "the rebuilt process sees the original identity pairs",
            originalIdentities.toSet(),
            afterRebuild.map { it.clientEventId to it.idempotencyKey }.toSet(),
        )
        assertTrue(afterRebuild.all { it.status == "PENDING" })

        // --- Phase 4: network restored; the worker path drains the outbox. ------------------------
        backend.offline = false
        // Retry backoff may sit in the future; advance virtual time and drain.
        repeat(8) {
            second.repository.reviewSync.syncOnce()
            if (second.cache.allOutbox("u-1").all { it.status == "COMPLETED" }) return@repeat
            clock.nowMs += 120_000
        }

        // --- Verdict 1 — persisted DB: every row completed, identities unchanged. ------------------
        val drained = second.cache.allOutbox("u-1")
        assertEquals(10, drained.size)
        assertTrue(drained.all { it.status == "COMPLETED" })
        assertEquals(originalIdentities.toSet(), drained.map { it.clientEventId to it.idempotencyKey }.toSet())

        // --- Verdict 2 — fake server: exactly 10 unique events, original identities. ---------------
        assertEquals("the server saw exactly one event per swipe", 10, backend.reviewEvents.size)
        assertEquals("every client_event_id is unique", 10, backend.reviewEvents.map { it.first }.toSet().size)
        assertEquals(
            "the server received the original identity pairs, not regenerated ones",
            originalIdentities.toSet(),
            backend.reviewEvents.toSet(),
        )

        // --- Verdict 3 — server review states became the local facts. -------------------------------
        val reviewState = second.cache.readReviewState("u-1", "c-1")
        assertEquals("REVIEW", reviewState?.state)
        second.database.close()
    }

    @Test
    fun `the WorkManager backstop is per-user unique work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ReviewSyncWorker.enqueue(context, "u-1")
        ReviewSyncWorker.enqueue(context, "u-1") // KEEP policy: still one work
        ReviewSyncWorker.enqueue(context, "u-2")
        val workManager = WorkManager.getInstance(context)
        val workSpecs = workManager.getWorkInfosForUniqueWork(ReviewSyncWorker.uniqueName("u-1")).get()
        assertEquals(1, workSpecs.size)
        val otherUser = workManager.getWorkInfosForUniqueWork(ReviewSyncWorker.uniqueName("u-2")).get()
        assertEquals(1, otherUser.size)
    }

    @Test
    fun `a drained outbox triggers one merged refresh, not a per-card fan-out`() = runBlocking {
        val stack = buildProcess(dbFile)
        stack.repository.todayPlan() // hydrate: baseline today=1, decks=0, dashboard=0

        backend.offline = true
        repeat(10) { index ->
            assertTrue(stack.repository.rateCard("c-${index + 1}", V25Rating.GOOD) is V25Result.Success)
        }
        // Let the in-process kicks discover the offline state (their transient failures schedule
        // per-row backoff), then restore the network and clear every backoff via virtual time.
        Thread.sleep(300)
        backend.offline = false

        // Drain: each pass that sends >= 1 event fires ONE merged refresh. A straggler row on
        // backoff may cost an extra pass, so refresh counts scale with passes, never with cards.
        repeat(8) {
            stack.repository.reviewSync.syncOnce()
            if (stack.cache.allOutbox("u-1").all { it.status == "COMPLETED" }) return@repeat
            clock.nowMs += 120_000
        }
        val drained = stack.cache.allOutbox("u-1")
        assertTrue("outbox never drained: " + drained.map { it.status }, drained.all { it.status == "COMPLETED" })

        // Wait for the final pass's merged refresh (3 background GETs) to reach the server.
        val deadline = System.currentTimeMillis() + 10_000
        while (backend.decksRequests.isEmpty() || backend.dashboardRequests.isEmpty()) {
            assertTrue("merged refresh never arrived", System.currentTimeMillis() < deadline)
            Thread.sleep(50)
        }
        // Settle window: a per-card fan-out (the removed behavior) would keep adding requests.
        Thread.sleep(500)

        assertEquals("exactly one server event per swipe", 10, backend.reviewEvents.size)
        // Baselines: decks 0, dashboard 0, today 1 (hydrate). Allowed: one refresh per sending
        // pass — at most 3 passes here. The old per-card fan-out produced one set per CARD (10+).
        assertTrue(
            "decks refreshes must scale with passes, not cards: ${backend.decksRequests.size}",
            backend.decksRequests.size in 1..3,
        )
        assertTrue(
            "today refreshes beyond the hydrate must scale with passes: ${backend.todayRequests.size}",
            backend.todayRequests.size in 2..4,
        )
        assertTrue(
            "dashboard refreshes must scale with passes, not cards: ${backend.dashboardRequests.size}",
            backend.dashboardRequests.size in 1..3,
        )
        stack.database.close()
    }

    // --- benchmarks (JVM/Robolectric, development machine; not device evidence) --------------------

    @Test
    fun `benchmark local swipe path p95 under 100ms`() = runBlocking {
        val stack = buildProcess(dbFile)
        stack.repository.todayPlan() // hydrate the queue
        backend.offline = true // pure local path: no network in the measurement

        val samples = mutableListOf<Long>()
        repeat(100) { index ->
            val cardId = "c-${index % 10 + 1}"
            val started = System.nanoTime()
            val result = stack.repository.rateCard(cardId, V25Rating.GOOD)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue(result is V25Result.Success)
            samples += elapsedMs
        }
        val p95 = samples.sorted()[94]
        println("SWIPE_BENCH samples=100 p50=${samples.sorted()[49]}ms p95=$p95 ms max=${samples.max()}ms")
        assertTrue("swipe p95 ${p95}ms exceeded the 100ms budget", p95 <= 100)
        stack.database.close()
    }

    @Test
    fun `benchmark room cold start first cached batch p95 under 300ms`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val samples = mutableListOf<Long>()
        repeat(20) { round ->
            val file = File(context.cacheDir, "cold-${System.nanoTime()}-$round.db")
            val started = System.nanoTime()
            val db = ShankaV25Database.buildOnFile(context, file.absolutePath)
            val cache = V25CacheStore(db)
            cache.replaceDeckCards("u-1", "d-1", (1..200).map { card(it) }, System.currentTimeMillis())
            val read = cache.readDeckCards("u-1", "d-1")
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertEquals(200, read.size)
            samples += elapsedMs
            db.close()
            file.delete()
            file.parentFile?.let { dir -> File(dir, "$round-journal").delete() }
        }
        val p95 = samples.sorted()[18]
        println("COLD_START_BENCH samples=20 p50=${samples.sorted()[9]}ms p95=$p95 ms max=${samples.max()}ms")
        assertTrue("cold start p95 ${p95}ms exceeded the 300ms budget", p95 <= 300)
    }

    private fun card(index: Int) = V25Card(
        cardId = "c-$index",
        deckId = "d-1",
        front = "正面 $index",
        back = "背面 $index",
        cardType = V25CardType.QUESTION,
        targetDifficulty = null,
        position = index,
        chapterId = null,
        sourceTaskId = null,
        publicationState = V25PublicationState.PUBLISHED,
        version = 1,
    )
}
