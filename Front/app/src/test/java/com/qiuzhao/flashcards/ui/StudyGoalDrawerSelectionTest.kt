package com.qiuzhao.flashcards.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 展开卡组抽屉的选中迁移（Figma 977:4937 范围区）：已配置用户带着现有计划的
 * 回填选中进入修改计划页，展开抽屉必须保留这些选中——整项目勾选仅迁移为逐
 * 卡组勾选，绝不出现「一展开就全没选」。
 */
class StudyGoalDrawerSelectionTest {

    private val decks = setOf("deck-1", "deck-2", "deck-3")

    @Test
    fun `whole-checked project migrates to per-deck picks`() {
        val (whole, picked) = drawerOpenSelection(
            projectId = "p1", projectDeckIds = decks,
            wholeProjectIds = setOf("p1"), selectedDeckIds = emptySet(),
        )
        assertEquals(emptySet<String>(), whole)
        assertEquals(decks, picked)
    }

    @Test
    fun `plan-seeded selection survives opening the drawer`() {
        val seeded = setOf("deck-1", "deck-2")
        val (whole, picked) = drawerOpenSelection(
            projectId = "p1", projectDeckIds = decks,
            wholeProjectIds = emptySet(), selectedDeckIds = seeded,
        )
        assertEquals(emptySet<String>(), whole)
        assertEquals(seeded, picked)
    }

    @Test
    fun `whole-check wins over stale partial picks`() {
        val (whole, picked) = drawerOpenSelection(
            projectId = "p1", projectDeckIds = decks,
            wholeProjectIds = setOf("p1"), selectedDeckIds = setOf("deck-1", "other-deck"),
        )
        assertEquals(emptySet<String>(), whole)
        assertEquals(decks + "other-deck", picked)
    }

    @Test
    fun `other projects are untouched`() {
        val (whole, picked) = drawerOpenSelection(
            projectId = "p1", projectDeckIds = decks,
            wholeProjectIds = setOf("p1", "p2"), selectedDeckIds = setOf("deck-x"),
        )
        assertEquals(setOf("p2"), whole)
        assertEquals(decks + "deck-x", picked)
    }

    @Test
    fun `unchecked project or no learnable decks is a no-op`() {
        val (whole, picked) = drawerOpenSelection(
            projectId = "p1", projectDeckIds = decks,
            wholeProjectIds = emptySet(), selectedDeckIds = emptySet(),
        )
        assertEquals(emptySet<String>(), whole)
        assertEquals(emptySet<String>(), picked)

        val pendingOnly = drawerOpenSelection(
            projectId = "p1", projectDeckIds = emptySet(),
            wholeProjectIds = setOf("p1"), selectedDeckIds = emptySet(),
        )
        assertEquals(setOf("p1"), pendingOnly.first)
        assertEquals(emptySet<String>(), pendingOnly.second)
    }
}
