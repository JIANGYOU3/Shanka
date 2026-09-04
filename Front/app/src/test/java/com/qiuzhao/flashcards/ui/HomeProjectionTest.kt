package com.qiuzhao.flashcards.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the home page's honesty projections on the JVM: the greeting carries the real account
 * nickname, a missing dashboard shows a dash instead of a fabricated zero, and the goal percent
 * is derived from the server plan (an unset goal stays a dash).
 */
class HomeProjectionTest {

    @Test
    fun `the greeting uses the real account nickname`() {
        assertEquals("小明，快来学习", homeGreeting("小明"))
        assertEquals("同学，快来学习", homeGreeting(null))
        assertEquals("同学，快来学习", homeGreeting(""))
    }

    @Test
    fun `a missing dashboard shows a dash for the streak instead of a zero`() {
        assertEquals("连续天数：5", homeStreakText(5))
        assertEquals("连续天数：0", homeStreakText(0))
        assertEquals("连续天数：—", homeStreakText(null))
    }

    @Test
    fun `the goal percent is derived from the real server plan`() {
        assertEquals("24%", homeGoalPercent(12, 50))
        assertEquals("100%", homeGoalPercent(60, 50))
        assertEquals("0%", homeGoalPercent(0, 50))
        assertEquals("—", homeGoalPercent(0, 0))
        assertEquals("—", homeGoalPercent(10, 0))
    }
}
