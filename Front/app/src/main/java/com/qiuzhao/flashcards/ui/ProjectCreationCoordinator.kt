package com.qiuzhao.flashcards.ui

import com.qiuzhao.flashcards.domain.v25.V25ErrorCodes
import com.qiuzhao.flashcards.domain.v25.V25Repository
import com.qiuzhao.flashcards.domain.v25.V25Result
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/**
 * The in-flight state of one project-creation attempt. It owns the attempt's fixed UUIDs and
 * remembers the created project id plus every finished material upload, so a retry after a lost
 * response replays only the failed step — it can never create a second project or duplicate a
 * material. After a full process death the attempt is gone by design; the caller refreshes
 * server state instead of replaying blindly.
 */
data class ProjectCreationAttempt(
    val name: String,
    val uploads: List<MaterialUpload>,
    val createProjectKey: String,
    val materialKeys: Map<String, String>,
    val createdProjectId: String? = null,
    val uploadedDraftIds: Set<String> = emptySet(),
)

/**
 * Runs one user project creation as at most 1 + N idempotent server steps (create the EMPTY
 * project, then attach every staged material) and keeps the attempt state so a retry replays
 * only the failed step. This is the network shape of the "两步创建" contract: one POST /projects
 * with a JSON name body, then one POST materials/pdf|text per staged material.
 */
class ProjectCreationCoordinator(private val repository: V25Repository) {

    companion object {
        /** A guarded re-entry while a creation is still running; callers ignore it silently. */
        const val IN_FLIGHT_CODE = "PROJECT_CREATION_IN_FLIGHT"
    }

    private val _attempt = MutableStateFlow<ProjectCreationAttempt?>(null)
    val attempt: StateFlow<ProjectCreationAttempt?> = _attempt.asStateFlow()

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    /**
     * Submits (or resumes) the creation for [name] and [uploads] and returns the created project
     * id. Resuming reuses the stored attempt's keys, the created project and the finished
     * uploads; starting a different creation generates fresh keys.
     */
    suspend fun submit(name: String, uploads: List<MaterialUpload>): V25Result<String> {
        if (_creating.value) return V25Result.Failure(IN_FLIGHT_CODE, null, null)
        val normalized = name.trim()
        val fingerprint = uploads.map { it.fingerprint }
        val attempt = _attempt.value
            ?.takeIf { it.name == normalized && it.uploads.map(MaterialUpload::fingerprint) == fingerprint }
            ?: ProjectCreationAttempt(
                name = normalized,
                uploads = uploads,
                createProjectKey = UUID.randomUUID().toString(),
                materialKeys = uploads.associate { upload -> upload.draftId to UUID.randomUUID().toString() },
            ).also { _attempt.value = it }
        _creating.value = true
        return try {
            run(attempt)
        } finally {
            _creating.value = false
        }
    }

    /** Clears a finished or abandoned attempt so the next creation starts fresh. */
    fun reset() {
        _attempt.value = null
    }

    private suspend fun run(attempt: ProjectCreationAttempt): V25Result<String> {
        var projectId = attempt.createdProjectId
        if (projectId == null) {
            // Step one: the EMPTY project. The JSON body carries only the name; no bytes travel here.
            projectId = when (val created = repository.createProject(attempt.name, attempt.createProjectKey)) {
                is V25Result.Success -> created.value.projectId
                is V25Result.Failure -> return created
            }
            // Remember the project so a retry never creates a second one.
            _attempt.value = attempt.copy(createdProjectId = projectId)
        }
        var current = _attempt.value ?: attempt.copy(createdProjectId = projectId)
        // Step two: attach every material that has not landed yet, each with its own fixed key.
        for (upload in attempt.uploads) {
            if (upload.draftId in current.uploadedDraftIds) continue
            val key = current.materialKeys[upload.draftId] ?: UUID.randomUUID().toString()
            val result = when (upload) {
                is MaterialUpload.Pdf -> {
                    val input = upload.openStream()
                        ?: return V25Result.Failure(V25ErrorCodes.INVALID_RESPONSE, null, "无法读取所选文件")
                    input.use { content ->
                        repository.addProjectMaterialPdf(projectId, upload.materialName, content, key)
                    }
                }
                is MaterialUpload.Text ->
                    repository.addProjectMaterialText(projectId, upload.materialName, upload.content, key)
            }
            when (result) {
                is V25Result.Success -> {
                    current = current.copy(uploadedDraftIds = current.uploadedDraftIds + upload.draftId)
                    _attempt.value = current
                }
                is V25Result.Failure -> return result
            }
        }
        _attempt.value = null
        return V25Result.Success(projectId)
    }
}
