package com.qiuzhao.flashcards.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V25-D-36 retryable-parse failure gating for the AI chapter-planning failure path. */
class AiChapterRetryableTest {

    @Test
    fun `ai chapter failure and missing key are retryable without re-upload`() {
        assertTrue(isAiChapterRetryable("PDF_AI_CHAPTERS_FAILED"))
        assertTrue(isAiChapterRetryable("API_KEY_NOT_SET"))
    }

    @Test
    fun `legacy parse failures stay on the replace-file path`() {
        assertFalse(isAiChapterRetryable("PDF_PARSE_FAILED"))
        assertFalse(isAiChapterRetryable("PDF_TOC_MISSING"))
        assertFalse(isAiChapterRetryable(null))
        assertFalse(isAiChapterRetryable(""))
    }
}
