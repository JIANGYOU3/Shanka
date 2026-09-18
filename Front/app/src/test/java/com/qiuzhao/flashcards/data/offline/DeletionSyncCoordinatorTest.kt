package com.qiuzhao.flashcards.data.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qiuzhao.flashcards.data.local.DeletionKind
import com.qiuzhao.flashcards.data.local.ShankaV25Database
import com.qiuzhao.flashcards.data.local.V25CacheStore
import com.qiuzhao.flashcards.data.session.InMemorySessionStore
import com.qiuzhao.flashcards.data.session.SessionUser
import com.qiuzhao.flashcards.domain.v25.V25ApiKeyStatus
import com.qiuzhao.flashcards.domain.v25.V25AuthUser
import com.qiuzhao.flashcards.domain.v25.V25AvatarKey
import com.qiuzhao.flashcards.domain.v25.V25BrowseFilter
import com.qiuzhao.flashcards.domain.v25.V25Card
import com.qiuzhao.flashcards.domain.v25.V25CardDeletionBatch
import com.qiuzhao.flashcards.domain.v25.V25CardDraft
import com.qiuzhao.flashcards.domain.v25.V25CardRewritePreview
import com.qiuzhao.flashcards.domain.v25.V25Chapter
import com.qiuzhao.flashcards.domain.v25.V25ChapterEdit
import com.qiuzhao.flashcards.domain.v25.V25Deck
import com.qiuzhao.flashcards.domain.v25.V25DeletionPreflight
import com.qiuzhao.flashcards.domain.v25.V25ErrorCodes
import com.qiuzhao.flashcards.domain.v25.V25GenerationConfig
import com.qiuzhao.flashcards.domain.v25.V25GenerationTask
import com.qiuzhao.flashcards.domain.v25.V25ImportResult
import com.qiuzhao.flashcards.domain.v25.V25LearningProject
import com.qiuzhao.flashcards.domain.v25.V25Material
import com.qiuzhao.flashcards.domain.v25.V25MaterialStatus
import com.qiuzhao.flashcards.domain.v25.V25MaterialType
import com.qiuzhao.flashcards.domain.v25.V25PreferencesPatch
import com.qiuzhao.flashcards.domain.v25.V25ProjectStatus
import com.qiuzhao.flashcards.domain.v25.V25ProjectStudySettings
import com.qiuzhao.flashcards.domain.v25.V25ProgressSummary
import com.qiuzhao.flashcards.domain.v25.V25Rating
import com.qiuzhao.flashcards.domain.v25.V25RatingResult
import com.qiuzhao.flashcards.domain.v25.V25Repository
import com.qiuzhao.flashcards.domain.v25.V25Result
import com.qiuzhao.flashcards.domain.v25.V25ReviewCard
import com.qiuzhao.flashcards.domain.v25.V25SampleCard
import com.qiuzhao.flashcards.domain.v25.V25StudyPlan
import com.qiuzhao.flashcards.domain.v25.V25StudyPlanUpdate
import com.qiuzhao.flashcards.domain.v25.V25StudySettingsPatch
import com.qiuzhao.flashcards.domain.v25.V25TaskConfigPatch
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import com.qiuzhao.flashcards.domain.v25.V25TodayPlan
import com.qiuzhao.flashcards.domain.v25.V25UserPreferences
import java.io.File
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Optimistic-deletion suite (Robolectric + file-backed `shanka-v25.db`): the tombstone enqueue
 * transaction, the resurrection guard on cache rewrites, and the coordinator's replay contract
 * — fixed keys, NOT_FOUND as success, transient backoff, AUTH pause, permanent rejection with
 * exactly one authoritative refresh.
 */
private val NOW: Instant = Instant.parse("2026-08-31T00:00:00Z")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeletionSyncCoordinatorTest {

    class TestClock(var nowMs: Long = Instant.parse("2026-08-31T00:00:00Z").toEpochMilli()) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    }

    private lateinit var dbFile: File
    private lateinit var db: ShankaV25Database
    private lateinit var cache: V25CacheStore
    private lateinit var store: InMemorySessionStore
    private lateinit var scope: CoroutineScope
    private var authoritativeRefreshes = 0
    private lateinit var remote: ScriptedRemote
    private lateinit var coordinator: DeletionSyncCoordinator

    private val user = SessionUser(userId = "u-1", username = "alice", createdAt = "2026-08-01T00:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Robolectric-on-Windows can hand us a dataDir whose cache/ is not materialised yet.
        context.cacheDir.mkdirs()
        dbFile = File(context.cacheDir, "deletion-sync-${System.nanoTime()}.db")
        db = ShankaV25Database.buildOnFile(context, dbFile.absolutePath)
        cache = V25CacheStore(db)
        store = InMemorySessionStore()
        store.save("token-u1", user)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        authoritativeRefreshes = 0
        remote = ScriptedRemote()
        coordinator = DeletionSyncCoordinator(
            remote = remote,
            cache = cache,
            sessionUser = { store.load()?.user?.userId },
            clock = TestClock(),
            lanes = RequestLanes(scope),
            scope = scope,
            onAuthoritativeRefreshNeeded = { authoritativeRefreshes++ },
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun project(id: String, name: String = "项目$id") = V25LearningProject(
        projectId = id,
        name = name,
        materials = listOf(
            V25Material(
                materialId = "$id-material",
                projectId = id,
                type = V25MaterialType.PDF,
                name = "教材.pdf",
                status = V25MaterialStatus.PARSED,
                createdAt = NOW,
            ),
        ),
        status = V25ProjectStatus.READY,
        chapterCount = 1,
        deckCount = 0,
        taskCount = 0,
        createdAt = NOW,
        updatedAt = NOW,
        version = 1,
    )

    // --- enqueue transaction ----------------------------------------------------------------------

    @Test
    fun `project enqueue removes local rows`() = runBlocking {
        cache.replaceProjects("u-1", listOf(project("p-1")), 1_000L)
        assertNotNull(cache.readProject("u-1", "p-1"))

        cache.enqueueProjectDeletion("u-1", "p-1", retainDecks = true, idempotencyKey = "key-1", now = 2_000L)

        assertNull("the project vanished from the cache in the same frame", cache.readProject("u-1", "p-1"))
        val pending = cache.allPendingDeletions("u-1")
        assertEquals(1, pending.size)
        assertEquals(V25CacheStore.projectDeletionOperationId("p-1"), pending.single().operationId)
        assertEquals(DeletionKind.PROJECT, pending.single().kind)
        assertEquals("key-1", pending.single().idempotencyKey)
    }

    @Test
    fun `material enqueue removes its rows`() = runBlocking {
        cache.replaceProjects("u-1", listOf(project("p-1")), 1_000L)

        cache.enqueueMaterialDeletion("u-1", "p-1", "p-1-material", retainCards = true, idempotencyKey = "key-2", now = 2_000L)

        val kept = cache.readProject("u-1", "p-1")
        assertNotNull(kept)
        assertTrue("the material vanished", kept!!.materials.isEmpty())
        assertEquals(1, cache.allPendingDeletions("u-1").size)
    }

    // --- resurrection guard -----------------------------------------------------------------------

    @Test
    fun `pending tombstone survives rewrite`() = runBlocking {
        cache.enqueueProjectDeletion("u-1", "p-1", retainDecks = true, idempotencyKey = "key-1", now = 2_000L)

        // A concurrent refresh returns a payload that still contains the deleted project.
        cache.replaceProjects("u-1", listOf(project("p-1"), project("p-2")), 3_000L)

        val visible = cache.readProjects("u-1").map { it.projectId }
        assertEquals("the tombstoned scope stays hidden", listOf("p-2"), visible)

        // After the server confirms the delete, the next payload (without p-1) is written verbatim.
        cache.completeDeletion("u-1", V25CacheStore.projectDeletionOperationId("p-1"))
        cache.replaceProjects("u-1", listOf(project("p-2")), 4_000L)
        assertEquals(listOf("p-2"), cache.readProjects("u-1").map { it.projectId })
    }

    @Test
    fun `material tombstone filters rewrite`() = runBlocking {
        cache.replaceProjects("u-1", listOf(project("p-1")), 1_000L)
        cache.enqueueMaterialDeletion("u-1", "p-1", "p-1-material", retainCards = true, idempotencyKey = "key-2", now = 2_000L)

        // A payload that still carries the deleted material must not resurrect it.
        cache.replaceProject("u-1", project("p-1"), 3_000L)

        assertTrue(cache.readProject("u-1", "p-1")!!.materials.isEmpty())
        assertTrue(cache.readProjects("u-1").single().materials.isEmpty())
    }

    // --- coordinator replay contract ----------------------------------------------------------------

    @Test
    fun `drain replays in order`() = runBlocking {
        cache.enqueueProjectDeletion("u-1", "p-1", retainDecks = true, idempotencyKey = "key-p", now = 1_000L)
        cache.enqueueMaterialDeletion("u-1", "p-2", "m-2", retainCards = false, idempotencyKey = "key-m", now = 2_000L)

        val outcome = coordinator.syncOnce()

        assertEquals(DeletionSyncOutcome.DRAINED, outcome)
        assertEquals(
            "deleteProject replayed with the fixed key and retain decision",
            "key-p" to true,
            remote.projectDeletions.single(),
        )
        assertEquals("key-m" to false, remote.materialDeletions.single().let { it.second to it.third })
        assertTrue("the tombstones are gone", cache.allPendingDeletions("u-1").isEmpty())
        assertEquals("the merged refresh revalidated projects once", 1, remote.listProjectsCalls)
    }

    @Test
    fun `not found replay is success`() = runBlocking {
        cache.enqueueProjectDeletion("u-1", "p-1", retainDecks = true, idempotencyKey = "key-p", now = 1_000L)
        remote.nextProjectResults += V25Result.Failure(V25ErrorCodes.PROJECT_NOT_FOUND, "project.not_found", "项目不存在")

        val outcome = coordinator.syncOnce()

        assertEquals(DeletionSyncOutcome.DRAINED, outcome)
        assertTrue(cache.allPendingDeletions("u-1").isEmpty())
        assertEquals(1, remote.listProjectsCalls)
    }

    @Test
    fun `transient failure schedules retry`() = runBlocking {
        cache.enqueueProjectDeletion("u-1", "p-1", retainDecks = true, idempotencyKey = "key-p", now = 1_000L)
        remote.nextProjectResults += V25Result.Failure(V25ErrorCodes.NETWORK_UNAVAILABLE)

        val outcome = coordinator.syncOnce()

        assertEquals(DeletionSyncOutcome.RETRY_SCHEDULED, outcome)
        val pending = cache.allPendingDeletions("u-1").single()
        assertEquals(1, pending.attemptCount)
        assertEquals(0, remote.listProjectsCalls)
        // Backoff: 1s after the first attempt.
        assertTrue(pending.nextAttemptAt > TestClock().nowMs)
    }

    @Test
    fun `auth failure pauses the pass`() = runBlocking {
        cache.enqueueProjectDeletion("u-1", "p-1", retainDecks = true, idempotencyKey = "key-p", now = 1_000L)
        remote.nextProjectResults += V25Result.Failure(V25ErrorCodes.AUTH_REQUIRED)

        assertEquals(DeletionSyncOutcome.PAUSED_AUTH, coordinator.syncOnce())
        val pending = cache.allPendingDeletions("u-1").single()
        assertEquals("no attempt penalty", 0, pending.attemptCount)

        // A fresh session resumes the paused pass.
        coordinator.resume()
        assertEquals(DeletionSyncOutcome.DRAINED, coordinator.syncOnce())
        assertTrue(cache.allPendingDeletions("u-1").isEmpty())
    }

    @Test
    fun `permanent rejection refreshes`() = runBlocking {
        cache.replaceProjects("u-1", listOf(project("p-1")), 1_000L)
        cache.enqueueProjectDeletion("u-1", "p-1", retainDecks = true, idempotencyKey = "key-p", now = 1_000L)
        remote.nextProjectResults += V25Result.Failure("PROJECT_HAS_ACTIVE_TASK", "project.delete.blocked", "存在任务")

        assertEquals(DeletionSyncOutcome.DRAINED, coordinator.syncOnce())

        assertTrue("the tombstone was dropped", cache.allPendingDeletions("u-1").isEmpty())
        assertEquals("exactly one authoritative refresh", 1, authoritativeRefreshes)
        assertEquals(DeletionKind.PROJECT, coordinator.lastPermanentFailure.value?.kind)
        coordinator.clearPermanentFailure()
        assertNull(coordinator.lastPermanentFailure.value)

        // The authoritative refresh restored the still-existing project.
        remote.nextListProjects += V25Result.Success(listOf(project("p-1")))
        val payload = when (val restored = remote.nextListProjects.removeFirst()) {
            is V25Result.Success -> restored.value
            is V25Result.Failure -> throw IllegalStateException(restored.code)
        }
        cache.replaceProjects("u-1", payload, 5_000L)
        assertNotNull(cache.readProject("u-1", "p-1"))
    }

    @Test
    fun `http 404 counts as gone`() = runBlocking {
        cache.enqueueProjectDeletion("u-1", "p-1", retainDecks = true, idempotencyKey = "key-p", now = 1_000L)
        remote.nextProjectResults += V25Result.Failure("HTTP_404")

        assertEquals(DeletionSyncOutcome.DRAINED, coordinator.syncOnce())
        assertTrue(cache.allPendingDeletions("u-1").isEmpty())
    }

    /** Scripted remote: deletion results come from queues; merged-refresh reads return Success empty. */
    private class ScriptedRemote : V25Repository {
        val projectDeletions = mutableListOf<Pair<String, Boolean>>() // (key, retainDecks)
        val materialDeletions = mutableListOf<Triple<String, String, Boolean>>() // (projectId, key, retainCards)
        val nextProjectResults = ArrayDeque<V25Result<Unit>>()
        val nextListProjects = ArrayDeque<V25Result<List<V25LearningProject>>>()
        var listProjectsCalls = 0

        override suspend fun deleteProject(
            projectId: String,
            retainDecks: Boolean,
            idempotencyKey: String?,
        ): V25Result<Unit> {
            projectDeletions += (idempotencyKey ?: "") to retainDecks
            return nextProjectResults.removeFirstOrNull() ?: V25Result.Success(Unit)
        }

        override suspend fun deleteProjectMaterial(
            projectId: String,
            materialId: String,
            retainCards: Boolean,
            idempotencyKey: String?,
        ): V25Result<Unit> {
            materialDeletions += Triple(projectId, idempotencyKey ?: "", retainCards)
            return V25Result.Success(Unit)
        }

        override suspend fun listProjects(forceRefresh: Boolean): V25Result<List<V25LearningProject>> {
            listProjectsCalls++
            return nextListProjects.removeFirstOrNull() ?: V25Result.Success(emptyList())
        }

        override suspend fun projectProgress(projectId: String): V25Result<com.qiuzhao.flashcards.domain.v25.V25ProgressSummary> =
            throw NotImplementedError()

        // Merged-refresh companions fail quietly on purpose (no writer runs).
        override suspend fun listDecks(projectId: String?): V25Result<List<V25Deck>> =
            V25Result.Failure(V25ErrorCodes.NETWORK_UNAVAILABLE)

        override suspend fun todayPlan(): V25Result<V25TodayPlan> =
            V25Result.Failure(V25ErrorCodes.NETWORK_UNAVAILABLE)

        override suspend fun statsDashboard(): V25Result<com.qiuzhao.flashcards.domain.v25.V25StatsDashboard> =
            V25Result.Failure(V25ErrorCodes.NETWORK_UNAVAILABLE)

        // Untouched boundary methods: any call is a test bug.
        override suspend fun getAuthUser(): V25Result<V25AuthUser> = throw NotImplementedError()
        override suspend fun updateAuthUser(username: String?, avatarKey: V25AvatarKey?): V25Result<V25AuthUser> = throw NotImplementedError()
        override suspend fun logout(): V25Result<Unit> = throw NotImplementedError()
        override suspend fun getPreferences(): V25Result<V25UserPreferences> = throw NotImplementedError()
        override suspend fun updatePreferences(patch: V25PreferencesPatch): V25Result<V25UserPreferences> = throw NotImplementedError()
        override suspend fun setCurrentProject(projectId: String?): V25Result<V25UserPreferences> = throw NotImplementedError()
        override suspend fun createProject(name: String, idempotencyKey: String?): V25Result<V25LearningProject> = throw NotImplementedError()
        override suspend fun addProjectMaterialPdf(projectId: String, fileName: String, content: InputStream, idempotencyKey: String?): V25Result<V25Material> = throw NotImplementedError()
        override suspend fun addProjectMaterialHtml(projectId: String, fileName: String, content: InputStream, idempotencyKey: String?): V25Result<V25Material> = throw NotImplementedError()
        override suspend fun addProjectMaterialZip(projectId: String, fileName: String, content: InputStream, idempotencyKey: String?): V25Result<V25Material> = throw NotImplementedError()
        override suspend fun addProjectMaterialText(projectId: String, name: String, content: String, idempotencyKey: String?): V25Result<V25Material> = throw NotImplementedError()
        override suspend fun listProjectMaterials(projectId: String): V25Result<List<V25Material>> = throw NotImplementedError()
        override suspend fun reparseProjectMaterial(projectId: String, materialId: String): V25Result<V25LearningProject> = throw NotImplementedError()


    override suspend fun fallbackWholeBookChapters(projectId: String, materialId: String): V25Result<V25LearningProject> = throw NotImplementedError()

    override suspend fun replaceProjectMaterialPdf(projectId: String, materialId: String, fileName: String, content: InputStream, idempotencyKey: String?): V25Result<V25Material> = throw NotImplementedError()
        override suspend fun getProject(projectId: String, forceRefresh: Boolean): V25Result<V25LearningProject> = throw NotImplementedError()
        override suspend fun renameProject(projectId: String, name: String): V25Result<V25LearningProject> = throw NotImplementedError()
        override suspend fun getProjectDeletionPreflight(projectId: String, retainDecks: Boolean, allowCancel: Boolean): V25Result<V25DeletionPreflight> = throw NotImplementedError()
        override suspend fun updateChapter(projectId: String, chapterId: String, edit: V25ChapterEdit): V25Result<V25Chapter> = throw NotImplementedError()
        override suspend fun deleteChapter(projectId: String, chapterId: String, deleteCards: Boolean): V25Result<Unit> = throw NotImplementedError()
        override suspend fun confirmChapters(projectId: String): V25Result<V25LearningProject> = throw NotImplementedError()
        override suspend fun getStudySettings(projectId: String): V25Result<V25ProjectStudySettings> = throw NotImplementedError()
        override suspend fun updateStudySettings(projectId: String, patch: V25StudySettingsPatch): V25Result<V25ProjectStudySettings> = throw NotImplementedError()
        override suspend fun createTask(projectId: String, deckId: String, chapterIds: List<String>, config: V25GenerationConfig): V25Result<V25GenerationTask> = throw NotImplementedError()
        override suspend fun listTasks(projectId: String?, status: V25TaskStatus?): V25Result<List<V25GenerationTask>> = throw NotImplementedError()
        override suspend fun getTask(taskId: String): V25Result<V25GenerationTask> = throw NotImplementedError()
        override suspend fun updateTaskConfig(taskId: String, patch: V25TaskConfigPatch): V25Result<V25GenerationTask> = throw NotImplementedError()
        override suspend fun generateSamples(taskId: String): V25Result<List<V25SampleCard>> = throw NotImplementedError()
        override suspend fun startTask(taskId: String): V25Result<V25GenerationTask> = throw NotImplementedError()
        override suspend fun abandonTask(taskId: String): V25Result<V25GenerationTask> = throw NotImplementedError()
        override suspend fun retryTask(taskId: String): V25Result<V25GenerationTask> = throw NotImplementedError()
        override suspend fun confirmTask(taskId: String): V25Result<V25GenerationTask> = throw NotImplementedError()
        override suspend fun listTaskCards(taskId: String): V25Result<List<V25Card>> = throw NotImplementedError()
        override suspend fun deleteTask(taskId: String, deleteGeneratedCards: Boolean): V25Result<Unit> = throw NotImplementedError()
        override suspend fun createDeck(name: String, projectId: String?, idempotencyKey: String?): V25Result<V25Deck> = throw NotImplementedError()
        override suspend fun getDeck(deckId: String): V25Result<V25Deck> = throw NotImplementedError()
        override suspend fun attachDeckToProject(projectId: String, deckId: String, idempotencyKey: String?): V25Result<V25Deck> = throw NotImplementedError()
        override suspend fun renameDeck(deckId: String, name: String): V25Result<V25Deck> = throw NotImplementedError()
        override suspend fun deleteDeck(deckId: String, idempotencyKey: String?): V25Result<Unit> = throw NotImplementedError()
        override suspend fun getDeckDeletionPreflight(deckId: String, allowCancel: Boolean): V25Result<V25DeletionPreflight> = throw NotImplementedError()
        override suspend fun listCards(deckId: String, filter: V25BrowseFilter): V25Result<List<V25Card>> = throw NotImplementedError()
        override suspend fun importCards(deckId: String, drafts: List<V25CardDraft>, idempotencyKey: String?): V25Result<List<V25ImportResult>> = throw NotImplementedError()
        override suspend fun updateCard(cardId: String, front: String, back: String): V25Result<V25Card> = throw NotImplementedError()
        override suspend fun deleteCard(cardId: String): V25Result<V25CardDeletionBatch> = throw NotImplementedError()
        override suspend fun pendingDeletionBatches(): V25Result<List<V25CardDeletionBatch>> = throw NotImplementedError()
        override suspend fun undoDeletionBatch(deleteBatchId: String): V25Result<Unit> = throw NotImplementedError()
        override suspend fun createRewritePreview(cardId: String, customRequirements: String?): V25Result<V25CardRewritePreview> = throw NotImplementedError()
        override suspend fun applyRewritePreview(cardId: String, rewriteId: String): V25Result<V25Card> = throw NotImplementedError()
        override suspend fun cancelRewritePreview(cardId: String, rewriteId: String): V25Result<Unit> = throw NotImplementedError()
        override suspend fun deckReviewQueue(deckId: String): V25Result<List<V25ReviewCard>> = throw NotImplementedError()
        override suspend fun rateCard(cardId: String, rating: V25Rating, clientEventId: String?, idempotencyKey: String?, origin: com.qiuzhao.flashcards.domain.v25.V25StudyOrigin?): V25Result<V25RatingResult> = throw NotImplementedError()
        override suspend fun beginStudySession(origin: com.qiuzhao.flashcards.domain.v25.V25StudyOrigin, deckId: String?, reset: Boolean): V25Result<com.qiuzhao.flashcards.domain.v25.V25StudySessionBegin> = throw NotImplementedError()
        override suspend fun reportStudySession(sessionId: String, studySeconds: Long, ended: Boolean): V25Result<com.qiuzhao.flashcards.domain.v25.V25StudySession> = throw NotImplementedError()
        override suspend fun studySessions(studyDate: String?): V25Result<List<com.qiuzhao.flashcards.domain.v25.V25StudySession>> = throw NotImplementedError()
        override suspend fun studySessionSummary(): V25Result<com.qiuzhao.flashcards.domain.v25.V25StudyDurationSummary> = throw NotImplementedError()
        override suspend fun getStudyPlan(): V25Result<V25StudyPlan> = throw NotImplementedError()
        override suspend fun updateStudyPlan(plan: V25StudyPlanUpdate, idempotencyKey: String?): V25Result<V25StudyPlan> = throw NotImplementedError()
        override suspend fun studyPlanBacklog(offset: Int, limit: Int): V25Result<List<com.qiuzhao.flashcards.domain.v25.V25PlanCard>> = throw NotImplementedError()
        override suspend fun apiKeyStatus(): V25Result<V25ApiKeyStatus> = throw NotImplementedError()
        override suspend fun saveApiKey(apiKey: String): V25Result<V25ApiKeyStatus> = throw NotImplementedError()
    }
}
