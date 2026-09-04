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
import com.qiuzhao.flashcards.domain.v25.V25LearningProject
import com.qiuzhao.flashcards.domain.v25.V25PreferencesPatch
import com.qiuzhao.flashcards.domain.v25.V25ProjectStudySettings
import com.qiuzhao.flashcards.domain.v25.V25Rating
import com.qiuzhao.flashcards.domain.v25.V25RatingResult
import com.qiuzhao.flashcards.domain.v25.V25Repository
import com.qiuzhao.flashcards.domain.v25.V25Result
import com.qiuzhao.flashcards.domain.v25.V25ReviewCard
import com.qiuzhao.flashcards.domain.v25.V25ReviewState
import com.qiuzhao.flashcards.domain.v25.V25SampleCard
import com.qiuzhao.flashcards.domain.v25.V25StatsDashboard
import com.qiuzhao.flashcards.domain.v25.V25StudySettingsPatch
import com.qiuzhao.flashcards.domain.v25.V25TaskConfigPatch
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import com.qiuzhao.flashcards.domain.v25.V25TodayPlan
import com.qiuzhao.flashcards.domain.v25.V25UserPreferences
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the review coordinator's replay semantics on the JVM: one user rating owns fixed
 * `client_event_id` and Idempotency-Key identifiers, so a retry after a lost response replays the
 * identical event and the server can never record two ratings for one swipe.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReviewCoordinatorTest {

    private val networkFailure = V25Result.Failure("NETWORK_UNAVAILABLE", null, "网络错误")

    private fun ratingResult() = V25RatingResult(
        reviewState = V25ReviewState(state = "review", due = null),
        studyDate = LocalDate.of(2026, 8, 28),
    )

    @Test
    fun `a rating submission carries its own client event id and idempotency key`() = runTest {
        val repo = ScriptedRepository()
        val coordinator = ReviewCoordinator(repo)

        val result = coordinator.submit("card-1", V25Rating.GOOD)

        assertTrue(result is V25Result.Success)
        val call = repo.rateCardCalls.single()
        assertEquals("card-1", call.cardId)
        assertEquals(V25Rating.GOOD, call.rating)
        assertTrue("the event id must be explicit for the server-side dedupe", call.clientEventId != null && call.clientEventId.isNotBlank())
        assertTrue("the submission must carry an explicit idempotency key", call.idempotencyKey != null && call.idempotencyKey.isNotBlank())
        assertTrue("event id and idempotency key are distinct identifiers", call.clientEventId != call.idempotencyKey)
        assertNull("a committed rating clears the attempt", coordinator.attempt.value)
        assertEquals(false, coordinator.submitting.value)
    }

    @Test
    fun `a failed rating keeps the attempt and a retry reuses the same identifiers`() = runTest {
        val repo = ScriptedRepository().apply { rateResultsQueue += networkFailure }
        val coordinator = ReviewCoordinator(repo)

        val failed = coordinator.submit("card-1", V25Rating.GOOD)
        assertTrue(failed is V25Result.Failure)
        val attempt = coordinator.attempt.value
        assertEquals("card-1", attempt!!.cardId)
        assertEquals(V25Rating.GOOD, attempt.rating)
        assertEquals("the failed card stays in the attempt with its keys", false, coordinator.submitting.value)

        repo.rateResultsQueue += V25Result.Success(ratingResult())
        val retried = coordinator.submit("card-1", V25Rating.GOOD)

        assertTrue(retried is V25Result.Success)
        assertNull("a committed rating clears the attempt", coordinator.attempt.value)
        assertEquals(2, repo.rateCardCalls.size)
        assertEquals(
            "the retry reuses the same client event id",
            repo.rateCardCalls[0].clientEventId,
            repo.rateCardCalls[1].clientEventId,
        )
        assertEquals(
            "the retry reuses the same idempotency key",
            repo.rateCardCalls[0].idempotencyKey,
            repo.rateCardCalls[1].idempotencyKey,
        )
    }

    @Test
    fun `a different rating for the same card starts fresh identifiers`() = runTest {
        val repo = ScriptedRepository().apply { rateResultsQueue += networkFailure }
        val coordinator = ReviewCoordinator(repo)

        coordinator.submit("card-1", V25Rating.GOOD)
        val firstEventId = repo.rateCardCalls[0].clientEventId

        repo.rateResultsQueue += V25Result.Success(ratingResult())
        coordinator.submit("card-1", V25Rating.AGAIN)

        // A different decision is a different event: new identifiers, never a dedupe collision.
        assertEquals(2, repo.rateCardCalls.size)
        assertTrue("changed rating must not reuse the old event id", repo.rateCardCalls[1].clientEventId != firstEventId)
        assertTrue("changed rating must not reuse the old idempotency key", repo.rateCardCalls[1].idempotencyKey != repo.rateCardCalls[0].idempotencyKey)
    }

    @Test
    fun `a concurrent submit is rejected while one is in flight`() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<V25Result<V25RatingResult>>()
        val repo = ScriptedRepository().apply { rateGate = gate }
        val coordinator = ReviewCoordinator(repo)

        val first = launch { coordinator.submit("card-1", V25Rating.GOOD) }
        // The unconfined dispatcher runs the first submission eagerly up to the gated call.
        assertTrue(coordinator.submitting.value)

        val rejected = coordinator.submit("card-1", V25Rating.GOOD)
        assertTrue(rejected is V25Result.Failure)
        assertEquals(ReviewCoordinator.IN_FLIGHT_CODE, (rejected as V25Result.Failure).code)

        gate.complete(V25Result.Success(ratingResult()))
        first.join()
        assertEquals(false, coordinator.submitting.value)
        assertEquals(1, repo.rateCardCalls.size)
    }

    /** Scriptable V25Repository: only rateCard is exercised here. */
    private class ScriptedRepository : V25Repository {
        data class RatingCall(
            val cardId: String,
            val rating: V25Rating,
            val clientEventId: String?,
            val idempotencyKey: String?,
        )

        val rateCardCalls = mutableListOf<RatingCall>()
        val rateResultsQueue = ArrayDeque<V25Result<V25RatingResult>>()
        var rateGate: CompletableDeferred<V25Result<V25RatingResult>>? = null

        override suspend fun rateCard(cardId: String, rating: V25Rating, clientEventId: String?, idempotencyKey: String?): V25Result<V25RatingResult> {
            rateCardCalls += RatingCall(cardId, rating, clientEventId, idempotencyKey)
            rateGate?.let { gate -> return gate.await() }
            return rateResultsQueue.removeFirstOrNull() ?: V25Result.Success(
                V25RatingResult(
                    reviewState = V25ReviewState(state = "review", due = null),
                    studyDate = LocalDate.of(2026, 8, 28),
                ),
            )
        }

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
        override suspend fun createDeck(name: String, projectId: String?, idempotencyKey: String?): V25Result<V25Deck> = throw NotImplementedError()
        override suspend fun getDeck(deckId: String): V25Result<V25Deck> = throw NotImplementedError()
        override suspend fun renameDeck(deckId: String, name: String): V25Result<V25Deck> = throw NotImplementedError()
        override suspend fun deleteDeck(deckId: String, idempotencyKey: String?): V25Result<Unit> =
            throw NotImplementedError()
        override suspend fun listCards(deckId: String, filter: V25BrowseFilter): V25Result<List<V25Card>> = throw NotImplementedError()
        override suspend fun importCards(deckId: String, drafts: List<V25CardDraft>, idempotencyKey: String?): V25Result<List<V25ImportResult>> = throw NotImplementedError()
        override suspend fun updateCard(cardId: String, front: String, back: String): V25Result<V25Card> = throw NotImplementedError()
        override suspend fun deleteCard(cardId: String): V25Result<V25CardDeletionBatch> = throw NotImplementedError()
        override suspend fun pendingDeletionBatches(): V25Result<List<V25CardDeletionBatch>> = throw NotImplementedError()
        override suspend fun undoDeletionBatch(deleteBatchId: String): V25Result<Unit> = throw NotImplementedError()
        override suspend fun createRewritePreview(cardId: String, customRequirements: String?): V25Result<V25CardRewritePreview> = throw NotImplementedError()
        override suspend fun applyRewritePreview(cardId: String, rewriteId: String): V25Result<V25Card> = throw NotImplementedError()
        override suspend fun cancelRewritePreview(cardId: String, rewriteId: String): V25Result<Unit> = throw NotImplementedError()
        override suspend fun todayPlan(): V25Result<V25TodayPlan> = throw NotImplementedError()
        override suspend fun deckReviewQueue(deckId: String): V25Result<List<V25ReviewCard>> = throw NotImplementedError()
        override suspend fun statsDashboard(): V25Result<V25StatsDashboard> = throw NotImplementedError()
        override suspend fun apiKeyStatus(): V25Result<V25ApiKeyStatus> = throw NotImplementedError()
        override suspend fun saveApiKey(apiKey: String): V25Result<V25ApiKeyStatus> = throw NotImplementedError()
    }
}
