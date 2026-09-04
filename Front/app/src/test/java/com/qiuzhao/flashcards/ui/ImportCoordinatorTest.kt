package com.qiuzhao.flashcards.ui

import com.qiuzhao.flashcards.domain.v25.V25ApiKeyStatus
import com.qiuzhao.flashcards.domain.v25.V25AuthUser
import com.qiuzhao.flashcards.domain.v25.V25BrowseFilter
import com.qiuzhao.flashcards.domain.v25.V25Card
import com.qiuzhao.flashcards.domain.v25.V25CardDeletionBatch
import com.qiuzhao.flashcards.domain.v25.V25CardDraft
import com.qiuzhao.flashcards.domain.v25.V25CardRewritePreview
import com.qiuzhao.flashcards.domain.v25.V25Chapter
import com.qiuzhao.flashcards.domain.v25.V25ChapterEdit
import com.qiuzhao.flashcards.domain.v25.V25Deck
import com.qiuzhao.flashcards.domain.v25.V25GenerationTask
import com.qiuzhao.flashcards.domain.v25.V25ImportResult
import com.qiuzhao.flashcards.domain.v25.V25ImportStatus
import com.qiuzhao.flashcards.domain.v25.V25LearningProject
import com.qiuzhao.flashcards.domain.v25.V25PreferencesPatch
import com.qiuzhao.flashcards.domain.v25.V25ProjectStudySettings
import com.qiuzhao.flashcards.domain.v25.V25Rating
import com.qiuzhao.flashcards.domain.v25.V25RatingResult
import com.qiuzhao.flashcards.domain.v25.V25Repository
import com.qiuzhao.flashcards.domain.v25.V25Result
import com.qiuzhao.flashcards.domain.v25.V25ReviewCard
import com.qiuzhao.flashcards.domain.v25.V25SampleCard
import com.qiuzhao.flashcards.domain.v25.V25StatsDashboard
import com.qiuzhao.flashcards.domain.v25.V25StudySettingsPatch
import com.qiuzhao.flashcards.domain.v25.V25TaskConfigPatch
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import com.qiuzhao.flashcards.domain.v25.V25TodayPlan
import com.qiuzhao.flashcards.domain.v25.V25UserPreferences
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the import coordinator's replay semantics on the JVM: one user operation owns fixed
 * idempotency keys, a remembered deck id, and the drafts — so any retry after a lost response
 * replays only the failed step and can never create a second deck or duplicate the batch.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ImportCoordinatorTest {

    private val drafts = listOf(V25CardDraft("正面一", "背面一"), V25CardDraft("正面二", "背面二"))

    private fun deck(deckId: String) = V25Deck(
        deckId = deckId,
        name = "导入卡组",
        projectId = null,
        cardCount = 0,
        dueCount = 0,
        masteredCards = 0,
        reviewCount = 0,
        masteryRatio = null,
    )

    private fun importResults(count: Int) = List(count) { index ->
        V25ImportResult(index = index, status = V25ImportStatus.CREATED, cardId = "c-$index")
    }

    private val networkFailure = V25Result.Failure("NETWORK_UNAVAILABLE", null, "网络错误")

    @Test
    fun `a new deck import creates the deck then imports with its own fixed keys`() = runTest {
        val repo = ScriptedRepository()
        val coordinator = ImportCoordinator(repo)

        val result = coordinator.submit(ImportTarget.NewDeck("线性代数"), drafts)

        assertTrue(result is V25Result.Success)
        assertEquals("deck-new-0", (result as V25Result.Success).value)
        assertEquals(1, repo.createDeckCalls.size)
        assertEquals(1, repo.importCardsCalls.size)
        val (createName, createKey) = repo.createDeckCalls.single()
        assertEquals("线性代数", createName)
        val (importDeckId, importCount, importKey) = repo.importCardsCalls.single()
        assertEquals("deck-new-0", importDeckId)
        assertEquals(2, importCount)
        assertTrue("create step must carry an explicit key", createKey != null && createKey.isNotBlank())
        assertTrue("import step must carry an explicit key", importKey != null && importKey.isNotBlank())
        assertTrue("each step carries its own key", createKey != importKey)
        assertNull("a committed import clears the attempt", coordinator.attempt.value)
    }

    @Test
    fun `a failed import keeps the deck and a retry never creates a second deck`() = runTest {
        val repo = ScriptedRepository().apply { importResultsQueue += networkFailure }
        val coordinator = ImportCoordinator(repo)

        val failed = coordinator.submit(ImportTarget.NewDeck("线性代数"), drafts)
        assertTrue(failed is V25Result.Failure)
        val attempt = coordinator.attempt.value
        assertEquals("deck-new-0", attempt!!.createdDeckId)

        repo.importResultsQueue += V25Result.Success(importResults(2))
        val retried = coordinator.submit(ImportTarget.NewDeck("线性代数"), drafts)

        assertTrue(retried is V25Result.Success)
        assertEquals("the deck must be created exactly once", 1, repo.createDeckCalls.size)
        assertEquals("the import is replayed exactly twice", 2, repo.importCardsCalls.size)
        val firstKey = repo.importCardsCalls[0].third
        val retryKey = repo.importCardsCalls[1].third
        assertEquals("the retry reuses the same import key", firstKey, retryKey)
    }

    @Test
    fun `a failed deck creation replays the same create key instead of creating another deck`() = runTest {
        val repo = ScriptedRepository().apply { createDeckResultsQueue += networkFailure }
        val coordinator = ImportCoordinator(repo)

        val failed = coordinator.submit(ImportTarget.NewDeck("线性代数"), drafts)
        assertTrue(failed is V25Result.Failure)
        val attemptAfterFailure = coordinator.attempt.value!!
        assertNull(attemptAfterFailure.createdDeckId)

        repo.createDeckResultsQueue += V25Result.Success(deck("deck-new-0"))
        val retried = coordinator.submit(ImportTarget.NewDeck("线性代数"), drafts)

        assertTrue(retried is V25Result.Success)
        assertEquals(2, repo.createDeckCalls.size)
        assertEquals(
            "both create attempts carry the identical key, so the server dedupes",
            repo.createDeckCalls[0].second,
            repo.createDeckCalls[1].second,
        )
        assertEquals("deck-new-0", (retried as V25Result.Success).value)
    }

    @Test
    fun `an existing deck import skips deck creation entirely`() = runTest {
        val repo = ScriptedRepository()
        val coordinator = ImportCoordinator(repo)

        val result = coordinator.submit(ImportTarget.ExistingDeck("deck-existing"), drafts)

        assertTrue(result is V25Result.Success)
        assertEquals("deck-existing", (result as V25Result.Success).value)
        assertTrue(repo.createDeckCalls.isEmpty())
        val (deckId, count, _) = repo.importCardsCalls.single()
        assertEquals("deck-existing", deckId)
        assertEquals(2, count)
    }

    @Test
    fun `a different import starts fresh with new keys`() = runTest {
        val repo = ScriptedRepository().apply { importResultsQueue += networkFailure }
        val coordinator = ImportCoordinator(repo)

        coordinator.submit(ImportTarget.NewDeck("线性代数"), drafts)
        val firstKey = coordinator.attempt.value!!.importCardsKey

        val otherDrafts = listOf(V25CardDraft("另一张", "另一答案"))
        repo.importResultsQueue += V25Result.Success(importResults(1))
        coordinator.submit(ImportTarget.NewDeck("概率论"), otherDrafts)

        // Two different operations: two decks, two distinct key pairs.
        assertEquals(2, repo.createDeckCalls.size)
        assertTrue(repo.createDeckCalls[0].second != repo.createDeckCalls[1].second)
        assertNull(coordinator.attempt.value)
        assertTrue(firstKey.isNotBlank())
    }

    @Test
    fun `a concurrent submit is rejected while one is in flight`() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<V25Result<V25Deck>>()
        val repo = ScriptedRepository().apply { createDeckGate = gate }
        val coordinator = ImportCoordinator(repo)

        val first = launch { coordinator.submit(ImportTarget.NewDeck("线性代数"), drafts) }
        // The unconfined dispatcher runs the first submission eagerly up to the gated call.
        assertTrue(coordinator.submitting.value)

        val rejected = coordinator.submit(ImportTarget.NewDeck("线性代数"), drafts)
        assertTrue(rejected is V25Result.Failure)
        assertEquals(ImportCoordinator.IN_FLIGHT_CODE, (rejected as V25Result.Failure).code)

        gate.complete(V25Result.Success(deck("deck-new-0")))
        first.join()
        assertEquals(false, coordinator.submitting.value)
        assertEquals(1, repo.createDeckCalls.size)
        assertEquals(1, repo.importCardsCalls.size)
    }

    /** Scriptable V25Repository: only createDeck/importCards are exercised here. */
    private class ScriptedRepository : V25Repository {
        val createDeckCalls = mutableListOf<Pair<String, String?>>()
        val importCardsCalls = mutableListOf<Triple<String, Int, String?>>()
        val createDeckResultsQueue = ArrayDeque<V25Result<V25Deck>>()
        val importResultsQueue = ArrayDeque<V25Result<List<V25ImportResult>>>()
        var createDeckGate: CompletableDeferred<V25Result<V25Deck>>? = null
        private var deckCounter = 0

        override suspend fun createDeck(name: String, projectId: String?, idempotencyKey: String?): V25Result<V25Deck> {
            createDeckCalls += name to idempotencyKey
            createDeckGate?.let { gate -> return gate.await() }
            return createDeckResultsQueue.removeFirstOrNull()
                ?: V25Result.Success(deck("deck-new-${deckCounter++}"))
        }

        override suspend fun importCards(deckId: String, drafts: List<V25CardDraft>, idempotencyKey: String?): V25Result<List<V25ImportResult>> {
            importCardsCalls += Triple(deckId, drafts.size, idempotencyKey)
            return importResultsQueue.removeFirstOrNull() ?: V25Result.Success(emptyList())
        }

        private fun deck(deckId: String) = V25Deck(
            deckId = deckId,
            name = "脚本卡组",
            projectId = null,
            cardCount = 0,
            dueCount = 0,
            masteredCards = 0,
            reviewCount = 0,
            masteryRatio = null,
        )

        // Untouched boundary methods: any call is a test bug.
        override suspend fun getAuthUser(): V25Result<V25AuthUser> = throw NotImplementedError()
        override suspend fun updateAuthUser(username: String?, avatarKey: com.qiuzhao.flashcards.domain.v25.V25AvatarKey?): V25Result<V25AuthUser> = throw NotImplementedError()
        override suspend fun logout(): V25Result<Unit> = throw NotImplementedError()
        override suspend fun getPreferences(): V25Result<V25UserPreferences> = throw NotImplementedError()
        override suspend fun updatePreferences(patch: V25PreferencesPatch): V25Result<V25UserPreferences> = throw NotImplementedError()
        override suspend fun setCurrentProject(projectId: String?): V25Result<V25UserPreferences> = throw NotImplementedError()
        override suspend fun createProject(name: String, idempotencyKey: String?): V25Result<V25LearningProject> = throw NotImplementedError()
        override suspend fun addProjectMaterialPdf(projectId: String, fileName: String, content: InputStream, idempotencyKey: String?): V25Result<com.qiuzhao.flashcards.domain.v25.V25Material> = throw NotImplementedError()
        override suspend fun addProjectMaterialText(projectId: String, name: String, content: String, idempotencyKey: String?): V25Result<com.qiuzhao.flashcards.domain.v25.V25Material> = throw NotImplementedError()
        override suspend fun listProjectMaterials(projectId: String): V25Result<List<com.qiuzhao.flashcards.domain.v25.V25Material>> = throw NotImplementedError()
        override suspend fun deleteProjectMaterial(projectId: String, materialId: String, retainCards: Boolean, idempotencyKey: String?): V25Result<V25LearningProject> = throw NotImplementedError()
        override suspend fun replaceProjectMaterialPdf(projectId: String, materialId: String, fileName: String, content: InputStream, idempotencyKey: String?): V25Result<com.qiuzhao.flashcards.domain.v25.V25Material> = throw NotImplementedError()
        override suspend fun listProjects(forceRefresh: Boolean): V25Result<List<V25LearningProject>> = throw NotImplementedError()
        override suspend fun getProject(projectId: String, forceRefresh: Boolean): V25Result<V25LearningProject> = throw NotImplementedError()
        override suspend fun renameProject(projectId: String, name: String): V25Result<V25LearningProject> = throw NotImplementedError()
        override suspend fun deleteProject(
            projectId: String,
            retainDecks: Boolean,
            idempotencyKey: String?,
        ): V25Result<Unit> = throw NotImplementedError()

        // Not exercised by this suite; the interface declares them abstract.
        override suspend fun projectProgress(
            projectId: String,
        ): V25Result<com.qiuzhao.flashcards.domain.v25.V25ProgressSummary> = throw NotImplementedError()

        override suspend fun getProjectDeletionPreflight(
            projectId: String,
            retainDecks: Boolean,
            allowCancel: Boolean,
        ): V25Result<com.qiuzhao.flashcards.domain.v25.V25DeletionPreflight> = throw NotImplementedError()

        override suspend fun attachDeckToProject(
            projectId: String,
            deckId: String,
            idempotencyKey: String?,
        ): V25Result<com.qiuzhao.flashcards.domain.v25.V25Deck> = throw NotImplementedError()

        override suspend fun getDeckDeletionPreflight(
            deckId: String,
            allowCancel: Boolean,
        ): V25Result<com.qiuzhao.flashcards.domain.v25.V25DeletionPreflight> = throw NotImplementedError()

        override suspend fun getStudyPlan(): V25Result<com.qiuzhao.flashcards.domain.v25.V25StudyPlan> =
            throw NotImplementedError()

        override suspend fun updateStudyPlan(
            plan: com.qiuzhao.flashcards.domain.v25.V25StudyPlanUpdate,
            idempotencyKey: String?,
        ): V25Result<com.qiuzhao.flashcards.domain.v25.V25StudyPlan> = throw NotImplementedError()

        override suspend fun studyPlanBacklog(
            offset: Int,
            limit: Int,
        ): V25Result<List<com.qiuzhao.flashcards.domain.v25.V25PlanCard>> = throw NotImplementedError()
                override suspend fun updateChapter(projectId: String, chapterId: String, edit: V25ChapterEdit): V25Result<V25Chapter> = throw NotImplementedError()
        override suspend fun deleteChapter(projectId: String, chapterId: String, deleteCards: Boolean): V25Result<Unit> = throw NotImplementedError()
        override suspend fun confirmChapters(projectId: String): V25Result<V25LearningProject> = throw NotImplementedError()
        override suspend fun getStudySettings(projectId: String): V25Result<V25ProjectStudySettings> = throw NotImplementedError()
        override suspend fun updateStudySettings(projectId: String, patch: V25StudySettingsPatch): V25Result<V25ProjectStudySettings> = throw NotImplementedError()
        override suspend fun createTask(projectId: String, deckId: String, chapterIds: List<String>, config: com.qiuzhao.flashcards.domain.v25.V25GenerationConfig): V25Result<V25GenerationTask> = throw NotImplementedError()
        override suspend fun listTasks(projectId: String?, status: V25TaskStatus?): V25Result<List<V25GenerationTask>> = throw NotImplementedError()
        override suspend fun getTask(taskId: String): V25Result<V25GenerationTask> = throw NotImplementedError()
        override suspend fun updateTaskConfig(taskId: String, patch: V25TaskConfigPatch): V25Result<V25GenerationTask> = throw NotImplementedError()
        override suspend fun generateSamples(taskId: String): V25Result<List<V25SampleCard>> = throw NotImplementedError()
        override suspend fun startTask(taskId: String): V25Result<V25GenerationTask> = throw NotImplementedError()
        override suspend fun abandonTask(taskId: String): V25Result<V25GenerationTask> = throw NotImplementedError()
        override suspend fun retryTask(taskId: String): V25Result<V25GenerationTask> = throw NotImplementedError()
        override suspend fun deleteTask(taskId: String, deleteGeneratedCards: Boolean): V25Result<Unit> = throw NotImplementedError()
        override suspend fun listDecks(projectId: String?): V25Result<List<V25Deck>> = throw NotImplementedError()
        override suspend fun getDeck(deckId: String): V25Result<V25Deck> = throw NotImplementedError()
        override suspend fun renameDeck(deckId: String, name: String): V25Result<V25Deck> = throw NotImplementedError()
        override suspend fun deleteDeck(deckId: String, idempotencyKey: String?): V25Result<Unit> =
            throw NotImplementedError()
        override suspend fun listCards(deckId: String, filter: V25BrowseFilter): V25Result<List<V25Card>> = throw NotImplementedError()
        override suspend fun updateCard(cardId: String, front: String, back: String): V25Result<V25Card> = throw NotImplementedError()
        override suspend fun deleteCard(cardId: String): V25Result<V25CardDeletionBatch> = throw NotImplementedError()
        override suspend fun pendingDeletionBatches(): V25Result<List<V25CardDeletionBatch>> = throw NotImplementedError()
        override suspend fun undoDeletionBatch(deleteBatchId: String): V25Result<Unit> = throw NotImplementedError()
        override suspend fun createRewritePreview(cardId: String, customRequirements: String?): V25Result<V25CardRewritePreview> = throw NotImplementedError()
        override suspend fun applyRewritePreview(cardId: String, rewriteId: String): V25Result<V25Card> = throw NotImplementedError()
        override suspend fun cancelRewritePreview(cardId: String, rewriteId: String): V25Result<Unit> = throw NotImplementedError()
        override suspend fun todayPlan(): V25Result<V25TodayPlan> = throw NotImplementedError()
        override suspend fun deckReviewQueue(deckId: String): V25Result<List<V25ReviewCard>> = throw NotImplementedError()
        override suspend fun rateCard(cardId: String, rating: V25Rating, clientEventId: String?, idempotencyKey: String?): V25Result<V25RatingResult> = throw NotImplementedError()
        override suspend fun statsDashboard(): V25Result<V25StatsDashboard> = throw NotImplementedError()
        override suspend fun apiKeyStatus(): V25Result<V25ApiKeyStatus> = throw NotImplementedError()
        override suspend fun saveApiKey(apiKey: String): V25Result<V25ApiKeyStatus> = throw NotImplementedError()
    }
}
