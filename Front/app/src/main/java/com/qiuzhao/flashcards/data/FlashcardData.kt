package com.qiuzhao.flashcards.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val chapter: Int? = null,
    val source: String = "imported",
    @ColumnInfo(defaultValue = "'azure'") val themeKey: String = "azure",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "cards",
    foreignKeys = [ForeignKey(
        entity = DeckEntity::class,
        parentColumns = ["id"],
        childColumns = ["deckId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val front: String,
    val back: String,
    val code: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "review_states",
    foreignKeys = [ForeignKey(
        entity = FlashcardEntity::class,
        parentColumns = ["id"],
        childColumns = ["cardId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ReviewStateEntity(
    @PrimaryKey val cardId: Long,
    val nextReviewAt: Long = 0,
    val intervalStep: Int = 0,
    val masteredCount: Int = 0,
    val lastRating: String? = null,
    val lastReviewedAt: Long? = null
)

@Entity(
    tableName = "review_history",
    foreignKeys = [ForeignKey(
        entity = FlashcardEntity::class,
        parentColumns = ["id"],
        childColumns = ["cardId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ReviewHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val rating: String,
    val reviewedAt: Long = System.currentTimeMillis()
)

data class DeckSummary(
    val id: Long,
    val name: String,
    val chapter: Int?,
    val source: String,
    val themeKey: String,
    val cardCount: Int,
    val dueCount: Int
)

/** Aggregated chapter progress for the deck-detail screen. */
data class DeckProgress(
    val cardCount: Int,
    val dueCount: Int,
    val masteredCards: Int,
    val reviewCount: Int
)

@Dao
interface FlashcardDao {
    @Query("SELECT d.id, d.name, d.chapter, d.source, d.themeKey, COUNT(c.id) AS cardCount, SUM(CASE WHEN r.nextReviewAt <= :now THEN 1 ELSE 0 END) AS dueCount FROM decks d LEFT JOIN cards c ON c.deckId = d.id LEFT JOIN review_states r ON r.cardId = c.id GROUP BY d.id ORDER BY CASE WHEN d.chapter IS NULL THEN 1 ELSE 0 END, d.chapter, d.createdAt")
    fun observeDecks(now: Long): Flow<List<DeckSummary>>

    @Query("SELECT COUNT(*) FROM review_states WHERE nextReviewAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("""
        SELECT
            (SELECT COUNT(*) FROM cards WHERE deckId = :deckId) AS cardCount,
            (SELECT COUNT(*) FROM cards c
                JOIN review_states r ON r.cardId = c.id
                WHERE c.deckId = :deckId AND r.nextReviewAt <= :now) AS dueCount,
            (SELECT COUNT(*) FROM cards c
                JOIN review_states r ON r.cardId = c.id
                WHERE c.deckId = :deckId AND r.masteredCount >= 3) AS masteredCards,
            (SELECT COUNT(*) FROM review_history h
                JOIN cards c ON c.id = h.cardId
                WHERE c.deckId = :deckId) AS reviewCount
    """)
    fun observeDeckProgress(deckId: Long, now: Long): Flow<DeckProgress>

    @Query("SELECT COUNT(*) FROM decks WHERE source = 'builtin'")
    suspend fun builtinDeckCount(): Int

    @Insert
    suspend fun insertDeck(deck: DeckEntity): Long

    @Query("DELETE FROM decks WHERE id = :deckId")
    suspend fun deleteDeck(deckId: Long)

    @Query("UPDATE decks SET name = :name, themeKey = :themeKey WHERE id = :deckId")
    suspend fun updateDeckPresentation(deckId: Long, name: String, themeKey: String)

    @Insert
    suspend fun insertCards(cards: List<FlashcardEntity>): List<Long>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM cards WHERE deckId = :deckId")
    suspend fun nextCardPosition(deckId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewStates(states: List<ReviewStateEntity>)

    @Query("SELECT c.* FROM cards c JOIN review_states r ON r.cardId = c.id WHERE c.deckId = :deckId AND r.nextReviewAt <= :now ORDER BY r.nextReviewAt, c.position")
    suspend fun dueCards(deckId: Long, now: Long): List<FlashcardEntity>

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY position")
    suspend fun allCards(deckId: Long): List<FlashcardEntity>

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY position")
    fun observeAllCards(deckId: Long): Flow<List<FlashcardEntity>>

    @Update
    suspend fun updateCard(card: FlashcardEntity)

    @Delete
    suspend fun deleteCard(card: FlashcardEntity)

    @Query("SELECT * FROM review_states WHERE cardId = :cardId")
    suspend fun reviewState(cardId: Long): ReviewStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReviewState(state: ReviewStateEntity)

    @Insert
    suspend fun insertHistory(history: ReviewHistoryEntity)

    /** Raw review timestamps used by the local dashboard when the optional API is unavailable. */
    @Query("SELECT reviewedAt FROM review_history WHERE reviewedAt >= :since ORDER BY reviewedAt")
    fun observeReviewTimestamps(since: Long): Flow<List<Long>>

    @Transaction
    suspend fun importDeck(name: String, cards: List<CardDraft>): Long {
        val deckId = insertDeck(DeckEntity(name = name.trim(), source = "imported"))
        val ids = insertCards(cards.mapIndexed { index, card ->
            FlashcardEntity(deckId = deckId, front = card.front.trim(), back = card.back.trim(), code = card.code?.trim()?.ifBlank { null }, position = index)
        })
        insertReviewStates(ids.map { ReviewStateEntity(cardId = it) })
        return deckId
    }

    /** Appends imported or manually authored cards without altering the deck itself. */
    @Transaction
    suspend fun addCardsToDeck(deckId: Long, cards: List<CardDraft>) {
        val cleanCards = cards.mapNotNull { card ->
            val front = card.front.trim()
            val back = card.back.trim()
            if (front.isBlank() || back.isBlank()) null
            else CardDraft(front = front, back = back, code = card.code?.trim()?.ifBlank { null })
        }
        if (cleanCards.isEmpty()) return
        val startPosition = nextCardPosition(deckId)
        val ids = insertCards(cleanCards.mapIndexed { index, card ->
            FlashcardEntity(
                deckId = deckId,
                front = card.front,
                back = card.back,
                code = card.code,
                position = startPosition + index
            )
        })
        insertReviewStates(ids.map { ReviewStateEntity(cardId = it) })
    }
}

@Database(
    entities = [DeckEntity::class, FlashcardEntity::class, ReviewStateEntity::class, ReviewHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class FlashcardDatabase : RoomDatabase() {
    abstract fun cards(): FlashcardDao

    companion object {
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE decks ADD COLUMN themeKey TEXT NOT NULL DEFAULT 'azure'")
                // Preserve the three colour families already used by the built-in
                // learning cards when upgrading an existing installation.
                database.execSQL("UPDATE decks SET themeKey = CASE chapter WHEN 2 THEN 'violet' WHEN 3 THEN 'mint' WHEN 4 THEN 'coral' ELSE 'azure' END WHERE source = 'builtin'")
            }
        }

        fun create(context: Context): FlashcardDatabase = Room.databaseBuilder(
            context, FlashcardDatabase::class.java, "autumn-flashcards.db"
        ).addMigrations(migration1To2).fallbackToDestructiveMigration().build()
    }
}

enum class Rating { AGAIN, HARD, GOOD }

object ReviewScheduler {
    private val intervals = longArrayOf(
        TimeUnit.MINUTES.toMillis(10), TimeUnit.DAYS.toMillis(1),
        TimeUnit.DAYS.toMillis(3), TimeUnit.DAYS.toMillis(7)
    )

    fun next(state: ReviewStateEntity, rating: Rating, now: Long): ReviewStateEntity {
        val step = when (rating) {
            Rating.AGAIN -> 0
            Rating.HARD -> (state.intervalStep + 1).coerceAtMost(3)
            Rating.GOOD -> (state.intervalStep + 1).coerceAtMost(3)
        }
        val delay = when (rating) {
            Rating.AGAIN -> intervals[0]
            Rating.HARD -> if (state.intervalStep == 0) intervals[1] else (intervals[state.intervalStep.coerceAtMost(3)] * 1.2).toLong()
            Rating.GOOD -> intervals[step]
        }
        return state.copy(
            nextReviewAt = now + delay,
            intervalStep = step,
            masteredCount = if (rating == Rating.AGAIN) 0 else state.masteredCount + 1,
            lastRating = rating.name,
            lastReviewedAt = now
        )
    }
}

class FlashcardRepository(private val dao: FlashcardDao) {
    fun decks() = dao.observeDecks(System.currentTimeMillis())
    fun dueCount() = dao.observeDueCount(System.currentTimeMillis())
    fun deckProgress(deckId: Long) = dao.observeDeckProgress(deckId, System.currentTimeMillis())

    suspend fun seedIfNeeded() {
        if (dao.builtinDeckCount() > 0) return
        BuiltinDecks.all.forEach { deck ->
            val deckId = dao.insertDeck(
                DeckEntity(
                    name = deck.name,
                    chapter = deck.chapter,
                    source = "builtin",
                    themeKey = defaultThemeKey(deck.chapter)
                )
            )
            val ids = dao.insertCards(deck.cards.mapIndexed { index, it ->
                FlashcardEntity(deckId = deckId, front = it.front, back = it.back, code = it.code, position = index)
            })
            dao.insertReviewStates(ids.map { ReviewStateEntity(cardId = it) })
        }
    }

    suspend fun loadCards(deckId: Long, reviewMode: Boolean): List<FlashcardEntity> =
        if (reviewMode) dao.dueCards(deckId, System.currentTimeMillis()) else dao.allCards(deckId)

    fun cards(deckId: Long): Flow<List<FlashcardEntity>> = dao.observeAllCards(deckId)

    fun reviewTimestamps(since: Long): Flow<List<Long>> = dao.observeReviewTimestamps(since)

    suspend fun rate(cardId: Long, rating: Rating) {
        val now = System.currentTimeMillis()
        val next = ReviewScheduler.next(dao.reviewState(cardId) ?: ReviewStateEntity(cardId), rating, now)
        dao.upsertReviewState(next)
        dao.insertHistory(ReviewHistoryEntity(cardId = cardId, rating = rating.name, reviewedAt = now))
    }

    suspend fun importDeck(name: String, drafts: List<CardDraft>) = dao.importDeck(name, drafts)

    suspend fun addCardsToDeck(deckId: Long, drafts: List<CardDraft>) =
        dao.addCardsToDeck(deckId, drafts)

    suspend fun deleteDeck(deckId: Long) = dao.deleteDeck(deckId)

    suspend fun updateDeckPresentation(deckId: Long, name: String, themeKey: String) =
        dao.updateDeckPresentation(deckId, name.trim(), themeKey)

    suspend fun updateCard(card: FlashcardEntity) = dao.updateCard(card)

    suspend fun deleteCard(card: FlashcardEntity) = dao.deleteCard(card)
}

private fun defaultThemeKey(chapter: Int?): String = when (chapter) {
    2 -> "violet"
    3 -> "mint"
    4 -> "coral"
    else -> "azure"
}
