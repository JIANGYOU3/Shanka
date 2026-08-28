package com.qiuzhao.flashcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Figma 257:6634. One structural card serves Home, Project, and Project-detail
 * lists. `variant` changes only the outer/background hierarchy; every accent is
 * still resolved from the owning project theme.
 */
@Composable
internal fun ProjectThemedCard(
    title: String,
    count: Int,
    countLabel: String,
    progress: Float,
    theme: DeckTheme,
    icon: String,
    variant: ProjectThemedCardVariant,
    designScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val palette = projectThemedCardPalette(theme, variant)
    Surface(
        onClick = onClick,
        color = palette.background,
        contentColor = theme.text,
        shape = RoundedCornerShape((AppShapeRadius * designScale).dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding((20 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
        ) {
            ProjectThemedCardHeader(
                title = title,
                count = count,
                countLabel = countLabel,
                theme = theme,
                badgeColor = palette.badge,
                icon = icon,
                designScale = designScale
            )
            FigmaDeckProgressPanel(
                progress = progress,
                theme = theme,
                panelColor = palette.panel,
                remainingColor = palette.progressTrack,
                designScale = designScale
            )
            if (actionLabel != null && onAction != null) {
                Surface(
                    onClick = onAction,
                    color = theme.action,
                    contentColor = theme.onPrimary,
                    shape = RoundedCornerShape((24 * designScale).dp),
                    modifier = Modifier.fillMaxWidth().height((61 * designScale).dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((6 * designScale).dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(
                            actionLabel,
                            AppTextRole.Label,
                            designScale = designScale
                        )
                        MaterialSymbol("arrow_forward", null, tint = LocalContentColor.current, size = fixedSp(24 * designScale), filled = true)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProjectThemedCardHeader(
    title: String,
    count: Int,
    countLabel: String,
    theme: DeckTheme,
    badgeColor: Color,
    icon: String,
    designScale: Float
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy((12 * designScale).dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy((8 * designScale).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = theme.primary,
            // Figma 257:6634 uses the same 24dp corner on every colour
            // variant. A per-theme 16dp fallback made the blue/green icons
            // visibly too square in both the project list and Home card.
            shape = RoundedCornerShape((24 * designScale).dp),
            modifier = Modifier.size((56 * designScale).dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                MaterialSymbol(icon, null, tint = theme.onPrimary, size = fixedSp(24 * designScale), filled = true)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy((4 * designScale).dp)
        ) {
            AppText(
                title,
                AppTextRole.CardTitle,
                modifier = Modifier.fillMaxWidth(),
                color = theme.text,
                designScale = designScale,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy((4 * designScale).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MaterialSymbol("brightness_alert", null, tint = AppColors.WarningStrong, size = fixedSp(18 * designScale), filled = true)
                AppText(
                    "高优先级",
                    AppTextRole.CardSubtitle,
                    color = AppColors.WarningStrong,
                    designScale = designScale
                )
            }
        }
    }
    ReviewCountBadge(
        count = count,
        background = badgeColor,
        contentColor = theme.strongText,
        compactScale = designScale,
        label = countLabel
    )
}

/** Figma's card progress is two sibling rounded rectangles, never an overlay. */
@Composable
internal fun FigmaDeckProgressPanel(
    progress: Float,
    theme: DeckTheme,
    panelColor: Color,
    remainingColor: Color,
    designScale: Float,
    modifier: Modifier = Modifier
) {
    val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
    Surface(
        color = panelColor,
        shape = RoundedCornerShape((24 * designScale).dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding((theme.cardProgressPanelPadding * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((8 * designScale).dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText("进度", AppTextRole.CardSubtitle, color = theme.strongText, designScale = designScale)
                Text("$percent%", color = theme.progress, fontFamily = AppFonts.GoogleSansFlexBold, fontSize = fixedSp(20 * designScale), lineHeight = fixedSp(20 * designScale), style = figmaCardTextStyle())
            }
            Row(
                modifier = Modifier.fillMaxWidth().height((20 * designScale).dp),
                horizontalArrangement = Arrangement.spacedBy((5 * designScale).dp)
            ) {
                if (progress > 0f) {
                    // Figma 257:6634 defines the completed segment as a 97dp pill.
                    // Keeping it fixed preserves the home card's already-approved appearance.
                    Box(
                        Modifier.width((97 * designScale).dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(theme.progressFill)
                    )
                }
                Box(
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(remainingColor)
                )
            }
        }
    }
}
