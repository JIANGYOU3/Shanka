package com.qiuzhao.flashcards.data.remote

import android.content.Context
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.qiuzhao.flashcards.BuildConfig
import com.qiuzhao.flashcards.data.CardDraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Server-facing UI models. IDs are opaque UUIDs and must never be parsed as numbers. */
data class DeckSummary(
    val id: String,
    val name: String,
    val chapter: Int? = null,
    val source: String = "MANUAL",
    val themeKey: String = "azure",
    val cardCount: Int = 0,
    val dueCount: Int = 0,
    val masteredCards: Int = 0,
    val reviewCount: Int = 0,
    val masteryRatio: Float? = null,
    /** Null is a legacy standalone deck until the server migration assigns a project. */
    val projectId: String? = null,
    /** Explicit source selections; a deck never implicitly reads every project material. */
    val materialScopes: List<DeckMaterialScope> = emptyList()
)

data class DeckMaterialScope(
    val materialId: String,
    val chapterIds: List<String> = emptyList(),
    val sourceLocator: String? = null
)

data class ProjectSummary(
    val id: String,
    val name: String,
    val themeKey: String = "azure",
    val deckCount: Int = 0,
    val materialCount: Int = 0
)

data class ProjectDetail(
    val project: ProjectSummary,
    val decks: List<DeckSummary> = emptyList(),
    val materials: List<MaterialSummary> = emptyList()
)

enum class ProjectStatisticsRange { TOTAL, TODAY }

/** Server-derived metrics for exactly one selected range; null means the server did not provide it. */
data class ProjectStatistics(
    val range: ProjectStatisticsRange,
    val reviewedCards: Int = 0,
    val cardCount: Int? = null,
    val dueCards: Int? = null,
    val masteredCards: Int? = null,
    val studyDurationMinutes: Int? = null,
    val masteryRatio: Float? = null,
    val reviewStateDistribution: Map<String, Int> = emptyMap()
)

enum class MaterialType { PDF, MARKDOWN, TEXT, UNKNOWN }
enum class MaterialStatus { PENDING, PARSING, READY, FAILED, UNKNOWN }

data class MaterialSummary(
    val id: String,
    val name: String,
    val type: MaterialType = MaterialType.UNKNOWN,
    val status: MaterialStatus = MaterialStatus.UNKNOWN,
    val projectIds: List<String> = emptyList(),
    val chapterCount: Int = 0,
    val errorCode: String? = null
)

const val LEGACY_UNASSIGNED_PROJECT_ID = "legacy-unassigned"
private const val LEGACY_UNASSIGNED_PROJECT_NAME = "未归类项目"

/**
 * Produces a stable project list while the backend migrates legacy standalone decks.
 * Unknown project IDs receive a neutral placeholder so no deck disappears from the UI.
 */
fun projectsForDisplay(knownProjects: List<ProjectSummary>, decks: List<DeckSummary>): List<ProjectSummary> {
    val byId = knownProjects.associateBy { it.id }.toMutableMap()
    decks.mapNotNull { it.projectId }.distinct().forEach { projectId ->
        byId.putIfAbsent(projectId, ProjectSummary(id = projectId, name = "未命名项目"))
    }
    if (decks.any { it.projectId == null }) {
        byId.putIfAbsent(LEGACY_UNASSIGNED_PROJECT_ID, ProjectSummary(LEGACY_UNASSIGNED_PROJECT_ID, LEGACY_UNASSIGNED_PROJECT_NAME))
    }
    return byId.values
        .map { project -> project.copy(deckCount = decks.count { deck -> (deck.projectId ?: LEGACY_UNASSIGNED_PROJECT_ID) == project.id }) }
        .sortedWith(compareBy<ProjectSummary> { it.id != LEGACY_UNASSIGNED_PROJECT_ID }.thenBy { it.name })
}

data class DeckProgress(
    val cardCount: Int,
    val dueCount: Int,
    val masteredCards: Int,
    val reviewCount: Int
)

data class FlashcardEntity(
    val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val code: String? = null,
    val position: Int = 0,
    val source: String = "MANUAL",
    val version: Int = 0,
    val sourceMaterialId: String? = null,
    val sourceLocator: String? = null
)

enum class Rating { AGAIN, HARD, GOOD, EASY }

data class ReviewState(val state: String, val due: String? = null)
data class ReviewCard(val card: FlashcardEntity, val reviewState: ReviewState?)
data class ApiKeyStatus(val status: String, val maskedKey: String)
data class PdfChapter(val id: String, val name: String, val startPage: Int, val endPage: Int)
data class PdfFile(val id: String, val name: String, val status: String, val errorCode: String? = null, val chapters: List<PdfChapter> = emptyList())
data class GeneratedTask(val id: String, val status: String, val stage: String? = null, val generatedCardCount: Int = 0, val resumable: Boolean = false, val errorCode: String? = null)
data class Dashboard(val hasData: Boolean, val weeklyGoal: Int?, val completed: Int, val masteryRatio: Float?, val raw: JSONObject)

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val status: Int, val code: String?, val localizationKey: String?, val message: String?) : ApiResult<Nothing>
}

internal data class HttpResult(val status: Int, val body: String, val headers: Map<String, List<String>>)

/**
 * The device id is deliberately encrypted at rest. It is a credential, so all callers only
 * receive it for a request header and no logging API accepts it.
 */
private class SecureDeviceIdentityStore(context: Context) {
    private val preferences = context.getSharedPreferences("remote_identity", Context.MODE_PRIVATE)
    private val alias = "shanka_device_identity_key"

    fun deviceId(): String {
        preferences.getString("device_id", null)?.let { stored -> decrypt(stored)?.let { return it } }
        return UUID.randomUUID().toString().also { preferences.edit().putString("device_id", encrypt(it)).apply() }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, key())
        Base64.encodeToString(iv + doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12)
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }
    }.getOrNull()
}

internal class BackendClient(context: Context, private val baseUrl: String = defaultBaseUrl()) {
    private val appContext = context.applicationContext
    private val identity = SecureDeviceIdentityStore(appContext)

    suspend fun request(
        operation: String,
        method: String,
        path: String,
        body: String? = null,
        contentType: String = "application/json",
        idempotent: Boolean = method in setOf("POST", "PUT", "PATCH", "DELETE") && path != "/samples"
    ): HttpResult = withContext(Dispatchers.IO) {
        val trace = UUID.randomUUID().toString().take(8)
        val key = if (idempotent) UUID.randomUUID().toString() else null
        var attempt = 0
        var last: HttpResult
        do {
            val started = System.nanoTime()
            // A development server is optional for the installed app.  In
            // particular, 10.0.2.2 is only meaningful to an emulator; a
            // physical phone must never be taken down by its absence.
            last = runCatching { execute(method, path, body, contentType, key) }
                .getOrElse { unavailableResult(it) }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            debug(requestLog(trace, operation, path, last, elapsedMs, attempt))
            if (last.status != 429 || attempt >= 1) break
            val seconds = last.headers.entries.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
                ?.value?.firstOrNull()?.toLongOrNull()?.coerceIn(1, 30) ?: break
            delay(seconds * 1_000L)
            attempt++
        } while (true)
        last
    }

    suspend fun uploadPdf(uri: Uri): HttpResult = withContext(Dispatchers.IO) {
        val trace = UUID.randomUUID().toString().take(8)
        val key = UUID.randomUUID().toString()
        var attempt = 0
        var last: HttpResult
        do {
            val started = System.nanoTime()
            last = runCatching { executeMultipart(uri, key) }
                .getOrElse { unavailableResult(it) }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            debug(requestLog(trace, "upload_pdf", "/pdfs", last, elapsedMs, attempt))
            if (last.status != 429 || attempt >= 1) break
            val seconds = last.headers.entries.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
                ?.value?.firstOrNull()?.toLongOrNull()?.coerceIn(1, 30) ?: break
            delay(seconds * 1_000L)
            attempt++
        } while (true)
        last
    }

    private fun execute(method: String, path: String, body: String?, contentType: String, key: String?): HttpResult {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 25_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Device-ID", identity.deviceId())
            if (key != null) setRequestProperty("Idempotency-Key", key)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                BufferedOutputStream(outputStream).use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val response = stream?.let { BufferedInputStream(it).bufferedReader().use { reader -> reader.readText() } }.orEmpty()
            HttpResult(status, response, connection.headerFields.filterKeys { it != null })
        } finally {
            connection.disconnect()
        }
    }

    private fun executeMultipart(uri: Uri, key: String): HttpResult {
        val boundary = "----Shanka${UUID.randomUUID()}"
        val connection = (URL(baseUrl.trimEnd('/') + "/pdfs").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Device-ID", identity.deviceId())
            setRequestProperty("Idempotency-Key", key)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        return try {
            BufferedOutputStream(connection.outputStream).use { output ->
                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Disposition: form-data; name=\"file\"; filename=\"upload.pdf\"\r\n".toByteArray())
                output.write("Content-Type: application/pdf\r\n\r\n".toByteArray())
                appContext.contentResolver.openInputStream(uri)?.use { input -> input.copyTo(output) }
                    ?: error("Cannot read selected PDF")
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val response = stream?.let { BufferedInputStream(it).bufferedReader().use { reader -> reader.readText() } }.orEmpty()
            HttpResult(status, response, connection.headerFields.filterKeys { it != null })
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Debug evidence is deliberately limited to the already-redacted fields assembled above.
     * It is kept in the app-private files directory so a user can retrieve it with adb without
     * exposing credentials or PDF/card content to shared storage.
     */
    private fun debug(message: String) {
        if (!BuildConfig.DEBUG) return
        Log.d("ShankaNetwork", message)
        runCatching {
            synchronized(debugLogLock) {
                val logFile = File(appContext.filesDir, DEBUG_LOG_FILE)
                val next = "${System.currentTimeMillis()} $message\n"
                if (logFile.length() + next.toByteArray(Charsets.UTF_8).size > MAX_DEBUG_LOG_BYTES) {
                    logFile.writeText("")
                }
                logFile.appendText(next, Charsets.UTF_8)
            }
        }
    }

    private fun requestLog(trace: String, operation: String, path: String, result: HttpResult, elapsedMs: Long, retry: Int): String {
        val requestId = result.headers.entries.firstOrNull { it.key.equals("X-Request-ID", ignoreCase = true) }
            ?.value?.firstOrNull().orEmpty().ifBlank { "-" }
        val pdf = if (operation == "get_pdf") runCatching { objectValue(result.body) }.getOrNull() else null
        val pdfStatus = pdf?.optString("status")?.ifBlank { null } ?: "-"
        val code = errorCode(result.body) ?: pdf?.optString("error_code")?.ifBlank { null } ?: "-"
        return "trace=$trace request_id=$requestId op=$operation path=$path status=${result.status} elapsed_ms=$elapsedMs retry=$retry code=$code pdf_status=$pdfStatus"
    }

    private fun unavailableResult(error: Throwable): HttpResult {
        val code = if (error is FileNotFoundException || error.message == "Cannot read selected PDF") {
            "PDF_FILE_UNREADABLE"
        } else {
            "NETWORK_UNAVAILABLE"
        }
        debug("network_unavailable type=${error.javaClass.simpleName} code=$code")
        return HttpResult(
            status = 503,
            body = JSONObject().put("error", JSONObject().put("code", code)).toString(),
            headers = emptyMap()
        )
    }

    private companion object {
        const val DEBUG_LOG_FILE = "shanka-network-debug.log"
        const val MAX_DEBUG_LOG_BYTES = 256 * 1024L
        val debugLogLock = Any()

        fun defaultBaseUrl(): String =
            if (BuildConfig.DEBUG && isAndroidEmulator()) "http://10.0.2.2:8000" else "https://shanka.kbzz1.top"

        fun isAndroidEmulator(): Boolean =
            Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true) ||
                Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
                Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                "google_sdk" == Build.PRODUCT
    }
}

internal class RemoteFlashcardRepository(
    context: Context,
    private val client: BackendClient = BackendClient(context)
) {
    private val _decks = MutableStateFlow<List<DeckSummary>>(emptyList())
    private val cardFlows = mutableMapOf<String, MutableStateFlow<List<FlashcardEntity>>>()
    val decks: Flow<List<DeckSummary>> = _decks

    fun dueCount(): Flow<Int> = _decks.map { decks -> decks.sumOf { it.dueCount } }
    fun deckProgress(deckId: String): Flow<DeckProgress> = _decks.map { decks ->
        decks.firstOrNull { it.id == deckId }?.let { DeckProgress(it.cardCount, it.dueCount, it.masteredCards, it.reviewCount) }
            ?: DeckProgress(0, 0, 0, 0)
    }

    suspend fun refreshDecks(): ApiResult<List<DeckSummary>> = client.request("list_decks", "GET", "/decks").decode { value ->
        values(value, "decks").mapNotNull(::deck).also { _decks.value = it }
    }

    fun cards(deckId: String): Flow<List<FlashcardEntity>> = cardFlows.getOrPut(deckId) { MutableStateFlow(emptyList()) }

    suspend fun refreshCards(deckId: String): ApiResult<List<FlashcardEntity>> = client.request("list_cards", "GET", "/decks/$deckId/cards").decode { value ->
        values(value, "cards").mapNotNull { card(it, deckId) }.also { cardFlows.getOrPut(deckId) { MutableStateFlow(emptyList()) }.value = it }
    }

    suspend fun loadCards(deckId: String, reviewMode: Boolean): ApiResult<List<FlashcardEntity>> {
        val path = if (reviewMode) "/decks/$deckId/review" else "/decks/$deckId/cards"
        return client.request(if (reviewMode) "review_queue" else "list_cards", "GET", path).decode { value ->
            values(value, if (reviewMode) "items" else "cards").mapNotNull {
                if (reviewMode) card(it.optJSONObject("card") ?: it, deckId) else card(it, deckId)
            }
        }
    }

    suspend fun rate(cardId: String, rating: Rating): ApiResult<ReviewState> {
        val body = JSONObject()
            .put("card_id", cardId)
            .put("rating", rating.name)
            .put("client_event_id", UUID.randomUUID().toString())
            .put("device_timezone", java.util.TimeZone.getDefault().id)
            .toString()
        return client.request("submit_review", "POST", "/review-events", body).decode { value ->
            ReviewState(value.optString("state", ""), value.optString("due").ifBlank { null })
        }
    }

    suspend fun importDeck(name: String, drafts: List<CardDraft>): ApiResult<String> {
        val create = createDeck(name)
        val deckId = (create as? ApiResult.Success)?.value?.takeIf { it.isNotBlank() } ?: return create.asFailure()
        val cards = JSONArray().apply { drafts.forEach { put(JSONObject().put("front", it.front.trim()).put("back", it.back.trim())) } }
        return client.request("import_cards", "POST", "/decks/$deckId/cards/import", JSONObject().put("cards", cards).toString()).decode { deckId }
            .also { if (it is ApiResult.Success) refreshDecks() }
    }

    suspend fun createDeck(name: String): ApiResult<String> = client.request("create_deck", "POST", "/decks", JSONObject().put("name", name.trim()).toString()).decode { it.optString("id", it.optString("deck_id")) }

    suspend fun addCardsToDeck(deckId: String, drafts: List<CardDraft>): ApiResult<Unit> {
        val cards = JSONArray().apply { drafts.filter { it.front.isNotBlank() && it.back.isNotBlank() }.forEach { put(JSONObject().put("front", it.front.trim()).put("back", it.back.trim())) } }
        return client.request("import_cards", "POST", "/decks/$deckId/cards/import", JSONObject().put("cards", cards).toString()).decode { Unit }
            .also { if (it is ApiResult.Success) refreshCards(deckId) }
    }

    suspend fun deleteDeck(deckId: String): ApiResult<Unit> = client.request("delete_deck", "DELETE", "/decks/$deckId").decode { Unit }
        .also { if (it is ApiResult.Success) refreshDecks() }

    suspend fun updateDeckPresentation(deckId: String, name: String, themeKey: String): ApiResult<DeckSummary> {
        val result = client.request("update_deck", "PATCH", "/decks/$deckId", JSONObject().put("name", name.trim()).toString()).decode(::deckOrThrow)
        if (result is ApiResult.Success) {
            val updated = result.value.copy(themeKey = themeKey)
            _decks.value = _decks.value.map { if (it.id == deckId) updated else it }
        }
        return result
    }

    suspend fun updateCard(card: FlashcardEntity): ApiResult<FlashcardEntity> {
        val body = JSONObject().put("front", card.front.trim()).put("back", card.back.trim()).toString()
        val result = client.request("update_card", "PATCH", "/cards/${card.id}", body).decode { value ->
            card(value, card.deckId) ?: error("Card response missing id")
        }
        if (result is ApiResult.Success) {
            cardFlows[card.deckId]?.let { flow -> flow.value = flow.value.map { if (it.id == card.id) result.value else it } }
            refreshDecks()
        }
        return result
    }

    suspend fun deleteCard(card: FlashcardEntity): ApiResult<Unit> {
        val result = client.request("delete_card", "DELETE", "/cards/${card.id}").decode { Unit }
        if (result is ApiResult.Success) {
            cardFlows[card.deckId]?.let { flow -> flow.value = flow.value.filterNot { it.id == card.id } }
            refreshDecks()
        }
        return result
    }

    suspend fun rewriteCard(cardId: String): ApiResult<FlashcardEntity> = client.request("rewrite_card", "POST", "/cards/$cardId/rewrite", JSONObject().toString()).decode { card(it, it.optString("deck_id")) ?: error("Card response missing id") }

    suspend fun apiKeyStatus(): ApiResult<ApiKeyStatus> = client.request("api_key_status", "GET", "/api-key/status").decode { ApiKeyStatus(it.optString("status", "UNKNOWN"), it.optString("masked_key")) }
    suspend fun saveApiKey(key: String): ApiResult<ApiKeyStatus> {
        val saved = client.request("save_api_key", "PUT", "/api-key", JSONObject().put("api_key", key).toString())
            .decode { ApiKeyStatus(it.optString("status", "UNKNOWN"), it.optString("masked_key")) }
        // PUT validates a candidate key but does not necessarily persist it. The status route
        // is the sole source of truth for whether this device now has a usable key.
        return if (saved is ApiResult.Success) apiKeyStatus() else saved
    }
    suspend fun dashboard(weeklyGoal: Int? = null): ApiResult<Dashboard> {
        val timezone = java.net.URLEncoder.encode(java.util.TimeZone.getDefault().id, "UTF-8")
        val path = buildString {
            append("/stats/dashboard?timezone=").append(timezone)
            weeklyGoal?.let { append("&weekly_goal=").append(it.coerceAtLeast(1)) }
        }
        return client.request("dashboard", "GET", path).decode { value ->
        Dashboard(
            value.optBoolean("has_data", false),
            value.optIntOrNull("weekly_goal"),
            value.optInt("weekly_total", value.optInt("completed", value.optInt("reviewed_count", 0))),
            value.optDoubleOrNull("mastery_ratio")?.toFloat(),
            value
        )
        }
    }

    suspend fun uploadPdf(uri: Uri): ApiResult<PdfFile> = client.uploadPdf(uri).decode(::pdf)
    suspend fun getPdf(fileId: String): ApiResult<PdfFile> = client.request("get_pdf", "GET", "/pdfs/$fileId").decode(::pdf)
    suspend fun updatePdfChapter(fileId: String, chapter: PdfChapter): ApiResult<PdfChapter> = client.request(
        "update_pdf_chapter", "PATCH", "/pdfs/$fileId/chapters/${chapter.id}",
        JSONObject().put("name", chapter.name).put("start_page", chapter.startPage).put("end_page", chapter.endPage).toString()
    ).decode { chapter(it) ?: chapter }

    suspend fun deletePdfChapter(fileId: String, chapterId: String): ApiResult<Unit> =
        client.request("delete_pdf_chapter", "DELETE", "/pdfs/$fileId/chapters/$chapterId").decode { Unit }

    suspend fun generateSamples(fileId: String, chapterIds: List<String>, quantity: String, basic: Float, understanding: Float, application: Float, requirement: String): ApiResult<List<CardDraft>> {
        val config = generationConfig(quantity, basic, understanding, application, requirement)
        val body = JSONObject().put("file_id", fileId).put("chapter_ids", JSONArray(chapterIds)).put("generation_config", config).toString()
        return client.request("generate_samples", "POST", "/samples", body, idempotent = false).decode { value ->
            val cards = sampleCards(value)
            cards.map { CardDraft(it.optString("front"), it.optString("back")) }.filter { it.front.isNotBlank() && it.back.isNotBlank() }
        }
    }

    suspend fun createTask(fileId: String, deckId: String, chapterIds: List<String>, quantity: String, basic: Float, understanding: Float, application: Float, requirement: String): ApiResult<GeneratedTask> {
        val config = generationConfig(quantity, basic, understanding, application, requirement)
        val body = JSONObject().put("file_id", fileId).put("deck_id", deckId).put("chapter_ids", JSONArray(chapterIds)).put("generation_config", config).toString()
        return client.request("create_task", "POST", "/tasks", body).decode(::task)
    }

    suspend fun getTask(taskId: String): ApiResult<GeneratedTask> = client.request("get_task", "GET", "/tasks/$taskId").decode(::task)
    suspend fun resumeTask(taskId: String): ApiResult<GeneratedTask> = client.request("resume_task", "POST", "/tasks/$taskId/resume", JSONObject().toString()).decode(::task)

    private fun <T> ApiResult<String>.asFailure(): ApiResult<T> = when (this) {
        is ApiResult.Failure -> this
        is ApiResult.Success -> ApiResult.Failure(500, "INVALID_RESPONSE", null, "Deck id is missing")
    }
}

private fun <T> HttpResult.decode(mapper: (JSONObject) -> T): ApiResult<T> {
    if (status !in 200..299) return ApiResult.Failure(status, errorCode(body), errorLocalizationKey(body), errorMessage(body))
    return runCatching { mapper(objectValue(body)) }.fold({ ApiResult.Success(it) }, { ApiResult.Failure(status, "INVALID_RESPONSE", null, it.message) })
}

private fun objectValue(body: String): JSONObject {
    if (body.isBlank()) return JSONObject()
    val value = JSONTokener(body).nextValue()
    return (value as? JSONObject) ?: JSONObject().put("items", value as? JSONArray ?: JSONArray())
}
private fun values(value: JSONObject, key: String): List<JSONObject> {
    val array = value.optJSONArray(key) ?: value.optJSONArray("items") ?: value.optJSONArray("data") ?: JSONArray()
    return List(array.length()) { index -> array.optJSONObject(index) }.filterNotNull()
}

/** Candidate server contract parser. No project endpoint is invoked until the backend exposes it. */
fun projectDetail(value: JSONObject): ProjectDetail {
    val projectValue = value.optJSONObject("project") ?: value
    val project = project(projectValue) ?: error("Project response missing id")
    return ProjectDetail(
        project = project,
        decks = values(value, "decks").mapNotNull(::deck),
        materials = values(value, "materials").mapNotNull(::material)
    )
}

fun projectStatistics(value: JSONObject, range: ProjectStatisticsRange): ProjectStatistics = ProjectStatistics(
    range = range,
    reviewedCards = value.optInt("reviewed_count", value.optInt("completed", 0)),
    cardCount = value.optIntOrNull("card_count"),
    dueCards = value.optIntOrNull("due_count"),
    masteredCards = value.optIntOrNull("mastered_card_count") ?: value.optIntOrNull("mastered_cards"),
    studyDurationMinutes = value.optIntOrNull("study_duration_minutes"),
    masteryRatio = value.optDoubleOrNull("mastery_ratio")?.toFloat(),
    reviewStateDistribution = reviewStateDistribution(value.optJSONObject("review_state_distribution"))
)

private fun project(value: JSONObject): ProjectSummary? {
    val id = value.optString("id", value.optString("project_id")); if (id.isBlank()) return null
    return ProjectSummary(
        id = id,
        name = value.optString("name", "未命名项目"),
        themeKey = value.optString("theme_key", value.optString("theme", "azure")),
        deckCount = value.optInt("deck_count", 0),
        materialCount = value.optInt("material_count", 0)
    )
}

private fun material(value: JSONObject): MaterialSummary? {
    val id = value.optString("id", value.optString("material_id", value.optString("file_id"))); if (id.isBlank()) return null
    val projectIds = value.optJSONArray("project_ids")?.let { array ->
        List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
    } ?: value.optString("project_id").takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
    return MaterialSummary(
        id = id,
        name = value.optString("name", value.optString("filename", "未命名资料")),
        type = materialType(value.optString("type", value.optString("material_type"))),
        status = materialStatus(value.optString("status")),
        projectIds = projectIds,
        chapterCount = value.optInt("chapter_count", values(value, "chapters").size),
        errorCode = value.optString("error_code").ifBlank { null }
    )
}

private fun materialType(value: String): MaterialType = when (value.trim().uppercase()) {
    "PDF", "APPLICATION/PDF" -> MaterialType.PDF
    "MD", "MARKDOWN", "TEXT/MARKDOWN" -> MaterialType.MARKDOWN
    "TEXT", "PLAIN_TEXT", "TXT", "TEXT/PLAIN" -> MaterialType.TEXT
    else -> MaterialType.UNKNOWN
}

private fun materialStatus(value: String): MaterialStatus = when (value.trim().uppercase()) {
    "PENDING", "UPLOADING" -> MaterialStatus.PENDING
    "PARSING", "PROCESSING" -> MaterialStatus.PARSING
    "PARSED", "READY", "AVAILABLE" -> MaterialStatus.READY
    "FAILED", "ERROR" -> MaterialStatus.FAILED
    else -> MaterialStatus.UNKNOWN
}

private fun reviewStateDistribution(value: JSONObject?): Map<String, Int> {
    if (value == null) return emptyMap()
    val result = linkedMapOf<String, Int>()
    val keys = value.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        result[key] = value.optInt(key, 0)
    }
    return result
}

/** `/samples` is specified as an object whose array property is implementation-defined. */
private fun sampleCards(value: JSONObject): List<JSONObject> {
    val keys = listOf("samples", "sample_cards", "cards", "items", "data")
    keys.forEach { key ->
        value.optJSONArray(key)?.let { array ->
            return List(array.length()) { index -> array.optJSONObject(index) }.filterNotNull()
        }
    }
    val dynamicKeys = value.keys()
    while (dynamicKeys.hasNext()) {
        value.optJSONArray(dynamicKeys.next())?.let { array ->
            return List(array.length()) { index -> array.optJSONObject(index) }.filterNotNull()
        }
    }
    return emptyList()
}

private fun deck(value: JSONObject): DeckSummary? {
    val id = value.optString("id", value.optString("deck_id")); if (id.isBlank()) return null
    return DeckSummary(
        id = id,
        name = value.optString("name", "未命名牌组"),
        source = value.optString("source", "MANUAL"),
        cardCount = value.optInt("card_count", 0),
        dueCount = value.optInt("due_count", 0),
        masteredCards = value.optInt("mastered_card_count", value.optInt("mastered", value.optInt("mastered_cards", 0))),
        reviewCount = value.optInt("review_count", 0),
        masteryRatio = value.optDoubleOrNull("mastery_ratio")?.toFloat(),
        projectId = value.optString("project_id").ifBlank { null },
        materialScopes = values(value, "material_scopes").mapNotNull(::materialScope)
    )
}

private fun deckOrThrow(value: JSONObject): DeckSummary = deck(value) ?: error("Deck response missing id")

private fun card(value: JSONObject, fallbackDeckId: String): FlashcardEntity? {
    val id = value.optString("id", value.optString("card_id")); if (id.isBlank()) return null
    return FlashcardEntity(
        id, value.optString("deck_id", fallbackDeckId), value.optString("front"), value.optString("back"),
        value.optString("code").ifBlank { null }, value.optInt("position", 0), value.optString("source", "MANUAL"), value.optInt("version", 0),
        value.optString("source_material_id", value.optString("material_id")).ifBlank { null },
        value.optString("source_locator").ifBlank { null }
    )
}

private fun materialScope(value: JSONObject): DeckMaterialScope? {
    val materialId = value.optString("material_id", value.optString("file_id")); if (materialId.isBlank()) return null
    val chapterIds = value.optJSONArray("chapter_ids")?.let { array ->
        List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
    }.orEmpty()
    return DeckMaterialScope(materialId, chapterIds, value.optString("source_locator").ifBlank { null })
}

private fun pdf(value: JSONObject): PdfFile {
    val chapters = values(value, "chapters").mapNotNull(::chapter)
    return PdfFile(
        id = value.optString("id", value.optString("file_id")),
        name = value.optString("name", value.optString("filename", "PDF")),
        status = value.optString("status", "PENDING"),
        errorCode = value.optString("error_code").ifBlank { null },
        chapters = chapters
    )
}

private fun chapter(value: JSONObject): PdfChapter? {
    val id = value.optString("id", value.optString("chapter_id")); if (id.isBlank()) return null
    return PdfChapter(id, value.optString("name", value.optString("title", "未命名章节")), value.optInt("start_page", value.optInt("start", 1)), value.optInt("end_page", value.optInt("end", 1)))
}

private fun task(value: JSONObject): GeneratedTask = GeneratedTask(
    id = value.optString("id", value.optString("task_id")),
    status = value.optString("status", "PENDING"),
    stage = value.optString("stage").ifBlank { null },
    generatedCardCount = value.optInt("generated_card_count", 0),
    resumable = value.optBoolean("resumable", false),
    errorCode = value.optString("error_code").ifBlank { null }
)

private fun generationConfig(quantity: String, basic: Float, understanding: Float, application: Float, requirement: String): JSONObject = JSONObject()
    .put("quantity_tendency", quantity)
    .put("difficulty_ratio", JSONObject().put("basic", basic).put("understanding", understanding).put("application", application))
    .apply { if (requirement.isNotBlank()) put("custom_requirements", requirement) }

private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null
private fun JSONObject.optDoubleOrNull(name: String): Double? = if (has(name) && !isNull(name)) optDouble(name) else null
private fun errorCode(body: String): String? = runCatching { objectValue(body).optJSONObject("error")?.optString("code")?.ifBlank { null } }.getOrNull()
private fun errorLocalizationKey(body: String): String? = runCatching { objectValue(body).optJSONObject("error")?.optString("localization_key")?.ifBlank { null } }.getOrNull()
private fun errorMessage(body: String): String? = runCatching { objectValue(body).optJSONObject("error")?.optString("message")?.ifBlank { null } }.getOrNull()
