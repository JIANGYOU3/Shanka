package com.qiuzhao.flashcards.data

data class CardDraft(val front: String, val back: String, val code: String? = null)
data class ParseResult(val cards: List<CardDraft>, val errors: List<String>)

/** Parser intentionally accepts the common text shapes copied from NotebookLM. */
object ImportParser {
    private val question = Regex("^\\s*(?:Q(?:uestion)?|问题)\\s*[:：]\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val answer = Regex("^\\s*(?:A(?:nswer)?|答案)\\s*[:：]\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val markdownQuestion = Regex("^\\s*#{1,4}\\s*(?:Q(?:uestion)?|问题)\\s*[:：]?\\s*(.+)$", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParseResult {
        val cards = mutableListOf<CardDraft>()
        val errors = mutableListOf<String>()
        var pendingQuestion: String? = null
        var sawQA = false // A question line was seen: never fall back to paragraph blocks.
        val answerLines = mutableListOf<String>()

        fun commit() {
            val q = pendingQuestion?.trim()
            val a = answerLines.joinToString("\n").trim()
            if (!q.isNullOrBlank() && a.isNotBlank()) cards += CardDraft(q, a)
            else if (!q.isNullOrBlank()) errors += "“$q” 缺少答案，已跳过。"
            pendingQuestion = null
            answerLines.clear()
        }

        text.replace("\r\n", "\n").lines().forEach { line ->
            val q = question.matchEntire(line)?.groupValues?.get(1)
                ?: markdownQuestion.matchEntire(line)?.groupValues?.get(1)
            val a = answer.matchEntire(line)?.groupValues?.get(1)
            when {
                q != null -> { sawQA = true; commit(); pendingQuestion = q }
                a != null && pendingQuestion != null -> answerLines += a
                line.trim() == "---" -> commit()
                pendingQuestion != null && answerLines.isNotEmpty() -> answerLines += line
            }
        }
        commit()

        if (cards.isEmpty() && !sawQA && text.isNotBlank()) {
            val blocks = text.trim().split(Regex("\\n\\s*\\n"))
            blocks.forEachIndexed { index, block ->
                val lines = block.lines().filter { it.isNotBlank() }
                if (lines.size >= 2) cards += CardDraft(lines.first(), lines.drop(1).joinToString("\n"))
                else errors += "第 ${index + 1} 段无法识别为一张卡片。"
            }
        }
        return ParseResult(cards, errors)
    }
}
