package com.qiuzhao.flashcards.ui

import com.qiuzhao.flashcards.domain.v25.V25CardDraft
import com.qiuzhao.flashcards.domain.v25.V25Repository
import com.qiuzhao.flashcards.domain.v25.V25Result
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where an import lands: a brand-new deck or one that already exists. */
sealed interface ImportTarget {
    data class NewDeck(val name: String) : ImportTarget
    data class ExistingDeck(val deckId: String) : ImportTarget
}

/**
 * The in-flight state of one user import operation. It owns the operation's fixed UUIDs and the
 * draft list, and remembers the deck once it has been created. Because the attempt survives
 * configuration changes in the ViewModel, a retry after a lost response replays only the failed
 * step with the same keys — it can never create a second deck or duplicate a card batch.
 *
 * After a full process death the attempt is gone by design; the caller then refreshes server
 * state instead of replaying blindly.
 */
data class ImportAttempt(
    val target: ImportTarget,
    val drafts: List<V25CardDraft>,
    val createDeckKey: String,
    val importCardsKey: String,
    val createdDeckId: String? = null,
)

/**
 * Runs one user card import as at most two idempotent server steps (create deck, then the atomic
 * bulk import) and keeps the attempt state so a retry replays only the failed step.
 */
class ImportCoordinator(private val repository: V25Repository) {

    companion object {
        /** A guarded re-entry while a submission is still running; callers ignore it silently. */
        const val IN_FLIGHT_CODE = "IMPORT_IN_FLIGHT"
    }

    private val _attempt = MutableStateFlow<ImportAttempt?>(null)
    val attempt: StateFlow<ImportAttempt?> = _attempt.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    /**
     * Submits (or resumes) the import for [target] and [drafts] and returns the committed deck
     * id. Resuming reuses the stored attempt's keys and any already-created deck; starting a
     * different import generates fresh keys.
     */
    suspend fun submit(target: ImportTarget, drafts: List<V25CardDraft>): V25Result<String> {
        if (_submitting.value) return V25Result.Failure(IN_FLIGHT_CODE, null, null)
        val attempt = _attempt.value?.takeIf { it.target == target && it.drafts == drafts }
            ?: ImportAttempt(
                target = target,
                drafts = drafts,
                createDeckKey = UUID.randomUUID().toString(),
                importCardsKey = UUID.randomUUID().toString(),
            ).also { _attempt.value = it }
        _submitting.value = true
        return try {
            run(attempt)
        } finally {
            _submitting.value = false
        }
    }

    /** Clears a finished or abandoned attempt so the next import starts fresh. */
    fun reset() {
        _attempt.value = null
    }

    private suspend fun run(attempt: ImportAttempt): V25Result<String> {
        var deckId = attempt.createdDeckId
        if (deckId == null) {
            deckId = when (val target = attempt.target) {
                is ImportTarget.ExistingDeck -> target.deckId
                is ImportTarget.NewDeck -> when (
                    val created = repository.createDeck(target.name, idempotencyKey = attempt.createDeckKey)
                ) {
                    is V25Result.Success -> created.value.deckId
                    is V25Result.Failure -> return created
                }
            }
            // Remember the deck so a retry never creates a second one.
            _attempt.value = attempt.copy(createdDeckId = deckId)
        }
        return when (val imported = repository.importCards(deckId, attempt.drafts, idempotencyKey = attempt.importCardsKey)) {
            is V25Result.Success -> {
                _attempt.value = null
                V25Result.Success(deckId)
            }
            is V25Result.Failure -> imported
        }
    }
}
