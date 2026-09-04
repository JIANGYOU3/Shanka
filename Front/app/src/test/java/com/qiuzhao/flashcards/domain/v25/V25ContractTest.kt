package com.qiuzhao.flashcards.domain.v25

import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the V2.5 typed bridge on the JVM: the exact enum values from the target Architecture
 * (sections 3 and 4), the immutable model shapes, and every success/empty/recoverable-failure
 * result variant the visual lane (V-02 through V-06) consumes. [StubV25Repository] below is the
 * compile-time contract: it must implement every [V25Repository] method, so any interface change
 * breaks this file before any UI depends on it. The stub contains no Android UI type, no HTTP
 * status and no JSON object — exactly like the production files it stands in for.
 */
class V25ContractTest {

    @Test
    fun `project and task status enums carry the exact V2-5 values`() {
        // Contract 3.16: EMPTY leads the aggregate (a project without materials is alive).
        assertEquals(
            listOf("EMPTY", "PARSING", "PARSE_FAILED", "AWAITING_CHAPTER_CONFIRMATION", "READY"),
            V25ProjectStatus.entries.map { it.name },
        )
        assertEquals(
            listOf("DRAFT", "SAMPLE_GENERATING", "AWAITING_SAMPLE_CONFIRMATION", "GENERATING", "COMPLETED", "FAILED", "ABANDONED"),
            V25TaskStatus.entries.map { it.name },
        )
        assertEquals(
            listOf("PLANNING", "GENERATING", "SCORING", "PUBLISHING"),
            V25InternalStage.entries.map { it.name },
        )
        assertEquals(
            listOf("STAGED", "PUBLISHED"),
            V25PublicationState.entries.map { it.name },
        )
    }

    @Test
    fun `material enums carry the exact V2-5 values`() {
        // structure-contract 3.2a: LINK is reserved and not modelled; PDF uses the parse
        // lifecycle while TEXT is always READY.
        assertEquals(listOf("PDF", "TEXT"), V25MaterialType.entries.map { it.name })
        assertEquals(
            listOf("PENDING", "PARSING", "PARSED", "FAILED", "READY"),
            V25MaterialStatus.entries.map { it.name },
        )
    }

    @Test
    fun `card and deletion enums carry the exact V2-5 values`() {
        assertEquals(listOf("QUESTION", "TRUE_FALSE"), V25CardType.entries.map { it.name })
        assertEquals(listOf("BASIC", "UNDERSTANDING", "DEEP_QUESTION"), V25Difficulty.entries.map { it.name })
        assertEquals(listOf("PENDING", "UNDONE", "FINALIZED"), V25DeletionBatchStatus.entries.map { it.name })
        assertEquals(listOf("PENDING", "APPLIED", "CANCELLED", "EXPIRED"), V25RewriteStatus.entries.map { it.name })
        assertEquals(listOf("AGAIN", "HARD", "GOOD", "EASY"), V25Rating.entries.map { it.name })
    }

    @Test
    fun `settings and browse enums carry the exact V2-5 values`() {
        assertEquals(listOf("COMPACT", "BALANCED", "EXTENSIVE"), V25CoverageMode.entries.map { it.name })
        // Query-parameter values are lowercase on the wire (Architecture 4.4) and stay lowercase here.
        assertEquals(listOf("position", "random"), V25BrowseOrder.entries.map { it.name })
        assertEquals(listOf("BASIC", "UNDERSTANDING", "DEEP_QUESTION", "UNLABELED"), V25ContentDifficulty.entries.map { it.name })
        assertEquals(listOf("all", "mastered", "unmastered"), V25MasteryFilter.entries.map { it.name })
    }

    @Test
    fun `avatar keys are exactly the twelve preset moods`() {
        assertEquals((1..12).map { "mood_%02d".format(it) }, V25AvatarKey.entries.map { it.name })
        assertEquals(12, V25AvatarKey.entries.size)
    }

    @Test
    fun `api key states cover the five settings semantics`() {
        assertEquals(
            listOf("AVAILABLE", "INVALID", "INSUFFICIENT_BALANCE", "VERIFICATION_UNAVAILABLE", "UNSET"),
            V25ApiKeyState.entries.map { it.name },
        )
    }

    @Test
    fun `failure codes match the architecture error-code increment`() {
        val v25Codes = listOf(
            V25ErrorCodes.PROJECT_NOT_FOUND,
            V25ErrorCodes.PROJECT_STATE_CONFLICT,
            V25ErrorCodes.PROJECT_HAS_ACTIVE_TASK,
            V25ErrorCodes.TASK_STATE_CONFLICT,
            V25ErrorCodes.TASK_ZERO_CARDS,
            V25ErrorCodes.SAMPLE_STALE,
            V25ErrorCodes.INVALID_LEARNING_TIMEZONE,
            V25ErrorCodes.INVALID_PREFERENCES,
            V25ErrorCodes.CARD_DELETE_WINDOW_EXPIRED,
            V25ErrorCodes.CARD_REWRITE_UNAVAILABLE,
            V25ErrorCodes.CARD_VERSION_CONFLICT,
        )
        assertEquals(11, v25Codes.size)
        assertEquals(v25Codes, v25Codes.distinct())
    }

    @Test
    fun `auth failures are distinguished from recoverable failures`() {
        assertTrue(V25Result.Failure(V25ErrorCodes.AUTH_REQUIRED, null, null).isAuthFailure)
        assertTrue(V25Result.Failure(V25ErrorCodes.AUTH_INVALID, null, null).isAuthFailure)
        assertFalse(V25Result.Failure(V25ErrorCodes.NETWORK_UNAVAILABLE, null, null).isAuthFailure)
        assertFalse(V25Result.Failure(V25ErrorCodes.SAMPLE_STALE, null, null).isAuthFailure)
    }

    @Test
    fun `difficulty ratios are integer ten-percent steps summing to one hundred`() {
        V25DifficultyRatio(0, 0, 100)
        V25DifficultyRatio(40, 40, 20)
        V25DifficultyRatio(50, 0, 50)
        assertThrows(IllegalArgumentException::class.java) { V25DifficultyRatio(40, 40, 30) }
        assertThrows(IllegalArgumentException::class.java) { V25DifficultyRatio(45, 45, 10) }
        assertThrows(IllegalArgumentException::class.java) { V25DifficultyRatio(110, -10, 0) }
    }

    @Test
    fun `daily learning goal is a multiple of ten between ten and two hundred`() {
        preferences(dailyGoal = 10)
        preferences(dailyGoal = 200)
        assertThrows(IllegalArgumentException::class.java) { preferences(dailyGoal = 5) }
        assertThrows(IllegalArgumentException::class.java) { preferences(dailyGoal = 55) }
        assertThrows(IllegalArgumentException::class.java) { preferences(dailyGoal = 250) }
    }

    @Test
    fun `preference and task patches require at least one field`() {
        assertThrows(IllegalArgumentException::class.java) { V25PreferencesPatch() }
        assertThrows(IllegalArgumentException::class.java) { V25TaskConfigPatch() }
        assertThrows(IllegalArgumentException::class.java) { V25StudySettingsPatch() }
    }

    @Test
    fun `auth profile success carries the typed email and preset avatar`() = runBlocking {
        val result = StubV25Repository().getAuthUser()
        assertTrue(result is V25Result.Success)
        val profile = (result as V25Result.Success).value
        assertEquals("alice@example.com", profile.email)
        assertEquals(V25AvatarKey.mood_03, profile.avatarKey)
        assertEquals(Instant.parse("2026-08-15T08:00:00Z"), profile.createdAt)
    }

    @Test
    fun `empty results are successes with no items rather than failures`() = runBlocking {
        val repository = StubV25Repository()
        val projects = repository.listProjects()
        assertTrue(projects is V25Result.Success)
        assertTrue((projects as V25Result.Success).value.isEmpty())

        val batches = repository.pendingDeletionBatches()
        assertTrue(batches is V25Result.Success)
        assertTrue((batches as V25Result.Success).value.isEmpty())

        val queue = repository.deckReviewQueue("deck-1")
        assertTrue(queue is V25Result.Success)
        assertTrue((queue as V25Result.Success).value.isEmpty())
    }

    @Test
    fun `today plan represents the no-current-project empty state`() = runBlocking {
        val result = StubV25Repository().todayPlan()
        assertTrue(result is V25Result.Success)
        val plan = (result as V25Result.Success).value
        assertNull(plan.currentProject)
        assertTrue(plan.cards.isEmpty())
        assertEquals("Asia/Shanghai", plan.learningTimezone)
        assertEquals(LocalDate.parse("2026-08-15"), plan.studyDate)
        assertEquals(0, plan.completedCount)
    }

    @Test
    fun `recoverable failures carry the exact V2-5 error codes`() = runBlocking {
        val repository = StubV25Repository()
        assertEquals(
            V25ErrorCodes.PROJECT_HAS_ACTIVE_TASK,
            failureCode(repository.deleteProject("project-1", retainDecks = true)),
        )
        assertEquals(V25ErrorCodes.SAMPLE_STALE, failureCode(repository.startTask("task-1")))
        assertEquals(
            V25ErrorCodes.CARD_VERSION_CONFLICT,
            failureCode(repository.applyRewritePreview("card-1", "rewrite-1")),
        )
        assertEquals(
            V25ErrorCodes.CARD_DELETE_WINDOW_EXPIRED,
            failureCode(repository.undoDeletionBatch("batch-1")),
        )
        assertEquals(
            V25ErrorCodes.NETWORK_UNAVAILABLE,
            failureCode(repository.rateCard("card-1", V25Rating.GOOD)),
        )
    }

    @Test
    fun `deleting a card returns a server-authoritative undo batch`() = runBlocking {
        val result = StubV25Repository().deleteCard("card-1")
        assertTrue(result is V25Result.Success)
        val batch = (result as V25Result.Success).value
        assertEquals("batch-1", batch.deleteBatchId)
        assertEquals(listOf("card-1"), batch.cardIds)
        assertEquals(V25DeletionBatchStatus.PENDING, batch.status)
        assertEquals(Instant.parse("2026-08-15T08:00:10Z"), batch.undoUntil)
    }

    @Test
    fun `rewrite preview carries base version and expiry`() = runBlocking {
        val result = StubV25Repository().createRewritePreview("card-1", customRequirements = "更口语")
        assertTrue(result is V25Result.Success)
        val preview = (result as V25Result.Success).value
        assertEquals("rewrite-1", preview.rewriteId)
        assertEquals("3", preview.baseCardVersion)
        assertEquals(V25RewriteStatus.PENDING, preview.status)
        assertEquals("更口语", preview.customRequirements)
        assertEquals(Instant.parse("2026-08-16T08:00:00Z"), preview.expiresAt)
    }

    @Test
    fun `two-step creation starts from an EMPTY project and materials attach afterwards`() = runBlocking {
        val repository = StubV25Repository()

        // Step one: POST /projects with only the JSON name → the EMPTY project.
        val created = repository.createProject("概率论")
        assertTrue(created is V25Result.Success)
        val project = (created as V25Result.Success).value
        assertEquals(V25ProjectStatus.EMPTY, project.status)
        assertTrue("a fresh project owns no materials", project.materials.isEmpty())

        // Step two: materials attach to the living project with their own endpoints.
        val pdf = repository.addProjectMaterialPdf("project-1", "线性代数.pdf", ByteArrayInputStream(ByteArray(0)))
        assertTrue(pdf is V25Result.Success)
        assertEquals(V25MaterialType.PDF, (pdf as V25Result.Success).value.type)
        assertEquals(V25MaterialStatus.PENDING, pdf.value.status)

        val text = repository.addProjectMaterialText("project-1", "课堂笔记", "第一章 绪论")
        assertTrue(text is V25Result.Success)
        val textMaterial = (text as V25Result.Success).value
        assertEquals(V25MaterialType.TEXT, textMaterial.type)
        assertEquals(V25MaterialStatus.READY, textMaterial.status)
        assertNull("a TEXT chapter has no page span", textMaterial.chapter?.startPage)
        assertNull(textMaterial.chapter?.endPage)
        assertEquals("a TEXT chapter belongs to its material", textMaterial.materialId, textMaterial.chapter?.materialId)
    }

    @Test
    fun `text chapter page spans stay null while pdf chapters carry pages`() {
        val textChapter = V25Chapter(id = "ch-text", materialId = "m-text", name = "全文", startPage = null, endPage = null)
        val pdfChapter = V25Chapter(id = "ch-pdf", materialId = "m-pdf", name = "第一章", startPage = 1, endPage = 20)

        assertNull(textChapter.pageSpanLabel)
        assertEquals("1-20 页", pdfChapter.pageSpanLabel)
    }

    @Test
    fun `deleting a material carries the retain-cards decision`() = runBlocking {
        val repository = StubV25Repository()

        val kept = repository.deleteProjectMaterial("project-1", "material-1", retainCards = true)
        val removed = repository.deleteProjectMaterial("project-1", "material-1", retainCards = false)

        assertTrue(kept is V25Result.Success)
        assertEquals(V25ProjectStatus.EMPTY, (kept as V25Result.Success).value.status)
        assertTrue(removed is V25Result.Failure)
        assertEquals(V25ErrorCodes.MATERIAL_NOT_FOUND, (removed as V25Result.Failure).code)
    }

    @Test
    fun `every boundary method is callable from a suspend context and returns a typed result`() = runBlocking {
        val repository = StubV25Repository()
        val results: List<V25Result<*>> = listOf(
            repository.getAuthUser(),
            repository.updateAuthUser(username = "alice", avatarKey = V25AvatarKey.mood_01),
            repository.logout(),
            repository.getPreferences(),
            repository.updatePreferences(V25PreferencesPatch(dailyLearningGoal = 60)),
            repository.setCurrentProject(null),
            repository.createProject("概率论"),
            repository.addProjectMaterialPdf("project-1", "概率论.pdf", ByteArrayInputStream(ByteArray(0))),
            repository.addProjectMaterialText("project-1", "课堂笔记", "第一章 绪论"),
            repository.listProjectMaterials("project-1"),
            repository.deleteProjectMaterial("project-1", "material-1", retainCards = true),
            repository.replaceProjectMaterialPdf("project-1", "material-1", "概率论-v2.pdf", ByteArrayInputStream(ByteArray(0))),
            repository.listProjects(),
            repository.getProject("project-1"),
            repository.renameProject("project-1", "概率论基础"),
            repository.deleteProject("project-1", retainDecks = true),
            repository.updateChapter("project-1", "chapter-1", V25ChapterEdit("第一章", 1, 20)),
            repository.deleteChapter("project-1", "chapter-1", deleteCards = false),
            repository.confirmChapters("project-1"),
            repository.getStudySettings("project-1"),
            repository.updateStudySettings("project-1", V25StudySettingsPatch(includeUnassigned = false)),
            repository.createTask(
                "project-1",
                "deck-1",
                listOf("chapter-1"),
                V25GenerationConfig(V25CoverageMode.COMPACT, V25DifficultyRatio(100, 0, 0), ""),
            ),
            repository.listTasks(projectId = "project-1", status = V25TaskStatus.DRAFT),
            repository.listTasks(),
            repository.getTask("task-1"),
            repository.updateTaskConfig("task-1", V25TaskConfigPatch(chapterIds = listOf("chapter-1"))),
            repository.generateSamples("task-1"),
            repository.startTask("task-1"),
            repository.abandonTask("task-1"),
            repository.retryTask("task-1"),
            repository.deleteTask("task-1", deleteGeneratedCards = true),
            repository.listDecks(),
            repository.listDecks(projectId = "project-1"),
            repository.createDeck("新牌组"),
            repository.createDeck("新牌组", projectId = "project-1"),
            repository.getDeck("deck-1"),
            repository.renameDeck("deck-1", "概率论"),
            repository.deleteDeck("deck-1"),
            repository.listCards("deck-1", V25BrowseFilter(V25BrowseOrder.position)),
            repository.listCards(
                "deck-1",
                V25BrowseFilter(V25BrowseOrder.random, V25ContentDifficulty.UNLABELED, V25MasteryFilter.unmastered),
            ),
            repository.importCards("deck-1", listOf(V25CardDraft("新卡正面", "新卡背面"))),
            repository.importCards("deck-1", listOf(V25CardDraft("重试卡", "重试答案")), idempotencyKey = "retry-key"),
            repository.updateCard("card-1", "新正面", "新背面"),
            repository.deleteCard("card-1"),
            repository.pendingDeletionBatches(),
            repository.undoDeletionBatch("batch-1"),
            repository.createRewritePreview("card-1"),
            repository.createRewritePreview("card-1", customRequirements = "更口语"),
            repository.applyRewritePreview("card-1", "rewrite-1"),
            repository.cancelRewritePreview("card-1", "rewrite-1"),
            repository.todayPlan(),
            repository.deckReviewQueue("deck-1"),
            repository.rateCard("card-1", V25Rating.EASY),
            repository.statsDashboard(),
            repository.apiKeyStatus(),
            repository.saveApiKey("sk-test-key"),
        )
        results.forEach { result ->
            assertTrue(
                "unexpected result type ${result::class.simpleName}",
                result is V25Result.Success<*> || result is V25Result.Failure,
            )
        }
    }

    private fun preferences(dailyGoal: Int) = V25UserPreferences(
        defaultCoverageMode = V25CoverageMode.BALANCED,
        difficultyRatio = V25DifficultyRatio(40, 40, 20),
        dailyLearningGoal = dailyGoal,
        learningTimezone = "Asia/Shanghai",
        currentProjectId = null,
        updatedAt = Instant.parse("2026-08-15T08:00:00Z"),
    )

    private fun failureCode(result: V25Result<*>): String? = (result as? V25Result.Failure)?.code
}

/**
 * In-memory stand-in for the remote implementation (Task 12). It exists so this test compiles
 * against every [V25Repository] method — the compile-time half of the contract — and exercises
 * each result variant at runtime. It is deliberately free of Android UI, HTTP and JSON types.
 */
private class StubV25Repository : V25Repository {

    private val now: Instant = Instant.parse("2026-08-15T08:00:00Z")
    private val ratio = V25DifficultyRatio(basic = 40, understanding = 40, deepQuestion = 20)
    private val config = V25GenerationConfig(
        coverageMode = V25CoverageMode.BALANCED,
        difficultyRatio = ratio,
        customRequirements = "来源优先",
    )
    private val chapter = V25Chapter(id = "chapter-1", materialId = "material-1", name = "引言", startPage = 1, endPage = 12)
    private val material = V25Material(
        materialId = "material-1",
        projectId = "project-1",
        type = V25MaterialType.PDF,
        name = "概率论.pdf",
        status = V25MaterialStatus.PARSED,
        sizeBytes = 1024L,
        chapter = null,
        createdAt = now,
    )
    private val project = V25LearningProject(
        projectId = "project-1",
        name = "概率论",
        materials = listOf(material),
        status = V25ProjectStatus.READY,
        chapterCount = 1,
        deckCount = 1,
        taskCount = 1,
        createdAt = now,
        updatedAt = now,
        version = 4,
        chapters = listOf(chapter),
    )
    private val preferences = V25UserPreferences(
        defaultCoverageMode = V25CoverageMode.BALANCED,
        difficultyRatio = ratio,
        dailyLearningGoal = 50,
        learningTimezone = "Asia/Shanghai",
        currentProjectId = "project-1",
        updatedAt = now,
    )
    private val authUser = V25AuthUser(
        userId = "user-1",
        username = "alice",
        email = "alice@example.com",
        avatarKey = V25AvatarKey.mood_03,
        createdAt = now,
    )
    private val deck = V25Deck(
        deckId = "deck-1",
        name = "概率论基础",
        projectId = "project-1",
        cardCount = 12,
        dueCount = 3,
        masteredCards = 4,
        reviewCount = 20,
        masteryRatio = 0.33f,
    )
    private val card = V25Card(
        cardId = "card-1",
        deckId = "deck-1",
        front = "大数定律的直觉是什么？",
        back = "重复试验的均值趋于期望",
        cardType = V25CardType.QUESTION,
        targetDifficulty = V25Difficulty.UNDERSTANDING,
        position = 1,
        chapterId = "chapter-1",
        sourceTaskId = "task-1",
        publicationState = V25PublicationState.PUBLISHED,
        version = 3,
    )
    private val task = V25GenerationTask(
        taskId = "task-1",
        projectId = "project-1",
        fileId = "file-1",
        deckId = "deck-1",
        retryOfTaskId = null,
        status = V25TaskStatus.AWAITING_SAMPLE_CONFIRMATION,
        internalStage = null,
        selectedChapters = listOf(chapter),
        generationConfig = config,
        sampleCards = listOf(
            V25SampleCard("样卡一", "答案一", V25CardType.QUESTION, V25Difficulty.BASIC),
            V25SampleCard("样卡二", "答案二", V25CardType.TRUE_FALSE, V25Difficulty.UNDERSTANDING),
            V25SampleCard("样卡三", "参考思路", V25CardType.QUESTION, V25Difficulty.DEEP_QUESTION),
        ),
        sampleConfigHash = "hash-1",
        sampleConfirmedAt = now,
        generatedCardCount = 0,
        errorCode = null,
        failureStage = null,
        createdAt = now,
        startedAt = null,
        endedAt = null,
        updatedAt = now,
    )
    private val stats = V25StatsDashboard(
        hasData = true,
        weeklyActivity = listOf(
            V25DailyActivity(LocalDate.parse("2026-08-10"), 5),
            V25DailyActivity(LocalDate.parse("2026-08-14"), 8),
        ),
        weeklyTotalRatings = 13,
        weeklyChangeRate = null,
        weeklyGoal = 350,
        weeklyGoalCompleted = 13,
        weeklyGoalRate = 0.04f,
        recallAccuracy = 0.8f,
        firstAttemptAccuracy = 0.7f,
        retentionRate = 0.9f,
        streakDays = 3,
        masteredCards = 4,
        progress = listOf(
            V25ProgressSummary(
                scopeId = "project-1",
                scopeName = "概率论",
                isProject = true,
                cardCount = 12,
                newCount = 5,
                learnedCount = 7,
                dueCount = 3,
                masteredCount = 4,
                masteryRatio = 0.33f,
            ),
        ),
        updatedAt = now,
    )

    override suspend fun getAuthUser(): V25Result<V25AuthUser> = V25Result.Success(authUser)

    override suspend fun updateAuthUser(username: String?, avatarKey: V25AvatarKey?): V25Result<V25AuthUser> =
        V25Result.Success(
            authUser.copy(
                username = username ?: authUser.username,
                avatarKey = avatarKey ?: authUser.avatarKey,
            ),
        )

    override suspend fun logout(): V25Result<Unit> = V25Result.Success(Unit)

    override suspend fun getPreferences(): V25Result<V25UserPreferences> = V25Result.Success(preferences)

    override suspend fun updatePreferences(patch: V25PreferencesPatch): V25Result<V25UserPreferences> =
        V25Result.Success(
            preferences.copy(
                defaultCoverageMode = patch.defaultCoverageMode ?: preferences.defaultCoverageMode,
                difficultyRatio = patch.difficultyRatio ?: preferences.difficultyRatio,
                dailyLearningGoal = patch.dailyLearningGoal ?: preferences.dailyLearningGoal,
                learningTimezone = patch.learningTimezone ?: preferences.learningTimezone,
                updatedAt = now,
            ),
        )

    override suspend fun setCurrentProject(projectId: String?): V25Result<V25UserPreferences> =
        V25Result.Success(preferences.copy(currentProjectId = projectId, updatedAt = now))

    override suspend fun createProject(name: String, idempotencyKey: String?): V25Result<V25LearningProject> =
        V25Result.Success(
            V25LearningProject(
                projectId = "project-new",
                name = name.trim(),
                materials = emptyList(),
                status = V25ProjectStatus.EMPTY,
                chapterCount = 0,
                deckCount = 0,
                taskCount = 0,
                createdAt = now,
                updatedAt = now,
                version = 1,
            ),
        )

    override suspend fun addProjectMaterialPdf(
        projectId: String,
        fileName: String,
        content: java.io.InputStream,
        idempotencyKey: String?,
    ): V25Result<V25Material> = V25Result.Success(
        V25Material(
            materialId = "material-new",
            projectId = projectId,
            type = V25MaterialType.PDF,
            name = fileName,
            status = V25MaterialStatus.PENDING,
            createdAt = now,
        ),
    )

    override suspend fun addProjectMaterialText(
        projectId: String,
        name: String,
        content: String,
        idempotencyKey: String?,
    ): V25Result<V25Material> = V25Result.Success(
        V25Material(
            materialId = "material-text",
            projectId = projectId,
            type = V25MaterialType.TEXT,
            name = name,
            status = V25MaterialStatus.READY,
            charCount = content.length,
            chapter = V25Chapter("chapter-text", "material-text", name, startPage = null, endPage = null),
            createdAt = now,
        ),
    )

    override suspend fun listProjectMaterials(projectId: String): V25Result<List<V25Material>> =
        V25Result.Success(listOf(material))

    override suspend fun deleteProjectMaterial(
        projectId: String,
        materialId: String,
        retainCards: Boolean,
        idempotencyKey: String?,
    ): V25Result<V25LearningProject> =
        if (deletedMaterialIds.add(materialId)) {
            V25Result.Success(project.copy(materials = emptyList(), status = V25ProjectStatus.EMPTY))
        } else {
            V25Result.Failure(V25ErrorCodes.MATERIAL_NOT_FOUND, "material.not_found", "资料不存在")
        }

    /** Models the server's one-time delete: a second delete of the same id is a 404. */
    private val deletedMaterialIds = mutableSetOf<String>()

    override suspend fun replaceProjectMaterialPdf(
        projectId: String,
        materialId: String,
        fileName: String,
        content: java.io.InputStream,
        idempotencyKey: String?,
    ): V25Result<V25Material> = V25Result.Success(
        material.copy(materialId = materialId, name = fileName, status = V25MaterialStatus.PENDING),
    )

    override suspend fun listProjects(forceRefresh: Boolean): V25Result<List<V25LearningProject>> = V25Result.Success(emptyList())

    override suspend fun getProject(projectId: String, forceRefresh: Boolean): V25Result<V25LearningProject> = V25Result.Success(project)

    override suspend fun renameProject(projectId: String, name: String): V25Result<V25LearningProject> =
        V25Result.Success(project.copy(name = name.trim(), version = project.version + 1))

    override suspend fun deleteProject(
        projectId: String,
        retainDecks: Boolean,
        idempotencyKey: String?,
    ): V25Result<Unit> =
        V25Result.Failure(V25ErrorCodes.PROJECT_HAS_ACTIVE_TASK, "project.delete.blocked", "存在正式生成中的任务")

    override suspend fun updateChapter(
        projectId: String,
        chapterId: String,
        edit: V25ChapterEdit,
    ): V25Result<V25Chapter> = V25Result.Success(chapter.copy(name = edit.name, startPage = edit.startPage, endPage = edit.endPage))

    override suspend fun deleteChapter(projectId: String, chapterId: String, deleteCards: Boolean): V25Result<Unit> =
        V25Result.Failure(V25ErrorCodes.PROJECT_STATE_CONFLICT, "chapter.delete.blocked", "章节被进行中的任务引用")

    override suspend fun confirmChapters(projectId: String): V25Result<V25LearningProject> = V25Result.Success(project)

    override suspend fun getStudySettings(projectId: String): V25Result<V25ProjectStudySettings> = V25Result.Success(
        V25ProjectStudySettings("project-1", listOf("chapter-1"), includeUnassigned = true, updatedAt = now),
    )

    override suspend fun updateStudySettings(
        projectId: String,
        patch: V25StudySettingsPatch,
    ): V25Result<V25ProjectStudySettings> = V25Result.Success(
        V25ProjectStudySettings(
            projectId,
            patch.selectedNewCardChapterIds ?: emptyList(),
            patch.includeUnassigned ?: false,
            now,
        ),
    )

    override suspend fun createTask(
        projectId: String,
        deckId: String,
        chapterIds: List<String>,
        config: V25GenerationConfig,
    ): V25Result<V25GenerationTask> =
        V25Result.Success(task.copy(taskId = "task-new", status = V25TaskStatus.DRAFT, generatedCardCount = 0))

    override suspend fun listTasks(projectId: String?, status: V25TaskStatus?): V25Result<List<V25GenerationTask>> =
        V25Result.Success(listOf(task))

    override suspend fun getTask(taskId: String): V25Result<V25GenerationTask> = V25Result.Success(task)

    override suspend fun updateTaskConfig(
        taskId: String,
        patch: V25TaskConfigPatch,
    ): V25Result<V25GenerationTask> = V25Result.Success(
        task.copy(
            generationConfig = patch.generationConfig ?: task.generationConfig,
            sampleCards = emptyList(),
            sampleConfigHash = null,
            sampleConfirmedAt = null,
        ),
    )

    override suspend fun generateSamples(taskId: String): V25Result<List<V25SampleCard>> =
        V25Result.Success(task.sampleCards)

    override suspend fun startTask(taskId: String): V25Result<V25GenerationTask> =
        V25Result.Failure(V25ErrorCodes.SAMPLE_STALE, "task.sample.stale", "生成配置已修改，请重新生成样卡")

    override suspend fun abandonTask(taskId: String): V25Result<V25GenerationTask> =
        V25Result.Success(task.copy(status = V25TaskStatus.ABANDONED, endedAt = now))

    override suspend fun retryTask(taskId: String): V25Result<V25GenerationTask> =
        V25Result.Success(task.copy(taskId = "task-retry", retryOfTaskId = taskId, status = V25TaskStatus.DRAFT))

    override suspend fun deleteTask(taskId: String, deleteGeneratedCards: Boolean): V25Result<Unit> =
        V25Result.Success(Unit)

    override suspend fun listDecks(projectId: String?): V25Result<List<V25Deck>> = V25Result.Success(listOf(deck))

    override suspend fun createDeck(name: String, projectId: String?, idempotencyKey: String?): V25Result<V25Deck> =
        V25Result.Success(deck.copy(deckId = "deck-new", name = name, projectId = projectId))

    override suspend fun getDeck(deckId: String): V25Result<V25Deck> = V25Result.Success(deck)

    override suspend fun renameDeck(deckId: String, name: String): V25Result<V25Deck> =
        V25Result.Success(deck.copy(name = name))

    override suspend fun deleteDeck(deckId: String, idempotencyKey: String?): V25Result<Unit> = V25Result.Success(Unit)

    override suspend fun listCards(deckId: String, filter: V25BrowseFilter): V25Result<List<V25Card>> {
        val difficulty = filter.contentDifficulty?.let { content ->
            V25Difficulty.entries.firstOrNull { it.name == content.name }
        }
        return V25Result.Success(listOf(card).filter { difficulty == null || it.targetDifficulty == difficulty })
    }

    override suspend fun importCards(deckId: String, drafts: List<V25CardDraft>, idempotencyKey: String?): V25Result<List<V25ImportResult>> =
        V25Result.Success(
            drafts.mapIndexed { index, _ ->
                V25ImportResult(index = index, status = V25ImportStatus.CREATED, cardId = "card-new-$index")
            },
        )

    override suspend fun updateCard(cardId: String, front: String, back: String): V25Result<V25Card> =
        V25Result.Success(card.copy(front = front, back = back, version = card.version + 1))

    override suspend fun deleteCard(cardId: String): V25Result<V25CardDeletionBatch> = V25Result.Success(
        V25CardDeletionBatch(
            deleteBatchId = "batch-1",
            cardIds = listOf(cardId),
            undoUntil = now.plusSeconds(10),
            status = V25DeletionBatchStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        ),
    )

    override suspend fun pendingDeletionBatches(): V25Result<List<V25CardDeletionBatch>> = V25Result.Success(emptyList())

    override suspend fun undoDeletionBatch(deleteBatchId: String): V25Result<Unit> =
        V25Result.Failure(V25ErrorCodes.CARD_DELETE_WINDOW_EXPIRED, "card.delete.window.expired", "撤销窗口已过")

    override suspend fun createRewritePreview(
        cardId: String,
        customRequirements: String?,
    ): V25Result<V25CardRewritePreview> = V25Result.Success(
        V25CardRewritePreview(
            rewriteId = "rewrite-1",
            cardId = cardId,
            baseCardVersion = "3",
            front = "新正面",
            back = "新背面",
            cardType = V25CardType.QUESTION,
            targetDifficulty = V25Difficulty.BASIC,
            customRequirements = customRequirements,
            status = V25RewriteStatus.PENDING,
            expiresAt = now.plusSeconds(86_400),
        ),
    )

    override suspend fun applyRewritePreview(cardId: String, rewriteId: String): V25Result<V25Card> =
        V25Result.Failure(V25ErrorCodes.CARD_VERSION_CONFLICT, "card.version.conflict", "卡片已被修改")

    override suspend fun cancelRewritePreview(cardId: String, rewriteId: String): V25Result<Unit> =
        V25Result.Success(Unit)

    override suspend fun todayPlan(): V25Result<V25TodayPlan> = V25Result.Success(
        V25TodayPlan(
            learningTimezone = "Asia/Shanghai",
            studyDate = LocalDate.parse("2026-08-15"),
            currentProject = null,
            dailyGoal = 50,
            completedCount = 0,
            dueCount = 0,
            planRemaining = 0,
            backlogCount = 0,
            cards = emptyList(),
        ),
    )

    override suspend fun deckReviewQueue(deckId: String): V25Result<List<V25ReviewCard>> =
        V25Result.Success(emptyList())

    override suspend fun rateCard(
        cardId: String,
        rating: V25Rating,
        clientEventId: String?,
        idempotencyKey: String?,
    ): V25Result<V25RatingResult> =
        V25Result.Failure(V25ErrorCodes.NETWORK_UNAVAILABLE, "network.unavailable", null)

    override suspend fun statsDashboard(): V25Result<V25StatsDashboard> = V25Result.Success(stats)

    override suspend fun apiKeyStatus(): V25Result<V25ApiKeyStatus> =
        V25Result.Success(V25ApiKeyStatus(V25ApiKeyState.AVAILABLE, "sk-****1234"))

    override suspend fun saveApiKey(apiKey: String): V25Result<V25ApiKeyStatus> =
        V25Result.Success(V25ApiKeyStatus(V25ApiKeyState.AVAILABLE, "sk-****1234"))

    // Not exercised by this suite; bodies mirror the old interface defaults.
    override suspend fun projectProgress(projectId: String): V25Result<V25ProgressSummary> =
        V25Result.Failure(V25ErrorCodes.INVALID_RESPONSE, message = "项目进度暂不可用")

    override suspend fun getProjectDeletionPreflight(
        projectId: String,
        retainDecks: Boolean,
        allowCancel: Boolean,
    ): V25Result<V25DeletionPreflight> =
        V25Result.Failure(V25ErrorCodes.INVALID_RESPONSE, message = "删除预检暂不可用")

    override suspend fun attachDeckToProject(
        projectId: String,
        deckId: String,
        idempotencyKey: String?,
    ): V25Result<V25Deck> = V25Result.Failure(V25ErrorCodes.INVALID_RESPONSE, message = "归入项目暂不可用")

    override suspend fun getDeckDeletionPreflight(
        deckId: String,
        allowCancel: Boolean,
    ): V25Result<V25DeletionPreflight> =
        V25Result.Failure(V25ErrorCodes.INVALID_RESPONSE, message = "删除预检暂不可用")

    override suspend fun getStudyPlan(): V25Result<V25StudyPlan> =
        V25Result.Failure(V25ErrorCodes.INVALID_RESPONSE, message = "学习计划暂不可用")

    override suspend fun updateStudyPlan(
        plan: V25StudyPlanUpdate,
        idempotencyKey: String?,
    ): V25Result<V25StudyPlan> =
        V25Result.Failure(V25ErrorCodes.INVALID_RESPONSE, message = "学习计划暂不可用")

    override suspend fun studyPlanBacklog(offset: Int, limit: Int): V25Result<List<V25PlanCard>> =
        V25Result.Failure(V25ErrorCodes.INVALID_RESPONSE, message = "积压卡片暂不可用")
}
