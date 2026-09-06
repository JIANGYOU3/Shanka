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
            AppColorFamily(Color(0xFFEEF4FA), Color(0xFFCCE6FF), Color(0xFFB0D7FF), Color(0xFF389DFF), Color(0xFF0063C4), Color(0xFF003C7A)),
            AppColors.Blue
        )
        assertEquals(
            AppColorFamily(Color(0xFFF3F3FF), Color(0xFFE4E4FF), Color(0xFFC8C8FF), Color(0xFF716FDD), Color(0xFF3836B7), Color(0xFF38387A)),
            AppColors.Purple
        )
        assertEquals(
            AppColorFamily(Color(0xFFEAF4E5), Color(0xFFD6EEC9), Color(0xFFB6DCA6), Color(0xFF65AA56), Color(0xFF278B00), Color(0xFF1F5225)),
            AppColors.Green
        )
        assertEquals(
            AppColorFamily(Color(0xFFF9EFF3), Color(0xFFF8DBE3), Color(0xFFFFBFD3), Color(0xFFF16692), Color(0xFFAA0047), Color(0xFF730022)),
            AppColors.Pink
        )
        assertEquals(
            AppColorFamily(Color(0xFFFAF2EB), Color(0xFFFBE7C8), Color(0xFFF4DDBA), Color(0xFFE48A4A), Color(0xFFD15700), Color(0xFF642A00)),
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
        assertEquals(AppColors.Purple.background, violet.background)
        assertEquals(AppColors.Purple.surface, violet.cardPanel)
        assertEquals(AppColors.Purple.primarySecondary, violet.secondary)
        assertEquals(AppColors.Purple.primarySecondary, violet.progressTrack)
        assertEquals(AppColors.Purple.primary, violet.progressFill)
        assertEquals(AppColors.Purple.primaryStrong, violet.progress)
        assertEquals(AppColors.Purple.ink, violet.strongText)
        assertEquals(24, violet.cardIconCornerRadius)
        assertEquals(16, violet.cardProgressPanelPadding)

        val blue = DeckThemes.first { it.key == "azure" }
        assertEquals(16, blue.cardIconCornerRadius)
        assertEquals(12, blue.cardProgressPanelPadding)
    }

    @Test
    fun projectThemeOverridesLegacyDeckThemeAndCardsFollowTheirCanvas() {
        val project = ProjectSummary(id = "project-1", name = "项目", themeKey = "violet")
        val deck = DeckSummary(
            id = "deck-1", name = "卡组", chapter = 1, source = "REMOTE",
            themeKey = "azure", cardCount = 10, dueCount = 3, projectId = project.id
        )

        val theme = deckTheme(deck, listOf(project))
        val basePagePalette = projectThemedCardPalette(theme, ProjectThemedCardVariant.BASE_PAGE)
        val themePagePalette = projectThemedCardPalette(theme, ProjectThemedCardVariant.THEME_BACKGROUND)

        assertEquals(AppColors.Purple.primary, theme.primary)
        assertEquals(AppColors.Purple.background, basePagePalette.background)
        assertEquals(AppColors.Purple.surface, basePagePalette.panel)
        assertEquals(AppColors.Purple.surface, basePagePalette.badge)
        assertEquals(AppColors.Purple.background, basePagePalette.progressTrack)
        assertEquals(AppColors.Purple.surface, themePagePalette.background)
        assertEquals(AppColors.Purple.background, themePagePalette.panel)
        assertEquals(AppColors.Purple.primarySecondary, themePagePalette.progressTrack)
    }

    @Test
    fun navigationBarUsesItsDedicatedFigmaSemanticColor() {
        assertEquals(Color(0xFF425161), AppColors.NavigationBar)
    }

    @Test
    fun warningSemanticRolesMatchTheRefreshedFigmaVariables() {
        assertEquals(Color(0xFFBD3F3F), AppColors.Warning)
        assertEquals(Color(0xFFD23535), AppColors.WarningStrong)
        assertEquals(Color(0xFFE87F77), AppColors.WarningSecondary)
        assertEquals(Color(0xFF670700), AppColors.WarningInk)
    }

    @Test
    fun rootPageBackgroundUsesTheFigmaBaseBackground() {
        assertEquals(Color.White, AppColors.BaseBackground)
        assertEquals(AppColors.BaseBackground, LightColors.background)
    }

    @Test
    fun statisticsCardsUseSurfaceOnWhiteDataAndWhiteOnThemeBackgrounds() {
        assertEquals(AppColors.Orange.surface, statisticsMetricContainerColor(StatisticsMetricKind.LearningTime, StatisticsMetricSurface.Tinted))
        assertEquals(AppColors.Pink.surface, statisticsMetricContainerColor(StatisticsMetricKind.LongestStreak, StatisticsMetricSurface.Tinted))
        assertEquals(AppColors.Purple.surface, statisticsMetricContainerColor(StatisticsMetricKind.OpenCount, StatisticsMetricSurface.Tinted))
        assertEquals(AppColors.Green.surface, statisticsMetricContainerColor(StatisticsMetricKind.MasteredCards, StatisticsMetricSurface.Tinted))
        assertEquals(AppColors.Card, statisticsMetricContainerColor(StatisticsMetricKind.LearningTime, StatisticsMetricSurface.White))
    }

    @Test
    fun sharedCornerTokensFollowTheUpdatedFigmaHierarchy() {
        assertEquals(36, AppShapeRadius)
        assertEquals(24, AppNestedShapeRadius)
        assertEquals(24, AppButtonShapeRadius)
        assertEquals(24, AppScrollableContentClipRadius)
    }
}
