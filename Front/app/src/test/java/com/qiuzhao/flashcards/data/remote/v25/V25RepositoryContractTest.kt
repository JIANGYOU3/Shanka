package com.qiuzhao.flashcards.data.remote.v25

import com.qiuzhao.flashcards.data.remote.http.NetworkStack
import com.qiuzhao.flashcards.data.session.InMemorySessionStore
import com.qiuzhao.flashcards.data.session.Session
import com.qiuzhao.flashcards.data.session.SessionUser
import com.qiuzhao.flashcards.domain.v25.V25ApiKeyState
import com.qiuzhao.flashcards.domain.v25.V25BrowseFilter
import com.qiuzhao.flashcards.domain.v25.V25BrowseOrder
import com.qiuzhao.flashcards.domain.v25.V25CardDraft
import com.qiuzhao.flashcards.domain.v25.V25ContentDifficulty
import com.qiuzhao.flashcards.domain.v25.V25CoverageMode
import com.qiuzhao.flashcards.domain.v25.V25DifficultyRatio
import com.qiuzhao.flashcards.domain.v25.V25ErrorCodes
import com.qiuzhao.flashcards.domain.v25.V25GenerationConfig
import com.qiuzhao.flashcards.domain.v25.V25ImportStatus
import com.qiuzhao.flashcards.domain.v25.V25MasteryFilter
import com.qiuzhao.flashcards.domain.v25.V25MaterialStatus
import com.qiuzhao.flashcards.domain.v25.V25MaterialType
import com.qiuzhao.flashcards.domain.v25.V25PreferencesPatch
import com.qiuzhao.flashcards.domain.v25.V25ProjectStatus
import com.qiuzhao.flashcards.domain.v25.V25Rating
import com.qiuzhao.flashcards.domain.v25.V25Repository
import com.qiuzhao.flashcards.domain.v25.V25Result
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import com.qiuzhao.flashcards.domain.v25.isAuthFailure
import java.io.ByteArrayInputStream
import java.lang.reflect.Proxy
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Locks the repository's wire behavior on the JVM against a real OkHttp/Retrofit stack pointed
 * at a MockWebServer: auth headers, Idempotency-Key ownership, contract paths and query flags,
 * failure mapping, multipart uploads and API-key redaction. Fixture semantics carried over
 * verbatim from the replaced handwritten-transport test: success, empty list, auth failure,
 * network failure, 429, missing envelope, malformed payload, multipart and sensitive-error
 * redaction.
 */
class V25RepositoryContractTest {

    private val user = SessionUser(userId = "u-1", username = "alice", createdAt = "2026-08-14T00:00:00Z")
    private val session = Session(token = "token-1", user = user)

    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var repo: V25Repository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = InMemorySessionStore()
        store.save(session.token, session.user)
        val stack = NetworkStack(store, baseUrlOverride = server.url("/").toString())
        repo = RemoteV25Repository.create(stack)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(body: String = "{}", code: Int = 200): MockResponse =
        MockResponse().setResponseCode(code).setBody(body).also(server::enqueue)

    private fun take(): RecordedRequest = server.takeRequest()

    /** Request-body assertions run on the JVM without the Android org.json stubs. */
    private fun bodyJson(request: RecordedRequest) =
        Json.parseToJsonElement(request.body.readUtf8()).jsonObject

    // --- auth headers and idempotency keys ------------------------------------------------

    @Test
    fun `reads authenticate but never carry an idempotency key`() = runBlocking {
        enqueue(meBody())
        enqueue(itemsBody())
        enqueue(dashboardBody())

        repo.getAuthUser()
        repo.listProjects()
        repo.statsDashboard()

        val paths = mutableListOf<String>()
        repeat(3) {
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("Bearer token-1", request.getHeader("Authorization"))
            assertNull("reads must not carry an Idempotency-Key", request.getHeader("Idempotency-Key"))
            paths += request.path!!
        }
        assertEquals(listOf("/auth/me", "/projects", "/stats/dashboard"), paths)
    }

    @Test
    fun `default writes generate a fresh idempotency key per request`() = runBlocking {
        enqueue(preferencesBody())
        enqueue(ratingBody())

        repo.updatePreferences(V25PreferencesPatch(dailyLearningGoal = 60))
        repo.rateCard("c-1", V25Rating.GOOD)

        val preferenceWrite = take()
        val ratingWrite = take()
        assertTrue(preferenceWrite.getHeader("Idempotency-Key")!!.isNotBlank())
        assertTrue(ratingWrite.getHeader("Idempotency-Key")!!.isNotBlank())
    }

    @Test
    fun `caller fixed keys are replayed verbatim`() = runBlocking {
        enqueue(ratingBody())

        repo.rateCard("c-1", V25Rating.GOOD, clientEventId = "event-1", idempotencyKey = "rating-key")

        val request = take()
        assertEquals("rating-key", request.getHeader("Idempotency-Key"))
        assertEquals("event-1", bodyJson(request)["client_event_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `query parameters follow the contract paths`() = runBlocking {
        enqueue(itemsBody())
        enqueue(itemsBody())
        enqueue(itemsBody())
        enqueue("", 204)
        enqueue("", 204)
        enqueue("", 204)

        repo.listTasks(projectId = "p-1", status = V25TaskStatus.DRAFT)
        repo.listDecks(projectId = "p-1")
        repo.listCards(
            "d-1",
            V25BrowseFilter(
                order = V25BrowseOrder.random,
                contentDifficulty = V25ContentDifficulty.UNLABELED,
                mastery = V25MasteryFilter.unmastered,
            ),
        )
        repo.deleteProject("p-1", retainDecks = false)
        repo.deleteChapter("p-1", "c-1", deleteCards = true)
        repo.deleteTask("t-1", deleteGeneratedCards = true)

        assertEquals("/tasks?project_id=p-1&status=DRAFT", take().path)
        assertEquals("/decks?project_id=p-1", take().path)
        assertEquals("/decks/d-1/cards?order=random&content_difficulty=UNLABELED&mastery=unmastered", take().path)
        assertEquals("/projects/p-1?retain_decks=false", take().path)
        assertEquals("/projects/p-1/chapters/c-1?delete_cards=true", take().path)
        assertEquals("/tasks/t-1?delete_generated_cards=true", take().path)
    }

    @Test
    fun `optional false query flags are omitted from the path`() = runBlocking {
        enqueue("", 204)
        enqueue("", 204)

        repo.deleteChapter("p-1", "c-1", deleteCards = false)
        repo.deleteTask("t-1", deleteGeneratedCards = false)

        assertEquals("/projects/p-1/chapters/c-1", take().path)
        assertEquals("/tasks/t-1", take().path)
    }

    @Test
    fun `deletion operations carry only the retain decision and stable retry key`() = runBlocking {
        enqueue("", 204)
        enqueue(preflightBody())
        enqueue("", 204)
        enqueue(preflightBody())

        repo.deleteProject("p-1", retainDecks = false, idempotencyKey = "project-key")
        repo.getProjectDeletionPreflight("p-1", retainDecks = false)
        repo.deleteDeck("d-1", idempotencyKey = "deck-key")
        repo.getDeckDeletionPreflight("d-1")

        val projectDelete = take()
        assertEquals("/projects/p-1?retain_decks=false", projectDelete.path)
        assertEquals("project-key", projectDelete.getHeader("Idempotency-Key"))
        assertEquals("/projects/p-1/deletion-preflight?retain_decks=false", take().path)
        val deckDelete = take()
        assertEquals("/decks/d-1", deckDelete.path)
        assertEquals("deck-key", deckDelete.getHeader("Idempotency-Key"))
        assertEquals("/decks/d-1/deletion-preflight", take().path)
    }

    @Test
    fun `task cancellation stays server-side while preflight remains advisory`() = runBlocking {
        enqueue("", 204)
        enqueue(preflightBody())
        enqueue("", 204)
        enqueue(preflightBody())

        repo.deleteProject("p-1", retainDecks = false, idempotencyKey = "project-cancel-key")
        repo.getProjectDeletionPreflight("p-1", retainDecks = false, allowCancel = true)
        repo.deleteDeck("d-1", idempotencyKey = "deck-cancel-key")
        repo.getDeckDeletionPreflight("d-1", allowCancel = true)

        assertEquals("/projects/p-1?retain_decks=false", take().path)
        assertEquals("/projects/p-1/deletion-preflight?retain_decks=false&cancel_active_tasks=true", take().path)
        assertEquals("/decks/d-1", take().path)
        assertEquals("/decks/d-1/deletion-preflight?cancel_active_tasks=true", take().path)
    }

    // --- empty and null values ------------------------------------------------------------

    @Test
    fun `an empty items payload is an empty list not a failure`() = runBlocking {
        enqueue("""{"items": []}""")

        val result = repo.listDecks()

        assertTrue(result is V25Result.Success)
        assertTrue((result as V25Result.Success).value.isEmpty())
    }

    // --- logout ---------------------------------------------------------------------------

    @Test
    fun `logout revokes the stored token and clears the store first`() = runBlocking {
        enqueue("", 204)

        val result = repo.logout()

        assertTrue(result is V25Result.Success)
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/auth/logout", request.path)
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Idempotency-Key")!!.isNotBlank())
        assertNull(store.load())
    }

    @Test
    fun `logout without a session is an immediate local success`() = runBlocking {
        store.clear()

        val result = repo.logout()

        assertTrue(result is V25Result.Success)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `logout keeps the local clear even when revocation fails`() = runBlocking {
        enqueue(authErrorBody("AUTH_INVALID"), 401)

        val result = repo.logout()

        assertTrue(result is V25Result.Failure)
        assertTrue((result as V25Result.Failure).isAuthFailure)
        assertNull(store.load())
    }

    // --- deletion batch chaining ------------------------------------------------------------

    @Test
    fun `deleteCard appends the pending batch id and adopts the newest batch`() = runBlocking {
        enqueue(deletionBatchBody("b-1"))
        enqueue(deletionBatchBody("b-2"))
        enqueue(deletionBatchBody("b-2"))

        repo.deleteCard("c-1")
        repo.deleteCard("c-2")
        repo.deleteCard("c-3")

        assertEquals("/cards/c-1", take().path)
        assertEquals("/cards/c-2?delete_batch_id=b-1", take().path)
        assertEquals("/cards/c-3?delete_batch_id=b-2", take().path)
    }

    @Test
    fun `a failed delete keeps the batch id for the next attempt`() = runBlocking {
        enqueue(deletionBatchBody("b-1"))
        enqueue(networkErrorBody(), 503)
        enqueue(deletionBatchBody("b-2"))

        repo.deleteCard("c-1")
        val failure = repo.deleteCard("c-2")
        repo.deleteCard("c-3")

        assertTrue(failure is V25Result.Failure)
        assertEquals(V25ErrorCodes.NETWORK_UNAVAILABLE, (failure as V25Result.Failure).code)
        take()
        take()
        assertEquals("/cards/c-3?delete_batch_id=b-1", take().path)
    }

    // --- recoverable failures and cancellation ----------------------------------------------

    @Test
    fun `transport failures map to coded results instead of throwing`() = runBlocking {
        enqueue(rateLimitBody(), 429)
        enqueue(networkErrorBody(), 503)
        enqueue("""{"error": {"code": "INTERNAL_ERROR", "message": "boom"}}""", 500)

        val limited = repo.getPreferences()
        val offline = repo.listDecks()
        val internal = repo.getProject("p-1")

        assertEquals("RATE_LIMITED", (limited as V25Result.Failure).code)
        assertEquals("rate.limited", limited.localizationKey)
        assertEquals(V25ErrorCodes.NETWORK_UNAVAILABLE, (offline as V25Result.Failure).code)
        assertEquals("INTERNAL_ERROR", (internal as V25Result.Failure).code)
    }

    @Test
    fun `auth failures surface as coded failures that read as auth`() = runBlocking {
        enqueue(authErrorBody("AUTH_REQUIRED"), 401)

        val result = repo.getAuthUser() as V25Result.Failure

        assertEquals("AUTH_REQUIRED", result.code)
        assertTrue(result.isAuthFailure)
    }

    @Test
    fun `network failures surface as NETWORK_UNAVAILABLE without the server`() = runBlocking {
        server.shutdown()

        val result = repo.listProjects()

        assertEquals(V25ErrorCodes.NETWORK_UNAVAILABLE, (result as V25Result.Failure).code)
    }

    @Test
    fun `cancellation propagates instead of becoming a failure`() {
        @Suppress("UNCHECKED_CAST")
        val cancellingApi = Proxy.newProxyInstance(
            V25Api::class.java.classLoader,
            arrayOf(V25Api::class.java),
        ) { _, _, _ -> throw CancellationException("job cancelled") } as V25Api
        val cancellingRepo = RemoteV25Repository(api = cancellingApi, uploadApi = cancellingApi, sessionStore = store)

        assertThrows(CancellationException::class.java) { runBlocking { cancellingRepo.getDeck("d-1") } }
    }

    @Test
    fun `a response without an error envelope gets a stable HTTP fallback code`() = runBlocking {
        enqueue("""{"detail": "bad gateway"}""", 502)

        val result = repo.getTask("t-1") as V25Result.Failure

        assertEquals("HTTP_502", result.code)
    }

    @Test
    fun `malformed success payloads become INVALID_RESPONSE failures`() = runBlocking {
        enqueue("""{"unexpected": true}""")

        val result = repo.getDeck("d-1") as V25Result.Failure

        assertEquals(V25ErrorCodes.INVALID_RESPONSE, result.code)
    }

    // --- API key safety -----------------------------------------------------------------------

    @Test
    fun `saveApiKey sends the plaintext only in the request body and never stores it`() = runBlocking {
        val freshStore = InMemorySessionStore()
        val stack = NetworkStack(freshStore, baseUrlOverride = server.url("/").toString())
        val freshRepo = RemoteV25Repository.create(stack)
        enqueue(apiKeyAvailableBody())

        val result = freshRepo.saveApiKey("sk-secret-1234")

        assertTrue(result is V25Result.Success)
        assertEquals("sk-****1234", (result as V25Result.Success).value.maskedKey)
        assertEquals("sk-secret-1234", bodyJson(take())["api_key"]!!.jsonPrimitive.content)
        assertNull("the repository must never persist the key", freshStore.load())
    }

    @Test
    fun `saveApiKey maps upstream unavailability to the verification status`() = runBlocking {
        enqueue("""{"error": {"code": "API_KEY_UNAVAILABLE", "message": "上游不可用"}}""", 502)

        val result = repo.saveApiKey("sk-secret-1234")

        assertTrue(result is V25Result.Success)
        assertEquals(V25ApiKeyState.VERIFICATION_UNAVAILABLE, (result as V25Result.Success).value.state)
        assertNull(result.value.maskedKey)
    }

    @Test
    fun `saveApiKey failures never echo the plaintext key`() = runBlocking {
        enqueue(authErrorBody("AUTH_INVALID", message = "rejected sk-secret-1234"), 401)

        val result = repo.saveApiKey("sk-secret-1234") as V25Result.Failure

        assertFalse(result.message.orEmpty().contains("sk-secret-1234"))
        assertFalse(result.localizationKey.orEmpty().contains("sk-secret-1234"))
    }

    @Test
    fun `saveApiKey redacts the plaintext key from every failure field including code`() = runBlocking {
        // Worst-case server reflection: the key echoed back in every envelope field.
        enqueue(
            """{"error": {"code": "sk-secret-1234", "message": "rejected sk-secret-1234", "localization_key": "sk-secret-1234"}}""",
            401,
        )

        val result = repo.saveApiKey("sk-secret-1234") as V25Result.Failure

        assertFalse(result.code.contains("sk-secret-1234"))
        assertFalse(result.message.orEmpty().contains("sk-secret-1234"))
        assertFalse(result.localizationKey.orEmpty().contains("sk-secret-1234"))
    }

    @Test
    fun `apiKeyStatus maps the wire UNKNOWN state to UNSET`() = runBlocking {
        enqueue("""{"status": "UNKNOWN", "masked_key": "", "updated_at": "2026-08-14T09:00:00Z"}""")

        val result = repo.apiKeyStatus()

        assertTrue(result is V25Result.Success)
        assertEquals(V25ApiKeyState.UNSET, (result as V25Result.Success).value.state)
        assertNull(result.value.maskedKey)
    }

    // --- uploads and card adds ---------------------------------------------------------------

    @Test
    fun `createProject posts the JSON name body without any file part`() = runBlocking {
        enqueue(emptyProjectBody(), 201)

        val result = repo.createProject("线性代数")

        assertTrue(result is V25Result.Success)
        assertEquals(V25ProjectStatus.EMPTY, (result as V25Result.Success).value.status)
        assertTrue("two-step creation step one owns no materials", result.value.materials.isEmpty())
        val request = take()
        assertEquals("/projects", request.path)
        assertEquals("POST", request.method)
        assertTrue("the JSON body carries only the name", request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("线性代数", bodyJson(request)["name"]!!.jsonPrimitive.content)
        assertTrue(request.getHeader("Idempotency-Key")!!.isNotBlank())
    }

    @Test
    fun `addProjectMaterialPdf uploads the file part to the materials endpoint`() = runBlocking {
        enqueue(materialBody(), 201)

        val result = repo.addProjectMaterialPdf("p-1", "linear.pdf", ByteArrayInputStream(byteArrayOf(1, 2)))

        assertTrue(result is V25Result.Success)
        val material = (result as V25Result.Success).value
        assertEquals(V25MaterialType.PDF, material.type)
        assertEquals(V25MaterialStatus.PENDING, material.status)
        val request = take()
        assertEquals("/projects/p-1/materials/pdf", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("linear.pdf"))
        assertTrue(request.getHeader("Idempotency-Key")!!.isNotBlank())
    }

    @Test
    fun `addProjectMaterialText posts the JSON name and content body`() = runBlocking {
        enqueue(textMaterialBody(), 201)

        val result = repo.addProjectMaterialText("p-1", "课堂笔记", "第一章 绪论")

        assertTrue(result is V25Result.Success)
        val material = (result as V25Result.Success).value
        assertEquals(V25MaterialType.TEXT, material.type)
        assertEquals(V25MaterialStatus.READY, material.status)
        assertEquals(13, material.charCount)
        assertNull("a TEXT chapter owns no page span", material.chapter?.startPage)
        assertNull(material.chapter?.endPage)
        assertEquals("m-text", material.chapter?.materialId)
        val request = take()
        assertEquals("/projects/p-1/materials/text", request.path)
        val body = bodyJson(request)
        assertEquals("课堂笔记", body["name"]!!.jsonPrimitive.content)
        assertEquals("第一章 绪论", body["content"]!!.jsonPrimitive.content)
        assertTrue(request.getHeader("Idempotency-Key")!!.isNotBlank())
    }

    @Test
    fun `listProjectMaterials returns the per-material items`() = runBlocking {
        enqueue("""{"items": [${materialBody()}]}""")

        val result = repo.listProjectMaterials("p-1")

        assertTrue(result is V25Result.Success)
        assertEquals(1, (result as V25Result.Success).value.size)
        assertEquals("/projects/p-1/materials", take().path)
    }

    @Test
    fun `deleteProjectMaterial carries the retain-cards decision on the query`() = runBlocking {
        enqueue(projectBody())
        enqueue(projectBody())

        repo.deleteProjectMaterial("p-1", "m-1", retainCards = true, idempotencyKey = "keep-key")
        repo.deleteProjectMaterial("p-1", "m-1", retainCards = false, idempotencyKey = "drop-key")

        val keep = take()
        assertEquals("/projects/p-1/materials/m-1", keep.path)
        assertNull("retain=true is the server default and stays off the wire", keep.requestUrl!!.queryParameter("retain_cards"))
        assertEquals("keep-key", keep.getHeader("Idempotency-Key"))
        val drop = take()
        assertEquals("/projects/p-1/materials/m-1?retain_cards=false", drop.path)
        assertEquals("drop-key", drop.getHeader("Idempotency-Key"))
    }

    @Test
    fun `replaceProjectMaterialPdf posts to the per-material replace endpoint`() = runBlocking {
        enqueue(materialBody().replace("\"status\": \"PENDING\"", "\"status\": \"PARSING\""), 200)

        val result = repo.replaceProjectMaterialPdf("p-1", "m-1", "linear-v2.pdf", ByteArrayInputStream(byteArrayOf(1)))

        assertTrue(result is V25Result.Success)
        assertEquals(V25MaterialStatus.PARSING, (result as V25Result.Success).value.status)
        val request = take()
        assertEquals("/projects/p-1/materials/m-1/replace", request.path)
        assertTrue(request.body.readUtf8().contains("linear-v2.pdf"))
    }

    @Test
    fun `importCards sends one atomic bulk request and maps the per-index results`() = runBlocking {
        enqueue(importResponseBody(), 201)

        val result = repo.importCards(
            "d-1",
            listOf(V25CardDraft("什么是矩阵？", "数表"), V25CardDraft("什么是秩？", "最大无关组")),
        )

        assertTrue(result is V25Result.Success)
        val results = (result as V25Result.Success).value
        assertEquals(1, server.requestCount)
        val request = take()
        assertEquals("/decks/d-1/cards/import", request.path)
        assertTrue(request.getHeader("Idempotency-Key")!!.isNotBlank())
        assertEquals(2, results.size)
        assertEquals(0, results[0].index)
        assertEquals(V25ImportStatus.CREATED, results[0].status)
        assertEquals("c-1", results[0].cardId)
        // 一次原子请求：请求体携带全部草稿，而不是逐张 POST。
        val cards = bodyJson(request)["cards"]!!.jsonArray
        assertEquals(2, cards.size)
        assertEquals("什么是矩阵？", cards[0].jsonObject["front"]!!.jsonPrimitive.content)
    }

    @Test
    fun `importCards carries the caller idempotency key for a retried batch`() = runBlocking {
        enqueue(importResponseBody(), 201)

        repo.importCards("d-1", listOf(V25CardDraft("正面", "背面")), idempotencyKey = "batch-key-1")

        assertEquals("batch-key-1", take().getHeader("Idempotency-Key"))
    }

    @Test
    fun `importCards maps a FAILED row without throwing`() = runBlocking {
        enqueue("""{"results":[{"index":0,"status":"FAILED"}]}""", 201)

        val result = repo.importCards("d-1", listOf(V25CardDraft("", "")))

        assertTrue(result is V25Result.Success)
        val row = (result as V25Result.Success).value.single()
        assertEquals(V25ImportStatus.FAILED, row.status)
        assertNull(row.cardId)
    }

    @Test
    fun `generateSamples returns the persisted sample cards from the task payload`() = runBlocking {
        enqueue(taskWithSamplesBody())

        val result = repo.generateSamples("t-1")

        assertTrue(result is V25Result.Success)
        assertEquals(1, (result as V25Result.Success).value.size)
        assertEquals("什么是矩阵？", result.value[0].front)
        assertEquals("/tasks/t-1/samples", take().path)
    }

    // --- request bodies -----------------------------------------------------------------------

    @Test
    fun `updateAuthUser sends only the provided fields`() = runBlocking {
        enqueue(meBody())

        repo.updateAuthUser(username = "bob")

        val body = bodyJson(take())
        assertEquals("bob", body["username"]!!.jsonPrimitive.content)
        assertFalse("avatar_key" in body)
    }

    @Test
    fun `setCurrentProject sends an explicit null to clear`() = runBlocking {
        enqueue(preferencesBody())

        repo.setCurrentProject(null)

        val body = bodyJson(take())
        assertTrue("current_project_id" in body)
        assertTrue("explicit null must stay on the wire", body["current_project_id"] is JsonNull)
    }

    @Test
    fun `createTask posts to the project tasks endpoint`() = runBlocking {
        enqueue(taskBody(), 201)

        val result = repo.createTask(
            projectId = "p-1",
            deckId = "d-1",
            chapterIds = listOf("c-1"),
            config = V25GenerationConfig(V25CoverageMode.BALANCED, V25DifficultyRatio(40, 40, 20)),
        )

        assertTrue(result is V25Result.Success)
        val request = take()
        assertEquals("/projects/p-1/tasks", request.path)
        assertEquals("d-1", bodyJson(request)["deck_id"]!!.jsonPrimitive.content)
    }

    // --- fixture bodies ------------------------------------------------------------------------

    private fun itemsBody(): String = """{"items": []}"""

    private fun authErrorBody(code: String, message: String = "认证失败"): String =
        """{"error": {"code": "$code", "message": "$message", "localization_key": "auth.invalid"}}"""

    private fun rateLimitBody(): String =
        """{"error": {"code": "RATE_LIMITED", "message": "请求过于频繁", "localization_key": "rate.limited"}}"""

    private fun networkErrorBody(): String =
        """{"error": {"code": "NETWORK_UNAVAILABLE", "message": "网络不可用"}}"""

    private fun meBody(): String =
        """{"user": {"user_id": "u-1", "username": "alice", "email": "alice@example.com",
            "avatar_key": "mood_03", "created_at": "2026-08-14T09:00:00+00:00"}}""".trimIndent()

    private fun preferencesBody(): String = """
        {"default_coverage_mode": "BALANCED",
         "default_difficulty_ratio": {"basic": 40, "understanding": 40, "deep_question": 20},
         "daily_learning_goal": 50, "learning_timezone": "Asia/Shanghai", "current_project_id": null,
         "updated_at": "2026-08-14T09:00:00+00:00"}
    """.trimIndent()

    private fun dashboardBody(): String = """
        {"has_data": false, "timezone": "Asia/Shanghai",
         "period": {"start": "2026-08-10T16:00:00.000Z", "end": "2026-08-17T16:00:00.000Z"},
         "weekly_activity": [0, 0, 0, 0, 0, 0, 0], "weekly_total": 0,
         "weekly_goal": 100, "weekly_completed_count": 0, "streak_days": 0, "mastered_card_count": 0,
         "updated_at": "2026-08-14T09:00:00+00:00"}
    """.trimIndent()

    private fun ratingBody(): String =
        """{"review_state": {"state": "REVIEW", "due": "2026-08-15T09:00:00+00:00"}, "study_date": "2026-08-14"}"""

    private fun preflightBody(): String = """
        {"resource_type": "project", "resource_id": "p-1", "can_delete": true, "blockers": [],
         "abandonable_task_ids": [], "has_uncancellable_tasks": false,
         "actions": ["delete"], "retain_decks": false,
         "impact": {"retain_decks": false, "deck_count": 0, "card_count": 0, "task_count": 0}}
    """.trimIndent()

    private fun deletionBatchBody(batchId: String): String = """
        {"delete_batch_id": "$batchId", "card_ids": ["c-1"],
         "undo_until": "2026-08-15T09:00:10+00:00", "status": "PENDING",
         "created_at": "2026-08-15T09:00:00+00:00", "updated_at": "2026-08-15T09:00:00+00:00"}
    """.trimIndent()

    private fun apiKeyAvailableBody(): String =
        """{"status": "AVAILABLE", "masked_key": "sk-****1234", "updated_at": "2026-08-14T09:00:00+00:00"}"""

    private fun projectBody(): String = """
        {"project_id": "p-1", "name": "线性代数",
         "materials": [{"material_id": "m-1", "project_id": "p-1", "type": "PDF",
                        "name": "linear.pdf", "status": "PARSED", "error_code": null,
                        "size_bytes": 1, "char_count": null, "chapter": null,
                        "created_at": "2026-08-14T09:00:00+00:00"}],
         "chapters": [{"chapter_id": "c-1", "material_id": "m-1", "name": "第一章",
                       "start_page": 1, "end_page": 20}],
         "status": "READY", "chapter_count": 1, "deck_count": 0, "task_count": 0,
         "created_at": "2026-08-14T09:00:00+00:00", "updated_at": "2026-08-14T10:00:00+00:00",
         "version": "2026-08-14T10:00:00+00:00"}
    """.trimIndent()

    /** POST /projects step one: the empty project (no materials, status EMPTY). */
    private fun emptyProjectBody(): String = """
        {"project_id": "p-1", "name": "线性代数", "materials": [],
         "status": "EMPTY", "chapter_count": 0, "deck_count": 0, "task_count": 0,
         "created_at": "2026-08-14T09:00:00+00:00", "updated_at": "2026-08-14T09:00:00+00:00"}
    """.trimIndent()

    /** One PENDING PDF material (openapi Material). */
    private fun materialBody(): String = """
        {"material_id": "m-1", "project_id": "p-1", "type": "PDF", "name": "linear.pdf",
         "status": "PENDING", "error_code": null, "size_bytes": 1, "char_count": null,
         "chapter": null, "created_at": "2026-08-14T09:00:00+00:00"}
    """.trimIndent()

    /** One READY TEXT material with its single whole-content chapter (null pages). */
    private fun textMaterialBody(): String = """
        {"material_id": "m-text", "project_id": "p-1", "type": "TEXT", "name": "课堂笔记",
         "status": "READY", "error_code": null, "size_bytes": null, "char_count": 13,
         "chapter": {"chapter_id": "ch-text", "material_id": "m-text", "name": "课堂笔记",
                     "start_page": null, "end_page": null},
         "created_at": "2026-08-14T09:00:00+00:00"}
    """.trimIndent()

    private fun importResponseBody(): String = """
        {"results":[
          {"index":0,"status":"CREATED","card_id":"c-1"},
          {"index":1,"status":"CREATED","card_id":"c-2"}
        ]}
    """.trimIndent()

    private fun taskBody(): String = """
        {"task_id": "t-1", "project_id": "p-1", "file_id": "f-1", "deck_id": "d-1", "retry_of_task_id": null,
         "status": "DRAFT", "internal_stage": null,
         "selected_chapters": [{"chapter_id": "c-1", "material_id": "m-1", "name": "第一章", "start_page": 1, "end_page": 20}],
         "generation_config": {"coverage_mode": "BALANCED",
                               "difficulty_ratio": {"basic": 40, "understanding": 40, "deep_question": 20},
                               "custom_requirements": null},
         "sample_cards": null, "sample_config_hash": null, "sample_confirmed_at": null,
         "generated_card_count": 0, "skipped_planning_group_count": 0, "resumable": false,
         "error_code": null, "failure_stage": null,
         "created_at": "2026-08-14T09:00:00+00:00", "started_at": null, "ended_at": null,
         "updated_at": "2026-08-14T09:00:00+00:00"}
    """.trimIndent()

    private fun taskWithSamplesBody(): String = taskBody().replace(
        "\"sample_cards\": null",
        """ "sample_cards": [{"card_id": "s-1", "front": "什么是矩阵？", "back": "数表", "card_type": "QUESTION", "target_difficulty": "BASIC"}] """,
    )
}
