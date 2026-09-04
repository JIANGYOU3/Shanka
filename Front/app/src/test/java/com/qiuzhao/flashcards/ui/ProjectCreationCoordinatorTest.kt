package com.qiuzhao.flashcards.ui

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
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the two-step project creation (contract V25-D-29/30) on the JVM: step one is a single
 * JSON POST /projects with only the name (the EMPTY project), step two attaches every staged
 * material through its own endpoint. One user operation owns fixed Idempotency Keys and
 * remembers the created project plus finished uploads, so a retry after a lost response replays
 * only the failed step and can never create a second project or duplicate a material.
 */
private val NOW: java.time.Instant = java.time.Instant.parse("2026-08-31T00:00:00Z")

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProjectCreationCoordinatorTest {

    private val networkFailure = V25Result.Failure("NETWORK_UNAVAILABLE", null, "网络错误")

    private fun emptyProject(name: String) = V25LearningProject(
        projectId = "project-new",
        name = name,
        materials = emptyList(),
        status = V25ProjectStatus.EMPTY,
        chapterCount = 0,
        deckCount = 0,
        taskCount = 0,
        createdAt = NOW,
        updatedAt = NOW,
        version = 1,
    )

    private fun pdfMaterial(name: String) = V25Material(
        materialId = "material-pdf",
        projectId = "project-new",
        type = V25MaterialType.PDF,
        name = name,
        status = V25MaterialStatus.PENDING,
        createdAt = NOW,
    )

    private fun textMaterial(name: String) = V25Material(
        materialId = "material-text",
        projectId = "project-new",
        type = V25MaterialType.TEXT,
        name = name,
        status = V25MaterialStatus.READY,
        charCount = 4,
        chapter = V25Chapter("chapter-text", "material-text", name, startPage = null, endPage = null),
        createdAt = NOW,
    )

    private fun pdfUpload(name: String = "线性代数.pdf", body: ByteArray = byteArrayOf(1)) =
        MaterialUpload.Pdf(draftId = "draft-pdf", materialName = name, openStream = { ByteArrayInputStream(body) })

    private fun textUpload(name: String = "课堂笔记", content: String = "绪论") =
        MaterialUpload.Text(draftId = "draft-text", materialName = name, content = content)

    @Test
    fun `step one creates the EMPTY project and step two attaches every staged material`() = runTest {
        val repo = ScriptedRepository()
        val coordinator = ProjectCreationCoordinator(repo)

        val result = coordinator.submit("概率论", listOf(pdfUpload(), textUpload()))

        assertTrue(result is V25Result.Success)
        assertEquals("project-new", (result as V25Result.Success).value)
        // Step one: exactly one JSON create call without any file bytes.
        assertEquals(1, repo.createProjectCalls.size)
        assertEquals("概率论", repo.createProjectCalls.single().first)
                // Step two: one materials call per staged draft, each with its own key.
        assertEquals(1, repo.addPdfCalls.size)
        assertEquals(1, repo.addTextCalls.size)
        val (createKey) = repo.createProjectCalls.single()
        val (pdfKey, _) = repo.addPdfCalls.single()
        val (textKey, _, _) = repo.addTextCalls.single()
        assertTrue(createKey.isNotBlank() && pdfKey.isNotBlank() && textKey.isNotBlank())
        assertTrue("each step owns a distinct key", createKey != pdfKey && pdfKey != textKey)
        assertNull("a committed creation clears the attempt", coordinator.attempt.value)
    }

    @Test
    fun `a failed material upload keeps the project and a retry uploads only the missing material`() = runTest {
        val repo = ScriptedRepository().apply { addPdfResultsQueue += networkFailure }
        val coordinator = ProjectCreationCoordinator(repo)

        // Text first so it lands before the PDF upload fails.
        val failed = coordinator.submit("概率论", listOf(textUpload(), pdfUpload()))
        assertTrue(failed is V25Result.Failure)
        val attempt = coordinator.attempt.value
        assertEquals("the created project is remembered", "project-new", attempt!!.createdProjectId)
        assertEquals("the text material landed before the failure", setOf("draft-text"), attempt.uploadedDraftIds)

        val retried = coordinator.submit("概率论", listOf(textUpload(), pdfUpload()))
        assertTrue(retried is V25Result.Success)

        // The project was created exactly once and the text material never duplicated.
        assertEquals(1, repo.createProjectCalls.size)
        assertEquals(1, repo.addTextCalls.size)
        assertEquals("only the PDF upload replayed", 2, repo.addPdfCalls.size)
        val firstPdfKey = repo.addPdfCalls[0].first
        val retryPdfKey = repo.addPdfCalls[1].first
        assertEquals("the retry reuses the same PDF key", firstPdfKey, retryPdfKey)
        assertNull(coordinator.attempt.value)
    }

    @Test
    fun `a failed project creation replays the identical create key`() = runTest {
        val repo = ScriptedRepository().apply { createProjectResultsQueue += networkFailure }
        val coordinator = ProjectCreationCoordinator(repo)

        val failed = coordinator.submit("概率论", listOf(pdfUpload()))
        assertTrue(failed is V25Result.Failure)
        assertNull(coordinator.attempt.value!!.createdProjectId)

        val retried = coordinator.submit("概率论", listOf(pdfUpload()))
        assertTrue(retried is V25Result.Success)
        assertEquals(2, repo.createProjectCalls.size)
        assertEquals(
            "both create attempts carry the identical key, so the server dedupes",
            repo.createProjectCalls[0].second,
            repo.createProjectCalls[1].second,
        )
        assertEquals(1, repo.addPdfCalls.size)
    }

    @Test
    fun `a different creation starts fresh with new keys`() = runTest {
        val repo = ScriptedRepository().apply { addPdfResultsQueue += networkFailure }
        val coordinator = ProjectCreationCoordinator(repo)

        coordinator.submit("概率论", listOf(pdfUpload()))
        val firstKey = coordinator.attempt.value!!.createProjectKey

        repo.addPdfResultsQueue += V25Result.Success(pdfMaterial("线性代数.pdf"))
        coordinator.submit("概率统计", listOf(pdfUpload()))

        assertEquals("a changed operation re-creates the project", 2, repo.createProjectCalls.size)
        assertTrue(repo.createProjectCalls[0].second != repo.createProjectCalls[1].second)
        assertTrue(firstKey.isNotBlank())
        assertNull(coordinator.attempt.value)
    }

    @Test
    fun `a concurrent submit is rejected while one creation is in flight`() = runTest(kotlinx.coroutines.test.UnconfinedTestDispatcher()) {
        val repo = ScriptedRepository().apply { creationGate = kotlinx.coroutines.CompletableDeferred() }
        val coordinator = ProjectCreationCoordinator(repo)

        val first = launch { coordinator.submit("概率论", listOf(pdfUpload())) }
        assertTrue(coordinator.creating.value)

        val rejected = coordinator.submit("概率论", listOf(pdfUpload()))
        assertTrue(rejected is V25Result.Failure)
        assertEquals(ProjectCreationCoordinator.IN_FLIGHT_CODE, (rejected as V25Result.Failure).code)

        repo.creationGate!!.complete(V25Result.Success(emptyProject("概率论")))
        first.join()
        assertEquals(false, coordinator.creating.value)
        assertEquals(1, repo.createProjectCalls.size)
    }

/** Scriptable V25Repository: only the creation/materials steps are exercised here. */
    private class ScriptedRepository : V25Repository {
        val createProjectCalls = mutableListOf<Pair<String, String>>() // name to key
        val addPdfCalls = mutableListOf<Pair<String, ByteArray?>>() // key to body bytes
        val addTextCalls = mutableListOf<Triple<String, String, String?>>() // key, name, content
        val createProjectResultsQueue = ArrayDeque<V25Result<V25LearningProject>>()
        val addPdfResultsQueue = ArrayDeque<V25Result<V25Material>>()
        var creationGate: kotlinx.coroutines.CompletableDeferred<V25Result<V25LearningProject>>? = null

        override suspend fun createProject(name: String, idempotencyKey: String?): V25Result<V25LearningProject> {
            createProjectCalls += name to (idempotencyKey ?: "")
            creationGate?.let { gate -> return gate.await() }
            return createProjectResultsQueue.removeFirstOrNull()
                ?: V25Result.Success(
                    V25LearningProject(
                        projectId = "project-new",
                        name = name,
                        materials = emptyList(),
                        status = V25ProjectStatus.EMPTY,
                        chapterCount = 0,
                        deckCount = 0,
                        taskCount = 0,
                        createdAt = NOW,
                        updatedAt = NOW,
                        version = 1,
                    ),
                )
        }

        override suspend fun addProjectMaterialPdf(
            projectId: String,
            fileName: String,
            content: InputStream,
            idempotencyKey: String?,
        ): V25Result<V25Material> {
            addPdfCalls += (idempotencyKey ?: "") to content.use { it.readBytes() }
            return addPdfResultsQueue.removeFirstOrNull()
                ?: V25Result.Success(
                    V25Material(
                        materialId = "material-pdf",
                        projectId = projectId,
                        type = V25MaterialType.PDF,
                        name = fileName,
                        status = V25MaterialStatus.PENDING,
                        createdAt = NOW,
                    ),
                )
        }

        override suspend fun addProjectMaterialText(
            projectId: String,
            name: String,
            content: String,
            idempotencyKey: String?,
        ): V25Result<V25Material> {
            addTextCalls += Triple(idempotencyKey ?: "", name, content)
            return V25Result.Success(
                V25Material(
                    materialId = "material-text",
                    projectId = projectId,
                    type = V25MaterialType.TEXT,
                    name = name,
                    status = V25MaterialStatus.READY,
                    charCount = content.length,
                    chapter = V25Chapter("chapter-text", "material-text", name, startPage = null, endPage = null),
                    createdAt = NOW,
                ),
            )
        }

        // Untouched boundary methods: any call is a test bug.
        override suspend fun getAuthUser(): V25Result<V25AuthUser> = throw NotImplementedError()
        override suspend fun updateAuthUser(username: String?, avatarKey: V25AvatarKey?): V25Result<V25AuthUser> = throw NotImplementedError()
        override suspend fun logout(): V25Result<Unit> = throw NotImplementedError()
        override suspend fun getPreferences(): V25Result<V25UserPreferences> = throw NotImplementedError()
        override suspend fun updatePreferences(patch: V25PreferencesPatch): V25Result<V25UserPreferences> = throw NotImplementedError()
        override suspend fun setCurrentProject(projectId: String?): V25Result<V25UserPreferences> = throw NotImplementedError()
        override suspend fun listProjects(forceRefresh: Boolean): V25Result<List<V25LearningProject>> = throw NotImplementedError()
        override suspend fun getProject(projectId: String, forceRefresh: Boolean): V25Result<V25LearningProject> = throw NotImplementedError()
        override suspend fun renameProject(projectId: String, name: String): V25Result<V25LearningProject> = throw NotImplementedError()
        override suspend fun deleteProject(projectId: String, retainDecks: Boolean, idempotencyKey: String?): V25Result<Unit> = throw NotImplementedError()
        override suspend fun listProjectMaterials(projectId: String): V25Result<List<V25Material>> = throw NotImplementedError()
        override suspend fun deleteProjectMaterial(
            projectId: String,
            materialId: String,
            retainCards: Boolean,
            idempotencyKey: String?,
        ): V25Result<V25LearningProject> = throw NotImplementedError()
        override suspend fun replaceProjectMaterialPdf(
            projectId: String,
            materialId: String,
            fileName: String,
            content: InputStream,
            idempotencyKey: String?,
        ): V25Result<V25Material> = throw NotImplementedError()
        override suspend fun projectProgress(projectId: String): V25Result<V25ProgressSummary> = throw NotImplementedError()
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
        override suspend fun deleteTask(taskId: String, deleteGeneratedCards: Boolean): V25Result<Unit> = throw NotImplementedError()
        override suspend fun listDecks(projectId: String?): V25Result<List<V25Deck>> = throw NotImplementedError()
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
        override suspend fun todayPlan(): V25Result<V25TodayPlan> = throw NotImplementedError()
        override suspend fun deckReviewQueue(deckId: String): V25Result<List<V25ReviewCard>> = throw NotImplementedError()
        override suspend fun rateCard(cardId: String, rating: V25Rating, clientEventId: String?, idempotencyKey: String?): V25Result<V25RatingResult> = throw NotImplementedError()
        override suspend fun getStudyPlan(): V25Result<V25StudyPlan> = throw NotImplementedError()
        override suspend fun updateStudyPlan(plan: V25StudyPlanUpdate, idempotencyKey: String?): V25Result<V25StudyPlan> = throw NotImplementedError()
        override suspend fun studyPlanBacklog(offset: Int, limit: Int): V25Result<List<com.qiuzhao.flashcards.domain.v25.V25PlanCard>> = throw NotImplementedError()
        override suspend fun statsDashboard(): V25Result<com.qiuzhao.flashcards.domain.v25.V25StatsDashboard> = throw NotImplementedError()
        override suspend fun apiKeyStatus(): V25Result<V25ApiKeyStatus> = throw NotImplementedError()
        override suspend fun saveApiKey(apiKey: String): V25Result<V25ApiKeyStatus> = throw NotImplementedError()

    }
}
