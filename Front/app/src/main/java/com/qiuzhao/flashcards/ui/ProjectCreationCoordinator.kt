package com.qiuzhao.flashcards.ui

import com.qiuzhao.flashcards.domain.v25.V25ErrorCodes
import com.qiuzhao.flashcards.domain.v25.V25Repository
import com.qiuzhao.flashcards.domain.v25.V25Result
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One staged material of a two-step project creation (contract V25-D-29/30). PDF uploads carry
 * a stream opener instead of a stream: an InputStream is single-use, so every attempt (including
 * a retry after a lost response) reopens it, while the fixed Idempotency-Key replays the
 * identical operation server-side.
 */
sealed interface MaterialUpload {
    /** Identifies the staged draft across retries of one creation attempt. */
    val draftId: String

    /** PDF = file name; TEXT = user title (1..60 trimmed characters, server-validated). */
    val materialName: String

    /**
     * Stable operation identity used to recognise an unchanged retry. The stream lambda is
     * deliberately excluded: a new lambda instance for the same draft is the same operation.
     */
    val fingerprint: String
        get() = "${this::class.simpleName}|$draftId|$materialName"

    /** POST /projects/{project_id}/materials/pdf (multipart). */
    class Pdf(
        override val draftId: String,
        override val materialName: String,
        val openStream: () -> InputStream?,
    ) : MaterialUpload

    /** POST /projects/{project_id}/materials/text (JSON body, ≤30000 characters). */
    class Text(
        override val draftId: String,
        override val materialName: String,
        val content: String,
    ) : MaterialUpload {
        // The text body is part of the idempotent operation: changed content starts a fresh key.
        override val fingerprint: String
            get() = "${super.fingerprint}|${content.hashCode()}"
    }
}

/** UI-facing phase of one background material upload after the create step. */
enum class MaterialUploadPhase { UPLOADING, FAILED, DONE }

/** One material's live upload state, rendered on the post-creation configuration screen. */
data class MaterialUploadState(
    val draftId: String,
    val name: String,
    val isPdf: Boolean,
    val phase: MaterialUploadPhase,
    val errorCode: String? = null,
)

/**
 * The two-step creation, split for latency (contract V25-D-29): [submit] runs only step one —
 * the fast JSON POST /projects — and returns the projectId immediately, so the caller can
 * navigate while the bytes are still travelling. Step two (every staged material, each with its
 * own fixed idempotency key) then runs in the injected [scope], materials in PARALLEL — each
 * add carries its own key, and the server treats concurrent material adds as independent
 * idempotent operations.
 *
 * Attempt state survives across screens (the scope outlives them) so a retry replays only the
 * materials that never landed. A fresh creation while an older one still uploads is a distinct
 * attempt under its own projectId. After a full process death the attempt is gone by design;
 * the caller refreshes server state instead of replaying blindly.
 */
class ProjectCreationCoordinator(
    private val repository: V25Repository,
    private val scope: CoroutineScope,
    /** Fired on the scope after each material settles DONE, for a fire-and-forget refresh. */
    private val onUploadsChanged: suspend (projectId: String) -> Unit = {},
) {

    companion object {
        /** A guarded re-entry while a create step is still running; callers ignore it silently. */
        const val IN_FLIGHT_CODE = "PROJECT_CREATION_IN_FLIGHT"
    }

    /** The create-step attempt, kept until its POST /projects lands so a retry reuses the key. */
    private var pendingCreate: ProjectCreationAttempt? = null

    /** Per-project upload bookkeeping, keyed by the created projectId. */
    private val runningUploads = mutableMapOf<String, ProjectCreationAttempt>()

    private val _submitting = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _uploadStates = MutableStateFlow<Map<String, List<MaterialUploadState>>>(emptyMap())

    /** Live per-project material upload states (only in-flight/failed sets are present). */
    val uploadStates: StateFlow<Map<String, List<MaterialUploadState>>> = _uploadStates.asStateFlow()

    /** Emits the projectId each time one of its materials lands; callers re-read the lists. */
    val materialLanded = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /**
     * Runs (or resumes) the create step for [name] and [uploads] and returns the created project
     * id right away — material uploads continue in [scope]. Resuming reuses the stored attempt's
     * fixed create key; starting a different creation generates a fresh one.
     */
    suspend fun submit(name: String, uploads: List<MaterialUpload>): V25Result<String> {
        if (_submitting.value) return V25Result.Failure(IN_FLIGHT_CODE, null, null)
        val normalized = name.trim()
        val fingerprint = uploads.map { it.fingerprint }
        val attempt = pendingCreate
            ?.takeIf { it.name == normalized && it.uploads.map(MaterialUpload::fingerprint) == fingerprint }
            ?: ProjectCreationAttempt(
                name = normalized,
                uploads = uploads,
                createProjectKey = UUID.randomUUID().toString(),
                materialKeys = uploads.associate { upload -> upload.draftId to UUID.randomUUID().toString() },
            ).also { pendingCreate = it }
        _submitting.value = true
        val projectId = try {
            // Step one: the EMPTY project. The JSON body carries only the name; no bytes travel here.
            when (val created = repository.createProject(attempt.name, attempt.createProjectKey)) {
                is V25Result.Success -> created.value.projectId
                is V25Result.Failure -> return created
            }
        } finally {
            _submitting.value = false
        }
        pendingCreate = null
        runningUploads[projectId] = attempt
        publishStates(projectId)
        startUploads(projectId)
        return V25Result.Success(projectId)
    }

    /** Retries only the FAILED uploads of [projectId]; the fixed keys replay identical requests. */
    fun retryUploads(projectId: String) {
        val attempt = runningUploads[projectId] ?: return
        startUploads(projectId, onlyFailed = true, attempt = attempt)
    }

    /**
     * Starts a new creation form: drop a stale not-yet-created attempt so the next submit
     * generates a fresh key. In-flight material uploads of other projects keep running and
     * stay visible on their own screens.
     */
    fun resetPendingCreate() {
        pendingCreate = null
    }

    /** Clears every attempt and state so the next creation starts fresh (sign-out). */
    fun reset() {
        pendingCreate = null
        runningUploads.clear()
        _uploadStates.value = emptyMap()
    }

    private fun startUploads(projectId: String, onlyFailed: Boolean = false, attempt: ProjectCreationAttempt? = null) {
        val current = attempt ?: runningUploads[projectId] ?: return
        val targets = current.uploads.filter { upload ->
            val state = _uploadStates.value[projectId]?.firstOrNull { it.draftId == upload.draftId }
            when {
                onlyFailed -> state?.phase == MaterialUploadPhase.FAILED
                else -> upload.draftId !in current.uploadedDraftIds &&
                    state?.phase != MaterialUploadPhase.DONE
            }
        }
        if (targets.isEmpty()) {
            finishProjectIfSettled(projectId)
            return
        }
        setPhase(projectId, targets.map { it.draftId }, MaterialUploadPhase.UPLOADING, null)
        scope.launch {
            kotlinx.coroutines.coroutineScope {
                targets.forEach { upload ->
                    launch {
                        val key = current.materialKeys[upload.draftId] ?: UUID.randomUUID().toString()
                        val result = try {
                            when (upload) {
                                is MaterialUpload.Pdf ->
                                    upload.openStream()?.let { input ->
                                        input.use { content ->
                                            repository.addProjectMaterialPdf(projectId, upload.materialName, content, key)
                                        }
                                    } ?: V25Result.Failure(V25ErrorCodes.INVALID_RESPONSE, null, "无法读取所选文件")
                                is MaterialUpload.Text ->
                                    repository.addProjectMaterialText(projectId, upload.materialName, upload.content, key)
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            V25Result.Failure(V25ErrorCodes.NETWORK_UNAVAILABLE)
                        }
                        when (result) {
                            is V25Result.Success -> {
                                markLanded(projectId, upload.draftId)
                                materialLanded.tryEmit(projectId)
                            }
                            is V25Result.Failure ->
                                setPhase(projectId, listOf(upload.draftId), MaterialUploadPhase.FAILED, result.code)
                        }
                    }
                }
            }
            finishProjectIfSettled(projectId)
            onUploadsChanged(projectId)
        }
    }

    private fun markLanded(projectId: String, draftId: String) {
        _uploadStates.update { all ->
            val states = all[projectId].orEmpty()
            all + (projectId to states.map { state ->
                if (state.draftId == draftId) state.copy(phase = MaterialUploadPhase.DONE, errorCode = null) else state
            })
        }
        runningUploads[projectId]?.let { attempt ->
            runningUploads[projectId] = attempt.copy(uploadedDraftIds = attempt.uploadedDraftIds + draftId)
        }
    }

    private fun setPhase(projectId: String, draftIds: List<String>, phase: MaterialUploadPhase, errorCode: String?) {
        _uploadStates.update { all ->
            val states = all[projectId].orEmpty()
            all + (projectId to states.map { state ->
                if (state.draftId in draftIds) state.copy(phase = phase, errorCode = errorCode) else state
            })
        }
    }

    /** Once every material of a project is DONE, drop its state — nothing left to render. */
    private fun finishProjectIfSettled(projectId: String) {
        val states = _uploadStates.value[projectId].orEmpty()
        if (states.isNotEmpty() && states.all { it.phase == MaterialUploadPhase.DONE }) {
            _uploadStates.update { it - projectId }
            runningUploads.remove(projectId)
        }
    }

    private fun publishStates(projectId: String) {
        val attempt = runningUploads[projectId] ?: return
        val states = attempt.uploads.map { upload ->
            val done = upload.draftId in attempt.uploadedDraftIds
            MaterialUploadState(
                draftId = upload.draftId,
                name = upload.materialName,
                isPdf = upload is MaterialUpload.Pdf,
                phase = if (done) MaterialUploadPhase.DONE else MaterialUploadPhase.UPLOADING,
            )
        }
        _uploadStates.update { it + (projectId to states) }
    }
}

/**
 * The in-flight state of one project-creation attempt. It owns the attempt's fixed UUIDs and
 * remembers the created project id plus every finished material upload, so a retry after a lost
 * response replays only the failed step — it can never create a second project or duplicate a
 * material.
 */
data class ProjectCreationAttempt(
    val name: String,
    val uploads: List<MaterialUpload>,
    val createProjectKey: String,
    val materialKeys: Map<String, String>,
    val createdProjectId: String? = null,
    val uploadedDraftIds: Set<String> = emptySet(),
)
