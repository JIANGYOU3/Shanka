package com.qiuzhao.flashcards.data.remote.v25

import com.qiuzhao.flashcards.data.remote.http.ErrorEnvelope
import com.qiuzhao.flashcards.data.session.SessionStore
import com.qiuzhao.flashcards.data.session.loadQuietly
import com.qiuzhao.flashcards.domain.v25.V25ApiKeyState
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
import com.qiuzhao.flashcards.domain.v25.V25PreferencesPatch
import com.qiuzhao.flashcards.domain.v25.V25ProjectStudySettings
import com.qiuzhao.flashcards.domain.v25.V25ProgressSummary
import com.qiuzhao.flashcards.domain.v25.V25PlanCard
import com.qiuzhao.flashcards.domain.v25.V25Rating
import com.qiuzhao.flashcards.domain.v25.V25RatingResult
import com.qiuzhao.flashcards.domain.v25.V25Repository
import com.qiuzhao.flashcards.domain.v25.V25Result
import com.qiuzhao.flashcards.domain.v25.V25ReviewCard
import com.qiuzhao.flashcards.domain.v25.V25SampleCard
import com.qiuzhao.flashcards.domain.v25.V25StatsDashboard
import com.qiuzhao.flashcards.domain.v25.V25StudyPlan
import com.qiuzhao.flashcards.domain.v25.V25StudyPlanUpdate
import com.qiuzhao.flashcards.domain.v25.V25StudySettingsPatch
import com.qiuzhao.flashcards.domain.v25.V25TaskConfigPatch
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import com.qiuzhao.flashcards.domain.v25.V25TodayPlan
import com.qiuzhao.flashcards.domain.v25.V25UserPreferences
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

private const val REDACTED = "[REDACTED]"

/**
 * The remote implementation of [V25Repository] (Architecture §8 `data/remote/v25`): every
 * method maps to exactly one typed endpoint of [V25Api], and every payload or error becomes a
 * typed [V25Result] — the visual lane never sees a DTO, HTTP status or JSON object. Transport
 * concerns (bearer session, connection reuse, 429 Retry-After with the caller's fixed
 * Idempotency-Key, evidence) live in the shared OkHttp stack, not here.
 *
 * Task 1 adjudication bindings honored here:
 * - [saveApiKey] receives the plaintext key (the upload flow requires it); the key travels
 *   only inside the PUT body over TLS, is never logged or persisted, and is redacted from
 *   any failure text before it crosses the boundary.
 * - [logout] has no token parameter: the implementation reads the stored session, clears it
 *   local-first, then revokes that explicit token server-side.
 * - Every write without an explicit caller key generates one fresh key per invocation; a
 *   retried user operation passes its own key so the replay is identical on the wire.
 */
class RemoteV25Repository internal constructor(
    private val api: V25Api,
    /** Multipart uploads use the 60s-read-timeout client; JSON calls share the default one. */
    private val uploadApi: V25Api = api,
    /** The bearer-session source for the local-first logout path. */
    private val sessionStore: SessionStore,
) : V25Repository {

    companion object {
        /** Production wiring: one shared network stack, two Retrofit proxies of the same API. */
        fun create(stack: com.qiuzhao.flashcards.data.remote.http.NetworkStack): RemoteV25Repository =
            RemoteV25Repository(
                api = stack.retrofit().create(V25Api::class.java),
                uploadApi = stack.retrofit(stack.uploadClient).create(V25Api::class.java),
                sessionStore = stack.sessionStore,
            )
    }

    // --- account profile (Architecture 4.1, V25-ACC) ------------------------------------------

    override suspend fun getAuthUser(): V25Result<V25AuthUser> =
        wire { api.getAuthUser().user.toDomain() }

    override suspend fun updateAuthUser(
        username: String?,
        avatarKey: V25AvatarKey?,
    ): V25Result<V25AuthUser> = wire {
        api.updateAuthUser(AuthMeUpdateRequest(username, avatarKey?.name), newKey()).user.toDomain()
    }

    /**
     * Local-first logout: the stored session is cleared before the server call, and the
     * explicit token is revoked so the request works even after the store is empty. With no
     * stored session the device is already signed out — an immediate success, no network.
     */
    override suspend fun logout(): V25Result<Unit> {
        val token = sessionStore.loadQuietly()?.token
        sessionStore.clear()
        if (token == null) return V25Result.Success(Unit)
        return wire { api.logout("Bearer $token", newKey()) }
    }

    // --- preferences (Architecture 4.1, V25-SET) ----------------------------------------------

    override suspend fun getPreferences(): V25Result<V25UserPreferences> =
        wire { api.getPreferences().toDomain() }

    override suspend fun updatePreferences(patch: V25PreferencesPatch): V25Result<V25UserPreferences> =
        wire { api.updatePreferences(patch.toWire(), newKey()).toDomain() }

    override suspend fun setCurrentProject(projectId: String?): V25Result<V25UserPreferences> =
        wire { api.setCurrentProject(SetCurrentProjectRequest(projectId), newKey()).toDomain() }

    // --- learning projects and chapters (Architecture 4.2) -------------------------------------

    override suspend fun createProject(name: String, idempotencyKey: String?): V25Result<V25LearningProject> =
        wire { api.createProject(CreateProjectRequest(name.trim()), idempotencyKey ?: newKey()).toDomain() }

    // --- materials (Architecture 4.2, structure-contract 3.2a) -----------------------------------

    override suspend fun addProjectMaterialPdf(
        projectId: String,
        fileName: String,
        content: InputStream,
        idempotencyKey: String?,
    ): V25Result<V25Material> = wire {
        uploadApi.addProjectMaterialPdf(projectId, idempotencyKey ?: newKey(), pdfPart(fileName, content)).toDomain()
    }

    override suspend fun addProjectMaterialText(
        projectId: String,
        name: String,
        content: String,
        idempotencyKey: String?,
    ): V25Result<V25Material> = wire {
        api.addProjectMaterialText(projectId, TextMaterialCreateRequest(name.trim(), content), idempotencyKey ?: newKey()).toDomain()
    }

    override suspend fun listProjectMaterials(projectId: String): V25Result<List<V25Material>> =
        wire { api.listProjectMaterials(projectId).items.map { it.toDomain() } }

    override suspend fun deleteProjectMaterial(
        projectId: String,
        materialId: String,
        retainCards: Boolean,
        idempotencyKey: String?,
    ): V25Result<V25LearningProject> = wire {
        // The retain decision is part of the idempotent operation: a replay must never flip it.
        api.deleteProjectMaterial(projectId, materialId, if (retainCards) null else false, idempotencyKey ?: newKey()).toDomain()
    }

    override suspend fun replaceProjectMaterialPdf(
        projectId: String,
        materialId: String,
        fileName: String,
        content: InputStream,
        idempotencyKey: String?,
    ): V25Result<V25Material> = wire {
        uploadApi.replaceProjectMaterialPdf(projectId, materialId, idempotencyKey ?: newKey(), pdfPart(fileName, content)).toDomain()
    }

    override suspend fun listProjects(forceRefresh: Boolean): V25Result<List<V25LearningProject>> =
        wire { api.listProjects().items.map { it.toDomain() } }

    override suspend fun getProject(projectId: String, forceRefresh: Boolean): V25Result<V25LearningProject> =
        wire { api.getProject(projectId).toDomain() }

    override suspend fun projectProgress(projectId: String): V25Result<V25ProgressSummary> =
        wire { api.projectProgress(projectId).toDomain(scopeId = projectId, scopeName = "", isProject = true) }

    override suspend fun renameProject(projectId: String, name: String): V25Result<V25LearningProject> =
        wire { api.renameProject(projectId, RenameRequest(name), newKey()).toDomain() }

    override suspend fun deleteProject(
        projectId: String,
        retainDecks: Boolean,
        idempotencyKey: String?,
    ): V25Result<Unit> = wire { api.deleteProject(projectId, retainDecks, idempotencyKey ?: newKey()) }

    override suspend fun getProjectDeletionPreflight(
        projectId: String,
        retainDecks: Boolean,
        allowCancel: Boolean,
    ): V25Result<V25DeletionPreflight> = wire {
        api.projectDeletionPreflight(
            projectId,
            retainDecks = if (retainDecks) null else false,
            cancelActiveTasks = if (allowCancel) true else null,
        ).toDomain()
    }

    override suspend fun updateChapter(
        projectId: String,
        chapterId: String,
        edit: V25ChapterEdit,
    ): V25Result<V25Chapter> = wire {
        api.updateChapter(projectId, chapterId, ChapterEditRequest(edit.name, edit.startPage, edit.endPage), newKey()).toDomain()
    }

    override suspend fun deleteChapter(
        projectId: String,
        chapterId: String,
        deleteCards: Boolean,
    ): V25Result<Unit> = wire { api.deleteChapter(projectId, chapterId, if (deleteCards) true else null, newKey()) }

    override suspend fun confirmChapters(projectId: String): V25Result<V25LearningProject> =
        wire { api.confirmChapters(projectId, newKey()).toDomain() }

    override suspend fun getStudySettings(projectId: String): V25Result<V25ProjectStudySettings> =
        wire { api.getStudySettings(projectId).toDomain(projectId) }

    override suspend fun updateStudySettings(
        projectId: String,
        patch: V25StudySettingsPatch,
    ): V25Result<V25ProjectStudySettings> = wire {
        api.updateStudySettings(
            projectId,
            StudySettingsPatchRequest(patch.selectedNewCardChapterIds, patch.includeUnassigned),
            newKey(),
        ).toDomain(projectId)
    }

    // --- generation tasks (Architecture 4.3) ---------------------------------------------------

    override suspend fun createTask(
        projectId: String,
        deckId: String,
        chapterIds: List<String>,
        config: V25GenerationConfig,
    ): V25Result<V25GenerationTask> = wire {
        api.createTask(projectId, TaskCreateRequest(deckId, chapterIds, config.toWire()), newKey()).toDomain()
    }

    override suspend fun listTasks(
        projectId: String?,
        status: V25TaskStatus?,
    ): V25Result<List<V25GenerationTask>> = wire {
        api.listTasks(projectId, status?.name).items.map { it.toDomain() }
    }

    override suspend fun getTask(taskId: String): V25Result<V25GenerationTask> =
        wire { api.getTask(taskId).toDomain() }

    override suspend fun updateTaskConfig(
        taskId: String,
        patch: V25TaskConfigPatch,
    ): V25Result<V25GenerationTask> = wire { api.updateTaskConfig(taskId, patch.toWire(), newKey()).toDomain() }

    /** The samples endpoint returns the updated task; its persisted sample cards are the payload. */
    override suspend fun generateSamples(taskId: String): V25Result<List<V25SampleCard>> =
        wire { api.generateSamples(taskId, newKey()).toDomain().sampleCards }

    override suspend fun startTask(taskId: String): V25Result<V25GenerationTask> =
        wire { api.startTask(taskId, newKey()).toDomain() }

    override suspend fun abandonTask(taskId: String): V25Result<V25GenerationTask> =
        wire { api.abandonTask(taskId, newKey()).toDomain() }

    override suspend fun retryTask(taskId: String): V25Result<V25GenerationTask> =
        wire { api.retryTask(taskId, newKey()).toDomain() }

    override suspend fun deleteTask(taskId: String, deleteGeneratedCards: Boolean): V25Result<Unit> =
        wire { api.deleteTask(taskId, if (deleteGeneratedCards) true else null, newKey()) }

    // --- decks and cards (Architecture 4.4) -----------------------------------------------------

    override suspend fun listDecks(projectId: String?): V25Result<List<V25Deck>> =
        wire { api.listDecks(projectId).items.map { it.toDomain() } }

    override suspend fun createDeck(name: String, projectId: String?, idempotencyKey: String?): V25Result<V25Deck> =
        wire { api.createDeck(CreateDeckRequest(name, projectId), idempotencyKey ?: newKey()).toDomain() }

    override suspend fun getDeck(deckId: String): V25Result<V25Deck> =
        wire { api.getDeck(deckId).toDomain() }

    override suspend fun attachDeckToProject(
        projectId: String,
        deckId: String,
        idempotencyKey: String?,
    ): V25Result<V25Deck> = wire {
        api.attachDeckToProject(projectId, deckId, idempotencyKey ?: newKey()).toDomain()
    }

    override suspend fun renameDeck(deckId: String, name: String): V25Result<V25Deck> =
        wire { api.renameDeck(deckId, RenameRequest(name), newKey()).toDomain() }

    override suspend fun deleteDeck(deckId: String, idempotencyKey: String?): V25Result<Unit> =
        wire { api.deleteDeck(deckId, idempotencyKey ?: newKey()) }

    override suspend fun getDeckDeletionPreflight(
        deckId: String,
        allowCancel: Boolean,
    ): V25Result<V25DeletionPreflight> = wire {
        api.deckDeletionPreflight(deckId, cancelActiveTasks = if (allowCancel) true else null).toDomain()
    }

    override suspend fun listCards(deckId: String, filter: V25BrowseFilter): V25Result<List<V25Card>> =
        wire {
            api.listCards(deckId, filter.order.name, filter.contentDifficulty?.name, filter.mastery.name)
                .items.map { it.toCard() }
        }

    /**
     * The atomic bulk import: one POST carries every draft, so either the whole batch lands or
     * none of it does. A retried call reuses the caller's Idempotency-Key, which the server
     * replays as the original result — a network retry can never create a second copy of a
     * partial batch (the legacy per-card loop could).
     */
    override suspend fun importCards(
        deckId: String,
        drafts: List<V25CardDraft>,
        idempotencyKey: String?,
    ): V25Result<List<V25ImportResult>> = wire {
        api.importCards(
            deckId,
            CardsImportRequest(drafts.map { CardDraftDto(it.front, it.back) }),
            idempotencyKey ?: newKey(),
        ).results.map { it.toDomain() }
    }

    override suspend fun updateCard(cardId: String, front: String, back: String): V25Result<V25Card> =
        wire { api.updateCard(cardId, CardPatchRequest(front, back), newKey()).toCard() }

    /**
     * The delete_batch_id of the last successful deletion is remembered and appended to the
     * next delete, so consecutive deletions merge into one pending batch and re-arm its
     * 10-second window (openapi deleteCard semantics). A failed delete keeps the id, so the
     * next successful attempt still joins the same still-pending batch.
     */
    override suspend fun deleteCard(cardId: String): V25Result<V25CardDeletionBatch> {
        val result = wire { api.deleteCard(cardId, lastDeleteBatchId, newKey()).toDomain() }
        if (result is V25Result.Success) lastDeleteBatchId = result.value.deleteBatchId
        return result
    }

    // --- deletion undo (Architecture 4.4 / 3.7) --------------------------------------------------

    override suspend fun pendingDeletionBatches(): V25Result<List<V25CardDeletionBatch>> =
        wire { api.pendingDeletionBatches().items.map { it.toDomain() } }

    override suspend fun undoDeletionBatch(deleteBatchId: String): V25Result<Unit> =
        wire { api.undoDeletionBatch(deleteBatchId, newKey()) }

    // --- AI rewrite previews (Architecture 4.4 / 3.8) --------------------------------------------

    override suspend fun createRewritePreview(
        cardId: String,
        customRequirements: String?,
    ): V25Result<V25CardRewritePreview> = wire {
        api.createRewritePreview(cardId, RewritePreviewRequest(customRequirements), newKey()).toDomain()
    }

    override suspend fun applyRewritePreview(cardId: String, rewriteId: String): V25Result<V25Card> =
        wire { api.applyRewritePreview(cardId, rewriteId, newKey()).toCard() }

    override suspend fun cancelRewritePreview(cardId: String, rewriteId: String): V25Result<Unit> =
        wire { api.cancelRewritePreview(cardId, rewriteId, newKey()) }

    // --- study and review (Architecture 4.5) ------------------------------------------------------

    override suspend fun getStudyPlan(): V25Result<V25StudyPlan> =
        wire { api.getStudyPlan().toDomain() }

    override suspend fun updateStudyPlan(
        plan: V25StudyPlanUpdate,
        idempotencyKey: String?,
    ): V25Result<V25StudyPlan> = wire { api.updateStudyPlan(plan.toWire(), idempotencyKey ?: newKey()).toDomain() }

    override suspend fun todayPlan(): V25Result<V25TodayPlan> =
        wire { api.todayPlan().toDomain() }

    override suspend fun studyPlanBacklog(
        offset: Int,
        limit: Int,
    ): V25Result<List<V25PlanCard>> = wire { api.studyPlanBacklog(offset, limit).items.map { it.toPlanCard() } }

    override suspend fun deckReviewQueue(deckId: String): V25Result<List<V25ReviewCard>> =
        wire { api.deckReviewQueue(deckId).items.map { it.toReviewCard() } }

    override suspend fun rateCard(
        cardId: String,
        rating: V25Rating,
        clientEventId: String?,
        idempotencyKey: String?,
    ): V25Result<V25RatingResult> = wire {
        api.submitReview(
            // client_event_id is the device-unique offline-retry idempotency identity: a retrying
            // submission reuses its original id so the server's fallback dedupe sees one event.
            ReviewEventRequest(cardId, rating.name, clientEventId ?: UUID.randomUUID().toString()),
            idempotencyKey ?: newKey(),
        ).toDomain()
    }

    // --- statistics (Architecture 4.5) ------------------------------------------------------------

    override suspend fun statsDashboard(): V25Result<V25StatsDashboard> =
        wire { api.statsDashboard().toDomain() }

    // --- AI service key (V25-SET-FR-05) -------------------------------------------------------------

    override suspend fun apiKeyStatus(): V25Result<V25ApiKeyStatus> =
        wire { api.apiKeyStatus().toDomain() }

    /**
     * The plaintext key travels only inside the PUT /api-key request body over TLS. This
     * implementation never logs, persists or echoes it: failure text is redacted below if
     * the server ever reflected it, and the key never reaches a debug log (the v25 layer
     * writes no log lines at all). Upstream verification being unavailable (502
     * API_KEY_UNAVAILABLE) is the PRD's "暂时无法验证" status, so it maps to a Success with
     * [V25ApiKeyState.VERIFICATION_UNAVAILABLE]; a failed validation keeps the old key
     * server-side (structure-contract 6.2).
     */
    override suspend fun saveApiKey(apiKey: String): V25Result<V25ApiKeyStatus> {
        val result = wire { api.saveApiKey(ApiKeyRequest(apiKey), newKey()).toDomain() }
        val failure = result as? V25Result.Failure ?: return result
        if (failure.code == "API_KEY_UNAVAILABLE") {
            return V25Result.Success(V25ApiKeyStatus(V25ApiKeyState.VERIFICATION_UNAVAILABLE, null))
        }
        return failure.withoutKey(apiKey)
    }

    // --- plumbing -----------------------------------------------------------------------------------

    /** Tracks the last pending deletion batch so consecutive deletes merge (see [deleteCard]). */
    private var lastDeleteBatchId: String? = null

    private fun newKey(): String = UUID.randomUUID().toString()

    private suspend fun <T> wire(call: suspend () -> T): V25Result<T> = try {
        V25Result.Success(call())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        failure.toFailure()
    }

    private fun Throwable.toFailure(): V25Result.Failure = when (this) {
        is HttpException -> {
            val envelope = runCatching { response()?.errorBody()?.string() }
                .getOrNull()?.let { ErrorEnvelope.parse(it) }
            V25Result.Failure(
                code = envelope?.code?.takeIf { it.isNotBlank() } ?: "HTTP_${code()}",
                localizationKey = envelope?.localizationKey,
                message = envelope?.message,
                actions = envelope?.actions.orEmpty(),
            )
        }
        is IOException -> V25Result.Failure(V25ErrorCodes.NETWORK_UNAVAILABLE)
        is SerializationException -> V25Result.Failure(V25ErrorCodes.INVALID_RESPONSE, message = message)
        // Contract violations (unknown enum, blank required value) surface as INVALID_RESPONSE.
        is IllegalArgumentException -> V25Result.Failure(V25ErrorCodes.INVALID_RESPONSE, message = message)
        else -> V25Result.Failure(V25ErrorCodes.INVALID_RESPONSE, message = message)
    }

    /** Defense in depth for [saveApiKey]: never let the plaintext cross the boundary in text. */
    private fun V25Result.Failure.withoutKey(key: String): V25Result.Failure =
        V25Result.Failure(
            code = code.replace(key, REDACTED),
            localizationKey = localizationKey?.replace(key, REDACTED),
            message = message?.replace(key, REDACTED),
            actions = actions,
        )
}

/** Frames the PDF part; the file name must never break the multipart Content-Disposition line. */
internal fun pdfPart(fileName: String, content: InputStream): MultipartBody.Part {
    val bytes = content.use { it.readBytes() }
    return MultipartBody.Part.createFormData(
        "file",
        fileName,
        bytes.toRequestBody("application/pdf".toMediaType()),
    )
}
