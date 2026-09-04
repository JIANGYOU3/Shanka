package com.qiuzhao.flashcards.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks the materials-list render state on the JVM (V25-GEN-AC-06): server-backed materials
 * surface the contract status as 解析中/解析失败/就绪, TEXT materials show their character count,
 * and creation-flow drafts (no server status) keep the extension/import-date line.
 */
class MaterialStatusLineTest {

    private fun material(
        type: ProjectDraftMaterialType,
        serverStatus: String?,
        charCount: Int? = null,
        errorCode: String? = null,
    ) = ProjectDraftMaterial(
        id = "card",
        type = type,
        title = "资料",
        extension = if (type == ProjectDraftMaterialType.FILE) "pdf" else null,
        serverStatus = serverStatus,
        charCount = charCount,
        errorCode = errorCode,
    )

    @Test
    fun `local drafts keep the extension line instead of a server status`() {
        assertNull(materialStatusLine(material(ProjectDraftMaterialType.FILE, serverStatus = null)))
    }

    @Test
    fun `parsing and pending pdf materials render as 解析中`() {
        assertEquals("解析中", materialStatusLine(material(ProjectDraftMaterialType.FILE, "PARSING")))
        assertEquals("解析中", materialStatusLine(material(ProjectDraftMaterialType.FILE, "PENDING")))
    }

    @Test
    fun `failed pdf materials render as 解析失败 with the backend error code`() {
        assertEquals(
            "解析失败 · PDF_PARSE_FAILED",
            materialStatusLine(material(ProjectDraftMaterialType.FILE, "FAILED", errorCode = "PDF_PARSE_FAILED")),
        )
        assertEquals("解析失败", materialStatusLine(material(ProjectDraftMaterialType.FILE, "FAILED")))
    }

    @Test
    fun `parsed pdf materials render as 就绪`() {
        assertEquals("就绪", materialStatusLine(material(ProjectDraftMaterialType.FILE, "PARSED")))
    }

    @Test
    fun `text materials render as 就绪 with their character count`() {
        assertEquals("就绪 · 30000字", materialStatusLine(material(ProjectDraftMaterialType.TEXT, "READY", charCount = 30000)))
        assertEquals("就绪", materialStatusLine(material(ProjectDraftMaterialType.TEXT, "READY")))
    }
}
