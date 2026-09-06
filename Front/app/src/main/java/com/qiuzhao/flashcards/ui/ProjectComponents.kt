package com.qiuzhao.flashcards.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A stable model keeps the bottom bar independent from the current route graph. */
internal data class AppBottomNavigationItem(
    val label: String,
    val symbol: String,
    val onClick: () -> Unit
)

/** Project-detail's two equal secondary destinations. */
internal enum class ProjectDetailSection { STATISTICS, DECKS }

private const val FigmaSelectionDurationMillis = 500

/**
 * Material-card import date, e.g. "26/8/11" (Figma 167:9679 shows the same
 * yy/M/d shape). Rendered in the device time zone; null drafts show a dash
 * instead of a fabricated date.
 */
internal val importDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yy/M/d", Locale.US)

internal fun formatImportDate(importedAt: Instant?): String =
    importedAt?.let { importDateFormatter.format(it.atZone(ZoneId.systemDefault())) } ?: "—"


/**
 * Figma 987:5203. The floating light root navigation: a translucent near-white
 * pill with a 12dp inset track of three 94×60 items and an animated #B0D7FF
 * selection indicator. Figma's `backdropFilter: blur(12px)` softens content
 * scrolling beneath; without a real backdrop-blur pass, the glass is
 * approximated by a stronger near-white fill (0xB3) so underlying text reads
 * as blurred-to-milk rather than plainly visible through the pill.
 */
@Composable
internal fun AppBottomNavigation(
    selectedIndex: Int,
    items: List<AppBottomNavigationItem>,
    modifier: Modifier = Modifier
) {
    require(items.size == 3) { "The Figma bottom navigation has exactly three destinations." }
    require(selectedIndex in items.indices) { "Selected bottom-navigation item must exist." }
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Surface(
        // Figma fill rgba(250,253,255,0.5) + blur(12px); the raised 0xB3 alpha
        // stands in for the blur. Its 0 4 16 rgba(218,218,218,0.5) shadow rides
        // on the container modifier below.
        color = Color(0xB3FAFDFF),
        shape = RoundedCornerShape((AppShapeRadius * designScale).dp),
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = (16 * designScale).dp, end = (16 * designScale).dp, bottom = (16 * designScale).dp)
            .shadow(
                elevation = (8 * designScale).dp,
                shape = RoundedCornerShape((AppShapeRadius * designScale).dp),
                ambientColor = NavigationShadowColor,
                spotColor = NavigationShadowColor
            )
            .height((84 * designScale).dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding((12 * designScale).dp)) {
            val itemGap = (32 * designScale).dp
            val itemWidth = (maxWidth - itemGap * 2) / 3
            val density = LocalDensity.current
            // Figma motion does not export a keyframe payload for the tab switch.
            // This duration is the user's explicit Smart Animate setting: 500ms, 轻巧.
            // Translation is a render-layer property, so switching root tabs does
            // not trigger a navigation-bar remeasure on each animation frame.
            val indicatorTranslationPx by animateFloatAsState(
                targetValue = with(density) { ((itemWidth + itemGap) * selectedIndex).toPx() },
                animationSpec = tween(durationMillis = FigmaSelectionDurationMillis, easing = FastOutSlowInEasing),
                label = "bottom navigation selection indicator"
            )
            Surface(
                color = AppColors.Blue.primarySecondary,
                shape = RoundedCornerShape((24 * designScale).dp),
                modifier = Modifier.width(itemWidth).fillMaxHeight()
                    .graphicsLayer { translationX = indicatorTranslationPx }
            ) {}
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(itemGap)) {
                for (index in items.indices) {
                    val item = items[index]
                    AppBottomNavigationItemContent(
                        item = item,
                        selected = index == selectedIndex,
                        designScale = designScale,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Figma 987:5203's soft grey elevation tint, never pure black. */
private val NavigationShadowColor = Color(0x80DADADA)

@Composable
private fun AppBottomNavigationItemContent(
    item: AppBottomNavigationItem,
    selected: Boolean,
    designScale: Float,
    modifier: Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) AppColors.Blue.ink else AppColors.TextIconDark,
        animationSpec = tween(durationMillis = FigmaSelectionDurationMillis, easing = FastOutSlowInEasing),
        label = "${item.label} navigation color"
    )
    Surface(
        onClick = item.onClick,
        color = Color.Transparent,
        contentColor = contentColor,
        shape = RoundedCornerShape((24 * designScale).dp),
        modifier = modifier.fillMaxSize().semantics(mergeDescendants = true) {
            contentDescription = if (selected) "${item.label}，当前页面" else item.label
            this.selected = selected
            role = Role.Tab
        }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            MaterialSymbol(
                item.symbol,
                null,
                tint = contentColor,
                size = fixedSp(24f * designScale),
                filled = selected
            )
            Spacer(Modifier.height((4 * designScale).dp))
            Text(
                text = item.label,
                color = contentColor,
                style = navigationBarLabelTextStyle(selected, designScale),
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Figma 540:4273: project data statistics / deck-management switcher. */
@Composable
internal fun ProjectSectionSwitcher(
    selected: ProjectDetailSection,
    onSelect: (ProjectDetailSection) -> Unit,
    theme: DeckTheme = DeckThemes.first(),
    modifier: Modifier = Modifier
) {
    Surface(
        color = theme.cardPanel,
        shape = RoundedCornerShape(AppShapeRadius.dp),
        modifier = modifier.fillMaxWidth().height(84.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
            val itemGap = 12.dp
            val itemWidth = (maxWidth - itemGap) / 2
            val density = LocalDensity.current
            val indicatorTranslationPx by animateFloatAsState(
                targetValue = with(density) {
                    (if (selected == ProjectDetailSection.STATISTICS) 0.dp else itemWidth + itemGap).toPx()
                },
                animationSpec = tween(durationMillis = FigmaSelectionDurationMillis, easing = FastOutSlowInEasing),
                label = "project detail section indicator"
            )
            Surface(
                // This selection track is a Figma product token, not the
                // device's dynamic Material primary color.
                color = theme.primary,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.width(itemWidth).fillMaxHeight()
                    .graphicsLayer { translationX = indicatorTranslationPx }
            ) {}
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(itemGap)) {
                ProjectSectionItem(
                    section = ProjectDetailSection.STATISTICS,
                    label = "数据统计",
                    symbol = "monitoring",
                    selected = selected == ProjectDetailSection.STATISTICS,
                    theme = theme,
                    onClick = { onSelect(ProjectDetailSection.STATISTICS) },
                    modifier = Modifier.weight(1f)
                )
                ProjectSectionItem(
                    section = ProjectDetailSection.DECKS,
                    label = "卡组管理",
                    symbol = "style",
                    selected = selected == ProjectDetailSection.DECKS,
                    theme = theme,
                    onClick = { onSelect(ProjectDetailSection.DECKS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProjectSectionItem(
    section: ProjectDetailSection,
    label: String,
    symbol: String,
    selected: Boolean,
    theme: DeckTheme,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val color by animateColorAsState(
        targetValue = if (selected) theme.onPrimary else theme.text,
        animationSpec = tween(durationMillis = FigmaSelectionDurationMillis, easing = FastOutSlowInEasing),
        label = "$section project section color"
    )
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        contentColor = color,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxHeight().semantics(mergeDescendants = true) {
            contentDescription = if (selected) "项目内容切换：$label，当前选中" else "项目内容切换：$label"
            this.selected = selected
            role = Role.Tab
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
        ) {
            MaterialSymbol(symbol, null, tint = color, size = fixedSp(24f), filled = true)
            Spacer(Modifier.width(8.dp))
            AppText(label, AppTextRole.Label, color = color, maxLines = 1)
        }
    }
}

