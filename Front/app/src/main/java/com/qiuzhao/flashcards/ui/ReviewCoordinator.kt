package com.qiuzhao.flashcards.ui

import com.qiuzhao.flashcards.domain.v25.V25Rating
import com.qiuzhao.flashcards.domain.v25.V25RatingResult
import com.qiuzhao.flashcards.domain.v25.V25Repository
import com.qiuzhao.flashcards.domain.v25.V25Result
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The in-flight state of one user rating. It owns the operation's fixed identifiers — the
 * `client_event_id` (the server's service-layer fallback dedupe key) and the transport
 * Idempotency-Key — so a retry after a lost response replays the identical event and the server
 * can never record two ratings for one swipe. Because the attempt survives configuration changes
 * in the ViewModel, the retry reuses the same pair of keys; after a full process death it is gone
 * by design and the caller refreshes server state instead of replaying blindly.
 */
data class ReviewAttempt(
    val cardId: String,
    val rating: V25Rating,
    val clientEventId: String,
    val idempotencyKey: String,
)

/**
 * Runs one user rating as a single idempotent server call and keeps the attempt state so a retry
 * reuses both dedupe identifiers. Only a successful submission clears the attempt; a failure keeps
 * the card and the attempt so the next swipe of the same decision replays the same event.
 */
class ReviewCoordinator(private val repository: V25Repository) {

    companion object {
        /** A guarded re-entry while a submission is still running; callers ignore it silently. */
        const val IN_FLIGHT_CODE = "REVIEW_IN_FLIGHT"
    }

    private val _attempt = MutableStateFlow<ReviewAttempt?>(null)
    val attempt: StateFlow<ReviewAttempt?> = _attempt.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    /**
     * Submits (or resumes) the rating for [cardId] and [rating]. Resuming reuses the stored
     * attempt's `clientEventId` and `idempotencyKey` when both the card and the rating match; a
     * different decision starts a fresh pair of identifiers.
     */
    suspend fun submit(cardId: String, rating: V25Rating): V25Result<V25RatingResult> {
        if (_submitting.value) return V25Result.Failure(IN_FLIGHT_CODE, null, null)
        val attempt = _attempt.value?.takeIf { it.cardId == cardId && it.rating == rating }
            ?: ReviewAttempt(
                cardId = cardId,
                rating = rating,
                clientEventId = UUID.randomUUID().toString(),
                idempotencyKey = UUID.randomUUID().toString(),
            ).also { _attempt.value = it }
        _submitting.value = true
        return try {
            run(attempt)
        } finally {
            _submitting.value = false
        }
    }

    /** Clears a finished or abandoned attempt so the next rating starts fresh. */
    fun reset() {
        _attempt.value = null
    }

    private suspend fun run(attempt: ReviewAttempt): V25Result<V25RatingResult> =
        when (val result = repository.rateCard(attempt.cardId, attempt.rating, attempt.clientEventId, attempt.idempotencyKey)) {
            is V25Result.Success -> {
                // Committed: the next rating is a fresh event.
                _attempt.value = null
                result
            }
            is V25Result.Failure -> {
                // Keep the attempt so the retry replays the identical event.
                result
            }
        }
}
