package com.qiuzhao.flashcards.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure gates of the Figma 977:4937 plan page: every user — first-time or
 * configured — must have a checked project and a learnable deck picked before
 * the save button unlocks.
 */
class StudyGoalFormTest {

    @Test
    fun `complete form saves`() {
        assertTrue(
            studyGoalCanSave(
                seeded = true, saving = false,
                validGoals = true, hasProject = true, hasLearnableSelection = true,
            )
        )
    }

    @Test
    fun `missing project or missing learnable deck selection blocks saving`() {
        assertFalse(
            studyGoalCanSave(
                seeded = true, saving = false,
                validGoals = true, hasProject = false, hasLearnableSelection = true,
            )
        )
        assertFalse(
            studyGoalCanSave(
                seeded = true, saving = false,
                validGoals = true, hasProject = true, hasLearnableSelection = false,
            )
        )
    }

    @Test
    fun `unsaved in-flight or unseeded or invalid goal states never save`() {
        assertFalse(
            studyGoalCanSave(
                seeded = false, saving = false,
                validGoals = true, hasProject = true, hasLearnableSelection = true,
            )
        )
        assertFalse(
            studyGoalCanSave(
                seeded = true, saving = true,
                validGoals = true, hasProject = true, hasLearnableSelection = true,
            )
        )
        assertFalse(
            studyGoalCanSave(
                seeded = true, saving = false,
                validGoals = false, hasProject = true, hasLearnableSelection = true,
            )
        )
    }
}
