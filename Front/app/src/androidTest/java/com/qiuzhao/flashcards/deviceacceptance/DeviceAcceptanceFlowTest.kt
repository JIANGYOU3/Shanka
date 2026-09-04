package com.qiuzhao.flashcards.deviceacceptance

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qiuzhao.flashcards.FlashcardsApplication
import com.qiuzhao.flashcards.data.remote.ApiResult
import com.qiuzhao.flashcards.data.session.loadQuietly
import com.qiuzhao.flashcards.domain.v25.V25CardDraft
import com.qiuzhao.flashcards.domain.v25.V25ImportStatus
import com.qiuzhao.flashcards.domain.v25.V25Rating
import com.qiuzhao.flashcards.domain.v25.V25Result
import com.qiuzhao.flashcards.domain.v25.V25StudyPlanUpdate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device offline acceptance for offline-foundation-v1 (DEVICE_ACCEPTANCE_CLOSEOUT).
 * Not part of the normal `connectedDebugAndroidTest` run: every stage requires the explicit
 * `shankaStage` instrumentation argument and assumes-skip otherwise.
 *
 * The host drives the phases in order with adb reverse add/remove + `am force-stop` between
 * invocations; each stage runs in the app's own process against the app's real shanka-v25.db,
 * the real Keystore session and the real network stack, so the evidence is the app's
 * production persistence (no mock transport, no extracted binary DB):
 *
 *   provision     online: register a throwaway account, create a project (tiny synthetic
 *                 PDF; local storage only, never an AI path), create a deck in it, import
 *                 10 cards, configure the study plan (today's plan is project-scoped and
 *                 an unconfigured plan legitimately returns zero cards) and hydrate the
 *                 plan into Room; outbox must stay empty.
 *   rate_offline  reverse removed: rate exactly 10 cards; each swipe must succeed locally;
 *                 outbox holds 10 PENDING rows with unique identity pairs.
 *   verify_pending after am force-stop + relaunch (still offline): cache still readable,
 *                 the 10 rows are still PENDING with the same identities.
 *   drain         reverse restored: poll the real coordinator until all 10 rows are
 *                 COMPLETED (no FAILED), identities unchanged.
 *   verify_stable second force-stop + relaunch: rows stay COMPLETED; the server-side
 *                 "no new events" check is done by the host against the isolated DB.
 *
 * Only non-sensitive identifiers (uuids, counts, statuses) are printed; passwords, tokens
 * and card text never reach the instrumentation output.
 */
@RunWith(AndroidJUnit4::class)
class DeviceAcceptanceFlowTest {

    private lateinit var app: FlashcardsApplication

    @Before
    fun requireExplicitStage() {
        app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FlashcardsApplication
    }

    private fun stage(): String =
        InstrumentationRegistry.getArguments().getString("shankaStage")?.toString() ?: ""

    private fun assumeStage(expected: String) {
        assumeTrue(
            "device-acceptance stage '$expected' runs only with -e shankaStage $expected, got '${stage()}'",
            stage() == expected,
        )
    }

    private fun report(line: String) = println("SHANKA_ACCEPTANCE $line")

    /**
     * Bring the real debug Activity to the foreground from the harness itself. MIUI may
     * restrict background networking, and androidx's startActivitySync is unusable here: its
     * idle detection never settles while the Compose UI repaints. A plain context start plus
     * an application-wide RESUMED latch is deterministic: the harness's own launch may be
     * aborted by vendor BAL policy, and the latch is then released by the host's shell
     * `am start` (shell launches are exempt), so the latch counts any resumed activity.
     */
    private fun awaitForeground() {
        val resumed = java.util.concurrent.CountDownLatch(1)
        val callbacks = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                resumed.countDown()
            }
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            val launcher = app.packageManager.getLaunchIntentForPackage(app.packageName)
            assertTrue("no launch intent for ${app.packageName}", launcher != null)
            launcher!!.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(launcher)
            assertTrue(
                "debug Activity never resumed within 30s",
                resumed.await(30, java.util.concurrent.TimeUnit.SECONDS),
            )
        } finally {
            app.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }

    /** Chain diagnostic: GET /healthz through the app's own OkHttp stack (no Compose, screen-off safe). */
    @Test
    fun probe() = runBlocking {
        // Runs as the dedicated 'probe' stage and as the no-stage full-run smoke, so the
        // whole-runner acceptance pass always executes a real, read-only network test.
        assumeTrue(
            "probe runs with -e shankaStage probe or without any stage, got '${stage()}'",
            stage() == "probe" || stage().isEmpty(),
        )
        awaitForeground()
        val url = com.qiuzhao.flashcards.BuildConfig.API_BASE_URL.trimEnd('/') + "/healthz"
        try {
            val request = okhttp3.Request.Builder().url(url).build()
            app.container.networkStack.apiClient.newCall(request).execute().use { response ->
                report("probe url=$url status=${response.code}")
                assertEquals("healthz through the app stack must return 200", 200, response.code)
            }
        } catch (failure: Throwable) {
            report("probe url=$url error=${failure.javaClass.simpleName}: ${failure.message}")
            throw failure
        }
    }

    @Test
    fun provision() = runBlocking {
        assumeStage("provision")
        awaitForeground()
        val suffix = UUID.randomUUID().toString().take(8)
        val username = "da-$suffix"
        val email = "da-$suffix@device-acceptance.invalid"
        val password = UUID.randomUUID().toString() // generated in-process, never printed

        val registered = app.container.authRepository.register(username, email, password)
        assertTrue(
            "register failed: ${(registered as? ApiResult.Failure)?.code} ${(registered as? ApiResult.Failure)?.message}",
            registered is ApiResult.Success,
        )
        app.container.onUserSignedIn()
        val userId = app.container.sessionStore.loadQuietly()?.user?.userId
        assertTrue("no signed-in user after register", !userId.isNullOrBlank())
        report("provision user_id=$userId")

        val repo = app.container.v25Repository

        // /study/today is scoped to the current project's configured plan; a standalone
        // deck would legitimately produce an empty plan. Provision the real business shape:
        // project -> deck -> cards -> plan selection.
        // Two-step creation (contract V25-D-29): the named EMPTY project first, then the PDF
        // material through the materials endpoint (asynchronously parsed server-side).
        val project = repo.createProject(name = "device-acceptance-$suffix")
        assertTrue("createProject failed: ${(project as? V25Result.Failure)?.code}", project is V25Result.Success)
        val projectId = (project as V25Result.Success).value.projectId
        report("provision project_id=$projectId")

        val material = repo.addProjectMaterialPdf(
            projectId = projectId,
            fileName = "device-acceptance.pdf",
            content = MINIMAL_PDF.inputStream(),
        )
        assertTrue("addProjectMaterialPdf failed: ${(material as? V25Result.Failure)?.code}", material is V25Result.Success)
        report("provision material_id=${(material as V25Result.Success).value.materialId}")

        val deck = repo.createDeck("device-acceptance-$suffix", projectId)
        assertTrue("createDeck failed: ${(deck as? V25Result.Failure)?.code}", deck is V25Result.Success)
        val deckId = (deck as V25Result.Success).value.deckId
        report("provision deck_id=$deckId")

        val drafts = (1..10).map { V25CardDraft(front = "验收卡 $it", back = "答案 $it") }
        val imported = repo.importCards(deckId, drafts)
        assertTrue("importCards failed: ${(imported as? V25Result.Failure)?.code}", imported is V25Result.Success)
        val created = (imported as V25Result.Success).value.count { it.status == V25ImportStatus.CREATED }
        assertEquals("all 10 cards created", 10, created)

        val planSaved = repo.updateStudyPlan(
            V25StudyPlanUpdate(
                currentProjectId = projectId,
                selectedDeckIds = listOf(deckId),
                dailyNewGoal = 10,
                dailyReviewGoal = 40,
            ),
        )
        assertTrue("updateStudyPlan failed: ${(planSaved as? V25Result.Failure)?.code}", planSaved is V25Result.Success)

        val plan = repo.todayPlan()
        assertTrue("todayPlan failed: ${(plan as? V25Result.Failure)?.code}", plan is V25Result.Success)
        val cards = (plan as V25Result.Success).value.cards
        assertTrue("today plan hydrated only ${cards.size} cards", cards.size >= 10)

        val outbox = app.container.cache.allOutbox(userId!!)
        assertEquals("provision must leave the outbox empty", 0, outbox.size)
        report("provision plan_cards=${cards.size} outbox=0")
    }

    @Test
    fun rateTenOffline() = runBlocking {
        assumeStage("rate_offline")
        val userId = signedInUserId()
        val repo = app.container.v25Repository

        val plan = repo.todayPlan()
        assertTrue("cached today plan must be readable offline", plan is V25Result.Success)
        val cards = (plan as V25Result.Success).value.cards
        assertTrue("need at least 10 cards in the cached plan, got ${cards.size}", cards.size >= 10)

        repeat(10) { index ->
            val cardId = cards[index].card.cardId
            val result = repo.rateCard(cardId, V25Rating.GOOD)
            assertTrue(
                "offline swipe $index must succeed locally: ${(result as? V25Result.Failure)?.code}",
                result is V25Result.Success,
            )
        }

        val rows = app.container.cache.allOutbox(userId)
        assertEquals("exactly 10 outbox rows after 10 offline swipes", 10, rows.size)
        assertTrue("every row must be PENDING: ${rows.map { it.status }.toSet()}", rows.all { it.status == "PENDING" })
        assertEquals("client_event_id must be unique", 10, rows.map { it.clientEventId }.toSet().size)
        assertEquals("idempotency_key must be unique", 10, rows.map { it.idempotencyKey }.toSet().size)
        rows.sortedBy { it.createdAt }.forEach { row ->
            report("rate_offline EVT ${row.clientEventId} ${row.idempotencyKey} ${row.status}")
        }
        report("rate_offline pending=10 completed=0")
    }

    @Test
    fun verifyPendingAfterRestart() = runBlocking {
        assumeStage("verify_pending")
        val userId = signedInUserId()

        // Cached reads still work in a fresh process with the reverse removed.
        val decks = app.container.v25Repository.listDecks()
        assertTrue("cached decks must be readable offline", decks is V25Result.Success)
        val deckList = (decks as V25Result.Success).value
        assertEquals("the acceptance deck must still be cached", 1, deckList.size)
        val cardCount = app.container.cache.readDeckCards(userId, deckList.first().deckId).size
        assertEquals("all 10 cards must still be cached", 10, cardCount)

        val rows = app.container.cache.allOutbox(userId)
        assertEquals("10 rows must survive the force-stop", 10, rows.size)
        assertTrue(
            "rows must still be PENDING after restart: ${rows.map { it.status }.toSet()}",
            rows.all { it.status == "PENDING" },
        )
        assertEquals(10, rows.map { it.clientEventId }.toSet().size)
        assertEquals(10, rows.map { it.idempotencyKey }.toSet().size)
        rows.sortedBy { it.createdAt }.forEach { row ->
            report("verify_pending EVT ${row.clientEventId} ${row.idempotencyKey} ${row.status}")
        }
        report("verify_pending pending=10 completed=0")
    }

    @Test
    fun drainAndVerify() = runBlocking {
        assumeStage("drain")
        awaitForeground()
        val userId = signedInUserId()
        val before = app.container.cache.allOutbox(userId)
        assertEquals(10, before.size)
        val originalIdentities = before.map { it.clientEventId to it.idempotencyKey }.toSet()

        val deadline = System.currentTimeMillis() + 120_000
        while (true) {
            app.container.reviewSync.syncOnce()
            val rows = app.container.cache.allOutbox(userId)
            if (rows.count { it.status == "COMPLETED" } == 10) break
            assertTrue(
                "drain did not finish: ${rows.map { it.status }.groupingBy { it }.eachCount()}",
                System.currentTimeMillis() < deadline,
            )
            assertTrue(
                "a row FAILED permanently: ${rows.filter { it.status == "FAILED" }.map { it.lastErrorCode }}",
                rows.none { it.status == "FAILED" },
            )
            Thread.sleep(1_000)
        }

        val rows = app.container.cache.allOutbox(userId)
        assertTrue("no FAILED rows allowed", rows.all { it.status == "COMPLETED" })
        assertEquals(
            "identity pairs must be the originals from the offline phase",
            originalIdentities,
            rows.map { it.clientEventId to it.idempotencyKey }.toSet(),
        )
        rows.sortedBy { it.createdAt }.forEach { row ->
            report("drain EVT ${row.clientEventId} ${row.idempotencyKey} ${row.status}")
        }
        report("drain pending=0 completed=10")
    }

    @Test
    fun verifyStableAfterSecondRestart() = runBlocking {
        assumeStage("verify_stable")
        val userId = signedInUserId()
        Thread.sleep(5_000) // let any startup sync/work-manager pass settle; server checked host-side
        val rows = app.container.cache.allOutbox(userId)
        assertEquals(10, rows.size)
        assertTrue("rows must stay COMPLETED after the second restart", rows.all { it.status == "COMPLETED" })
        assertEquals(10, rows.map { it.clientEventId }.toSet().size)
        report("verify_stable pending=0 completed=10")
    }

    private fun signedInUserId(): String {
        val session = app.container.sessionStore.loadQuietly()
        assertTrue("a signed-in session must persist across process restarts", session != null)
        return session!!.user.userId
    }

    private companion object {
        /**
         * Structure-only PDF: project creation stores the file locally and never parses or
         * generates anything on the create path, so a minimal frame is enough for a real
         * project row (and keeps the fixture out of AI/PDF-processing behavior entirely).
         */
        val MINIMAL_PDF = (
            "%PDF-1.4\n" +
                "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
                "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n" +
                "xref\n0 4\ntrailer<</Size 4/Root 1 0 R>>\n%%EOF"
            ).toByteArray()
    }
}
