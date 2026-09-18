package com.qiuzhao.flashcards.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qiuzhao.flashcards.domain.v25.V25Card
import com.qiuzhao.flashcards.domain.v25.V25CardType
import com.qiuzhao.flashcards.domain.v25.V25Difficulty
import com.qiuzhao.flashcards.domain.v25.V25PublicationState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Device-local usage facts on a real in-memory Room database: per-day rows must accumulate
 * (never overwrite) and stay day-scoped, a signed-out delta must drop rather than crash, and
 * the deck difficulty mix must aggregate off the card projection while unlabeled cards stay
 * uncounted. Lifetime duration is server truth (V25-D-37) and intentionally has no local table.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalUsageStoreTest {
    private var user: String? = "u-1"
    private lateinit var db: ShankaV25Database
    private lateinit var cache: V25CacheStore
    private lateinit var store: LocalUsageStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ShankaV25Database::class.java,
        ).build()
        cache = V25CacheStore(db)
        store = LocalUsageStore(db) { user }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `today's seconds accumulate into the same day row per deck`() = runBlocking {
        val now = 1_700_000_000_000L
        store.addTodayStudySeconds(mapOf("d-1" to 60L), nowMs = now)
        store.addTodayStudySeconds(mapOf("d-1" to 30L, "d-2" to 10L), nowMs = now + 1)

        val today = store.observeDeckDailyActivity(nowMs = now).first()
        assertEquals(90L, today["d-1"]?.studySeconds)
        assertEquals(10L, today["d-2"]?.studySeconds)
    }

    @Test
    fun `daily reviews and seconds accumulate into the same day row`() = runBlocking {
        val today = 1_700_000_000_000L
        store.addDeckReview("d-1", nowMs = today)
        store.addDeckReview("d-1", nowMs = today + 1)
        store.addTodayStudySeconds(mapOf("d-1" to 60L), nowMs = today + 2)

        val todayRow = store.observeDeckDailyActivity(nowMs = today).first()
        assertEquals(2, todayRow["d-1"]?.reviewedCount)
        assertEquals(60L, todayRow["d-1"]?.studySeconds)
    }

    @Test
    fun `yesterday's activity never leaks into today`() = runBlocking {
        val today = 1_700_000_000_000L
        store.addDeckReview("d-1", nowMs = today - 86_400_000)
        store.addTodayStudySeconds(mapOf("d-1" to 30L), nowMs = today - 86_400_000)

        assertEquals(emptyMap<String, DeckDailyActivity>(), store.observeDeckDailyActivity(nowMs = today).first())
    }

    @Test
    fun `a signed-out review drops instead of attributing to nobody`() = runBlocking {
        user = null
        store.addDeckReview("d-1", nowMs = 1_000)
        user = "u-1"
        assertEquals(emptyMap<String, DeckDailyActivity>(), store.observeDeckDailyActivity(nowMs = 1_000).first())
    }

    @Test
    fun `a signed-out delta drops instead of attributing to nobody`() = runBlocking {
        user = null
        store.addTodayStudySeconds(mapOf("d-1" to 60L), nowMs = 1_000)
        user = "u-1"
        assertEquals(emptyMap<String, DeckDailyActivity>(), store.observeDeckDailyActivity(nowMs = 1_000).first())
    }

    @Test
    fun `difficulty counts aggregate per deck and skip unlabeled cards`() = runBlocking {
        cache.replaceDeckCards(
            "u-1", "d-1",
            listOf(
                card(1, "d-1", V25Difficulty.BASIC),
                card(2, "d-1", V25Difficulty.BASIC),
                card(3, "d-1", V25Difficulty.DEEP_QUESTION),
                card(4, "d-1", null),
            ),
            now = 1_000,
        )
        cache.replaceDeckCards("u-1", "d-2", listOf(card(5, "d-2", V25Difficulty.UNDERSTANDING)), now = 1_000)

        assertEquals(
            mapOf("BASIC" to 2, "DEEP_QUESTION" to 1),
            store.observeDeckDifficultyCounts("d-1").first(),
        )
        assertEquals(
            mapOf("UNDERSTANDING" to 1),
            store.observeDeckDifficultyCounts("d-2").first(),
        )
    }

    private fun card(index: Int, deckId: String, difficulty: V25Difficulty?) = V25Card(
        cardId = "card-$index",
        deckId = deckId,
        front = "front $index",
        back = "back $index",
        cardType = V25CardType.QUESTION,
        targetDifficulty = difficulty,
        position = index,
        chapterId = null,
        sourceTaskId = null,
        publicationState = V25PublicationState.PUBLISHED,
        version = 1,
    )
}
