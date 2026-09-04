package com.qiuzhao.flashcards.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportParserTest {
    @Test fun `parses notebooklm question answer pairs`() {
        val result = ImportParser.parse("问题：什么是 RAG？\n答案：检索增强生成。\n---\nQ: MCP 的作用？\nA: 连接工具。")
        assertEquals(2, result.cards.size)
        assertEquals("什么是 RAG？", result.cards.first().front)
        assertEquals("连接工具。", result.cards.last().back)
    }

    @Test fun `reports question without answer`() {
        val result = ImportParser.parse("问题：没有答案怎么办？")
        assertTrue(result.cards.isEmpty())
        assertTrue(result.errors.single().contains("缺少答案"))
    }
}
