package com.qiuzhao.flashcards.ui

import androidx.compose.ui.graphics.Color
import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class AppColorSystemTest {
    @Test
    fun figmaFamiliesPreserveTheirSixVariableSteps() {
        assertEquals(
            AppColorFamily(Color(0xFFF1F9FF), Color(0xFFD9EBFF), Color(0xFFB0D7FF), Color(0xFF489FFF), Color(0xFF006EE0), Color(0xFF003C7A)),
            AppColors.Blue
        )
        assertEquals(
            AppColorFamily(Color(0xFFF3F3FF), Color(0xFFE4E4FF), Color(0xFFC8C8FF), Color(0xFF716FDD), Color(0xFF3836B7), Color(0xFF38387A)),
            AppColors.Purple
        )
        assertEquals(
            AppColorFamily(Color(0xFFEAFBEB), Color(0xFFCDEFD1), Color(0xFFA3DFAA), Color(0xFF7AC583), Color(0xFF138120), Color(0xFF1F5225)),
            AppColors.Green
        )
        assertEquals(
            AppColorFamily(Color(0xFFFFF5F9), Color(0xFFFFE2EE), Color(0xFFF9C6DB), Color(0xFFEF9BBE), Color(0xFFAA0047), Color(0xFF4E1B30)),
            AppColors.Pink
        )
        assertEquals(
            AppColorFamily(Color(0xFFFFFAEF), Color(0xFFFBEBD2), Color(0xFFFFE2B6), Color(0xFFE1975E), Color(0xFFEF6800), Color(0xFF733200)),
            AppColors.Orange
        )
    }

    @Test
    fun brandAndDeckKeysResolveToTheExpectedFamilies() {
        assertEquals(AppColors.Blue.primary, DeckThemes.first { it.key == "azure" }.primary)
        assertEquals(AppColors.Purple.primary, DeckThemes.first { it.key == "violet" }.primary)
        assertEquals(AppColors.Green.primary, DeckThemes.first { it.key == "mint" }.primary)
        assertEquals(AppColors.Pink.primary, DeckThemes.first { it.key == "coral" }.primary)
        assertEquals(AppColors.Orange.primary, DeckThemes.first { it.key == "amber" }.primary)
    }

    @Test
    fun brandMaterialSchemeAndDeckRolesUseTheNewSemanticLevels() {
        assertEquals(AppColors.BaseBackground, LightColors.background)
        assertEquals(AppColors.Card, LightColors.surface)
        assertEquals(AppColors.Blue.primary, LightColors.primary)
        assertEquals(AppColors.Blue.primarySecondary, LightColors.primaryContainer)

        val violet = DeckThemes.first { it.key == "violet" }
        assertEquals(AppColors.Purple.surface, violet.cardPanel)
        assertEquals(AppColors.Purple.primarySecondary, violet.secondary)
        assertEquals(AppColors.Purple.primarySecondary, violet.progressTrack)
        assertEquals(AppColors.Purple.primary, violet.progressFill)
        assertEquals(AppColors.Purple.primaryStrong, violet.progress)
        assertEquals(AppColors.Purple.ink, violet.strongText)
    }

    @Test
    fun projectThemeOverridesLegacyDeckThemeAndWhiteCardsKeepProjectAccents() {
        val project = ProjectSummary(id = "project-1", name = "项目", themeKey = "violet")
        val deck = DeckSummary(
            id = "deck-1", name = "卡组", chapter = 1, source = "REMOTE",
            themeKey = "azure", cardCount = 10, dueCount = 3, projectId = project.id
        )

        val theme = deckTheme(deck, listOf(project))
        val whitePalette = projectThemedCardPalette(theme, ProjectThemedCardVariant.WHITE)

        assertEquals(AppColors.Purple.primary, theme.primary)
        assertEquals(AppColors.Card, whitePalette.background)
        assertEquals(AppColors.Purple.background, whitePalette.panel)
        assertEquals(AppColors.Purple.surface, whitePalette.progressTrack)
        assertEquals(ProjectThemedCardVariant.TINTED, projectThemedCardVariant(0))
        assertEquals(ProjectThemedCardVariant.WHITE, projectThemedCardVariant(1))
    }

    @Test
    fun navigationBarUsesItsDedicatedFigmaSemanticColor() {
        assertEquals(Color(0xFF425161), AppColors.NavigationBar)
    }
}
