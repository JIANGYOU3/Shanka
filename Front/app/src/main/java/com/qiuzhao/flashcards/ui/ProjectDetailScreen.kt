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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.ui.navigation.AppRoute

/** Figma 540:3778: a project owns a statistics and deck-management view. */
@Composable
internal fun ProjectDetailScreen(project: ProjectSummary, decks: List<DeckSummary>, nav: ScreenNavigator) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    var section by rememberSaveable { mutableStateOf(ProjectDetailSection.STATISTICS) }
    // Figma 540:3778 uses the pale-blue page canvas behind every white data
    // card. Keeping it solid also preserves the contrast after scrolling.
    Box(Modifier.fillMaxSize().background(AppColors.Blue.background)) {
        ScreenTopInformationBar(
            title = project.name, subtitle = null, onBack = nav::goBack,
            backContainer = AppColors.Blue.surface,
            onTrailingAction = { /* Project editing is introduced with material management. */ },
            trailingActionSymbol = "edit", trailingActionDescription = "编辑项目",
            trailingActionContainer = AppColors.Blue.surface
        )
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            ProjectSectionSwitcher(section, { section = it }, theme = deckTheme(project))
            when (section) {
                ProjectDetailSection.STATISTICS -> ProjectStatisticsContent(decks, scale, Modifier.weight(1f))
                ProjectDetailSection.DECKS -> ProjectDecksContent(project, decks, scale, nav, Modifier.weight(1f))
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
        if (section == ProjectDetailSection.DECKS) {
            ProjectDeckActions(
                theme = deckTheme(project),
                scale = scale,
                onAddDeck = { },
                onManageMaterials = { nav.navigate(AppRoute.MaterialManagement) },
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f)
            )
        }
    }
}

@Composable
private fun ProjectStatisticsContent(decks: List<DeckSummary>, scale: Float, modifier: Modifier) {
    var showToday by rememberSaveable { mutableStateOf(true) }
    val totalCards = decks.sumOf { it.cardCount }
    val mastered = decks.sumOf { it.masteredCards }
    val due = decks.sumOf { it.dueCount }
    val reviewed = if (showToday) due else decks.sumOf { it.reviewCount }
    val ratio = if (totalCards == 0) 0f else mastered.toFloat() / totalCards
    LazyColumn(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape((32 * scale).dp)), contentPadding = PaddingValues(bottom = (180 * scale).dp),
        verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
    ) {
        item {
            Surface(
                color = AppColors.Blue.primary, contentColor = AppColors.TextIconLight,
                shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding((24 * scale).dp), verticalArrangement = Arrangement.spacedBy((24 * scale).dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MaterialSymbol("local_fire_department", null, tint = LocalContentColor.current, size = fixedSp(28 * scale), filled = true)
                            Spacer(Modifier.width((8 * scale).dp))
                            AppText("学习数据", AppTextRole.CardTitle, color = LocalContentColor.current, designScale = scale)
                        }
                        OverviewSwitcher(showToday, { showToday = it }, scale)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            AppText("$reviewed", AppTextRole.MetricLarge, color = LocalContentColor.current, designScale = scale)
                            // Figma 540:4465 uses separate baseline-aligned text
                            // runs: 4dp between the large value and / total, then
                            // the CJK label directly after the fraction.
                            Spacer(Modifier.width((4 * scale).dp))
                            AppText("/ $totalCards", AppTextRole.MetricXSmall, color = AppColors.TextIconLight.copy(alpha = .75f), designScale = scale, modifier = Modifier.padding(bottom = (4 * scale).dp))
                            AppText(" 已复习", AppTextRole.CardTitle, color = AppColors.TextIconLight.copy(alpha = .75f), designScale = scale, modifier = Modifier.padding(bottom = (2 * scale).dp))
                        }
                        AppText("${(ratio * 100).toInt()}%", AppTextRole.MetricLarge, color = LocalContentColor.current, designScale = scale)
                    }
                    // 540:4465 is two adjacent pills with a visible 5dp blue
                    // separation, rather than one track painted underneath fill.
                    BoxWithConstraints(Modifier.fillMaxWidth().height((20 * scale).dp)) {
                        val gap = (5 * scale).dp
                        val completedWidth = (maxWidth - gap) * ratio.coerceIn(0f, 1f)
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            Box(Modifier.width(completedWidth).fillMaxHeight().clip(RoundedCornerShape(999.dp)).background(AppColors.Card))
                            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(999.dp)).background(AppColors.Card.copy(alpha = .5f)))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
                ProjectMetricCard("${if (showToday) "12min" else "2.4h"}", "学习时长", "acute", AppColors.Orange.primary, Modifier.weight(1f))
                ProjectMetricCard("${if (showToday) mastered.coerceAtMost(2) else mastered}", "已掌握卡片", "editor_choice", AppColors.Green.primary, Modifier.weight(1f))
            }
        }
        item { ProjectProgressDistribution(scale, totalCards, mastered) }
    }
}

@Composable
private fun OverviewSwitcher(today: Boolean, onSelect: (Boolean) -> Unit, scale: Float) = Surface(
    color = AppColors.TextIconLight, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.width((160 * scale).dp).height((61 * scale).dp)
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding((8 * scale).dp)) {
        val density = LocalDensity.current
        val itemWidth = (64 * scale).dp
        val itemGap = (12 * scale).dp
        val translationPx by animateFloatAsState(
            targetValue = with(density) { (if (today) itemWidth + itemGap else 0.dp).toPx() },
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing), label = "project overview selection"
        )
        Surface(color = AppColors.Blue.primary, shape = RoundedCornerShape((16 * scale).dp), modifier = Modifier.width(itemWidth).height((45 * scale).dp).graphicsLayer { translationX = translationPx }) {}
        Row(horizontalArrangement = Arrangement.spacedBy(itemGap)) {
            OverviewOption("总览", !today, { onSelect(false) }, itemWidth, scale)
            OverviewOption("今日", today, { onSelect(true) }, itemWidth, scale)
        }
    }
}

@Composable
private fun OverviewOption(label: String, selected: Boolean, onClick: () -> Unit, width: androidx.compose.ui.unit.Dp, scale: Float) = Surface(
    onClick = onClick,
    // The inactive pill is explicitly #EBF4FF at 50% in 540:4465; transparent
    // makes it blend into the selector and loses the designed state distinction.
    color = if (selected) Color.Transparent else AppColors.Blue.surface.copy(alpha = .5f),
    contentColor = if (selected) AppColors.TextIconLight else AppColors.TextIconDark,
    shape = RoundedCornerShape((16 * scale).dp), modifier = Modifier.width(width).height((45 * scale).dp)
) { Box(contentAlignment = Alignment.Center) { AppText(label, AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1) } }

@Composable
private fun ProjectProgressDistribution(scale: Float, total: Int, mastered: Int) = Surface(
    color = AppColors.Card, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth()
) {
    Column(Modifier.padding((24 * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { MaterialSymbol("local_fire_department", null, tint = AppColors.TextIconDark, size = fixedSp(28 * scale), filled = true); Spacer(Modifier.width((8 * scale).dp)); AppText("复习进度", AppTextRole.CardTitle, color = AppColors.TextIconDark, designScale = scale) }
        val values = listOf(
            ProgressColumn("熟识", AppColors.Green.primaryStrong, 12.dp),
            ProgressColumn("认识", AppColors.Green.primarySecondary, 27.dp),
            ProgressColumn("模糊", AppColors.Orange.primary, 87.dp),
            ProgressColumn("陌生", AppColors.WarningStrong, 19.dp),
            ProgressColumn("没学", AppColors.Blue.primarySecondary, 97.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            values.forEach { value ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
                    Box(Modifier.size((16 * scale).dp).clip(RoundedCornerShape(999.dp)).background(value.color))
                    AppText(value.label, AppTextRole.CardSubtitle, color = AppColors.TextIconDark.copy(alpha = .75f), designScale = scale, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
            values.forEach { value -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height((120 * scale).dp).clip(RoundedCornerShape((16 * scale).dp)).background(value.color.copy(alpha = .25f))) {
                    Box(Modifier.fillMaxWidth().height((value.fillHeight.value * scale).dp).align(Alignment.BottomCenter).clip(RoundedCornerShape(999.dp)).background(value.color))
                }
                Spacer(Modifier.height((8 * scale).dp)); AppText("M", AppTextRole.Label, color = AppColors.TextIconDark, designScale = scale)
            } }
        }
        AppText("$mastered / $total 张卡片已掌握", AppTextRole.CardSubtitle, color = AppColors.Blue.ink, designScale = scale)
    }
}

private data class ProgressColumn(val label: String, val color: Color, val fillHeight: androidx.compose.ui.unit.Dp)

@Composable
private fun ProjectDecksContent(project: ProjectSummary, decks: List<DeckSummary>, scale: Float, nav: ScreenNavigator, modifier: Modifier) = LazyColumn(
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape((32 * scale).dp)), contentPadding = PaddingValues(bottom = (180 * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
) {
    itemsIndexed(decks, key = { _, deck -> deck.id }) { index, deck ->
        val theme = deckTheme(project)
        val progress = deck.masteryRatio ?: if (deck.cardCount == 0) 0f else deck.masteredCards.toFloat() / deck.cardCount
        ProjectThemedCard(
            title = displayDeckTitle(deck),
            count = deck.cardCount,
            countLabel = "cards",
            progress = progress,
            theme = theme,
            icon = "heap_snapshot_multiple",
            variant = projectThemedCardVariant(index),
            designScale = scale,
            onClick = { nav.navigate(AppRoute.Deck(deck.id)) }
        )
    }
}

/** Figma 494:1447 / 540:3778 deck-management fixed actions. */
@Composable
private fun ProjectDeckActions(
    theme: DeckTheme,
    scale: Float,
    onAddDeck: () -> Unit,
    onManageMaterials: () -> Unit,
    modifier: Modifier = Modifier
) = Row(
    modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp),
    horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
) {
    Surface(onClick = onManageMaterials, color = theme.secondary, contentColor = theme.strongText, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.weight(1f).height((60 * scale).dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol("folder", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
            Spacer(Modifier.width((8 * scale).dp)); AppText("文件管理", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
        }
    }
    Surface(onClick = onAddDeck, color = theme.primary, contentColor = theme.onPrimary, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.weight(1f).height((60 * scale).dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol("note_stack_add", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
            Spacer(Modifier.width((8 * scale).dp)); AppText("添加卡片组", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
        }
    }
}
