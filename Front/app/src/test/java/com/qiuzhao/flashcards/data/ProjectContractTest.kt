package com.qiuzhao.flashcards.data

import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.data.remote.MaterialStatus
import com.qiuzhao.flashcards.data.remote.MaterialType
import com.qiuzhao.flashcards.data.remote.ProjectStatisticsRange
import com.qiuzhao.flashcards.data.remote.projectDetail
import com.qiuzhao.flashcards.data.remote.projectStatistics
import com.qiuzhao.flashcards.data.remote.projectsForDisplay
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectContractTest {
    @Test fun `parses a project with two decks and scoped materials`() {
        val detail = projectDetail(JSONObject("""
            {
              "project": { "id": "project-design", "name": "世界现代设计史", "theme_key": "violet" },
              "decks": [
                { "id": "deck-bauhaus", "name": "包豪斯", "project_id": "project-design", "material_scopes": [{ "material_id": "history-pdf", "chapter_ids": ["chapter-1"] }] },
                { "id": "deck-ulm", "name": "乌尔姆学院", "project_id": "project-design", "material_scopes": [{ "material_id": "history-pdf", "chapter_ids": ["chapter-2"] }] }
              ],
              "materials": [
                { "id": "history-pdf", "name": "世界现代设计史.pdf", "type": "PDF", "status": "PARSED", "project_ids": ["project-design"] }
              ]
            }
        """))

        assertEquals("世界现代设计史", detail.project.name)
        assertEquals(2, detail.decks.size)
        assertEquals("project-design", detail.decks.first().projectId)
        assertEquals(listOf("chapter-1"), detail.decks.first().materialScopes.single().chapterIds)
        assertEquals(MaterialType.PDF, detail.materials.single().type)
        assertEquals(MaterialStatus.READY, detail.materials.single().status)
    }

    @Test fun `keeps a legacy deck in the unassigned project`() {
        val projects = projectsForDisplay(
            knownProjects = emptyList(),
            decks = listOf(
                DeckSummary(id = "legacy", name = "旧卡组"),
                DeckSummary(id = "assigned", name = "新卡组", projectId = "project-1")
            )
        )

        assertEquals("未归类项目", projects.first().name)
        assertEquals(1, projects.first().deckCount)
        assertTrue(projects.any { it.id == "project-1" && it.deckCount == 1 })
    }

    @Test fun `parses total and today statistics without fabricating values`() {
        val total = projectStatistics(JSONObject("""
            { "reviewed_count": 50, "card_count": 100, "mastered_card_count": 40, "mastery_ratio": 0.4,
              "review_state_distribution": { "NEW": 10, "REVIEW": 30 } }
        """), ProjectStatisticsRange.TOTAL)
        val today = projectStatistics(JSONObject("""
            { "reviewed_count": 12, "study_duration_minutes": 18, "mastered_card_count": 2 }
        """), ProjectStatisticsRange.TODAY)

        assertEquals(50, total.reviewedCards)
        assertEquals(100, total.cardCount)
        assertEquals(30, total.reviewStateDistribution["REVIEW"])
        assertEquals(12, today.reviewedCards)
        assertEquals(18, today.studyDurationMinutes)
        assertNull(today.masteryRatio)
    }

    @Test fun `preserves material states for UI rendering`() {
        val detail = projectDetail(JSONObject("""
            {
              "project": { "id": "project-1", "name": "测试" },
              "materials": [
                { "id": "pending", "name": "导入中.pdf", "type": "PDF", "status": "PARSING" },
                { "id": "failed", "name": "损坏.pdf", "type": "PDF", "status": "FAILED", "error_code": "PDF_PARSE_FAILED" },
                { "id": "notes", "name": "笔记.md", "type": "MD", "status": "READY" }
              ]
            }
        """))

        assertEquals(MaterialStatus.PARSING, detail.materials[0].status)
        assertEquals("PDF_PARSE_FAILED", detail.materials[1].errorCode)
        assertEquals(MaterialType.MARKDOWN, detail.materials[2].type)
    }
}
