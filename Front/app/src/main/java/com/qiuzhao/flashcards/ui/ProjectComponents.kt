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
 * Figma 568:2326. The item model lets the existing Library route retain its
 * old label temporarily; Task 3 will provide the final 主页 / 项目 / 数据 model.
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
    val lightSurface = MaterialTheme.colorScheme.background.luminance() > .5f
    Surface(
        color = if (lightSurface) Color(0xFF4A545F) else Color(0xFF253644),
        shape = RoundedCornerShape((AppShapeRadius * designScale).dp),
        shadowElevation = 14.dp,
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = (16 * designScale).dp, end = (16 * designScale).dp, bottom = (16 * designScale).dp)
            .height((85 * designScale).dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding((12 * designScale).dp)) {
            val itemGap = (32 * designScale).dp
            val itemWidth = (maxWidth - itemGap * 2) / 3
            val density = LocalDensity.current
            // Figma motion does not export a keyframe payload for 568:2326.
            // This duration is the user's explicit Smart Animate setting: 500ms, 轻巧.
            // Translation is a render-layer property, so switching root tabs does
            // not trigger a navigation-bar remeasure on each animation frame.
            val indicatorTranslationPx by animateFloatAsState(
                targetValue = with(density) { ((itemWidth + itemGap) * selectedIndex).toPx() },
                animationSpec = tween(durationMillis = FigmaSelectionDurationMillis, easing = FastOutSlowInEasing),
                label = "bottom navigation selection indicator"
            )
            Surface(
                color = if (lightSurface) Color(0xFFECF5FF) else Color(0xFF36546C),
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
                        lightSurface = lightSurface,
                        designScale = designScale,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBottomNavigationItemContent(
    item: AppBottomNavigationItem,
    selected: Boolean,
    lightSurface: Boolean,
    designScale: Float,
    modifier: Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            if (lightSurface) Color(0xFF425161) else Color(0xFFE5F1FF)
        } else {
            if (lightSurface) Color(0xCCFFFFFF) else Color(0xFFB8C7D6)
        },
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
                size = fixedSp(25.263f * designScale),
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
    modifier: Modifier = Modifier
) {
    val lightSurface = MaterialTheme.colorScheme.background.luminance() > .5f
    Surface(
        color = if (lightSurface) Color(0xFFD0E7FF) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(32.dp),
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
                color = Color(0xFF489FFF),
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
                    onClick = { onSelect(ProjectDetailSection.STATISTICS) },
                    modifier = Modifier.weight(1f)
                )
                ProjectSectionItem(
                    section = ProjectDetailSection.DECKS,
                    label = "卡组管理",
                    symbol = "style",
                    selected = selected == ProjectDetailSection.DECKS,
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
    onClick: () -> Unit,
    modifier: Modifier
) {
    val color by animateColorAsState(
        targetValue = if (selected) Color(0xE6FFFFFF) else Color(0xCC000000),
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

/** Shared white metric tile used by Project statistics and Card Group overview. */
@Composable
internal fun ProjectMetricCard(
    value: String,
    label: String,
    symbol: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(32.dp),
        modifier = modifier.widthIn(min = 0.dp).height(176.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(color = accent, shape = RoundedCornerShape(999.dp), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol(symbol, null, tint = Color.White, size = fixedSp(24f), filled = true)
                }
            }
            Column {
                AppText(value, AppTextRole.MetricMedium, color = PageForegroundColor(), maxLines = 1)
                AppText(label, AppTextRole.Label, color = accent, maxLines = 1)
            }
        }
    }
}
