package com.qiuzhao.flashcards.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
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


/** Figma 568:2326 glass token: rgba(250,253,255,0.5) over backdropFilter blur(12px). */
private val NavigationGlassColor = Color(0x80FAFDFF)

/** Figma 568:2326 shadow token: #DADADA at 50% opacity. */
private val NavigationShadowColor = Color(0xFFDADADA)

/**
 * Figma 568:2326 surface shadow, 0 4 16 rgba(218,218,218,0.5), on the Compose
 * drop-shadow API that the miuix floating bars use. It renders a genuinely
 * blurred shadow layer: the RenderNode elevation shadow was measured at ~1%
 * darkening on this light background — invisible — and Modifier.blur clipped
 * the spread to a hard-edged sliver, so both earlier attempts are gone.
 */
private fun navigationBarShadow(designScale: Float) = Shadow(
    radius = (16 * designScale).dp,
    color = NavigationShadowColor,
    offset = DpOffset(0.dp, (4 * designScale).dp),
    alpha = 0.5f
)

/**
 * One Figma 568:2326 glass surface (the tab pill or the round add button),
 * layered the miuix way: soft drop shadow first, then the clipped glass —
 * a real backdrop blur (haze, RenderEffect on API 31+) under the 50% frost
 * tint. On pre-RenderEffect devices haze degrades to its near-white fallback
 * scrim so text beneath still reads as frosted rather than plainly visible.
 */
@Composable
private fun GlassBarSurface(
    shape: Shape,
    designScale: Float,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = NavigationGlassColor,
        shape = shape,
        modifier = modifier
            .dropShadow(shape = shape, shadow = navigationBarShadow(designScale))
            .clip(shape)
            .hazeEffect(hazeState) {
                // Device feedback: the Figma 12px blur + 50% tint read as a
                // see-through strip on real hardware. Double the blur radius
                // and lift the frost to ~78% so background cards stop bleeding
                // through; fallback covers pre-RenderEffect devices.
                blurRadius = (24f * designScale).dp
                tints = listOf(HazeTint(Color(0x33FAFDFF)))
                fallbackTint = HazeTint(Color(0xE6FAFDFF))
            }
    ) {
        content()
    }
}

/**
 * Figma 568:2326 (NavBar). The 362dp group, centered 20dp inside the screen
 * edges on the 402dp design canvas: a 276×68 translucent near-white pill
 * holding three adjacent 88×56 tab items with an animated rgba(#CCE6FF,75%)
 * selection indicator, an 18dp fixed gap, and a separate 68×68 round
 * "添加项目" button on the right. The glass is a real backdrop blur (haze,
 * RenderEffect on API 31+); on older devices the blur degrades to the stronger
 * near-white scrim so text beneath still reads as frosted rather than plainly
 * visible.
 */
@Composable
internal fun AppBottomNavigation(
    selectedIndex: Int,
    items: List<AppBottomNavigationItem>,
    hazeState: HazeState,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    require(items.size == 3) { "The Figma bottom navigation has exactly three destinations." }
    require(selectedIndex in items.indices) { "Selected bottom-navigation item must exist." }
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Row(
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
            .padding(bottom = (16 * designScale).dp),
        horizontalArrangement = Arrangement.spacedBy((18 * designScale).dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationTabPill(selectedIndex, items, hazeState, designScale)
        NavigationAddProjectButton(hazeState, designScale, onAddClick)
    }
}

/** The left 276×68 glass pill: three 88×56 tabs over the sliding indicator. */
@Composable
private fun NavigationTabPill(
    selectedIndex: Int,
    items: List<AppBottomNavigationItem>,
    hazeState: HazeState,
    designScale: Float
) {
    val pillShape = RoundedCornerShape((AppShapeRadius * designScale).dp)
    GlassBarSurface(
        shape = pillShape,
        designScale = designScale,
        hazeState = hazeState,
        modifier = Modifier.width((276 * designScale).dp).height((68 * designScale).dp)
    ) {
        Box(Modifier.fillMaxSize().padding((6 * designScale).dp)) {
            val density = LocalDensity.current
            // miuix slides its floating-bar indicator with a critically damped
            // spring (dampingRatio 1, stiffness 300): no overshoot, settling in
            // about half a second — Figma's own 500ms 轻巧 Smart Animate feel.
            // Translation is a render-layer property, so switching root tabs
            // does not trigger a navigation-bar remeasure on each frame.
            val indicatorTranslationPx by animateFloatAsState(
                targetValue = with(density) { ((88 * designScale) * selectedIndex).dp.toPx() },
                animationSpec = spring(dampingRatio = 1f, stiffness = 300f),
                label = "bottom navigation selection indicator"
            )
            Surface(
                // Figma 568:2326 selection indicator: #CCE6FF at 75% over the glass.
                color = AppColors.Blue.surface.copy(alpha = 0.75f),
                shape = RoundedCornerShape((AppShapeRadius * designScale).dp),
                modifier = Modifier.width((88 * designScale).dp).fillMaxHeight()
                    .graphicsLayer { translationX = indicatorTranslationPx }
            ) {}
            Row(Modifier.fillMaxSize().selectableGroup()) {
                for (index in items.indices) {
                    val item = items[index]
                    AppBottomNavigationItemContent(
                        item = item,
                        selected = index == selectedIndex,
                        designScale = designScale,
                        modifier = Modifier.width((88 * designScale).dp).fillMaxHeight()
                    )
                }
            }
        }
    }
}

/** The right 68×68 round glass button: the global 添加项目 entry. */
@Composable
private fun NavigationAddProjectButton(
    hazeState: HazeState,
    designScale: Float,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    GlassBarSurface(
        shape = RoundedCornerShape(999.dp),
        designScale = designScale,
        hazeState = hazeState,
        modifier = Modifier.size((68 * designScale).dp)
    ) {
        Box(
            Modifier.fillMaxSize()
                // miuix item convention: no ripple; the press reads through a
                // whole-surface alpha drop instead (FloatingNavigationBarItem).
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .semantics(mergeDescendants = true) { contentDescription = "添加项目" }
                .graphicsLayer { alpha = if (isPressed) 0.6f else 1f },
            contentAlignment = Alignment.Center
        ) {
            MaterialSymbol(
                "add_2",
                null,
                tint = AppColors.TextIconDark,
                size = fixedSp(22.8f * designScale),
                filled = false
            )
        }
    }
}

@Composable
private fun AppBottomNavigationItemContent(
    item: AppBottomNavigationItem,
    selected: Boolean,
    designScale: Float,
    modifier: Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val contentColor by animateColorAsState(
        targetValue = if (selected) AppColors.Blue.ink else AppColors.TextIconDark,
        animationSpec = tween(durationMillis = FigmaSelectionDurationMillis, easing = FastOutSlowInEasing),
        label = "${item.label} navigation color"
    )
    Column(
        modifier = modifier.fillMaxSize()
            // miuix NavigationBarItem convention: selectable with Role.Tab and
            // no ripple inside a selectableGroup, pressed feedback via alpha.
            .selectable(
                selected = selected,
                onClick = item.onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null
            )
            .semantics(mergeDescendants = true) {
                contentDescription = if (selected) "${item.label}，当前页面" else item.label
            }
            .graphicsLayer { alpha = if (isPressed) 0.6f else 1f },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
    // 与底部导航同款 no-ripple：滑动的主题色指示器已经是选中反馈，点击不再叠涟漪。
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        color = Color.Transparent,
        contentColor = color,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxHeight().selectable(
            selected = selected,
            onClick = onClick,
            role = Role.Tab,
            interactionSource = interactionSource,
            indication = null
        ).semantics(mergeDescendants = true) {
            contentDescription = if (selected) "项目内容切换：$label，当前选中" else "项目内容切换：$label"
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

