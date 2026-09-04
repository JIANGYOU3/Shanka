package com.qiuzhao.flashcards.data.remote.v25

import com.qiuzhao.flashcards.data.remote.http.ErrorEnvelope
import com.qiuzhao.flashcards.data.remote.http.NetworkStack
import com.qiuzhao.flashcards.data.session.InMemorySessionStore
import com.qiuzhao.flashcards.domain.v25.V25ApiKeyState
import com.qiuzhao.flashcards.domain.v25.V25AvatarKey
import com.qiuzhao.flashcards.domain.v25.V25BrowseOrder
import com.qiuzhao.flashcards.domain.v25.V25CardDraft
import com.qiuzhao.flashcards.domain.v25.V25CardType
import com.qiuzhao.flashcards.domain.v25.V25ContentDifficulty
import com.qiuzhao.flashcards.domain.v25.V25CoverageMode
import com.qiuzhao.flashcards.domain.v25.V25DeletionBatchStatus
import com.qiuzhao.flashcards.domain.v25.V25Difficulty
import com.qiuzhao.flashcards.domain.v25.V25ImportStatus
import com.qiuzhao.flashcards.domain.v25.V25InternalStage
import com.qiuzhao.flashcards.domain.v25.V25MasteryFilter
import com.qiuzhao.flashcards.domain.v25.V25ProjectStatus
import com.qiuzhao.flashcards.domain.v25.V25PublicationState
import com.qiuzhao.flashcards.domain.v25.V25Rating
import com.qiuzhao.flashcards.domain.v25.V25RewriteStatus
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serialization fixtures taken verbatim from the committed backend OpenAPI examples
 * (openapi.yaml schemas + structure-contract.md resource models): one fixture per payload
 * family the V2.5 boundary consumes, including the failure envelope. These tests lock the
 * wire-to-domain mapping behind the kotlinx-serialization DTO layer — enum names, snake_case
 * fields, ISO timestamps, nullable fields, unknown-field tolerance — so a backend contract
 * change breaks this file before it reaches a single UI.
 */
class V25SerializationTest {

    /** The production Json configuration (snake_case, unknown keys tolerated, nulls explicit). */
    private val json: Json = NetworkStack(InMemorySessionStore(), baseUrlOverride = "http://localhost/").json

    private inline fun <reified T> decode(body: String): T = json.decodeFromString(body)

    @Test
    fun `auth user payload maps to the typed profile`() {
        val user = decode<AuthUserResponse>(meBody()).user.toDomain()

        assertEquals("u-1", user.userId)
        assertEquals("alice", user.username)
        assertEquals("alice@example.com", user.email)
        assertEquals(V25AvatarKey.mood_03, user.avatarKey)
        assertEquals(Instant.parse("2026-08-14T09:00:00Z"), user.createdAt)
    }

    @Test
    fun `preferences payload maps ratios goal timezone and nullable current project`() {
        val prefs = decode<PreferencesDto>(preferencesBody()).toDomain()

        assertEquals(V25CoverageMode.BALANCED, prefs.defaultCoverageMode)
        assertEquals(40, prefs.difficultyRatio.basic)
        assertEquals(40, prefs.difficultyRatio.understanding)
        assertEquals(20, prefs.difficultyRatio.deepQuestion)
        assertEquals(50, prefs.dailyLearningGoal)
        assertEquals("Asia/Shanghai", prefs.learningTimezone)
        assertNull(prefs.currentProjectId)
        assertEquals(Instant.parse("2026-08-14T09:00:00Z"), prefs.updatedAt)
    }

    @Test
    fun `preferences payload keeps a set current project id`() {
        val prefs = decode<PreferencesDto>(preferencesBody().replace("\"current_project_id\": null", "\"current_project_id\": \"p-1\"")).toDomain()

        assertEquals("p-1", prefs.currentProjectId)
    }

    @Test
    fun `empty items payload is an empty list not a failure`() {
        val items = decode<ItemsResponse<DeckDto>>("""{"items": []}""").items

        assertTrue(items.isEmpty())
    }

    @Test
    fun `project payload maps materials chapters and derived counts`() {
        val project = decode<ProjectDto>(projectBody()).toDomain()

        assertEquals("p-1", project.projectId)
        assertEquals("线性代数", project.name)
        assertEquals(V25ProjectStatus.READY, project.status)
        assertEquals(1, project.chapterCount)
        assertEquals(2, project.deckCount)
        assertEquals(3, project.taskCount)
        assertEquals(1, project.materials.size)
        val material = project.materials[0]
        assertEquals("m-1", material.materialId)
        assertEquals("p-1", material.projectId)
        assertEquals(com.qiuzhao.flashcards.domain.v25.V25MaterialType.PDF, material.type)
        assertEquals("linear.pdf", material.name)
        assertEquals(com.qiuzhao.flashcards.domain.v25.V25MaterialStatus.PARSED, material.status)
        assertEquals(1048576L, material.sizeBytes)
        assertNull(material.charCount)
        assertNull(material.chapter)
        assertEquals(Instant.parse("2026-08-14T09:00:00Z"), material.createdAt)
        // Detail-only chapter summary across materials, each owned by its material.
        assertEquals(1, project.chapters.size)
        assertEquals("c-1", project.chapters[0].id)
        assertEquals("m-1", project.chapters[0].materialId)
        assertEquals("第一章 矩阵", project.chapters[0].name)
        assertEquals(1, project.chapters[0].startPage)
        assertEquals(20, project.chapters[0].endPage)
        assertEquals(Instant.parse("2026-08-14T09:00:00Z"), project.createdAt)
        assertEquals(Instant.parse("2026-08-14T10:00:00Z"), project.updatedAt)
    }

    @Test
    fun `empty project payload maps the EMPTY aggregate with no materials`() {
        val project = decode<ProjectDto>(emptyProjectBody()).toDomain()

        assertEquals(V25ProjectStatus.EMPTY, project.status)
        assertTrue(project.materials.isEmpty())
        assertEquals(0, project.chapterCount)
    }

    @Test
    fun `text material payload maps the ready status and the null-page chapter`() {
        val material = decode<MaterialDto>(textMaterialBody()).toDomain()

        assertEquals(com.qiuzhao.flashcards.domain.v25.V25MaterialType.TEXT, material.type)
        assertEquals(com.qiuzhao.flashcards.domain.v25.V25MaterialStatus.READY, material.status)
        assertEquals(30_000, material.charCount)
        assertNull("TEXT chapters carry no page span (structure-contract 3.2a)", material.chapter?.startPage)
        assertNull(material.chapter?.endPage)
        assertEquals("m-text", material.chapter?.materialId)
        assertNull(material.chapter!!.pageSpanLabel)
    }

    @Test
    fun `material payload maps pdf failure codes and nullable metadata`() {
        val material = decode<MaterialDto>(materialBody()
            .replace("\"status\": \"PENDING\"", "\"status\": \"FAILED\"")
            .replace("\"error_code\": null", "\"error_code\": \"PDF_PARSE_FAILED\"")
            .replace("\"size_bytes\": 1048576", "\"size_bytes\": null")).toDomain()

        assertEquals(com.qiuzhao.flashcards.domain.v25.V25MaterialStatus.FAILED, material.status)
        assertEquals("PDF_PARSE_FAILED", material.errorCode)
        assertNull(material.sizeBytes)
        assertNull(material.charCount)
    }

    @Test
    fun `project payload maps the parsing status without chapters`() {
        val project = decode<ProjectDto>(projectBody(parsing = true)).toDomain()

        assertEquals(V25ProjectStatus.PARSING, project.status)
        assertTrue(project.chapters.isEmpty())
        assertEquals(com.qiuzhao.flashcards.domain.v25.V25MaterialStatus.PARSING, project.materials[0].status)
    }

    @Test
    fun `project payload tolerates unknown added fields`() {
        // The backend can add fields; the client must not break before a coordinated upgrade.
        val project = decode<ProjectDto>(projectBody().replace("\"version\":", "\"future_field\": {\"x\": 1}, \"version\":")).toDomain()

        assertEquals("p-1", project.projectId)
    }

    @Test
    fun `task payload maps status stage config and nullables`() {
        val task = decode<TaskDto>(taskBody()).toDomain()

        assertEquals("t-1", task.taskId)
        assertEquals("p-1", task.projectId)
        assertEquals("f-1", task.fileId)
        assertEquals("d-1", task.deckId)
        assertNull(task.retryOfTaskId)
        assertEquals(V25TaskStatus.GENERATING, task.status)
        assertEquals(V25InternalStage.SCORING, task.internalStage)
        assertEquals(1, task.selectedChapters.size)
        assertEquals(V25CoverageMode.EXTENSIVE, task.generationConfig.coverageMode)
        assertEquals(40, task.generationConfig.difficultyRatio.basic)
        assertEquals("", task.generationConfig.customRequirements)
        assertTrue(task.sampleCards.isEmpty())
        assertNull(task.sampleConfigHash)
        assertNull(task.sampleConfirmedAt)
        assertEquals(3, task.generatedCardCount)
        assertNull(task.errorCode)
        assertNull(task.failureStage)
        assertEquals(Instant.parse("2026-08-14T09:01:00Z"), task.startedAt)
        assertNull(task.endedAt)
    }

    @Test
    fun `task payload maps confirmed samples per difficulty tier`() {
        val task = decode<TaskDto>(taskBody()
            .replace("\"sample_cards\": null", """ "sample_cards": [{"card_id": "s-1", "front": "什么是矩阵？", "back": "数表", "card_type": "QUESTION", "target_difficulty": "BASIC"}] """)
            .replace("\"status\": \"GENERATING\"", "\"status\": \"AWAITING_SAMPLE_CONFIRMATION\"")
            .replace("\"internal_stage\": \"SCORING\"", "\"internal_stage\": null")
            .replace("\"sample_config_hash\": null", "\"sample_config_hash\": \"abc123\"")
            .replace("\"sample_confirmed_at\": null", "\"sample_confirmed_at\": \"2026-08-14T09:02:00Z\"")).toDomain()

        assertEquals(V25TaskStatus.AWAITING_SAMPLE_CONFIRMATION, task.status)
        assertNull(task.internalStage)
        assertEquals(1, task.sampleCards.size)
        assertEquals("什么是矩阵？", task.sampleCards[0].front)
        assertEquals(V25Difficulty.BASIC, task.sampleCards[0].targetDifficulty)
        assertEquals(V25CardType.QUESTION, task.sampleCards[0].cardType)
        assertEquals("abc123", task.sampleConfigHash)
        assertEquals(Instant.parse("2026-08-14T09:02:00Z"), task.sampleConfirmedAt)
    }

    @Test
    fun `deletion preflight maps blockers actions and impact`() {
        val preflight = decode<DeletionPreflightDto>(deletionPreflightBody()).toDomain()

        assertEquals("PROJECT", preflight.resourceType)
        assertEquals("p-1", preflight.resourceId)
        assertFalse(preflight.canDelete)
        assertEquals(listOf("t-draft"), preflight.abandonableTaskIds)
        assertTrue(preflight.hasUncancellableTasks)
        assertEquals(listOf("t-draft", "t-generating"), preflight.cancelableTaskIds)
        assertTrue(preflight.canCancel)
        assertEquals(
            listOf("CANCEL_AND_DELETE", "ABANDON_AND_RETRY", "WAIT_FOR_TERMINAL", "VIEW_TASKS"),
            preflight.actions,
        )
        assertEquals(2, preflight.blockers.size)
        assertEquals(V25TaskStatus.DRAFT, preflight.blockers[0].status)
        assertTrue(preflight.blockers[0].canAbandon)
        assertTrue(preflight.blockers[0].canCancel)
        assertEquals(V25TaskStatus.GENERATING, preflight.blockers[1].status)
        assertFalse(preflight.blockers[1].canAbandon)
        assertTrue(preflight.blockers[1].canCancel)
        assertEquals(true, preflight.impact.retainDecks)
        assertEquals(2, preflight.impact.deckCount)
        assertEquals(3, preflight.impact.cardCount)
        assertEquals(V25ProjectStatus.READY, preflight.impact.projectStatus)
    }

    @Test
    fun `deck payload maps counts and mastery ratio`() {
        val deck = decode<DeckDto>(deckBody()).toDomain()

        assertEquals("d-1", deck.deckId)
        assertEquals("线性代数", deck.name)
        assertEquals("p-1", deck.projectId)
        assertEquals(42, deck.cardCount)
        assertEquals(7, deck.dueCount)
        assertEquals(12, deck.masteredCards)
        assertEquals(30, deck.reviewCount)
        assertEquals(12f / 42f, deck.masteryRatio!!, 0.0001f)
    }

    @Test
    fun `deck payload with zero cards keeps a zero ratio`() {
        val deck = decode<DeckDto>(deckBody()
            .replace("\"card_count\": 42", "\"card_count\": 0")
            .replace("\"due_count\": 7", "\"due_count\": 0")
            .replace("\"mastered_card_count\": 12", "\"mastered_card_count\": 0")
            .replace("\"review_count\": 30", "\"review_count\": 0")
            .replace("\"mastery_ratio\": 0.2857143", "\"mastery_ratio\": 0")).toDomain()

        assertEquals(0f, deck.masteryRatio!!, 0.0001f)
    }

    @Test
    fun `deck payload with a null project is an independent deck`() {
        val deck = decode<DeckDto>(deckBody().replace("\"project_id\": \"p-1\"", "\"project_id\": null")).toDomain()

        assertNull(deck.projectId)
    }

    @Test
    fun `card payload maps position chapter source task and state`() {
        val card = decode<CardDto>(cardBody()).toCard()

        assertEquals("c-1", card.cardId)
        assertEquals("d-1", card.deckId)
        assertEquals("什么是矩阵？", card.front)
        assertEquals("按行列排列的数表", card.back)
        assertEquals(V25CardType.QUESTION, card.cardType)
        assertEquals(V25Difficulty.BASIC, card.targetDifficulty)
        assertEquals(3, card.position)
        assertEquals("ch-1", card.chapterId)
        assertEquals("t-1", card.sourceTaskId)
        assertEquals(V25PublicationState.PUBLISHED, card.publicationState)
        assertEquals(3, card.version)
    }

    @Test
    fun `card payload without publication state defaults to PUBLISHED`() {
        val card = decode<CardDto>(cardBody().replace("\"publication_state\": \"PUBLISHED\", ", "")).toCard()

        assertEquals(V25PublicationState.PUBLISHED, card.publicationState)
    }

    @Test
    fun `card version parses vN and iso timestamp wire values`() {
        assertEquals(3, parseVersion("v3"))
        assertEquals(0, parseVersion(null))
        // V2.5 writes version=now (ISO 8601 UTC string); the boundary models it as an Int
        // marker that only serves cache-refresh change detection (updated_at is authoritative).
        assertEquals(
            Instant.parse("2026-08-14T10:00:00Z").epochSecond.toInt(),
            parseVersion("2026-08-14T10:00:00Z"),
        )
        assertEquals(0, parseVersion("garbage"))
    }

    @Test
    fun `import response maps per-index results with status and nullable card id`() {
        val results = decode<ImportResponse>(importResponseBody()).results.map { it.toDomain() }

        assertEquals(2, results.size)
        assertEquals(0, results[0].index)
        assertEquals(V25ImportStatus.CREATED, results[0].status)
        assertEquals("c-1", results[0].cardId)
        assertEquals(1, results[1].index)
        assertEquals(V25ImportStatus.FAILED, results[1].status)
        assertNull(results[1].cardId)
    }

    @Test
    fun `import response rejects a missing results array`() {
        assertThrows(SerializationException::class.java) {
            decode<ImportResponse>("""{"unexpected": true}""")
        }
    }

    @Test
    fun `cards import body serializes every draft front and back`() {
        val parsed = json.decodeFromString(
            CardsImportRequest.serializer(),
            json.encodeToString(
                CardsImportRequest.serializer(),
                CardsImportRequest(
                    listOf(V25CardDraft("正面一", "背面一"), V25CardDraft("正面二", "背面二"))
                        .map { CardDraftDto(it.front, it.back) },
                ),
            ),
        )

        assertEquals(2, parsed.cards.size)
        assertEquals("正面一", parsed.cards[0].front)
        assertEquals("背面一", parsed.cards[0].back)
        assertEquals("正面二", parsed.cards[1].front)
    }

    @Test
    fun `rating body reuses the caller client event id on retry`() {
        val first = json.decodeFromString(
            ReviewEventRequest.serializer(),
            json.encodeToString(
                ReviewEventRequest.serializer(),
                ReviewEventRequest("c-1", V25Rating.GOOD.name, UUID.randomUUID().toString()),
            ),
        )
        assertEquals("c-1", first.cardId)
        assertEquals("GOOD", first.rating)
        assertTrue(first.clientEventId.isNotBlank())

        val retry = json.decodeFromString(
            ReviewEventRequest.serializer(),
            json.encodeToString(ReviewEventRequest.serializer(), ReviewEventRequest("c-1", "GOOD", "event-1")),
        )
        assertEquals("event-1", retry.clientEventId)
    }

    @Test
    fun `validation error envelope maps to a coded failure`() {
        val error = ErrorEnvelope.parse(validationErrorBody())!!

        assertEquals("VALIDATION_ERROR", error.code)
        assertEquals("error.validation", error.localizationKey)
        assertEquals("比例合计必须为 100", error.message)
    }

    @Test
    fun `expired auth envelope maps to the auth failure code`() {
        val error = ErrorEnvelope.parse(expiredAuthBody())!!

        assertEquals("AUTH_INVALID", error.code)
        assertEquals("auth.invalid", error.localizationKey)
    }

    @Test
    fun `conflict envelope passes the stable code through`() {
        val error = ErrorEnvelope.parse(conflictBody())!!

        assertEquals("CARD_VERSION_CONFLICT", error.code)
    }

    @Test
    fun `error envelope without an error object yields no code`() {
        assertNull(ErrorEnvelope.parse("""{"detail": "not the contract shape"}"""))
    }

    @Test
    fun `deletion batch payload maps the undo window`() {
        val batch = decode<DeletionBatchDto>(deletionBatchBody()).toDomain()

        assertEquals("b-1", batch.deleteBatchId)
        assertEquals(listOf("c-1", "c-2"), batch.cardIds)
        assertEquals(Instant.parse("2026-08-15T09:00:10Z"), batch.undoUntil)
        assertEquals(V25DeletionBatchStatus.PENDING, batch.status)
        assertEquals(Instant.parse("2026-08-15T09:00:00Z"), batch.createdAt)
    }

    @Test
    fun `rewrite preview payload maps the two-stage contract`() {
        val preview = decode<RewritePreviewDto>(rewritePreviewBody()).toDomain()

        assertEquals("r-1", preview.rewriteId)
        assertEquals("c-1", preview.cardId)
        assertEquals("v3", preview.baseCardVersion)
        assertEquals("矩阵的秩是什么？", preview.front)
        assertEquals(V25CardType.QUESTION, preview.cardType)
        assertEquals(V25Difficulty.UNDERSTANDING, preview.targetDifficulty)
        assertEquals("强调直觉", preview.customRequirements)
        assertEquals(V25RewriteStatus.PENDING, preview.status)
        assertEquals(Instant.parse("2026-08-16T09:00:00Z"), preview.expiresAt)
    }

    @Test
    fun `stats dashboard maps weekly buckets goal and nullable rates`() {
        val stats = decode<DashboardDto>(statsBody()).toDomain()

        assertTrue(stats.hasData)
        assertEquals(7, stats.weeklyActivity.size)
        assertEquals(5, stats.weeklyActivity[0].ratingCount)
        assertEquals(1, stats.weeklyActivity[6].ratingCount)
        assertEquals(21, stats.weeklyTotalRatings)
        assertEquals(7, stats.weeklyGoalCompleted)
        assertNull(stats.weeklyChangeRate)
        assertEquals(350, stats.weeklyGoal)
        assertEquals(0.02f, stats.weeklyGoalRate!!, 0.0001f)
        assertEquals(0.8f, stats.recallAccuracy!!, 0.0001f)
        assertEquals(0.75f, stats.firstAttemptAccuracy!!, 0.0001f)
        assertEquals(0.82f, stats.retentionRate!!, 0.0001f)
        assertEquals(3, stats.streakDays)
        assertEquals(12, stats.masteredCards)
        assertEquals(Instant.parse("2026-08-15T12:00:00Z"), stats.updatedAt)
        // The dashboard wire carries no per-scope progress summaries yet (V25-STATS-FR-05);
        // the boundary keeps the honest empty list instead of fabricating numbers.
        assertTrue(stats.progress.isEmpty())
    }

    @Test
    fun `stats activity dates derive from the timezone aware week start`() {
        val stats = decode<DashboardDto>(statsBody()).toDomain()

        // The wire start is UTC ("...T16:00:00.000Z"): it must be projected through the
        // dashboard's timezone field (Asia/Shanghai) to land on the account-local Monday
        // 2026-08-10; projecting the raw UTC instant instead would yield 2026-08-09.
        assertEquals(LocalDate.parse("2026-08-10"), stats.weeklyActivity[0].studyDate)
        assertEquals(LocalDate.parse("2026-08-16"), stats.weeklyActivity[6].studyDate)
    }

    @Test
    fun `stats activity dates hold for a UTC bucketing timezone`() {
        val stats = decode<DashboardDto>(statsBody()
            .replace("2026-08-09T16:00:00.000Z", "2026-08-10T00:00:00.000Z")
            .replace("2026-08-15T15:59:59.000Z", "2026-08-16T23:59:59.000Z")
            .replace("\"timezone\": \"Asia/Shanghai\"", "\"timezone\": \"UTC\"")).toDomain()

        assertEquals(LocalDate.parse("2026-08-10"), stats.weeklyActivity[0].studyDate)
        assertEquals(LocalDate.parse("2026-08-16"), stats.weeklyActivity[6].studyDate)
    }

    @Test
    fun `stats dashboard empty state maps without fabrication`() {
        val stats = decode<DashboardDto>(statsBody()
            .replace("\"has_data\": true", "\"has_data\": false")
            .replace("\"recall_accuracy\": 0.8", "\"recall_accuracy\": null")
            .replace("\"first_answer_accuracy\": 0.75", "\"first_answer_accuracy\": null")
            .replace("\"retention_rate\": 0.82", "\"retention_rate\": null")).toDomain()

        assertTrue(!stats.hasData)
        assertNull(stats.recallAccuracy)
        assertNull(stats.firstAttemptAccuracy)
        assertNull(stats.retentionRate)
    }

    @Test
    fun `today plan empty state has a null current project`() {
        val plan = decode<TodayPlanDto>(todayPlanEmptyBody()).toDomain()

        assertEquals("Asia/Shanghai", plan.learningTimezone)
        assertEquals(LocalDate.parse("2026-08-15"), plan.studyDate)
        assertNull(plan.currentProject)
        assertEquals(50, plan.dailyGoal)
        assertEquals(0, plan.completedCount)
        assertEquals(0, plan.dueCount)
        assertEquals(0, plan.planRemaining)
        assertEquals(0, plan.backlogCount)
        assertTrue(plan.cards.isEmpty())
    }

    @Test
    fun `plan card marks review-state NEW as isNew and keeps the summary project`() {
        val plan = decode<TodayPlanDto>(todayPlanBody()).toDomain()

        assertEquals("p-1", plan.currentProject!!.projectId)
        assertEquals("线性代数", plan.currentProject!!.name)
        assertEquals(2, plan.dueCount)
        assertEquals(47, plan.planRemaining)
        assertEquals(1, plan.cards.size)
        val planCard = plan.cards[0]
        assertEquals("c-1", planCard.card.cardId)
        assertTrue(planCard.isNew)
        assertEquals("NEW", planCard.reviewState!!.state)
        assertEquals(Instant.parse("2026-08-15T09:00:00Z"), planCard.reviewState!!.due)
    }

    @Test
    fun `plan card with a learned review state is not new`() {
        val plan = decode<TodayPlanDto>(todayPlanBody().replace("\"state\": \"NEW\"", "\"state\": \"REVIEW\"")).toDomain()

        assertTrue(!plan.cards[0].isNew)
    }

    @Test
    fun `review queue item maps the card and its fsrs state`() {
        val item = decode<CardDto>(reviewQueueItemBody()).toReviewCard()

        assertEquals("c-1", item.card.cardId)
        assertEquals("REVIEW", item.reviewState!!.state)
        assertEquals(Instant.parse("2026-08-20T09:00:00Z"), item.reviewState!!.due)
    }

    @Test
    fun `rating result maps the updated fsrs state and study date`() {
        val result = decode<RatingResultDto>(ratingResultBody()).toDomain()

        assertEquals("LEARNING", result.reviewState.state)
        assertEquals(Instant.parse("2026-08-15T09:10:00Z"), result.reviewState.due)
        assertEquals(LocalDate.parse("2026-08-15"), result.studyDate)
    }

    @Test
    fun `api key status maps UNKNOWN to UNSET with a null mask`() {
        val status = decode<ApiKeyStatusDto>(apiKeyUnknownBody()).toDomain()

        assertEquals(V25ApiKeyState.UNSET, status.state)
        assertNull(status.maskedKey)
    }

    @Test
    fun `api key status maps a masked key and available state`() {
        val status = decode<ApiKeyStatusDto>(apiKeyAvailableBody()).toDomain()

        assertEquals(V25ApiKeyState.AVAILABLE, status.state)
        assertEquals("sk-****1234", status.maskedKey)
    }

    @Test
    fun `malformed success payloads fail decode so the repository maps INVALID_RESPONSE`() {
        // Missing required project fields.
        assertThrows(SerializationException::class.java) {
            decode<ProjectDto>("""{"project_id": "p-1"}""")
        }
        // Unknown enum value: a contract violation, not a silent fallback.
        assertThrows(IllegalArgumentException::class.java) {
            decode<CardDto>(cardBody().replace("\"card_type\": \"QUESTION\"", "\"card_type\": \"ESSAY\"")).toCard()
        }
    }

    @Test
    fun `project creation and text material bodies match the request schemas`() {
        val created = json.decodeFromString(
            CreateProjectRequest.serializer(),
            json.encodeToString(CreateProjectRequest.serializer(), CreateProjectRequest("概率论")),
        )
        assertEquals("概率论", created.name)

        val text = json.decodeFromString(
            TextMaterialCreateRequest.serializer(),
            json.encodeToString(TextMaterialCreateRequest.serializer(), TextMaterialCreateRequest("课堂笔记", "第一章 绪论")),
        )
        assertEquals("课堂笔记", text.name)
        assertEquals("第一章 绪论", text.content)
    }

    @Test
    fun `generation config body matches the TaskCreateRequest schema`() {
        val request = TaskCreateRequest(
            deckId = "d-1",
            chapterIds = listOf("c-1", "c-2"),
            generationConfig = com.qiuzhao.flashcards.domain.v25.V25GenerationConfig(
                coverageMode = V25CoverageMode.BALANCED,
                difficultyRatio = com.qiuzhao.flashcards.domain.v25.V25DifficultyRatio(40, 40, 20),
                customRequirements = "多举例子",
            ).toWire(),
        )
        val parsed = json.decodeFromString(TaskCreateRequest.serializer(), json.encodeToString(TaskCreateRequest.serializer(), request))

        assertEquals("d-1", parsed.deckId)
        assertEquals(listOf("c-1", "c-2"), parsed.chapterIds)
        assertEquals("BALANCED", parsed.generationConfig.coverageMode)
        assertEquals(40, parsed.generationConfig.difficultyRatio.basic)
        assertEquals("多举例子", parsed.generationConfig.customRequirements)
    }

    @Test
    fun `browse filter values map to the lowercase wire parameter values`() {
        // The wire contract locks `order`/`mastery` to lowercase values; the filter enums
        // carry exactly those names and the Retrofit @Query mapping forwards them verbatim.
        assertEquals("random", V25BrowseOrder.random.name)
        assertEquals("position", V25BrowseOrder.position.name)
        assertEquals("all", V25MasteryFilter.all.name)
        assertEquals("mastered", V25MasteryFilter.mastered.name)
        assertEquals("unmastered", V25MasteryFilter.unmastered.name)
        assertEquals("UNLABELED", V25ContentDifficulty.UNLABELED.name)
    }

    @Test
    fun `sample list decode uses the shared serializer`() {
        val items = json.decodeFromString(
            ListSerializer(SampleCardDto.serializer()),
            """[{"card_id": "s-1", "front": "f", "back": "b", "card_type": "QUESTION", "target_difficulty": "BASIC"}]""",
        )
        assertEquals(1, items.size)
    }

    // --- fixtures (verbatim OpenAPI shapes) ---

    private fun meBody(): String = """
        {"user": {"user_id": "u-1", "username": "alice", "email": "alice@example.com",
                  "avatar_key": "mood_03", "created_at": "2026-08-14T09:00:00Z"}}
    """.trimIndent()

    private fun preferencesBody(): String = """
        {"default_coverage_mode": "BALANCED",
         "default_difficulty_ratio": {"basic": 40, "understanding": 40, "deep_question": 20},
         "daily_learning_goal": 50, "learning_timezone": "Asia/Shanghai",
         "current_project_id": null, "updated_at": "2026-08-14T09:00:00Z"}
    """.trimIndent()

    private fun projectBody(parsing: Boolean = false): String = if (parsing) {
        """
        {"project_id": "p-1", "name": "线性代数",
         "materials": [{"material_id": "m-1", "project_id": "p-1", "type": "PDF",
                        "name": "linear.pdf", "status": "PARSING", "error_code": null,
                        "size_bytes": 1048576, "char_count": null, "chapter": null,
                        "created_at": "2026-08-14T09:00:00Z"}],
         "status": "PARSING", "chapter_count": 0, "deck_count": 2, "task_count": 3,
         "created_at": "2026-08-14T09:00:00Z", "updated_at": "2026-08-14T10:00:00Z",
         "version": "2026-08-14T10:00:00Z"}
    """.trimIndent()
    } else {
        """
        {"project_id": "p-1", "name": "线性代数",
         "materials": [{"material_id": "m-1", "project_id": "p-1", "type": "PDF",
                        "name": "linear.pdf", "status": "PARSED", "error_code": null,
                        "size_bytes": 1048576, "char_count": null, "chapter": null,
                        "created_at": "2026-08-14T09:00:00Z"}],
         "chapters": [{"chapter_id": "c-1", "material_id": "m-1", "name": "第一章 矩阵",
                       "start_page": 1, "end_page": 20}],
         "status": "READY", "chapter_count": 1, "deck_count": 2, "task_count": 3,
         "created_at": "2026-08-14T09:00:00Z", "updated_at": "2026-08-14T10:00:00Z",
         "version": "2026-08-14T10:00:00Z"}
    """.trimIndent()
    }

    private fun emptyProjectBody(): String = """
        {"project_id": "p-2", "name": "空项目", "materials": [],
         "status": "EMPTY", "chapter_count": 0, "deck_count": 0, "task_count": 0,
         "created_at": "2026-08-14T09:00:00Z", "updated_at": "2026-08-14T09:00:00Z"}
    """.trimIndent()

    private fun materialBody(): String = """
        {"material_id": "m-1", "project_id": "p-1", "type": "PDF", "name": "linear.pdf",
         "status": "PENDING", "error_code": null, "size_bytes": 1048576, "char_count": null,
         "chapter": null, "created_at": "2026-08-14T09:00:00Z"}
    """.trimIndent()

    private fun textMaterialBody(): String = """
        {"material_id": "m-text", "project_id": "p-1", "type": "TEXT", "name": "课堂笔记",
         "status": "READY", "error_code": null, "size_bytes": null, "char_count": 30000,
         "chapter": {"chapter_id": "ch-text", "material_id": "m-text", "name": "课堂笔记",
                     "start_page": null, "end_page": null},
         "created_at": "2026-08-14T09:00:00Z"}
    """.trimIndent()

    private fun deletionPreflightBody(): String = """
        {"resource_type": "PROJECT", "resource_id": "p-1", "can_delete": false,
         "blockers": [
           {"task_id": "t-draft", "status": "DRAFT", "internal_stage": null,
            "project_id": "p-1", "deck_id": "d-1", "can_abandon": true,
            "can_cancel": true, "allowed_actions": ["ABANDON_AND_RETRY"]},
           {"task_id": "t-generating", "status": "GENERATING", "internal_stage": "PLANNING",
            "project_id": "p-1", "deck_id": "d-2", "can_abandon": false,
            "can_cancel": true, "allowed_actions": ["CANCEL_AND_DELETE", "WAIT_FOR_TERMINAL", "VIEW_TASKS"]}
         ],
         "abandonable_task_ids": ["t-draft"],
         "has_uncancellable_tasks": true,
         "cancelable_task_ids": ["t-draft", "t-generating"],
         "can_cancel": true,
         "actions": ["CANCEL_AND_DELETE", "ABANDON_AND_RETRY", "WAIT_FOR_TERMINAL", "VIEW_TASKS"],
         "impact": {"retain_decks": true, "deck_count": 2, "card_count": 3,
                    "task_count": 4, "project_status": "READY"}
        }
    """.trimIndent()

    private fun taskBody(): String = """
        {"task_id": "t-1", "project_id": "p-1", "file_id": "f-1", "deck_id": "d-1", "retry_of_task_id": null,
         "status": "GENERATING", "internal_stage": "SCORING",
         "selected_chapters": [{"chapter_id": "c-1", "material_id": "m-1", "name": "第一章", "start_page": 1, "end_page": 20}],
         "generation_config": {"coverage_mode": "EXTENSIVE",
                               "difficulty_ratio": {"basic": 40, "understanding": 40, "deep_question": 20},
                               "custom_requirements": null},
         "sample_cards": null, "sample_config_hash": null, "sample_confirmed_at": null,
         "generated_card_count": 3, "skipped_planning_group_count": 0, "resumable": false,
         "error_code": null, "failure_stage": null,
         "created_at": "2026-08-14T09:00:00Z", "started_at": "2026-08-14T09:01:00Z", "ended_at": null,
         "updated_at": "2026-08-14T09:05:00Z"}
    """.trimIndent()

    private fun deckBody(): String = """
        {"deck_id": "d-1", "name": "线性代数", "source": "GENERATED", "project_id": "p-1",
         "card_count": 42, "due_count": 7, "mastered_card_count": 12, "review_count": 30,
         "mastery_ratio": 0.2857143,
         "created_at": "2026-08-14T09:00:00Z", "updated_at": "2026-08-14T10:00:00Z", "version": "v2"}
    """.trimIndent()

    private fun cardBody(): String = """
        {"card_id": "c-1", "deck_id": "d-1", "source": "GENERATED", "position": 3,
         "front": "什么是矩阵？", "back": "按行列排列的数表",
         "card_type": "QUESTION", "target_difficulty": "BASIC",
         "chapter_id": "ch-1", "source_task_id": "t-1",
         "publication_state": "PUBLISHED", "version": "v3",
         "created_at": "2026-08-14T09:00:00Z", "updated_at": "2026-08-14T10:00:00Z"}
    """.trimIndent()

    private fun importResponseBody(): String = """
        {"results":[
          {"index":0,"status":"CREATED","card_id":"c-1"},
          {"index":1,"status":"FAILED","error":{"field":"back"}}
        ]}
    """.trimIndent()

    private fun validationErrorBody(): String =
        """{"error": {"code": "VALIDATION_ERROR", "message": "比例合计必须为 100", "localization_key": "error.validation"}}"""

    private fun expiredAuthBody(): String =
        """{"error": {"code": "AUTH_INVALID", "message": "会话已过期", "localization_key": "auth.invalid"}}"""

    private fun conflictBody(): String =
        """{"error": {"code": "CARD_VERSION_CONFLICT", "message": "卡片已被修改", "localization_key": "card.version.conflict"}}"""

    private fun deletionBatchBody(): String = """
        {"delete_batch_id": "b-1", "card_ids": ["c-1", "c-2"],
         "undo_until": "2026-08-15T09:00:10Z", "status": "PENDING",
         "created_at": "2026-08-15T09:00:00Z", "updated_at": "2026-08-15T09:00:00Z"}
    """.trimIndent()

    private fun rewritePreviewBody(): String = """
        {"rewrite_id": "r-1", "card_id": "c-1", "base_card_version": "v3",
         "front": "矩阵的秩是什么？", "back": "行向量组的最大无关组所含向量个数",
         "card_type": "QUESTION", "target_difficulty": "UNDERSTANDING",
         "custom_requirements": "强调直觉", "status": "PENDING",
         "expires_at": "2026-08-16T09:00:00Z", "created_at": "2026-08-15T09:00:00Z"}
    """.trimIndent()

    // period timestamps are real wire shapes: infra/db/session.py format_utc always emits
    // UTC ("...T16:00:00.000Z" = Asia/Shanghai Monday 00:00); the dashboard timezone field
    // names the actual bucketing zone.
    private fun statsBody(): String = """
        {"period": {"start": "2026-08-09T16:00:00.000Z", "end": "2026-08-15T15:59:59.000Z", "week_ordinal": 33},
         "timezone": "Asia/Shanghai",
         "weekly_activity": [5, 3, 0, 4, 2, 6, 1], "weekly_total": 21, "weekly_completed_count": 7,
         "week_change_rate": null, "weekly_goal": 350, "weekly_goal_progress": 0.02,
         "recall_accuracy": 0.8, "first_answer_accuracy": 0.75, "retention_rate": 0.82,
         "streak_days": 3, "mastered_card_count": 12,
         "updated_at": "2026-08-15T12:00:00Z", "has_data": true}
    """.trimIndent()

    private fun todayPlanEmptyBody(): String = """
        {"timezone": "Asia/Shanghai", "study_date": "2026-08-15",
         "current_project": null, "daily_goal": 50,
         "today_completed_count": 0, "due_count": 0, "main_plan_remaining": 0, "backlog_count": 0,
         "cards": []}
    """.trimIndent()

    private fun todayPlanBody(): String = """
        {"timezone": "Asia/Shanghai", "study_date": "2026-08-15",
         "current_project": {"project_id": "p-1", "name": "线性代数"},
         "daily_goal": 50, "today_completed_count": 3, "due_count": 2,
         "main_plan_remaining": 47, "backlog_count": 5,
         "cards": [{"card_id": "c-1", "deck_id": "d-1", "source": "GENERATED", "position": 1,
                    "front": "什么是矩阵？", "back": "数表", "card_type": "QUESTION",
                    "target_difficulty": "BASIC", "chapter_id": "ch-1", "source_task_id": "t-1",
                    "publication_state": "PUBLISHED", "version": "v1",
                    "created_at": "2026-08-14T09:00:00Z", "updated_at": "2026-08-14T09:00:00Z",
                    "review_state": {"review_state_id": "rs-1", "card_id": "c-1", "state": "NEW",
                                     "stability": 0.0, "difficulty": 1.0, "due": "2026-08-15T09:00:00Z",
                                     "reps": 0, "lapses": 0, "updated_at": "2026-08-14T09:00:00Z"},
                    "forgetting_risk": 0}]}
    """.trimIndent()

    private fun reviewQueueItemBody(): String = """
        {"card_id": "c-1", "deck_id": "d-1", "source": "GENERATED", "position": 1,
         "front": "什么是矩阵？", "back": "数表", "card_type": "QUESTION",
         "target_difficulty": "BASIC", "chapter_id": "ch-1", "source_task_id": "t-1",
         "publication_state": "PUBLISHED", "version": "v1",
         "created_at": "2026-08-14T09:00:00Z", "updated_at": "2026-08-14T09:00:00Z",
         "review_state": {"review_state_id": "rs-1", "card_id": "c-1", "state": "REVIEW",
                          "stability": 42.0, "difficulty": 3.0, "due": "2026-08-20T09:00:00Z",
                          "reps": 5, "lapses": 0, "updated_at": "2026-08-15T09:00:00Z"}}
    """.trimIndent()

    private fun ratingResultBody(): String = """
        {"review_state": {"review_state_id": "rs-1", "card_id": "c-1", "state": "LEARNING",
                          "stability": 1.0, "difficulty": 4.0, "due": "2026-08-15T09:10:00Z",
                          "reps": 1, "lapses": 0, "updated_at": "2026-08-15T09:05:00Z"},
         "study_date": "2026-08-15"}
    """.trimIndent()

    private fun apiKeyUnknownBody(): String =
        """{"status": "UNKNOWN", "masked_key": "", "updated_at": "2026-08-14T09:00:00Z"}"""

    private fun apiKeyAvailableBody(): String =
        """{"status": "AVAILABLE", "masked_key": "sk-****1234", "updated_at": "2026-08-14T09:00:00Z"}"""
}
