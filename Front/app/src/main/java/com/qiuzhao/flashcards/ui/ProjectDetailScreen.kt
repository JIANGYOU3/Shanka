package com.qiuzhao.flashcards.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import kotlin.math.roundToInt

/** Figma 540:3778: a project owns a statistics and deck-management view. */
@Composable
internal fun ProjectDetailScreen(project: ProjectSummary, decks: List<DeckSummary>, nav: ScreenNavigator, onDeleteDeck: (String) -> Unit) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = deckTheme(project)
    var section by rememberSaveable { mutableStateOf(ProjectDetailSection.STATISTICS) }
    // The coloured project canvas uses the family Background token; every
    // project-owned deck card then lifts to that family's Surface token.
    Box(Modifier.fillMaxSize().background(theme.background)) {
        ScreenTopInformationBar(
            title = project.name, subtitle = null, onBack = nav::goBack,
            backContainer = theme.cardPanel,
            onTrailingAction = { nav.navigate(AppRoute.ProjectEdit(project.id)) },
            trailingActionSymbol = "edit", trailingActionDescription = "编辑项目",
            trailingActionContainer = theme.cardPanel
        )
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            ProjectSectionSwitcher(section, { section = it }, theme = deckTheme(project))
            when (section) {
                ProjectDetailSection.STATISTICS -> ProjectStatisticsContent(decks, theme, scale, Modifier.weight(1f))
                ProjectDetailSection.DECKS -> ProjectDecksContent(project, decks, scale, nav, onDeleteDeck, Modifier.weight(1f))
            }
        }
        if (section == ProjectDetailSection.DECKS) {
            BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = theme.background)
            ProjectDeckActions(
                theme = deckTheme(project),
                scale = scale,
                onAddDeck = { nav.navigate(AppRoute.DeckGeneration(project.id)) },
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f)
            )
        }
    }
}

@Composable
private fun ProjectStatisticsContent(decks: List<DeckSummary>, theme: DeckTheme, scale: Float, modifier: Modifier) {
    var showToday by rememberSaveable { mutableStateOf(true) }
    val totalCards = decks.sumOf { it.cardCount }
    val mastered = decks.sumOf { it.masteredCards }
    val due = decks.sumOf { it.dueCount }
    val reviewed = if (showToday) due else decks.sumOf { it.reviewCount }
    val ratio = if (totalCards == 0) 0f else mastered.toFloat() / totalCards
    LazyColumn(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)),
        // Statistics has no fixed bottom action bar. A 32dp tail places the
        // final cards just above the system navigation area, as in Figma.
        contentPadding = PaddingValues(bottom = (NaturalScrollTail * scale).dp),
        verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
    ) {
        item {
            LearningDataProgressCard(
                reviewedCards = reviewed,
                totalCards = totalCards,
                progressPercent = (ratio * 100).toInt(),
                todaySelected = showToday,
                onTodaySelected = { showToday = it },
                theme = theme,
                designScale = scale
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
                StatisticsMetricCard(
                    value = if (showToday) "12min" else "2.4h",
                    kind = StatisticsMetricKind.LearningTime,
                    surface = StatisticsMetricSurface.White,
                    designScale = scale,
                    modifier = Modifier.weight(1f)
                )
                StatisticsMetricCard(
                    value = if (showToday) mastered.coerceAtMost(2).toString() else mastered.toString(),
                    kind = StatisticsMetricKind.MasteredCards,
                    surface = StatisticsMetricSurface.White,
                    designScale = scale,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item { ProjectProgressDistribution(scale) }
        item { ProjectStreakMetrics(scale) }
    }
}

@Composable
private fun ProjectProgressDistribution(scale: Float) = ReviewProgressCard(
    entries = listOf(
        // Project statistics are mutually exclusive and total 100%.
        ReviewProgressEntry("熟识", AppColors.ReviewKnown, 2, 12),
        ReviewProgressEntry("认识", AppColors.ReviewRecognised, 8, 12),
        ReviewProgressEntry("模糊", AppColors.ReviewUncertain, 57, 68),
        ReviewProgressEntry("陌生", AppColors.ReviewUnfamiliar, 8, 12),
        ReviewProgressEntry("没学", AppColors.ReviewUnseen, 25, 30)
    ),
    designScale = scale
)

/** Figma 540:4661, the lower pair of project-only summary cards. */
@Composable
private fun ProjectStreakMetrics(scale: Float) = Row(
    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
) {
    StatisticsMetricCard(
        value = "12",
        kind = StatisticsMetricKind.LongestStreak,
        surface = StatisticsMetricSurface.White,
        designScale = scale,
        modifier = Modifier.weight(1f)
    )
    StatisticsMetricCard(
        value = "4",
        kind = StatisticsMetricKind.OpenCount,
        surface = StatisticsMetricSurface.White,
        designScale = scale,
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun ProjectDecksContent(project: ProjectSummary, decks: List<DeckSummary>, scale: Float, nav: ScreenNavigator, onDeleteDeck: (String) -> Unit, modifier: Modifier) = LazyColumn(
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)), contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
) {
    itemsIndexed(decks, key = { _, deck -> deck.id }) { _, deck ->
        val theme = deckTheme(project)
        val progress = deck.masteryRatio ?: if (deck.cardCount == 0) 0f else deck.masteredCards.toFloat() / deck.cardCount
        ProjectSwipeAuto(
            actions = listOf(ProjectSwipeAction("delete", "删除", AppColors.Warning, AppColors.TextIconLight, { onDeleteDeck(deck.id) })),
            scale = scale
        ) {
            ProjectThemedCard(
                title = displayDeckTitle(deck),
                count = deck.cardCount,
                countLabel = "cards",
                progress = progress,
                theme = theme,
                icon = "heap_snapshot_multiple",
                variant = ProjectThemedCardVariant.THEME_BACKGROUND,
                designScale = scale,
                onClick = { nav.navigate(AppRoute.Deck(deck.id)) }
            )
        }
    }
}

/** Figma 494:1447 / 540:3778 deck-management fixed actions. */
@Composable
private fun ProjectDeckActions(
    theme: DeckTheme,
    scale: Float,
    onAddDeck: () -> Unit,
    modifier: Modifier = Modifier
) = Surface(
    onClick = onAddDeck,
    color = theme.primary,
    contentColor = theme.onPrimary,
    shape = RoundedCornerShape((24 * scale).dp),
    modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp).height((60 * scale).dp)
) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol("note_stack_add", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
            Spacer(Modifier.width((8 * scale).dp)); AppText("添加卡片组", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
        }
}
