package com.qiuzhao.flashcards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The eight Figma 685 statistic-card instances share one layout and typography contract. */
internal enum class StatisticsMetricKind {
    LearningTime,
    LongestStreak,
    OpenCount,
    MasteredCards
}

/** White cards are used by the card-group and project pages; tinted cards by global data. */
internal enum class StatisticsMetricSurface { White, Tinted }

private data class StatisticsMetricAppearance(
    val symbol: String,
    val label: String,
    val labelColor: Color,
    val whiteIconBackground: Color,
    val tintedIconBackground: Color,
    val tintedBackground: Color
)

/**
 * Figma 685:4593–4600.
 *
 * The 176dp frame is deliberately one dp taller than the 175dp content stack:
 * 24 + 40 + 16 + 40 + 4 + 27 + 24. That spare pixel keeps the MiSans descent
 * inside the card on physical devices without changing Figma's 24dp insets.
 */
@Composable
internal fun StatisticsMetricCard(
    value: String,
    kind: StatisticsMetricKind,
    surface: StatisticsMetricSurface,
    designScale: Float,
    modifier: Modifier = Modifier
) {
    val appearance = statisticsMetricAppearance(kind)
    val isTinted = surface == StatisticsMetricSurface.Tinted
    Surface(
        color = statisticsMetricContainerColor(kind, surface),
        shape = RoundedCornerShape((AppShapeRadius * designScale).dp),
        modifier = modifier.height((176 * designScale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
        ) {
            Surface(
                color = if (isTinted) appearance.tintedIconBackground else appearance.whiteIconBackground,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.size((40 * designScale).dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol(
                        appearance.symbol,
                        null,
                        tint = AppColors.TextIconLight,
                        size = fixedSp(24 * designScale),
                        filled = true
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy((4 * designScale).dp)
            ) {
                // Figma's Heading 4 is a 40px frame with no Android font padding.
                // Keeping that same metric makes the visual glyphs sit at the
                // intended baseline rather than being pushed toward the label.
                Box(
                    modifier = Modifier.height((40 * designScale).dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = value,
                        // Compose would otherwise constrain this font to the
                        // 40dp Figma frame before it can shape its glyphs. The
                        // frame remains 40dp; the child is measured at its real
                        // 59dp Android line height and can paint around the
                        // center baseline without clipping.
                        modifier = Modifier.requiredHeight((59 * designScale).dp),
                        color = AppColors.TextIconDark,
                        fontFamily = AppFonts.GoogleSansFlexBold,
                        fontWeight = FontWeight.Normal,
                        fontSize = fixedSp(40 * designScale),
                        lineHeight = fixedSp(40 * designScale),
                        letterSpacing = fixedSp(-.6f * designScale),
                        style = figmaCardTextStyle(),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                AppText(
                    text = appearance.label,
                    role = AppTextRole.SectionTitle,
                    modifier = Modifier.height((27 * designScale).dp),
                    color = appearance.labelColor,
                    designScale = designScale,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

private fun statisticsMetricAppearance(kind: StatisticsMetricKind): StatisticsMetricAppearance = when (kind) {
    StatisticsMetricKind.LearningTime -> StatisticsMetricAppearance(
        symbol = "acute", label = "学习时长", labelColor = Color(0xFF484100),
        whiteIconBackground = Color(0xFFB7AC4A), tintedIconBackground = Color(0xFFB7AC4A),
        tintedBackground = AppColors.Orange.surface
    )
    StatisticsMetricKind.LongestStreak -> StatisticsMetricAppearance(
        symbol = "fire_check", label = "单次最大连胜", labelColor = Color(0xFF650800),
        whiteIconBackground = Color(0xFFD94C3D), tintedIconBackground = AppColors.WarningStrong,
        tintedBackground = AppColors.Pink.surface
    )
    StatisticsMetricKind.OpenCount -> StatisticsMetricAppearance(
        symbol = "coffee", label = "打开次数", labelColor = Color(0xFF36002E),
        whiteIconBackground = Color(0xFFA63E97), tintedIconBackground = Color(0xFFA63E97),
        tintedBackground = AppColors.Purple.surface
    )
    StatisticsMetricKind.MasteredCards -> StatisticsMetricAppearance(
        symbol = "editor_choice", label = "已掌握卡片", labelColor = Color(0xFF004904),
        whiteIconBackground = Color(0xFF2E8B3A), tintedIconBackground = AppColors.Green.primaryStrong,
        tintedBackground = AppColors.Green.surface
    )
}

internal fun statisticsMetricContainerColor(
    kind: StatisticsMetricKind,
    surface: StatisticsMetricSurface
): Color = if (surface == StatisticsMetricSurface.Tinted) {
    statisticsMetricAppearance(kind).tintedBackground
} else {
    AppColors.Card
}
