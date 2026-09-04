package com.qiuzhao.flashcards.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the honest-metric projections on the JVM: real counts are integers (never a
 * `0.042k`-style abbreviation) and a missing server source stays a dash instead of a
 * fabricated value.
 */
class HonestMetricsTest {

    @Test
    fun `a real count is always a plain integer`() {
        assertEquals("42", honestCount(42))
        assertEquals("999", honestCount(999))
        assertEquals("1500", honestCount(1500))
        assertEquals("0", honestCount(0))
    }

    @Test
    fun `a missing count shows a dash`() {
        assertEquals("—", honestCount(null))
    }

    @Test
    fun `a real percent keeps its value and a missing one shows a dash`() {
        assertEquals("0%", honestPercent(0))
        assertEquals("70%", honestPercent(70))
        assertEquals("100%", honestPercent(100))
        assertEquals("—", honestPercent(null))
    }
}
