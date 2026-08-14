package com.qiuzhao.flashcards.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographySystemTest {
    @Test
    fun `Figma variable font axes expose every approved weight`() {
        assertEquals(listOf(150, 200, 250, 305, 330, 380, 450, 520, 630, 700), AppTypographyTokens.miSansWeights)
        assertEquals((1..9).map { it * 100 }, AppTypographyTokens.googleSansFlexWeights)
    }

    @Test
    fun `mixed text assigns only Han characters to the Chinese run`() {
        val runs = splitBilingualRuns("第 12 章：API-v2！")
        assertEquals(listOf(true to "第", false to " 12 ", true to "章", false to "：API-v2！"), runs)
        assertTrue(isHanCharacter('汉'))
        assertFalse(isHanCharacter('A'))
        assertFalse(isHanCharacter('，'))
        assertFalse(isHanCharacter('9'))
    }

    @Test
    fun `every role retains Figma language weight and maximum line height`() {
        assertEquals(700, AppTypographyTokens.spec(AppTextRole.PageTitle, AppTextLanguage.Latin).weight)
        assertEquals(630, AppTypographyTokens.spec(AppTextRole.Label, AppTextLanguage.Chinese).weight)
        assertEquals(800, AppTypographyTokens.spec(AppTextRole.Label, AppTextLanguage.Latin).weight)
        assertEquals(700, AppTypographyTokens.spec(AppTextRole.MetricXSmall, AppTextLanguage.Latin).weight)
        assertEquals(FigmaTextSpec(40f, 40f, -.6f, 700), AppTypographyTokens.spec(AppTextRole.MetricMedium, AppTextLanguage.Latin))
        assertEquals(FigmaTextSpec(20f, 27f, 0f, 400), AppTypographyTokens.spec(AppTextRole.Body, AppTextLanguage.Latin))
        assertEquals(.4f, AppTypographyTokens.spec(AppTextRole.Label, AppTextLanguage.Latin).letterSpacing)
        AppTextRole.values().forEach { role ->
            assertEquals(
                maxOf(
                    AppTypographyTokens.spec(role, AppTextLanguage.Chinese).lineHeight,
                    AppTypographyTokens.spec(role, AppTextLanguage.Latin).lineHeight
                ),
                AppTypographyTokens.lineHeight(role)
            )
        }
    }
}
