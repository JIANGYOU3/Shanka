package com.qiuzhao.flashcards.ui

import com.qiuzhao.flashcards.domain.v25.V25Chapter
import com.qiuzhao.flashcards.domain.v25.V25CoverageMode
import com.qiuzhao.flashcards.domain.v25.V25DifficultyRatio
import com.qiuzhao.flashcards.domain.v25.V25GenerationConfig
import com.qiuzhao.flashcards.domain.v25.V25GenerationTask
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SampleTaskResumeTest {

    private val config = V25GenerationConfig(
        coverageMode = V25CoverageMode.BALANCED,
        difficultyRatio = V25DifficultyRatio(40, 40, 20),
    )
    private val chapterIds = listOf("chapter-1")

    @Test
    fun `unfinished sample task is reused for the same input`() {
        val task = task(V25TaskStatus.DRAFT)

        assertEquals(
            task,
            reusableSampleTask(task, "project-1", null, chapterIds, config),
        )
    }

    @Test
    fun `sample task is not reused after input changes or terminal state`() {
        val draft = task(V25TaskStatus.DRAFT)

        assertNull(reusableSampleTask(draft, "project-2", null, chapterIds, config))
        assertNull(reusableSampleTask(draft, "project-1", null, listOf("chapter-2"), config))
        assertNull(
            reusableSampleTask(
                draft,
                "project-1",
                null,
                chapterIds,
                config.copy(customRequirements = "更简短"),
            ),
        )
        assertNull(reusableSampleTask(task(V25TaskStatus.FAILED), "project-1", null, chapterIds, config))
    }

    @Test
    fun `explicit deck selection must match the unfinished task`() {
        val task = task(V25TaskStatus.SAMPLE_GENERATING)

        assertEquals(task, reusableSampleTask(task, "project-1", "deck-1", chapterIds, config))
        assertNull(reusableSampleTask(task, "project-1", "deck-2", chapterIds, config))
    }

    @Test
    fun `failed sample task without cards is retried on the same deck`() {
        val task = task(V25TaskStatus.FAILED)

        assertEquals(
            task,
            retryableSampleTask(task, "project-1", null, chapterIds, config),
        )
        assertNull(
            retryableSampleTask(
                task.copy(sampleCards = listOf(sampleCard())),
                "project-1",
                null,
                chapterIds,
                config,
            ),
        )
    }

    private fun task(status: V25TaskStatus) = V25GenerationTask(
        taskId = "task-1",
        projectId = "project-1",
        fileId = "file-1",
        deckId = "deck-1",
        retryOfTaskId = null,
        status = status,
        internalStage = null,
        selectedChapters = listOf(V25Chapter("chapter-1", "material-1", "第一章", 1, 2)),
        generationConfig = config,
        sampleCards = emptyList(),
        sampleConfigHash = null,
        sampleConfirmedAt = null,
        generatedCardCount = 0,
        errorCode = null,
        failureStage = null,
        createdAt = Instant.parse("2026-08-28T00:00:00Z"),
        startedAt = null,
        endedAt = null,
        updatedAt = Instant.parse("2026-08-28T00:00:00Z"),
    )

    private fun sampleCard() = com.qiuzhao.flashcards.domain.v25.V25SampleCard(
        front = "问题",
        back = "答案",
        cardType = com.qiuzhao.flashcards.domain.v25.V25CardType.QUESTION,
        targetDifficulty = com.qiuzhao.flashcards.domain.v25.V25Difficulty.BASIC,
    )
}
