package com.qiuzhao.flashcards.ui

import com.qiuzhao.flashcards.data.remote.DeckSummary

/**
 * Honest metric projections shared by the deck/data/project pages on the JVM.
 *
 * - A real count below 1000 is displayed as an integer — never as a `0.042k`-style abbreviation
 *   that makes 42 mastered cards read like 42 thousand.
 * - A missing server source stays a dash (`—`); the visual slot is preserved but no value is
 *   invented to fill it.
 */
internal fun honestCount(value: Int?): String = value?.toString() ?: "—"

/** Renders a real ten-percent metric; a missing source stays a dash. */
internal fun honestPercent(value: Int?): String = value?.let { "$it%" } ?: "—"

/**
 * Real project aggregates derived only from its decks. There is no project-statistics endpoint,
 * so every project metric must be the sum of the project's decks — whose counts all come from
 * `GET /decks`. Nothing here is invented; an empty project aggregates to zeros.
 */
internal data class ProjectDeckAggregate(
    val cardCount: Int,
    val masteredCount: Int,
    val dueCount: Int,
    val reviewCount: Int,
    val notStartedCount: Int,
    val learningCount: Int,
    val relearningCount: Int,
    val consolidatingCount: Int,
)

/** Sums the real per-deck counts of one project. */
internal fun projectDeckAggregate(decks: List<DeckSummary>): ProjectDeckAggregate = ProjectDeckAggregate(
    cardCount = decks.sumOf { it.cardCount },
    masteredCount = decks.sumOf { it.masteredCards },
    dueCount = decks.sumOf { it.dueCount },
    reviewCount = decks.sumOf { it.reviewCount },
    notStartedCount = decks.sumOf { it.notStartedCount },
    learningCount = decks.sumOf { it.learningCount },
    relearningCount = decks.sumOf { it.relearningCount },
    consolidatingCount = decks.sumOf { it.consolidatingCount },
)
