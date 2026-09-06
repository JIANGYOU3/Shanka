package com.qiuzhao.flashcards.data.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qiuzhao.flashcards.data.local.ShankaV25Database
import com.qiuzhao.flashcards.data.local.V25CacheStore
import com.qiuzhao.flashcards.data.remote.http.NetworkStack
import com.qiuzhao.flashcards.data.remote.v25.RemoteV25Repository
import com.qiuzhao.flashcards.data.session.InMemorySessionStore
import com.qiuzhao.flashcards.data.session.SessionUser
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JVM (Robolectric) discriminating suite for the offline foundation: a real Retrofit/OkHttp
 * stack against a fake backend (MockWebServer with an airplane-mode toggle) and a real
 * file-backed `shanka-v25.db`. Every assertion re-reads the persisted database and the fake
 * server's recorded state — mock call counts alone are never the verdict.
 */
// --- wire fixtures shared by the harness classes ------------------------------------------------------

private fun decksBody(deckId: String): String = """
    {"items": [{
        "deck_id": "$deckId", "name": "线性代数", "card_count": 3, "due_count": 3,
        "mastered_card_count": 0, "review_count": 0, "mastery_ratio": 0.0
    }]}
""".trimIndent()

private fun todayBody(studyDate: LocalDate, clock: Clock): String {
    val cards = (1..3).joinToString(",") { index ->
        """
        {"card_id": "c-$index", "deck_id": "d-u1", "position": $index,
         "front": "问题$index", "back": "答案$index", "card_type": "QUESTION",
         "review_state": {"state": "NEW", "due": null}}
        """.trimIndent()
    }
    return """
        {"timezone": "UTC", "study_date": "$studyDate", "daily_goal": 10,
         "today_completed_count": 0, "due_count": 3, "main_plan_remaining": 3, "backlog_count": 0,
         "cards": [$cards]}
    """.trimIndent()
}

private fun dashboardBody(): String = """
    {"period": {"start": "2026-08-24T00:00:00Z"}, "timezone": "UTC",
     "weekly_activity": [0,0,0,0,0,0,0], "weekly_total": 0, "weekly_completed_count": 0,
     "weekly_goal": 100, "streak_days": 0, "mastered_card_count": 0,
     "updated_at": "2026-08-30T00:00:00Z", "has_data": false}
""".trimIndent()

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class OfflineFoundationTest {

    /** Mutable clock: TTL judgments, cross-day rollovers and retry backoffs are all testable. */
    class TestClock(var nowMs: Long = Instant.parse("2026-08-30T00:00:00Z").toEpochMilli()) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
        fun advance(millis: Long) { nowMs += millis }
    }

    /** Fake backend: airplane mode, scripted per-user payloads, recorded review events. */
    class FakeBackend(private val clock: Clock) : Dispatcher() {
        @Volatile var offline = false

        /** review-events actually RECEIVED by the server: path to identity evidence. */
        val receivedReviewEvents = CopyOnWriteArrayList<Pair<String, String>>()

        /** How many of the next review-events requests fail with a 503 before succeeding. */
        @Volatile var failNextReviews = 0

        /** Scripted failure mode of /review-events for the lifecycle tests. */
        enum class ReviewMode { NORMAL, UNAUTHORIZED_401, CARD_NOT_FOUND_404 }
        @Volatile var reviewMode = ReviewMode.NORMAL

        /** Gate for lane tests: when non-null, /decks blocks on the latch (background busy). */
        @Volatile var decksGate: java.util.concurrent.CountDownLatch? = null
        var decksRequestCount = 0

        override fun dispatch(request: RecordedRequest): MockResponse {
            if (offline) throw IOException("airplane mode")
            val path = request.path ?: return notFound()
            return when {
                path.startsWith("/decks") -> {
                    decksRequestCount++
                    decksGate?.await()
                    ok(decksBody(deckFor(request.getHeader("Authorization"))))
                }
                path == "/study/today" -> ok(todayBody(LocalDate.now(clock), clock))
                path == "/stats/dashboard" -> ok(dashboardBody())
                path == "/review-events" -> {
                    when (reviewMode) {
                        ReviewMode.UNAUTHORIZED_401 -> return MockResponse().setResponseCode(401).setBody(
                            """{"error": {"code": "AUTH_REQUIRED", "message": "missing bearer"}}""",
                        )
                        ReviewMode.CARD_NOT_FOUND_404 -> return MockResponse().setResponseCode(404).setBody(
                            """{"error": {"code": "CARD_NOT_FOUND"}}""",
                        )
                        ReviewMode.NORMAL -> Unit
                    }
                    val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                    receivedReviewEvents += (
                        body["client_event_id"]!!.jsonPrimitive.content to
                            request.getHeader("Idempotency-Key")!!
                        )
                    if (failNextReviews > 0) {
                        failNextReviews--
                        // No error envelope on purpose: the repository derives the transient HTTP_503 code.
                        MockResponse().setResponseCode(503).setBody("""{"detail": "unavailable"}""")
                    } else {
                        ok(
                            """{"review_state": {"state": "REVIEW", "due": "2026-09-01T00:00:00Z"},
                                "study_date": "${LocalDate.now(clock)}"}""",
                        )
                    }
                }
                else -> notFound()
            }
        }

        /** The bearer token names the account: u-2 sees a different deck set. */
        private fun deckFor(authorization: String?): String =
            if (authorization == "Bearer token-u2") "d-u2" else "d-u1"

        private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
        private fun notFound() = MockResponse().setResponseCode(404).setBody("""{"error":{"code":"NOT_FOUND"}}""")
    }

    // --- harness ---------------------------------------------------------------------------------

    private lateinit var server: MockWebServer
    private lateinit var backend: FakeBackend
    private lateinit var clock: TestClock
    private lateinit var store: InMemorySessionStore
    private lateinit var dbFile: File
    private var dbGeneration = 0

    private val user1 = SessionUser(userId = "u-1", username = "alice", createdAt = "2026-08-01T00:00:00Z")
    private val user2 = SessionUser(userId = "u-2", username = "bob", createdAt = "2026-08-01T00:00:00Z")

    @Before
    fun setUp() {
        clock = TestClock()
        server = MockWebServer()
        backend = FakeBackend(clock)
        server.dispatcher = backend
        server.start()
        store = InMemorySessionStore()
        store.save("token-u1", user1)
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbFile = File(context.cacheDir, "offline-foundation-${System.nanoTime()}.db")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Count of one-shot authoritative refreshes triggered by permanent outbox failures. */
    private var authoritativeRefreshes = 0

    /** Builds a full process-level object graph on the shared file-backed database. */
    private fun buildStack(db: ShankaV25Database, offlineRepoScope: CoroutineScope): OfflineFirstV25Repository {
        val stack = NetworkStack(store, baseUrlOverride = server.url("/").toString())
        val remote = RemoteV25Repository.create(stack)
        val cache = V25CacheStore(db)
        val lanes = RequestLanes(offlineRepoScope)
        val sync = ReviewSyncCoordinator(
            remote = remote,
            cache = cache,
            sessionUser = { store.load()?.user?.userId },
            clock = clock,
            lanes = lanes,
            scope = offlineRepoScope,
            onAuthoritativeRefreshNeeded = { authoritativeRefreshes++ },
        )
        return OfflineFirstV25Repository(
            remote = remote,
            cache = cache,
            sessionStore = store,
            lanes = lanes,
            reviewSync = sync,
            clock = clock,
        )
    }

    private fun openDatabase(): ShankaV25Database {
        dbGeneration++
        val context = ApplicationProvider.getApplicationContext<Context>()
        return ShankaV25Database.buildOnFile(context, dbFile.absolutePath)
    }

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun signInAs(user: SessionUser, token: String) = store.save(token, user)

    // --- 1. outbox atomicity: the swipe commits locally before any network ----------------------------

    @Test
    fun `rating offline lands in the outbox and advances the queue atomically`() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db, scope())
        // Prime today's plan online: 3 cards become the local queue.
        val plan = repo.todayPlan()
        assertTrue(plan is com.qiuzhao.flashcards.domain.v25.V25Result.Success)
        assertEquals(3, (plan as com.qiuzhao.flashcards.domain.v25.V25Result.Success).value.cards.size)

        backend.offline = true

        val swipes = listOf("c-1" to "GOOD", "c-2" to "HARD", "c-3" to "AGAIN")
        swipes.forEach { (card, rating) ->
            val result = repo.rateCard(card, com.qiuzhao.flashcards.domain.v25.V25Rating.valueOf(rating))
            // The local transaction succeeded: the swipe is acknowledged immediately.
            assertTrue(result is com.qiuzhao.flashcards.domain.v25.V25Result.Success)
        }

        // Persisted DB (re-read through a fresh query): 3 pending rows, all queue cards hidden.
        val rows = V25CacheStore(db).allOutbox("u-1")
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.status == "PENDING" })
        assertTrue(rows.all { it.lastErrorCode == null })
        val state = repo.todayPlanState()
        assertTrue(state is TodayPlanState.Fresh)
        assertEquals(0, (state as TodayPlanState.Fresh).plan.cards.size)
        db.close()
    }

    // --- 2. fixed identities across retries ---------------------------------------------------------------

    /**
     * A completed plan write (updateStudyPlan.alsoOnSuccess) drops the today-plan metadata row.
     * The next read must then observe the server's recomputed remainder — serving the pre-save
     * Room projection would leave the home page's 今日计划 frozen on stale counts.
     */
    @Test
    fun `an invalidated today plan is refetched instead of served stale`() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db, scope())
        val cache = V25CacheStore(db)
        val user = user1.userId
        val today = LocalDate.now(clock)

        assertTrue(repo.todayPlan() is com.qiuzhao.flashcards.domain.v25.V25Result.Success)

        // Replay the save-side effects: mutate the cached projection, then drop its metadata.
        val stale = cache.readTodayPlan(user, today)!!
        cache.replaceTodayPlan(user, stale.copy(planRemaining = 99), clock.nowMs + 1_000)
        clock.advance(2_000)
        cache.invalidate(user, V25CacheStore.KEY_TODAY_PLAN)

        val next = repo.todayPlan()
        assertTrue(next is com.qiuzhao.flashcards.domain.v25.V25Result.Success)
        assertEquals(3, (next as com.qiuzhao.flashcards.domain.v25.V25Result.Success).value.planRemaining)
        assertEquals(3, cache.readTodayPlan(user, today)!!.planRemaining)
        db.close()
    }

    @Test
    fun `retries replay the original client_event_id and idempotency key verbatim`() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db, scope())
        repo.todayPlan() // prime the plan so the local queue exists

        // First two attempts hit a 503; the third succeeds.
        backend.failNextReviews = 2
        val result = repo.rateCard("c-1", com.qiuzhao.flashcards.domain.v25.V25Rating.GOOD)
        assertTrue(result is com.qiuzhao.flashcards.domain.v25.V25Result.Success)

        // Drive retries through the coordinator with the mutable clock past each backoff.
        repeat(4) {
            clock.advance(61_000)
            repo.reviewSync.syncOnce()
        }

        // Fake server received three attempts of ONE event with identical identities.
        assertEquals(3, backend.receivedReviewEvents.size)
        val identities = backend.receivedReviewEvents.toSet()
        assertEquals("all three attempts replayed the same pair of identities", 1, identities.size)

        // Persisted outbox row is completed with attempt bookkeeping and no error code.
        val row = V25CacheStore(db).allOutbox("u-1").single()
        assertEquals("COMPLETED", row.status)
        assertEquals(identities.single().first, row.clientEventId)
        assertEquals(identities.single().second, row.idempotencyKey)
        db.close()
    }

    // --- 3. account isolation -------------------------------------------------------------------------------

    @Test
    fun `caches and outbox rows are isolated per user`() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db, scope())
        val cache = V25CacheStore(db)

        // u-1 primes decks + today plan.
        repo.listDecks()
        repo.todayPlan()

        // Switch the session to u-2 on the same device: a different deck set is fetched.
        signInAs(user2, "token-u2")
        val decksForUser2 = repo.listDecks()
        assertTrue(decksForUser2 is com.qiuzhao.flashcards.domain.v25.V25Result.Success)
        assertEquals("d-u2", (decksForUser2 as com.qiuzhao.flashcards.domain.v25.V25Result.Success).value.single().deckId)

        // Persisted rows of both users coexist and never mix.
        assertEquals("d-u1", cache.readDecks("u-1").single().deckId)
        assertEquals("d-u2", cache.readDecks("u-2").single().deckId)

        // u-2 rates offline; u-1's identical card queue stays untouched.
        backend.offline = true
        repo.rateCard("c-1", com.qiuzhao.flashcards.domain.v25.V25Rating.GOOD)
        val user1Plan = cache.readTodayPlan("u-1", LocalDate.now(clock))
        assertEquals(3, user1Plan!!.cards.size)
        val user2Outbox = cache.allOutbox("u-2")
        assertEquals(1, user2Outbox.size)
        assertEquals("u-2", user2Outbox.single().userId)

        // The signed-in session's sync only drains that user's rows.
        clock.advance(120_000)
        repo.reviewSync.syncOnce()
        assertEquals("u-2 row survives a u-2 sync attempt offline", "PENDING", cache.allOutbox("u-2").single().status)
        db.close()
    }

    // --- 4. network failure never clears the cache ------------------------------------------------------------

    @Test
    fun `a failed refresh keeps the last successful cache`() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db, scope())
        val cache = V25CacheStore(db)
        val first = repo.listDecks()
        assertEquals("d-u1", (first as com.qiuzhao.flashcards.domain.v25.V25Result.Success).value.single().deckId)

        // Go offline and force past the TTL: the refresh fails, the cache must survive.
        backend.offline = true
        clock.advance(10 * 60_000)
        repo.currentPolicy = RefreshPolicy.FORCE
        val forced = repo.listDecks()
        assertTrue(forced is com.qiuzhao.flashcards.domain.v25.V25Result.Failure)

        // Soft read while still offline serves the preserved snapshot, not an error.
        repo.currentPolicy = RefreshPolicy.SOFT_TTL
        val cached = repo.listDecks()
        assertTrue(cached is com.qiuzhao.flashcards.domain.v25.V25Result.Success)
        assertEquals("d-u1", (cached as com.qiuzhao.flashcards.domain.v25.V25Result.Success).value.single().deckId)
        assertEquals("d-u1", cache.readDecks("u-1").single().deckId)
        db.close()
    }

    // --- 5. cross-day staleness --------------------------------------------------------------------------------

    @Test
    fun `an old study date is never served as today's plan`() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db, scope())
        repo.todayPlan() // cached for 2026-08-30

        // Midnight passes; the device is offline with only yesterday's cached plan.
        clock.advance(24 * 60 * 60 * 1_000L)
        backend.offline = true
        repo.currentPolicy = RefreshPolicy.FORCE

        val state = repo.todayPlanState()
        assertTrue(state is TodayPlanState.StaleNoData)
        assertEquals(LocalDate.parse("2026-08-30"), (state as TodayPlanState.StaleNoData).latestCachedStudyDate)

        // The judged empty state: today's date, zero cards — never yesterday's queue.
        val plan = repo.todayPlan()
        assertTrue(plan is com.qiuzhao.flashcards.domain.v25.V25Result.Success)
        val value = (plan as com.qiuzhao.flashcards.domain.v25.V25Result.Success).value
        assertEquals(LocalDate.now(clock), value.studyDate)
        assertEquals(0, value.cards.size)
        assertEquals(false, value.planConfigured)
        db.close()
    }

    // --- 6. single-flight revalidation ---------------------------------------------------------------------------

    @Test
    fun `concurrent stale reads collapse into one background fetch`() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db, scope())
        repo.listDecks() // prime

        // Expire the TTL and block the deck refresh at the server, then fire 5 stale reads:
        // the first grabs the single background permit, the other four must join its flight.
        clock.advance(10 * 60_000)
        val gate = java.util.concurrent.CountDownLatch(1)
        backend.decksGate = gate
        repeat(5) { repo.listDecks() }

        // Exactly one revalidation reached the server even before the gate opens.
        waitUntil { backend.decksRequestCount == 2 }
        gate.countDown()
        db.close()
    }

    // --- 7. foreground ratings never queue behind background refreshes ---------------------------------------------

    @Test
    fun `a rating completes while a background refresh is blocked`() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db, scope())
        repo.listDecks()
        repo.todayPlan()

        // Block the (single-permit) background lane with a stuck /decks refresh.
        val gate = java.util.concurrent.CountDownLatch(1)
        backend.decksGate = gate
        clock.advance(10 * 60_000)
        repo.listDecks() // stale → background revalidation grabs the lane permit and blocks

        // The foreground swipe must not wait for that stuck refresh.
        val started = System.nanoTime()
        val result = repo.rateCard("c-1", com.qiuzhao.flashcards.domain.v25.V25Rating.GOOD)
        val swipeMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(result is com.qiuzhao.flashcards.domain.v25.V25Result.Success)
        assertTrue("swipe queued behind the background refresh: ${swipeMs}ms", swipeMs < 2_000)
        assertEquals(1, V25CacheStore(db).allOutbox("u-1").size)

        gate.countDown()
        db.close()
    }

    // --- outbox lifecycle: 401 pause and permanent-4xx failure --------------------------------------------

    @Test
    fun `a session-death 401 pauses the sync and keeps the row pending`() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db, scope())
        repo.todayPlan()
        backend.reviewMode = FakeBackend.ReviewMode.UNAUTHORIZED_401

        repo.rateCard("c-1", com.qiuzhao.flashcards.domain.v25.V25Rating.GOOD)
        Thread.sleep(200) // let the in-process kick meet the 401
        clock.advance(120_000)

        val outcome = repo.reviewSync.syncOnce()
        assertEquals(SyncOutcome.PAUSED_AUTH, outcome)
        assertTrue(repo.reviewSync.isPausedForAuth())

        val row = V25CacheStore(db).allOutbox("u-1").single()
        assertEquals("PENDING", row.status)
        assertEquals("AUTH_REQUIRED", row.lastErrorCode)

        // A paused coordinator short-circuits instead of hot-looping on the dead session.
        assertEquals(SyncOutcome.PAUSED_AUTH, repo.reviewSync.syncOnce())
        assertEquals(1, V25CacheStore(db).allOutbox("u-1").size)
        db.close()
    }

    @Test
    fun `a permanent 4xx marks the row failed once and triggers one authoritative refresh`() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db, scope())
        repo.todayPlan()
        backend.reviewMode = FakeBackend.ReviewMode.CARD_NOT_FOUND_404

        repo.rateCard("c-1", com.qiuzhao.flashcards.domain.v25.V25Rating.GOOD)
        Thread.sleep(200) // in-process kick
        clock.advance(120_000)
        repo.reviewSync.syncOnce()
        repo.reviewSync.syncOnce() // a second pass must not refresh again

        val row = V25CacheStore(db).allOutbox("u-1").single()
        assertEquals("FAILED", row.status)
        assertEquals("CARD_NOT_FOUND", row.lastErrorCode)
        assertEquals("exactly one authoritative refresh", 1, authoritativeRefreshes)
        db.close()
    }

    // --- persistence identity (no destructive fallback) ---------------------------------------------------------------

    @Test
    fun `closing and reopening the database preserves every projection`() = runBlocking {
        val db = openDatabase()
        val repo = buildStack(db, scope())
        repo.listDecks()
        repo.todayPlan()
        val cache = V25CacheStore(db)
        db.close()

        val reopened = openDatabase()
        val reopenedCache = V25CacheStore(reopened)
        assertEquals("d-u1", reopenedCache.readDecks("u-1").single().deckId)
        assertNotNull(reopenedCache.readTodayPlan("u-1", LocalDate.now(clock)))
        reopened.close()
    }

    // --- helpers --------------------------------------------------------------------------------------------------

    private fun waitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(condition())
    }
}
