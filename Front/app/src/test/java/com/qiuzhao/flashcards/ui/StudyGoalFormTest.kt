package com.qiuzhao.flashcards.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure gates of the Figma 977:4937 plan page: every user — first-time or
 * configured — must have at least one learnable deck picked (any own deck,
 * V25-D-39) before the save button unlocks.
 */
class StudyGoalFormTest {

    @Test
    fun `complete form saves`() {
        assertTrue(
            studyGoalCanSave(
                seeded = true, saving = false,
                validGoals = true, hasLearnableSelection = true,
            )
        )
    }

    @Test
    fun `missing learnable deck selection blocks saving`() {
        assertFalse(
            studyGoalCanSave(
                seeded = true, saving = false,
                validGoals = true, hasLearnableSelection = false,
            )
        )
    }

    @Test
    fun `unsaved in-flight or unseeded or invalid goal states never save`() {
        assertFalse(
            studyGoalCanSave(
                seeded = false, saving = false,
                validGoals = true, hasLearnableSelection = true,
            )
        )
        assertFalse(
            studyGoalCanSave(
                seeded = true, saving = true,
                validGoals = true, hasLearnableSelection = true,
            )
        )
        assertFalse(
            studyGoalCanSave(
                seeded = true, saving = false,
                validGoals = false, hasLearnableSelection = true,
            )
        )
    }
}
