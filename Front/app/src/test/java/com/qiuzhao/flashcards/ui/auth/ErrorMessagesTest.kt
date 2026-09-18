package com.qiuzhao.flashcards.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the full error-code → message table (spec §4.1): every backend error code must
 * map to a dedicated, non-blank Chinese message and must never fall through to the
 * generic fallback; unknown or null codes hit the fallback. Client-side codes
 * (NETWORK_UNAVAILABLE, INVALID_RESPONSE, PDF_NOT_READY, MATERIAL_NOT_FOUND) are part
 * of the table; HTTP_nnn synthetic codes resolve by status class.
 */
class ErrorMessagesTest {

    @Test fun `every known backend code maps to a non-blank Chinese message`() {
        val codes = listOf(
            "VALIDATION_ERROR", "AUTH_REQUIRED", "AUTH_INVALID", "INVALID_CREDENTIALS",
            "EMAIL_TAKEN", "RATE_LIMITED", "IDEMPOTENCY_CONFLICT", "INTERNAL_ERROR",
            "NETWORK_UNAVAILABLE", "INVALID_RESPONSE",
            "PDF_UPLOAD_INVALID", "PDF_PARSE_FAILED", "PDF_TOC_MISSING", "PDF_AI_CHAPTERS_FAILED", "PDF_NOT_READY",
            "PDF_NOT_FOUND", "CHAPTER_NOT_FOUND", "PROJECT_NOT_FOUND", "MATERIAL_NOT_FOUND",
            "PROJECT_NAME_TAKEN",
            "PROJECT_STATE_CONFLICT", "PROJECT_HAS_ACTIVE_TASK", "API_KEY_UNAVAILABLE",
            "API_KEY_NOT_SET", "TASK_NOT_FOUND", "TASK_STATE_CONFLICT", "TASK_ZERO_CARDS",
            "SAMPLE_STALE", "TASK_NOT_RESUMABLE", "TASK_IN_PROGRESS", "GENERATION_FAILED",
            "DECK_NOT_FOUND", "CARD_NOT_FOUND", "CARD_DELETE_WINDOW_EXPIRED",
            "CARD_REWRITE_UNAVAILABLE", "CARD_VERSION_CONFLICT", "INVALID_LEARNING_TIMEZONE",
            "INVALID_PREFERENCES", "GENERATION_ITEM_CONFLICT", "IMPORT_PARSE_ERROR",
            "REWRITE_SCHEMA_INVALID", "REVIEW_EVENT_INVALID", "REVIEW_EVENT_CONFLICT",
        )
        codes.forEach { code ->
            val message = ErrorMessages.forCode(code)
            assertTrue("$code should map to a message", message.isNotBlank())
            assertNotEquals(ErrorMessages.UNKNOWN_ERROR_MESSAGE, message, "$code should not fall through")
        }
    }

    @Test fun `http synthetic codes resolve by status class and never leak the raw code`() {
        assertNotEquals(ErrorMessages.UNKNOWN_ERROR_MESSAGE, ErrorMessages.forCode("HTTP_404"))
        assertNotEquals(ErrorMessages.UNKNOWN_ERROR_MESSAGE, ErrorMessages.forCode("HTTP_429"))
        assertNotEquals(ErrorMessages.UNKNOWN_ERROR_MESSAGE, ErrorMessages.forCode("HTTP_500"))
        assertNotEquals(ErrorMessages.UNKNOWN_ERROR_MESSAGE, ErrorMessages.forCode("HTTP_502"))
        assertNotEquals(ErrorMessages.UNKNOWN_ERROR_MESSAGE, ErrorMessages.forCode("HTTP_400"))
        assertTrue(ErrorMessages.forCode("HTTP_429").contains("频繁"))
    }

    @Test fun `unknown and null codes fall back to the generic message`() {
        assertEquals(ErrorMessages.UNKNOWN_ERROR_MESSAGE, ErrorMessages.forCode("NO_SUCH_CODE"))
        assertEquals(ErrorMessages.UNKNOWN_ERROR_MESSAGE, ErrorMessages.forCode("HTTP_abc"))
        assertEquals(ErrorMessages.UNKNOWN_ERROR_MESSAGE, ErrorMessages.forCode("HTTP_302"))
        assertEquals(ErrorMessages.UNKNOWN_ERROR_MESSAGE, ErrorMessages.forCode(null))
    }
}
