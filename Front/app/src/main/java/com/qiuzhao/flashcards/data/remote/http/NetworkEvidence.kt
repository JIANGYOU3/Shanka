package com.qiuzhao.flashcards.data.remote.http

import android.content.Context
import android.util.Log
import com.qiuzhao.flashcards.BuildConfig
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Debug evidence for the unified network stack. Deliberately limited to metadata already
 * redacted at the call site — operation name, path, status, elapsed time and the error code
 * from the error envelope — and never a request/response body: tokens, API keys, PDF content
 * and card text must never reach a log. Mirror of the legacy client's evidence format
 * (`trace=… request_id=… op=… path=… status=… elapsed_ms=… retry=… code=…`), kept in the
 * app-private files directory so adb can retrieve it without exposing it to shared storage.
 */
class NetworkEvidence(context: Context) {

    private val appContext = context.applicationContext

    fun record(request: Request, response: Response, elapsedMs: Long, retry: Int = 0) {
        val code = if (response.code in 200..299) "-" else errorCode(response)
        post(
            "trace=${request.header(TRACE_HEADER) ?: "-"} " +
                "request_id=${response.header("X-Request-ID") ?: "-"} " +
                "op=${request.header(OP_HEADER) ?: "-"} path=${request.url.encodedPath} " +
                "status=${response.code} elapsed_ms=$elapsedMs retry=$retry code=$code",
        )
    }

    fun recordFailure(request: Request, failure: Throwable, elapsedMs: Long) {
        val code = when {
            failure is IOException -> "NETWORK_UNAVAILABLE"
            failure.message == "Cannot read selected PDF" -> "PDF_FILE_UNREADABLE"
            else -> failure.javaClass.simpleName
        }
        post(
            "trace=${request.header(TRACE_HEADER) ?: "-"} " +
                "op=${request.header(OP_HEADER) ?: "-"} path=${request.url.encodedPath} " +
                "elapsed_ms=$elapsedMs retry=- code=$code",
        )
    }

    private fun errorCode(response: Response): String =
        runCatching {
            val body = response.peekBody(MAX_EVIDENCE_BODY_BYTES).string()
            ErrorEnvelope.parse(body)?.code ?: "-"
        }.getOrDefault("-")

    private fun post(message: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(LOG_TAG, message)
        runCatching {
            synchronized(lock) {
                val logFile = File(appContext.filesDir, DEBUG_LOG_FILE)
                val next = "${System.currentTimeMillis()} $message\n"
                if (logFile.length() + next.toByteArray(Charsets.UTF_8).size > MAX_DEBUG_LOG_BYTES) {
                    logFile.writeText("")
                }
                logFile.appendText(next, Charsets.UTF_8)
            }
        }
    }

    companion object {
        const val LOG_TAG = "ShankaNetwork"
        const val TRACE_HEADER = "X-Shanka-Trace"
        const val OP_HEADER = "X-Shanka-Op"
        private const val DEBUG_LOG_FILE = "shanka-network-debug.log"
        private const val MAX_DEBUG_LOG_BYTES = 256 * 1024L
        private const val MAX_EVIDENCE_BODY_BYTES = 8 * 1024L
        private val lock = Any()

        /** Each attempt gets a short trace id; a 429-retried attempt reuses it (evidence lineage). */
        fun newTrace(): String = UUID.randomUUID().toString().take(8)
    }
}

/**
 * A request header set by the Retrofit interfaces via a static @Headers annotation, so the
 * evidence log can name the repository operation without logging a path/body.
 */
object ShankaOps {
    const val GET_AUTH_USER = "get_auth_user"
    const val UPDATE_AUTH_USER = "update_auth_user"
    const val LOGOUT = "logout"
    const val REGISTER = "register"
    const val LOGIN = "login"
    const val ME = "me"
    const val GET_PREFERENCES = "get_preferences"
    const val UPDATE_PREFERENCES = "update_preferences"
    const val SET_CURRENT_PROJECT = "set_current_project"
    const val CREATE_PROJECT = "create_project"
    const val LIST_PROJECTS = "list_projects"
    const val GET_PROJECT = "get_project"
    const val PROJECT_PROGRESS = "project_progress"
    const val RENAME_PROJECT = "rename_project"
    const val DELETE_PROJECT = "delete_project"
    const val PROJECT_DELETION_PREFLIGHT = "project_deletion_preflight"
    const val LIST_MATERIALS = "list_project_materials"
    const val ADD_MATERIAL_PDF = "add_project_material_pdf"
    const val ADD_MATERIAL_TEXT = "add_project_material_text"
    const val DELETE_MATERIAL = "delete_project_material"
    const val REPLACE_MATERIAL_PDF = "replace_project_material_pdf"
    const val UPDATE_CHAPTER = "update_chapter"
    const val DELETE_CHAPTER = "delete_chapter"
    const val CONFIRM_CHAPTERS = "confirm_chapters"
    const val GET_STUDY_SETTINGS = "get_study_settings"
    const val UPDATE_STUDY_SETTINGS = "update_study_settings"
    const val CREATE_TASK = "create_task"
    const val LIST_TASKS = "list_tasks"
    const val GET_TASK = "get_task"
    const val UPDATE_TASK = "update_task"
    const val GENERATE_SAMPLES = "generate_samples"
    const val START_TASK = "start_task"
    const val ABANDON_TASK = "abandon_task"
    const val RETRY_TASK = "retry_task"
    const val DELETE_TASK = "delete_task"
    const val LIST_DECKS = "list_decks"
    const val CREATE_DECK = "create_deck"
    const val GET_DECK = "get_deck"
    const val ATTACH_DECK = "attach_deck_to_project"
    const val RENAME_DECK = "rename_deck"
    const val DELETE_DECK = "delete_deck"
    const val DECK_DELETION_PREFLIGHT = "deck_deletion_preflight"
    const val LIST_CARDS = "list_cards"
    const val IMPORT_CARDS = "import_cards"
    const val UPDATE_CARD = "update_card"
    const val DELETE_CARD = "delete_card"
    const val PENDING_DELETION_BATCHES = "pending_deletion_batches"
    const val UNDO_DELETION_BATCH = "undo_deletion_batch"
    const val CREATE_REWRITE_PREVIEW = "create_rewrite_preview"
    const val APPLY_REWRITE_PREVIEW = "apply_rewrite_preview"
    const val CANCEL_REWRITE_PREVIEW = "cancel_rewrite_preview"
    const val GET_STUDY_PLAN = "get_study_plan"
    const val UPDATE_STUDY_PLAN = "update_study_plan"
    const val TODAY_PLAN = "today_plan"
    const val STUDY_PLAN_BACKLOG = "study_plan_backlog"
    const val REVIEW_QUEUE = "review_queue"
    const val SUBMIT_REVIEW = "submit_review"
    const val DASHBOARD = "dashboard"
    const val API_KEY_STATUS = "api_key_status"
    const val SAVE_API_KEY = "save_api_key"
}

/**
 * Error-envelope shape shared by every endpoint (structure-contract 1.4). Parsed from error
 * responses by the repositories and from evidence peek bodies by [NetworkEvidence].
 */
@Serializable
data class ErrorEnvelope(
    @SerialName("code") val code: String? = null,
    @SerialName("localization_key") val localizationKey: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("actions") val actions: List<String>? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun parse(body: String): ErrorEnvelope? = runCatching {
            val wrapper = json.decodeFromString(EnvelopeWrapper.serializer(), body)
            wrapper.error
        }.getOrNull()
    }
}

@Serializable
internal data class EnvelopeWrapper(@SerialName("error") val error: ErrorEnvelope? = null)

/** Attaches the attempt trace id so the evidence log can group a retried request. */
class TraceInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val trace = NetworkEvidence.newTrace()
        return chain.proceed(chain.request().newBuilder().header(NetworkEvidence.TRACE_HEADER, trace).build())
    }
}

/** Per-attempt metadata-only evidence (innermost interceptor: one record per attempt). */
class EvidenceInterceptor(private val evidence: NetworkEvidence) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = System.nanoTime()
        try {
            val response = chain.proceed(request)
            evidence.record(request, response, (System.nanoTime() - started) / 1_000_000)
            return response
        } catch (failure: Throwable) {
            evidence.recordFailure(request, failure, (System.nanoTime() - started) / 1_000_000)
            throw failure
        }
    }
}
