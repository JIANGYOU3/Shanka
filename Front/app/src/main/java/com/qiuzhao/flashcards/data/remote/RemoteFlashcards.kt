package com.qiuzhao.flashcards.data.remote

/**
 * Server-facing UI presentation models shared by the Compose screens. They are the stable
 * visual projection of the typed V2.5 domain models: the repositories map network payloads
 * into these, and no screen ever sees a DTO, HTTP status or JSON object.
 *
 * The handwritten HttpURLConnection/org.json transport that used to live in this file was
 * replaced by the unified Retrofit/OkHttp stack (`data/remote/http/NetworkStack.kt` +
 * `AuthNetwork.kt` + `v25/`), so this file now holds only the pure models the visual lane
 * already consumes.
 */

data class DeckSummary(
    val id: String,
    val name: String,
    val chapter: Int? = null,
    val source: String = "MANUAL",
    val themeKey: String = "azure",
    val cardCount: Int = 0,
    val dueCount: Int = 0,
    val masteredCards: Int = 0,
    val reviewCount: Int = 0,
    val masteryRatio: Float? = null,
    val notStartedCount: Int = 0,
    val learningCount: Int = 0,
    val relearningCount: Int = 0,
    val consolidatingCount: Int = 0,
    val masteredLifecycleCount: Int = 0,
    val reviewEventCount: Int = 0,
    val lastStudiedAt: String? = null,
    /** Null is an independent deck (Architecture 3.8)；projects own their decks. */
    val projectId: String? = null,
)

data class ProjectSummary(
    val id: String,
    val name: String,
    val themeKey: String = "azure",
    val deckCount: Int = 0,
    val materialCount: Int = 0,
    /** Server-aggregated lifecycle (EMPTY/PARSING/.../READY); drives the empty-project guide. */
    val status: String = "READY",
)

data class DeckProgress(
    val cardCount: Int,
    val dueCount: Int,
    val masteredCards: Int,
    val reviewCount: Int
)

data class FlashcardEntity(
    val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val code: String? = null,
    val position: Int = 0,
    val source: String = "MANUAL",
    val version: Int = 0,
    val sourceMaterialId: String? = null,
    val sourceLocator: String? = null
)

enum class Rating { AGAIN, HARD, GOOD, EASY }

data class ApiKeyStatus(val status: String, val maskedKey: String)
data class PdfChapter(val id: String, val name: String, val startPage: Int, val endPage: Int)
data class PdfFile(val id: String, val name: String, val status: String, val errorCode: String? = null, val chapters: List<PdfChapter> = emptyList())
