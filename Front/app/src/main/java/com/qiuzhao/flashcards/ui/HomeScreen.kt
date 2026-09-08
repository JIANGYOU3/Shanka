package com.qiuzhao.flashcards.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qiuzhao.flashcards.R
import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.ui.navigation.AppRoute

/**
 * Figma 184:616 — the redesigned home. One 402dp design canvas; on a narrower
 * phone every visual value uses this one scale instead of responding
 * independently to display/font settings. All numbers on the page are real
 * projections of the server plan/dashboard/deck state, never Figma samples.
 */
@Composable
internal fun HomeScreen(
    decks: List<DeckSummary>,
    projects: List<ProjectSummary>,
    nickname: String?,
    todayPlan: TodayPlanUiState,
    streakDays: Int?,
    nav: ScreenNavigator,
) {
    val activeDeck = decks.firstOrNull { it.dueCount > 0 } ?: decks.firstOrNull()
    val compactScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    // The persistent root shell owns the navigation. Home owns only its scrollable
    // body: the fixed header keeps its 16+56dp bar, and the Figma 16dp gap to the
    // first card makes the 88dp viewport inset below the status bar.
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground).statusBarsPadding()) {
        Box(
            Modifier.fillMaxSize().padding(
                start = (16 * compactScale).dp,
                top = (88 * compactScale).dp,
                end = (16 * compactScale).dp
            )
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(AppScrollableContentClipRadius.dp))) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (RootNavigationScrollTail * compactScale).dp),
                    verticalArrangement = Arrangement.spacedBy((16 * compactScale).dp)
                ) {
                    item { StreakCard(compactScale, streakDays) }
                    item { HomeSectionHeading(homeGreeting(nickname), compactScale) }
                    item {
                        TodayPlanCard(
                            compactScale,
                            todayPlan,
                            onSetPlan = { nav.navigate(AppRoute.StudyGoal) },
                            onContinue = { nav.navigate(AppRoute.StudyToday) }
                        )
                    }
                    item { HomeSectionHeading("今日待学卡组", compactScale) }
                    if (activeDeck == null) {
                        item { EmptyHomeCard(compactScale, onGoImport = { nav.navigate(AppRoute.Import) }) }
                    } else {
                        item {
                            // The today deck card keeps its real project theme
                            // (Figma 1130:8438's latest card revision).
                            ProjectThemedCard(
                                title = displayDeckTitle(activeDeck),
                                count = activeDeck.cardCount,
                                countLabel = "group",
                                progress = deckLearnedProgress(activeDeck),
                                theme = deckTheme(activeDeck, projects),
                                icon = studyDeckIcon(activeDeck),
                                variant = ProjectThemedCardVariant.BASE_PAGE,
                                designScale = compactScale,
                                onClick = { nav.navigate(AppRoute.Deck(activeDeck.id)) }
                            )
                        }
                    }
                }
            }
        }
        BottomContentFade(compactScale, Modifier.align(Alignment.BottomCenter), heightDp = 112)
    }
}

/** JVM-testable home projections; a null or blank nickname falls back to the honest generic greeting. */
internal fun homeGreeting(nickname: String?): String = "${nickname?.takeIf { it.isNotBlank() } ?: "同学"}，快来学习"

/**
 * The streak card's five flame slots project the server streak onto a fixed
 * 5-slot track (Figma 895:5089). It is a progress projection of the real
 * streak — no per-day history is invented to fill the track.
 */
internal fun streakTrackFillCount(streakDays: Int?): Int = streakDays?.coerceIn(0, 5) ?: 0

/** A not-yet-loaded dashboard shows a dash, never a fabricated zero. */
internal fun streakNumberText(streakDays: Int?): String = streakDays?.toString() ?: "—"

/** Learned share of a deck, from the same server counters the old home used. */
internal fun deckLearnedProgress(deck: DeckSummary): Float =
    if (deck.cardCount == 0) 0f
    else ((deck.cardCount - deck.dueCount).coerceAtLeast(0).toFloat() / deck.cardCount).coerceIn(0f, 1f)

/** Figma 184:641 / 960:4994 — the shared 20/27 section heading, inset 8dp. */
@Composable
private fun HomeSectionHeading(text: String, compactScale: Float) {
    Box(Modifier.fillMaxWidth().padding(horizontal = (8 * compactScale).dp)) {
        AppText(text, AppTextRole.SectionTitle, color = PageForegroundColor(), designScale = compactScale)
    }
}

/**
 * Figma 895:5089 — the streak card. The clock decoration is the node's own
 * 4x canvas render (drawable-nodpi/streak_clock.png) placed at the node's
 * declared box — (192, -29, 239.02dp inside a 370dp card) — and scaled by
 * compactScale, so every device reproduces the Figma canvas 1:1: the arc
 * grazes the top edge, bleeds off the right, and covers the bottom-right
 * corner. The icon-font glyph drifted inside its em box across devices and
 * is replaced by this exact rendering.
 */
@Composable
internal fun StreakCard(compactScale: Float, streakDays: Int?) {
    val clockSize = 239f * compactScale
    val clockX = 192f * compactScale
    val clockY = -29f * compactScale
    Box(
        Modifier.fillMaxWidth()
            .height((186 * compactScale).dp)
            .clip(RoundedCornerShape((AppShapeRadius * compactScale).dp))
            .background(AppColors.Orange.surface)
    ) {
        Image(
            painter = painterResource(R.drawable.streak_clock),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = clockX.dp, y = clockY.dp)
                .requiredSize(clockSize.dp)
        )
        Column(
            Modifier.padding((20 * compactScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * compactScale).dp)
        ) {
            // Figma 895:5089 Frame 122: the metric line and its caption are a
            // 2dp-gap pair; the card-level 16dp gap only separates the track.
            // The line draws its ink half a step low (font metrics, not the
            // 48dp frame), so the content is lifted back to Figma's baseline.
            Column(verticalArrangement = Arrangement.spacedBy((2 * compactScale).dp)) {
                // Exact 48dp metric line: the icon font's default line padding would
                // otherwise push the column past the 186dp card and squeeze the track.
                Row(
                    Modifier.height((48 * compactScale).dp)
                        .offset(y = (-5.5f * compactScale).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialSymbol(
                        "mode_heat", null,
                        tint = AppColors.Orange.primaryStrong,
                        size = fixedSp(48 * compactScale), filled = true,
                        includeFontPadding = false
                    )
                    Spacer(Modifier.width((4 * compactScale).dp))
                    Text(
                        streakNumberText(streakDays),
                        color = AppColors.Orange.primaryStrong,
                        fontFamily = AppFonts.GoogleSansFlexBold,
                        fontWeight = FontWeight.Normal,
                        fontSize = fixedSp(48 * compactScale),
                        lineHeight = fixedSp(48 * compactScale),
                        style = figmaCardTextStyle()
                    )
                }
                Box(Modifier.padding(start = (12 * compactScale).dp)) {
                    AppText("连胜天数", AppTextRole.CardTitle, color = AppColors.Orange.ink, designScale = compactScale)
                }
            }
            Row(
                Modifier.height((56 * compactScale).dp)
                    .clip(RoundedCornerShape(999.dp)).background(AppColors.Orange.primarySecondary)
                    .padding((8 * compactScale).dp),
                horizontalArrangement = Arrangement.spacedBy((8 * compactScale).dp)
            ) {
                val filled = streakTrackFillCount(streakDays)
                for (slot in 0 until 5) {
                    val active = slot < filled
                    Box(
                        Modifier.size((40 * compactScale).dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) AppColors.Orange.primaryStrong else AppColors.Orange.background),
                        contentAlignment = Alignment.Center
                    ) {
                        // 895:5090/5112 fill the reached slots with fire_check;
                        // 895:5100/5106/5118 keep the pending slots on mode_heat.
                        MaterialSymbol(
                            if (active) "fire_check" else "mode_heat",
                            null,
                            tint = if (active) AppColors.Orange.surface else AppColors.Orange.primaryStrong,
                            size = fixedSp(24 * compactScale), filled = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * Figma 961:5003 — today's plan. The header carries the 12dp-radius 设定计划
 * chip; the card closes with the shared 61dp 继续学习 action that starts the
 * server's today-plan session. The three metrics are the server's own
 * remaining counts (GET /study/today): new, review, and the plan's total
 * remainder. No client-side recomputation joins them.
 */
@Composable
private fun TodayPlanCard(
    compactScale: Float,
    todayPlan: TodayPlanUiState,
    onSetPlan: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape((AppShapeRadius * compactScale).dp))
            .background(AppColors.Blue.background)
            .padding((20 * compactScale).dp),
        verticalArrangement = Arrangement.spacedBy((16 * compactScale).dp)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = (32 * compactScale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbol(
                "local_fire_department", null,
                tint = AppColors.TextIconDark,
                size = fixedSp(28 * compactScale), filled = true
            )
            Spacer(Modifier.width((8 * compactScale).dp))
            AppText("今日计划", AppTextRole.SectionTitle, color = AppColors.TextIconDark, designScale = compactScale)
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = onSetPlan,
                color = AppColors.Blue.primarySecondary,
                contentColor = AppColors.TextIconDark,
                shape = RoundedCornerShape((12 * compactScale).dp)
            ) {
                Row(
                    Modifier.padding(
                        horizontal = (12 * compactScale).dp,
                        vertical = (8 * compactScale).dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy((8 * compactScale).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialSymbol(
                        "edit_calendar", null,
                        tint = AppColors.TextIconDark,
                        size = fixedSp(20 * compactScale), filled = true
                    )
                    AppText("设定计划", AppTextRole.CardSubtitle, color = AppColors.TextIconDark, designScale = compactScale)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * compactScale).dp)) {
            PlanMetricBox(
                modifier = Modifier.weight(1f),
                label = "新学",
                value = todayPlan.newRemainingCount.coerceAtLeast(0),
                container = AppColors.Blue.primary,
                content = AppColors.TextIconLight,
                compactScale = compactScale
            )
            PlanMetricBox(
                modifier = Modifier.weight(1f),
                label = "全部",
                value = todayPlan.remainingCount.coerceAtLeast(0),
                container = AppColors.Blue.primarySecondary,
                content = AppColors.TextIconDark,
                compactScale = compactScale
            )
            PlanMetricBox(
                modifier = Modifier.weight(1f),
                label = "待复习",
                value = todayPlan.reviewRemainingCount.coerceAtLeast(0),
                container = AppColors.Blue.primary,
                content = AppColors.TextIconLight,
                compactScale = compactScale
            )
        }
        Surface(
            onClick = onContinue,
            color = AppColors.Blue.primary,
            contentColor = AppColors.TextIconLight,
            shape = RoundedCornerShape((AppButtonShapeRadius * compactScale).dp),
            modifier = Modifier.fillMaxWidth().height((61 * compactScale).dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy((8 * compactScale).dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MaterialSymbol("book_5", null, tint = AppColors.TextIconLight, size = fixedSp(24 * compactScale), filled = true)
                AppText("继续学习", AppTextRole.CardTitle, color = AppColors.TextIconLight, designScale = compactScale)
            }
        }
    }
}

@Composable
private fun PlanMetricBox(
    modifier: Modifier,
    label: String,
    value: Int,
    container: Color,
    content: Color,
    compactScale: Float
) {
    Column(
        modifier.height((97 * compactScale).dp)
            .clip(RoundedCornerShape((AppNestedShapeRadius * compactScale).dp))
            .background(container)
            .padding((16 * compactScale).dp),
        verticalArrangement = Arrangement.spacedBy((4 * compactScale).dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppText(label, AppTextRole.Label, color = content, designScale = compactScale, textAlign = TextAlign.Center)
        Text(
            value.toString(),
            color = content,
            fontFamily = AppFonts.GoogleSansFlexBold,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(40 * compactScale),
            lineHeight = fixedSp(40 * compactScale),
            letterSpacing = fixedSp(-0.6f * compactScale),
            style = figmaCardTextStyle()
        )
    }
}

@Composable
private fun EmptyHomeCard(compactScale: Float, onGoImport: () -> Unit) {
    Card(
        shape = RoundedCornerShape(AppShapeRadius.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Blue.background),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxSize().padding((24 * compactScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * compactScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MaterialSymbol("note_stack_add", null, tint = AppColors.Blue.ink, size = fixedSp(40 * compactScale), filled = true)
            AppText("还没有卡组", AppTextRole.SectionTitle, color = AppColors.TextIconDark, designScale = compactScale)
            AppText(
                "导入一组问答卡，开始你的第一轮学习。",
                AppTextRole.Supporting,
                color = AppColors.TextIconDark.copy(alpha = .6f),
                designScale = compactScale,
                textAlign = TextAlign.Center
            )
            Surface(
                onClick = onGoImport,
                color = AppColors.Blue.primary,
                contentColor = AppColors.TextIconLight,
                shape = RoundedCornerShape((AppButtonShapeRadius * compactScale).dp),
                modifier = Modifier.fillMaxWidth().height((52 * compactScale).dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppText("导入卡片", AppTextRole.Label, color = AppColors.TextIconLight, designScale = compactScale)
                }
            }
        }
    }
}

