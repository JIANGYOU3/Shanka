package com.qiuzhao.flashcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Figma 655:4143. The owning project's primary colour is the card colour. */
@Composable
internal fun DeckLearningDataCard(
    reviewedToday: Int,
    dailyGoal: Int,
    theme: DeckTheme,
    designScale: Float,
    modifier: Modifier = Modifier
) {
    val safeGoal = dailyGoal.coerceAtLeast(1)
    val percent = (reviewedToday.coerceIn(0, safeGoal) * 100 / safeGoal)
    LearningDataProgressCard(
        reviewedCards = reviewedToday,
        totalCards = safeGoal,
        progressPercent = percent,
        todaySelected = true,
        onTodaySelected = {},
        theme = theme,
        designScale = designScale,
        modifier = modifier
    )
}

/**
 * Figma 695:4670 / 655:4039.
 *
 * This is deliberately the one layout contract for the project-level and
 * deck-level learning-data cards. Its 225dp content geometry is fixed by
 * Figma: 24 + 61 + 24 + 48 + 24 + 20 + 24 = 225. Text is measured with a
 * larger Android line box inside those visual frames, so its real ascenders
 * and descenders can never be clipped by the Figma-height rows.
 */
@Composable
internal fun LearningDataProgressCard(
    reviewedCards: Int,
    totalCards: Int,
    progressPercent: Int,
    todaySelected: Boolean,
    onTodaySelected: (Boolean) -> Unit,
    theme: DeckTheme,
    designScale: Float,
    modifier: Modifier = Modifier
) {
    val safeTotal = totalCards.coerceAtLeast(1)
    val safePercent = progressPercent.coerceIn(0, 100)
    Surface(
        color = theme.primary,
        contentColor = theme.onPrimary,
        shape = RoundedCornerShape((AppShapeRadius * designScale).dp),
        modifier = modifier.fillMaxWidth().height((225 * designScale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((24 * designScale).dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height((61 * designScale).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.width((115 * designScale).dp).height((28 * designScale).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialSymbol(
                        "local_fire_department",
                        null,
                        tint = theme.onPrimary,
                        size = fixedSp(28 * designScale),
                        filled = true,
                        includeFontPadding = false
                    )
                    Spacer(Modifier.width((8 * designScale).dp))
                    FigmaLearningCardTitle("学习数据", theme.onPrimary, designScale)
                }
                LearningDataSwitcher(todaySelected, onTodaySelected, theme, designScale)
            }
            Row(
                modifier = Modifier.fillMaxWidth().height((48 * designScale).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    FigmaLearningLargeMetric(reviewedCards.toString(), theme.onPrimary, designScale)
                    Spacer(Modifier.width((4 * designScale).dp))
                    FigmaLearningSmallMetric("/ $safeTotal", theme.onPrimary, designScale)
                    AppText(" 已复习", AppTextRole.CardSubtitle, color = theme.onPrimary, designScale = designScale)
                }
                FigmaLearningLargeMetric("$safePercent%", theme.onPrimary, designScale)
            }
            Row(
                Modifier.fillMaxWidth().height((20 * designScale).dp),
                horizontalArrangement = Arrangement.spacedBy((5 * designScale).dp)
            ) {
                val completedWeight = safePercent / 100f
                Box(
                    Modifier.weight(completedWeight.coerceAtLeast(0.001f)).fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp)).background(theme.surface)
                )
                Box(
                    Modifier.weight((1f - completedWeight).coerceAtLeast(0.001f)).fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp)).background(theme.onPrimary.copy(alpha = .45f))
                )
            }
        }
    }
}

@Composable
private fun LearningDataSwitcher(todaySelected: Boolean, onTodaySelected: (Boolean) -> Unit, theme: DeckTheme, designScale: Float) {
    Surface(
        color = theme.surface,
        shape = RoundedCornerShape((24 * designScale).dp),
        modifier = Modifier.width((160 * designScale).dp).height((61 * designScale).dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding((8 * designScale).dp),
            horizontalArrangement = Arrangement.spacedBy((12 * designScale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LearningDataTab("总览", selected = !todaySelected, { onTodaySelected(false) }, theme, designScale)
            LearningDataTab("今日", selected = todaySelected, { onTodaySelected(true) }, theme, designScale)
        }
    }
}

@Composable
private fun LearningDataTab(label: String, selected: Boolean, onClick: () -> Unit, theme: DeckTheme, designScale: Float) = Surface(
    onClick = onClick,
    color = if (selected) theme.primary else theme.surface,
    contentColor = if (selected) theme.onPrimary else theme.text,
    shape = RoundedCornerShape((16 * designScale).dp),
    modifier = Modifier.width((64 * designScale).dp).height((45 * designScale).dp)
) {
    Box(contentAlignment = Alignment.Center) {
        AppText(
            label,
            AppTextRole.Label,
            designScale = designScale,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun FigmaLearningCardTitle(text: String, color: Color, designScale: Float) {
    AppText(
        text,
        AppTextRole.SectionTitle,
        color = color,
        designScale = designScale,
        maxLines = 1,
        softWrap = false
    )
}

@Composable
private fun FigmaLearningSmallMetric(text: String, color: Color, designScale: Float) = Box(
    modifier = Modifier.height((28 * designScale).dp),
    contentAlignment = Alignment.CenterStart
) {
    Text(
        text,
        color = color,
        fontFamily = AppFonts.GoogleSansFlexBold,
        fontWeight = FontWeight.Normal,
        fontSize = fixedSp(20 * designScale),
        lineHeight = fixedSp(28 * designScale),
        style = figmaCardTextStyle(),
        maxLines = 1,
        softWrap = false
    )
}

@Composable
private fun FigmaLearningLargeMetric(text: String, color: Color, designScale: Float) = Box(
    modifier = Modifier.height((48 * designScale).dp),
    contentAlignment = Alignment.CenterStart
) {
    Text(
        text,
        color = color,
        fontFamily = AppFonts.GoogleSansFlexBold,
        fontWeight = FontWeight.Normal,
        fontSize = fixedSp(48 * designScale),
        lineHeight = fixedSp(48 * designScale),
        style = figmaCardTextStyle(),
        maxLines = 1,
        softWrap = false
    )
}

/** Figma 297:8547. Question types retain semantic accents inside a project-tinted shell. */
@Composable
internal fun DeckQuestionTypesCard(
    foundationCards: Int,
    understandingCards: Int,
    applicationCards: Int,
    theme: DeckTheme,
    designScale: Float,
    modifier: Modifier = Modifier
) {
    Surface(color = theme.cardPanel, shape = RoundedCornerShape((AppShapeRadius * designScale).dp), modifier = modifier.fillMaxWidth().height((101 * designScale).dp)) {
        Row(Modifier.fillMaxSize().padding((12 * designScale).dp), horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
            DeckQuestionType(foundationCards, "基础记忆", Color.White, theme.text, designScale, Modifier.weight(1f))
            DeckQuestionType(understandingCards, "理解分析", theme.secondary, theme.strongText, designScale, Modifier.weight(1f))
            DeckQuestionType(applicationCards, "综合应用", AppColors.WarningSecondary, AppColors.WarningInk, designScale, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeckQuestionType(count: Int, label: String, container: Color, content: Color, designScale: Float, modifier: Modifier) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape((24 * designScale).dp), modifier = modifier.fillMaxHeight()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            MixedLanguageText("$count cards", color = content, chineseFont = AppFonts.MiSansBold, latinFont = AppFonts.GoogleSansFlexBold, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale), style = figmaCardTextStyle(), includeFontPadding = false)
            Text(label, color = content, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(21 * designScale), style = figmaCardTextStyle())
        }
    }
}

/**
 * One item in Figma's 120dp review-status column. Height is explicit because
 * the visual bucket height and the displayed percentage are independent in
 * the supplied designs; deriving one from the other visibly distorts the card.
 */
internal data class ReviewProgressEntry(
    val label: String,
    val color: Color,
    val percentage: Int,
    val fillHeight: Int
)

/** Figma 577:2464 / 297:8521: shared review-progress card geometry. */
@Composable
internal fun ReviewProgressCard(
    entries: List<ReviewProgressEntry>,
    designScale: Float,
    modifier: Modifier = Modifier
) {
    require(entries.size == 5) { "Review progress cards require exactly five Figma columns." }
    Surface(
        color = AppColors.Card,
        shape = RoundedCornerShape((AppShapeRadius * designScale).dp),
        modifier = modifier.fillMaxWidth().height((277 * designScale).dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding((24 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
        ) {
            Row(
                modifier = Modifier.height((32 * designScale).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MaterialSymbol("local_fire_department", null, tint = AppColors.TextIconDark, size = fixedSp(28 * designScale), filled = true)
                Spacer(Modifier.width((8 * designScale).dp))
                FigmaReviewTitle("复习进度", designScale)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                entries.forEach { entry -> ReviewProgressLegend(entry, designScale) }
            }
            Row(
                Modifier.fillMaxWidth().height((144 * designScale).dp),
                horizontalArrangement = Arrangement.spacedBy((12 * designScale).dp)
            ) {
                entries.forEach { entry -> ReviewProgressColumn(entry, designScale, Modifier.weight(1f)) }
            }
        }
    }
}

/** Figma 297:8521. Values remain explicit until the review API supplies buckets. */
@Composable
internal fun DeckWeeklyReviewCard(designScale: Float, modifier: Modifier = Modifier) = ReviewProgressCard(
    entries = listOf(
        ReviewProgressEntry("熟识", AppColors.ReviewKnown, 2, 12),
        ReviewProgressEntry("认识", AppColors.ReviewRecognised, 10, 27),
        ReviewProgressEntry("模糊", AppColors.ReviewUncertain, 70, 87),
        ReviewProgressEntry("陌生", AppColors.ReviewUnfamiliar, 10, 19),
        ReviewProgressEntry("没学", AppColors.ReviewUnseen, 30, 97)
    ),
    designScale = designScale,
    modifier = modifier
)

@Composable
private fun FigmaReviewTitle(text: String, designScale: Float) = Box(
    modifier = Modifier.height((27 * designScale).dp),
    contentAlignment = Alignment.CenterStart
) {
    AppText(
        text,
        AppTextRole.SectionTitle,
        modifier = Modifier.requiredHeight((34 * designScale).dp),
        color = AppColors.TextIconDark,
        designScale = designScale,
        maxLines = 1,
        softWrap = false
    )
}

@Composable
private fun ReviewProgressLegend(entry: ReviewProgressEntry, designScale: Float) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy((8 * designScale).dp)
) {
    Box(Modifier.size((16 * designScale).dp).clip(RoundedCornerShape(999.dp)).background(entry.color))
    Box(
        modifier = Modifier.height((21 * designScale).dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            entry.label,
            modifier = Modifier.requiredHeight((28 * designScale).dp),
            color = AppColors.TextIconDark.copy(alpha = .75f),
            fontFamily = AppFonts.MiSansSemibold,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(16 * designScale),
            lineHeight = fixedSp(21 * designScale),
            style = figmaCardTextStyle(),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ReviewProgressColumn(entry: ReviewProgressEntry, designScale: Float, modifier: Modifier) = Column(
    modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy((8 * designScale).dp)
) {
    val safeHeight = entry.fillHeight.coerceIn(0, 116)
    Column(
        Modifier.fillMaxWidth().height((120 * designScale).dp),
        verticalArrangement = Arrangement.spacedBy((4 * designScale).dp, Alignment.Bottom)
    ) {
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(999.dp))
                .background(entry.color.copy(alpha = .25f))
        )
        Box(
            Modifier.fillMaxWidth().height((safeHeight * designScale).dp)
                .clip(RoundedCornerShape(999.dp)).background(entry.color)
        )
    }
    Box(
        modifier = Modifier.height((16 * designScale).dp),
        contentAlignment = Alignment.Center
    ) {
        // A 16dp Figma label frame is smaller than Android's true glyph box.
        // Keep the visual frame but measure the child at its safe 24dp height.
        Text(
            "${entry.percentage}%",
            modifier = Modifier.requiredHeight((24 * designScale).dp),
            color = AppColors.TextIconDark.copy(alpha = .8f),
            fontFamily = AppFonts.GoogleSansFlexExtraBold,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(16 * designScale),
            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
            letterSpacing = fixedSp(.6f * designScale),
            style = figmaCardTextStyle(),
            maxLines = 1,
            softWrap = false
        )
    }
}
