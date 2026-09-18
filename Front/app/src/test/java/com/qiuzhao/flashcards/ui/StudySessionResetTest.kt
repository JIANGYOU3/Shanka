package com.qiuzhao.flashcards.ui

import com.qiuzhao.flashcards.data.remote.FlashcardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V25-D-37 会话重置队列回归：服务端 review-all 是卡组全量可见卡（无到期过滤），重建的
 * 本轮必须完整保留服务端顺序——不得与重置前加载的到期子集求交集（旧实现曾把全量复盘
 * 截断回到期队列，如卡组 100 张、当日 6 张到期时重置后仍只剩 6 张）。
 */
class StudySessionResetTest {

    private fun card(id: String) = FlashcardEntity(id = id, deckId = "deck-1", front = "q-$id", back = "a-$id")

    @Test
    fun `reset round keeps the full server queue in server order`() {
        val reviewAll = (1..40).map { card("card-$it") }

        val queue = resetRoundQueue(reviewAll)

        assertEquals(40, queue.size)
        assertEquals(reviewAll.map { it.id }, queue.map { it.cardId })
    }

    @Test
    fun `empty deck reset resolves to the completion page`() {
        assertTrue(resetRoundQueue(emptyList()).isEmpty())
    }
}
