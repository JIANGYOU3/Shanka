package com.qiuzhao.flashcards.data.local

import android.content.Context
import android.database.Cursor
import android.os.CancellationSignal
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.qiuzhao.flashcards.domain.v25.V25Card
import com.qiuzhao.flashcards.domain.v25.V25CardType
import com.qiuzhao.flashcards.domain.v25.V25Difficulty
import com.qiuzhao.flashcards.domain.v25.V25PlanCard
import com.qiuzhao.flashcards.domain.v25.V25PublicationState
import com.qiuzhao.flashcards.domain.v25.V25ReviewCard
import com.qiuzhao.flashcards.domain.v25.V25ReviewState
import com.qiuzhao.flashcards.domain.v25.V25TodayPlan
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Query-shape gate for the plan/queue read paths: the DAO projections must stay single-SELECT
 * JOINs. A per-card lookup regression (the old `getCard`/`getReviewState` loops, 1 + 2N selects)
 * fails the constant bounds asserted here — a passing correctness test alone cannot catch it.
 * SELECTs are counted by wrapping the open-helper factory, so every statement Room issues goes
 * through [QueryCountingFactory] regardless of call site.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class V25DaoQueryShapeTest {
    private val user = "user-shape"
    private val date = LocalDate.of(2026, 8, 31)

    private lateinit var factory: QueryCountingFactory
    private lateinit var db: ShankaV25Database
    private lateinit var store: V25CacheStore

    @Before
    fun setUp() {
        factory = QueryCountingFactory()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ShankaV25Database::class.java,
        )
            .openHelperFactory(factory)
            .build()
        store = V25CacheStore(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun card(index: Int) = V25Card(
        cardId = "card-$index",
        deckId = "deck-1",
        front = "front $index",
        back = "back $index",
        cardType = V25CardType.QUESTION,
        targetDifficulty = V25Difficulty.BASIC,
        position = index,
        chapterId = null,
        sourceTaskId = null,
        publicationState = V25PublicationState.PUBLISHED,
        version = 1,
    )

    @Test
    fun readTodayPlanIssuesConstantSelectsForFortyCards() = runBlocking {
        val plan = V25TodayPlan(
            learningTimezone = "UTC",
            studyDate = date,
            currentProject = null,
            dailyGoal = 40,
            completedCount = 0,
            dueCount = 40,
            planRemaining = 40,
            backlogCount = 0,
            cards = (1..40).map { index ->
                V25PlanCard(
                    card = card(index),
                    isNew = index <= 10,
                    reviewState = if (index % 2 == 0) V25ReviewState(state = "REVIEW") else null,
                    planKind = if (index <= 10) "NEW" else "REVIEW",
                )
            },
        )
        store.replaceTodayPlan(user, plan, now = 1_000L)

        factory.reset()
        val read = store.readTodayPlan(user, date)

        assertNotNull(read)
        assertEquals(40, read!!.cards.size)
        assertEquals("card-1", read.cards.first().card.cardId)
        assertEquals("NEW", read.cards.first().planKind)
        assertNull("odd cards carry no review state", read.cards[0].reviewState)
        assertNotNull("even cards carry their review state", read.cards[1].reviewState)
        // 2 selects: plan row + joined projection. The per-card loop this replaced issued 81.
        // The lower bound proves the counter is live, so the upper bound cannot pass vacuously.
        assertTrue(
            "readTodayPlan issued ${factory.selectCount} selects for 40 cards (N+1 regression?)",
            factory.selectCount in 2..3,
        )
    }

    @Test
    fun observeTodayPlanCardsIssuesConstantSelectsPerEmission() = runBlocking {
        val plan = V25TodayPlan(
            learningTimezone = "UTC",
            studyDate = date,
            currentProject = null,
            dailyGoal = 40,
            completedCount = 0,
            dueCount = 40,
            planRemaining = 40,
            backlogCount = 0,
            cards = (1..40).map { index ->
                V25PlanCard(card = card(index), isNew = false, reviewState = null, planKind = "REVIEW")
            },
        )
        store.replaceTodayPlan(user, plan, now = 1_000L)

        // First collection also installs Room's invalidation triggers; measure the steady state.
        assertEquals(40, store.observeTodayPlanCards(user, date).first().size)
        factory.reset()
        val cards = store.observeTodayPlanCards(user, date).first()

        assertEquals(40, cards.size)
        assertEquals("card-1", cards.first().cardId)
        assertEquals("card-40", cards.last().cardId)
        // 1 select per emission. The per-card lookup this replaced issued 41.
        assertTrue(
            "observeTodayPlanCards issued ${factory.selectCount} selects for 40 cards (N+1 regression?)",
            factory.selectCount in 1..3,
        )
    }

    @Test
    fun readDeckReviewQueueIssuesConstantSelectsForThirtyCards() = runBlocking {
        val queue = (1..30).map { index ->
            V25ReviewCard(
                card = card(index),
                reviewState = if (index % 3 == 0) V25ReviewState(state = "REVIEW") else null,
            )
        }
        store.replaceDeckReviewQueue(user, "deck-1", queue, now = 1_000L)

        factory.reset()
        val read = store.readDeckReviewQueue(user, "deck-1")

        assertEquals(30, read.size)
        assertEquals("card-1", read.first().card.cardId)
        assertNull(read[0].reviewState)
        assertEquals("REVIEW", read[2].reviewState?.state)
        // 1 select. The per-card review-state loop this replaced issued 31.
        assertTrue(
            "readDeckReviewQueue issued ${factory.selectCount} selects for 30 cards (N+1 regression?)",
            factory.selectCount in 1..2,
        )
    }
}

/** Counts every SELECT reaching SQLite; inserts/updates use compiled statements and are free. */
private class QueryCountingFactory : SupportSQLiteOpenHelper.Factory {
    var selectCount: Int = 0
        private set

    fun reset() {
        selectCount = 0
    }

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val inner = FrameworkSQLiteOpenHelperFactory().create(configuration)
        return object : SupportSQLiteOpenHelper by inner {
            override val writableDatabase: SupportSQLiteDatabase
                get() = CountingDb(inner.writableDatabase)

            override val readableDatabase: SupportSQLiteDatabase
                get() = CountingDb(inner.readableDatabase)
        }
    }

    private inner class CountingDb(private val delegate: SupportSQLiteDatabase) :
        SupportSQLiteDatabase by delegate {
        override fun query(query: SupportSQLiteQuery): Cursor {
            selectCount += 1
            return delegate.query(query)
        }

        override fun query(query: SupportSQLiteQuery, signal: CancellationSignal?): Cursor {
            selectCount += 1
            return delegate.query(query, signal)
        }
    }
}
