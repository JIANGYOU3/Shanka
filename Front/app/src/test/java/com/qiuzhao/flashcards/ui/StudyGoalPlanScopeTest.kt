package com.qiuzhao.flashcards.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 计划的单项目范围语义（PUT /study/plan 只接受一个 project_id，且要求所选
 * 卡组全部属于它——跨项目提交必然 404 DECK_NOT_FOUND）：勾选任一项目或卡组
 * 都把整个范围切过去，提交集合只含主项目。
 */
class StudyGoalPlanScopeTest {

    private val p1Decks = setOf("d1", "d2")
    private val p2Decks = setOf("d3")
    private val learnableByProject = mapOf("p1" to p1Decks, "p2" to p2Decks)

    @Test
    fun `checking a project switches the whole scope to it`() {
        val (whole, picked) = projectToggleSelection(
            projectId = "p2", projectDeckIds = p2Decks,
            wholeProjectIds = setOf("p1"), selectedDeckIds = emptySet(),
        )
        assertEquals(setOf("p2"), whole)
        assertEquals(emptySet<String>(), picked)
    }

    @Test
    fun `checking a project drops another project's per-deck picks`() {
        val (whole, picked) = projectToggleSelection(
            projectId = "p2", projectDeckIds = p2Decks,
            wholeProjectIds = emptySet(), selectedDeckIds = setOf("d1"),
        )
        assertEquals(setOf("p2"), whole)
        assertEquals(emptySet<String>(), picked)
    }

    @Test
    fun `unchecking a project keeps other projects' picks`() {
        val (whole, picked) = projectToggleSelection(
            projectId = "p1", projectDeckIds = p1Decks,
            wholeProjectIds = setOf("p1"), selectedDeckIds = emptySet(),
        )
        assertEquals(emptySet<String>(), whole)
        assertEquals(emptySet<String>(), picked)
    }

    @Test
    fun `picking a deck moves the scope to its project`() {
        val (whole, picked) = deckToggleSelection(
            deckId = "d3", projectDeckIds = p2Decks,
            wholeProjectIds = setOf("p1"), selectedDeckIds = setOf("d1"),
        )
        assertEquals(emptySet<String>(), whole)
        assertEquals(setOf("d3"), picked)
    }

    @Test
    fun `picking keeps own project's picks and migrates whole check`() {
        val (whole, picked) = deckToggleSelection(
            deckId = "d2", projectDeckIds = p1Decks,
            wholeProjectIds = setOf("p1"), selectedDeckIds = setOf("d1"),
        )
        assertEquals(emptySet<String>(), whole)
        assertEquals(setOf("d1", "d2"), picked)
    }

    @Test
    fun `unpicking a deck is scope preserving`() {
        val (whole, picked) = deckToggleSelection(
            deckId = "d1", projectDeckIds = p1Decks,
            wholeProjectIds = emptySet(), selectedDeckIds = setOf("d1", "d2"),
        )
        assertEquals(emptySet<String>(), whole)
        assertEquals(setOf("d2"), picked)
    }

    @Test
    fun `scope resolves whole-checked project to all its learnable decks`() {
        val (project, decks) = studyGoalScope(
            projectIds = listOf("p1", "p2"),
            wholeProjectIds = setOf("p1"),
            selectedDeckIds = setOf("d3"),
            learnableDeckIdsByProject = learnableByProject,
        )
        assertEquals("p1", project)
        assertEquals(p1Decks, decks)
    }

    @Test
    fun `scope resolves to the project holding per-deck picks`() {
        val (project, decks) = studyGoalScope(
            projectIds = listOf("p1", "p2"),
            wholeProjectIds = emptySet(),
            selectedDeckIds = setOf("d2"),
            learnableDeckIdsByProject = learnableByProject,
        )
        assertEquals("p1", project)
        assertEquals(setOf("d2"), decks)
    }

    @Test
    fun `scope never leaks decks from other projects`() {
        val (project, decks) = studyGoalScope(
            projectIds = listOf("p1", "p2"),
            wholeProjectIds = setOf("p1"),
            selectedDeckIds = setOf("d3"),
            learnableDeckIdsByProject = learnableByProject,
        )
        assertEquals("p1", project)
        assertEquals(setOf("d1", "d2"), decks)
    }

    @Test
    fun `empty selection resolves to no scope`() {
        val (project, decks) = studyGoalScope(
            projectIds = listOf("p1", "p2"),
            wholeProjectIds = emptySet(),
            selectedDeckIds = emptySet(),
            learnableDeckIdsByProject = learnableByProject,
        )
        assertEquals("", project)
        assertEquals(emptySet<String>(), decks)
    }
}
