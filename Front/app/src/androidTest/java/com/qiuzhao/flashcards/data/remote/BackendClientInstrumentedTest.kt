package com.qiuzhao.flashcards.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.net.Uri
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackendClientInstrumentedTest {
    private val server = MockWebServer()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val debugLog get() = File(context.filesDir, "shanka-network-debug.log")

    @Before fun start() {
        debugLog.delete()
        server.start()
    }

    @After fun stop() {
        server.shutdown()
        debugLog.delete()
    }

    private fun client() = BackendClient(context, server.url("").toString().trimEnd('/'))

    @Test fun addsDeviceIdentityAndIdempotencyOnlyForWrites() = runBlocking {
        val client = client()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client.request("list_decks", "GET", "/decks")
        val read = server.takeRequest()
        assertEquals("/decks", read.path)
        assertNotNull(read.getHeader("X-Device-ID"))
        assertEquals(null, read.getHeader("Idempotency-Key"))

        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        client.request("create_deck", "POST", "/decks", "{\"name\":\"test\"}")
        val write = server.takeRequest()
        assertEquals("POST", write.method)
        assertNotNull(write.getHeader("X-Device-ID"))
        assertNotNull(write.getHeader("Idempotency-Key"))
    }

    @Test fun samplesWriteIsExplicitlyExemptFromIdempotencyKey() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client().request("generate_samples", "POST", "/samples", "{}", idempotent = false)

        val request = server.takeRequest()
        assertEquals("/samples", request.path)
        assertEquals(null, request.getHeader("Idempotency-Key"))
        assertNotNull(request.getHeader("X-Device-ID"))
    }

    @Test fun retries429OnceUsingTheSameIdempotencyKey() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "1").setBody("{\"error\":{\"code\":\"RATE_LIMITED\"}}"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val result = client().request("create_deck", "POST", "/decks", "{\"name\":\"retry\"}")

        assertEquals(201, result.status)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"))
        assertNotNull(first.getHeader("Idempotency-Key"))
        assertEquals(2, server.requestCount)
    }

    @Test fun doesNotRetryValidationFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(422).setBody("{\"error\":{\"code\":\"VALIDATION_ERROR\"}}"))

        val result = client().request("create_deck", "POST", "/decks", "{}")

        assertEquals(422, result.status)
        assertEquals(1, server.requestCount)
    }

    @Test fun uploadsPdfAsMultipartFileWithIdempotencyKey() = runBlocking {
        val pdf = File(context.cacheDir, "network-client-test.pdf").apply { writeText("%PDF-1.4 test") }
        try {
            server.enqueue(MockResponse().setResponseCode(201).setBody("{\"file_id\":\"test\",\"status\":\"PENDING\"}"))

            val result = client().uploadPdf(Uri.fromFile(pdf))

            assertEquals(201, result.status)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/pdfs", request.path)
            assertNotNull(request.getHeader("X-Device-ID"))
            assertNotNull(request.getHeader("Idempotency-Key"))
            assertTrue(request.body.readUtf8().contains("name=\"file\""))
        } finally {
            pdf.delete()
        }
    }

    @Test fun debugEvidenceIsPersistedAndRedactsRequestSecrets() = runBlocking {
        val requestBody = "super-secret-card-content"
        server.enqueue(MockResponse().setResponseCode(201).addHeader("X-Request-ID", "request-evidence").setBody("{}"))

        client().request("create_deck", "POST", "/decks", requestBody)

        val sent = server.takeRequest()
        val saved = debugLog.readText()
        assertTrue(saved.contains("op=create_deck"))
        assertTrue(saved.contains("path=/decks"))
        assertTrue(saved.contains("status=201"))
        assertTrue(saved.contains("request_id=request-evidence"))
        assertFalse(saved.contains(requestBody))
        assertFalse(saved.contains(sent.getHeader("X-Device-ID")!!))
        assertFalse(saved.contains(sent.getHeader("Idempotency-Key")!!))
    }

    @Test fun remoteRepositoryMapsAuthoritativeDeckAndDashboardFields() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[{"deck_id":"deck-uuid","name":"远端牌组","source":"MANUAL","card_count":8,"due_count":3,"mastered_card_count":5,"review_count":9,"mastery_ratio":0.625,"created_at":"2026-08-11T00:00:00Z","updated_at":"2026-08-11T00:00:00Z","version":"v1"}]}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"period":{"start":"2026-08-10T16:00:00Z","end":"2026-08-17T16:00:00Z","week_ordinal":33},"timezone":"Asia/Shanghai","weekly_activity":[0,1,0,2,0,0,0],"weekly_total":3,"week_change_rate":0.5,"weekly_goal":60,"weekly_goal_progress":0.05,"recall_accuracy":0.8,"first_answer_accuracy":0.7,"retention_rate":0.9,"streak_days":2,"mastered_card_count":5,"updated_at":"2026-08-11T00:00:00Z","has_data":true}"""
            )
        )
        val repository = RemoteFlashcardRepository(context, client())

        val decks = repository.refreshDecks()
        val dashboard = repository.dashboard(60)

        assertTrue(decks is ApiResult.Success)
        assertEquals(5, repository.decks.first().single().masteredCards)
        assertTrue(dashboard is ApiResult.Success)
        assertEquals(3, (dashboard as ApiResult.Success).value.completed)
        assertEquals(60, dashboard.value.weeklyGoal)
        assertEquals("/decks", server.takeRequest().path)
        assertTrue(server.takeRequest().path!!.contains("weekly_goal=60"))
    }

    @Test fun repositoryUsesTheFourNewWriteRoutesAndAcceptsEmptyDeleteResponses() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"deck_id":"deck-uuid","name":"已改名","card_count":2,"due_count":1,"mastered_card_count":0,"review_count":1}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"card_id":"card-uuid","deck_id":"deck-uuid","front":"新问题","back":"新答案","position":0,"version":2}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"items\":[]}"))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"items\":[]}"))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"items\":[]}"))
        val repository = RemoteFlashcardRepository(context, client())
        val card = FlashcardEntity("card-uuid", "deck-uuid", "新问题", "新答案")

        assertTrue(repository.updateDeckPresentation("deck-uuid", "已改名", "azure") is ApiResult.Success)
        assertTrue(repository.updateCard(card) is ApiResult.Success)
        assertTrue(repository.deleteCard(card) is ApiResult.Success)
        assertTrue(repository.deletePdfChapter("pdf-uuid", "chapter-uuid") is ApiResult.Success)
        assertTrue(repository.deleteDeck("deck-uuid") is ApiResult.Success)

        val rename = server.takeRequest()
        assertEquals("PATCH", rename.method)
        assertEquals("/decks/deck-uuid", rename.path)
        assertTrue(rename.body.readUtf8().contains("已改名"))
        val edit = server.takeRequest()
        assertEquals("PATCH", edit.method)
        assertEquals("/cards/card-uuid", edit.path)
        assertTrue(edit.body.readUtf8().contains("新问题"))
        assertEquals("/decks", server.takeRequest().path)
        val deleteCard = server.takeRequest()
        assertEquals("DELETE", deleteCard.method)
        assertEquals("/cards/card-uuid", deleteCard.path)
        assertEquals("/decks", server.takeRequest().path)
        val deleteChapter = server.takeRequest()
        assertEquals("DELETE", deleteChapter.method)
        assertEquals("/pdfs/pdf-uuid/chapters/chapter-uuid", deleteChapter.path)
        val deleteDeck = server.takeRequest()
        assertEquals("DELETE", deleteDeck.method)
        assertEquals("/decks/deck-uuid", deleteDeck.path)
        assertEquals("/decks", server.takeRequest().path)
        listOf(rename, edit, deleteCard, deleteChapter, deleteDeck).forEach { request ->
            assertNotNull(request.getHeader("Idempotency-Key"))
            assertNotNull(request.getHeader("X-Device-ID"))
        }
    }

    @Test fun sampleCardsUseFrontBackAndAcceptTheOpenApiDynamicArrayKey() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"sample_cards":[{"card_id":"sample-1","front":"样卡问题","back":"样卡答案","card_type":"QUESTION","question":null,"answer":null}]}"""
        ))

        val result = RemoteFlashcardRepository(context, client()).generateSamples(
            fileId = "pdf-uuid",
            chapterIds = listOf("chapter-uuid"),
            quantity = "BALANCED",
            basic = .4f,
            understanding = .4f,
            application = .2f,
            requirement = ""
        )

        assertTrue(result is ApiResult.Success)
        val cards = (result as ApiResult.Success).value
        assertEquals("样卡问题", cards.single().front)
        assertEquals("样卡答案", cards.single().back)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/samples", request.path)
        assertEquals(null, request.getHeader("Idempotency-Key"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"quantity_tendency\":\"BALANCED\""))
        assertTrue(body.contains("\"difficulty_ratio\""))
    }

    @Test fun apiKeySaveRefreshesTheAuthoritativeStatusAfterPut() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"status\":\"INVALID\",\"masked_key\":\"\"}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"status\":\"AVAILABLE\",\"masked_key\":\"sk-****1234\"}"))

        val result = RemoteFlashcardRepository(context, client()).saveApiKey("candidate-key")

        assertTrue(result is ApiResult.Success)
        assertEquals("AVAILABLE", (result as ApiResult.Success).value.status)
        assertEquals("PUT", server.takeRequest().method)
        assertEquals("/api-key/status", server.takeRequest().path)
    }

    @Test fun taskCreationPreservesTheApiKeyErrorForTheUiGate() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(422).setBody(
            """{"error":{"code":"API_KEY_NOT_SET","localization_key":"error.api_key_not_set"}}"""
        ))

        val result = RemoteFlashcardRepository(context, client()).createTask(
            fileId = "pdf-uuid",
            deckId = "deck-uuid",
            chapterIds = listOf("chapter-uuid"),
            quantity = "BALANCED",
            basic = .4f,
            understanding = .4f,
            application = .2f,
            requirement = ""
        )

        assertTrue(result is ApiResult.Failure)
        assertEquals("API_KEY_NOT_SET", (result as ApiResult.Failure).code)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/tasks", request.path)
    }

    @Test fun sampleValidationErrorIsPreservedForTheUiFeedback() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody(
            """{"error":{"code":"VALIDATION_ERROR","localization_key":"error.validation"}}"""
        ))

        val result = RemoteFlashcardRepository(context, client()).generateSamples(
            fileId = "pdf-uuid",
            chapterIds = listOf("chapter-uuid"),
            quantity = "BALANCED",
            basic = .4f,
            understanding = .4f,
            application = .2f,
            requirement = ""
        )

        assertTrue(result is ApiResult.Failure)
        assertEquals("VALIDATION_ERROR", (result as ApiResult.Failure).code)
        assertEquals("/samples", server.takeRequest().path)
    }
}
