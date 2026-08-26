package com.qiuzhao.flashcards.ui

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.qiuzhao.flashcards.data.CardDraft
import com.qiuzhao.flashcards.data.remote.DeckProgress
import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.data.remote.FlashcardEntity
import com.qiuzhao.flashcards.data.remote.Dashboard
import com.qiuzhao.flashcards.data.ImportParser
import com.qiuzhao.flashcards.data.remote.Rating
import com.qiuzhao.flashcards.R
import com.qiuzhao.flashcards.ui.motion.AppMotion
import com.qiuzhao.flashcards.ui.navigation.AppNavigator
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import com.qiuzhao.flashcards.ui.navigation.rememberAppNavigationState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay


@Composable
internal fun DeckScreen(deck: DeckSummary, viewModel: AppViewModel, nav: ScreenNavigator) {
    val progress by viewModel.deckProgress(deck.id).collectAsState(
        initial = DeckProgress(deck.cardCount, deck.dueCount, masteredCards = 0, reviewCount = 0)
    )
    val projects by viewModel.projects.collectAsState()
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    // A deck belongs visually to its project. Legacy, unassigned decks retain
    // their stored family through deckTheme's existing fallback.
    val theme = deckTheme(deck, projects)
    Surface(modifier = Modifier.fillMaxSize(), color = theme.surface) {
        Box(Modifier.fillMaxSize()) {
            // Figma 41:1623: this is the only scrollable region. It is clipped so
            // a long overview or future metrics never travel into the fixed header.
            Box(
                Modifier.fillMaxSize()
                    .padding(start = (16 * designScale).dp, top = (136 * designScale).dp, end = (16 * designScale).dp)
                    .clip(RoundedCornerShape((AppScrollableContentClipRadius * designScale).dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail() * designScale).dp),
                    verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
                ) {
                    item { DeckLearningDataCard(reviewedToday = progress.dueCount, dailyGoal = 50, theme = theme, designScale = designScale) }
                    item { DeckQuestionTypesCard(progress.cardCount, 0, 0, theme, designScale) }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
                            StatisticsMetricCard("${progress.dueCount}min", StatisticsMetricKind.LearningTime, StatisticsMetricSurface.White, designScale, Modifier.weight(1f))
                            StatisticsMetricCard("${progress.masteredCards / 1000}.${(progress.masteredCards % 1000).toString().padStart(3, '0')}k", StatisticsMetricKind.MasteredCards, StatisticsMetricSurface.White, designScale, Modifier.weight(1f))
                        }
                    }
                    item { DeckWeeklyReviewCard(designScale) }
                }
            }
            DeckDetailHeader(
                title = displayDeckTitle(deck), designScale = designScale, onBack = nav::popBackStack,
                theme = theme,
                modifier = Modifier.zIndex(1f)
            )
            BottomContentFade(designScale, Modifier.align(Alignment.BottomCenter))
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = (16 * designScale).dp, end = (16 * designScale).dp, bottom = (32 * designScale).dp)
                    .fillMaxWidth().height((60 * designScale).dp).zIndex(1f),
                horizontalArrangement = Arrangement.spacedBy((12 * designScale).dp)
            ) {
                CardListActionButton("编辑", "edit", false, Modifier.weight(1f), designScale, theme) { nav.navigate(AppRoute.EditCardList(deck.id)) }
                CardListActionButton("开始复习", "play_circle", true, Modifier.weight(1f), designScale, theme) { nav.navigate(AppRoute.Study(deck.id, true)) }
            }
        }
    }
}

/** The fixed primary/secondary action style shared by Figma nodes 41:1623 and 48:4562. */
@Composable
internal fun DetailPrimaryButton(
    text: String,
    icon: String,
    primary: Boolean,
    designScale: Float,
    onClick: () -> Unit
) {
    val container = if (primary) AppColors.Blue.primary else AppColors.Blue.primarySecondary
    val content = if (primary) AppColors.TextIconLight else AppColors.Blue.ink
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height((60 * designScale).dp),
        shape = RoundedCornerShape((24 * designScale).dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = (24 * designScale).dp)
    ) {
        MaterialSymbol(icon, null, tint = content, size = fixedSp(24 * designScale), filled = true)
        Spacer(Modifier.width((8 * designScale).dp))
        AppText(text, AppTextRole.Label, color = content, designScale = designScale, maxLines = 1)
    }
}

@Composable
private fun DeckOverviewCard(summary: String, designScale: Float, theme: DeckTheme) {
    Card(
        shape = RoundedCornerShape(AppShapeRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = theme.surface
        ),
        // Figma 48:4511 is a fixed 102dp synopsis card: 24dp insets around a
        // two-line, 20/27 text frame. Letting it grow to four lines made the
        // bottom inset visibly larger than the reference.
        modifier = Modifier.fillMaxWidth().height((102 * designScale).dp)
    ) {
        Text(
            text = summary,
            modifier = Modifier.padding((24 * designScale).dp),
            color = theme.text,
            // Figma 48:4512 specifies MiSans VF Medium for the chapter synopsis.
            fontFamily = AppFonts.MiSansMedium,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(20 * designScale),
            lineHeight = fixedSp(27 * designScale),
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = figmaCardTextStyle()
        )
    }
}

private fun deckOverview(deck: DeckSummary): String = when (deck.chapter) {
    1 -> "理解 Agent 的核心组成：模型、上下文与工具如何协同完成任务。"
    2 -> "学习如何组织、筛选和压缩上下文，让 Agent 在长任务中保持有效信息。"
    3 -> "梳理记忆、知识库与检索增强生成，建立可用的长期知识能力。"
    4 -> "掌握工具调用与 MCP 的工程要点：接口、权限、重试与可观察性。"
    5 -> "理解 Coding Agent 如何规划、修改、验证并交付可运行的代码改动。"
    6 -> "建立可重复的 Agent 评估体系，兼顾任务质量、成本、时延与稳定性。"
    7 -> "认识监督微调、强化学习和工具轨迹如何塑造模型的可靠行为。"
    8 -> "学习从运行轨迹持续改进 Agent，并为线上演进建立安全边界。"
    9 -> "关注多模态与实时 Agent 的低延迟交互、状态同步和操作风险。"
    10 -> "了解多 Agent 协作的分工、交接与共享上下文策略。"
    else -> "这个卡组正在持续整理中；你可以添加问题，随时开始复习。"
}

@Composable
private fun ChapterProgressCard(progress: DeckProgress, masteryRatio: Float, designScale: Float, theme: DeckTheme) {
    // The stored model does not yet contain a per-question-type field.  Keep the
    // Figma statistics layout truthful by placing the current cards in the base
    // type and reserving the other two types for future generated-card metadata.
    val foundationCards = progress.cardCount
    val understandingCards = 0
    val applicationCards = 0
    Card(
        shape = RoundedCornerShape(AppShapeRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = theme.surface
        ),
        // Figma 48:4450 contains metrics, three question-type chips and the
        // review total in this single 305dp card.
        modifier = Modifier.fillMaxWidth().height((305 * designScale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "本章进度", color = theme.text, fontFamily = AppFonts.MiSansBold,
                    fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * designScale),
                    lineHeight = fixedSp(27 * designScale), style = figmaCardTextStyle()
                )
                MixedLanguageText(
                    text = "${(masteryRatio * 100).roundToInt()}%已掌握", color = theme.strongText,
                    chineseFont = AppFonts.MiSansBold, latinFont = AppFonts.GoogleSansFlexBold,
                    fontSize = fixedSp(20 * designScale), lineHeight = fixedSp(24 * designScale),
                    style = figmaCardTextStyle()
                )
            }
            // Figma 48:4490 uses two independent pill segments rather than a
            // fill overlay. Keep its 5dp inter-segment gap while making the
            // segment widths follow the same mastered ratio shown in the label.
            Row(
                modifier = Modifier.fillMaxWidth().height((20 * designScale).dp),
                horizontalArrangement = Arrangement.spacedBy((5 * designScale).dp)
            ) {
                val safeMasteryRatio = masteryRatio.coerceIn(0f, 1f)
                if (safeMasteryRatio > 0f) {
                    Box(
                        Modifier.weight(safeMasteryRatio).fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp)).background(theme.progressFill)
                    )
                }
                if (safeMasteryRatio < 1f) {
                    Box(
                        Modifier.weight(1f - safeMasteryRatio).fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp)).background(theme.progressTrack)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ChapterMetric("共", "${progress.cardCount}张", designScale, theme = theme)
                ChapterMetric("已掌握", "${progress.masteredCards}张", designScale, TextAlign.Center, theme)
                ChapterMetric("待复习", "${progress.dueCount}张", designScale, TextAlign.End, theme)
            }
            Row(
                modifier = Modifier.fillMaxWidth().height((77 * designScale).dp),
                horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp)
            ) {
                ChapterQuestionTypeStat(
                    count = foundationCards,
                    label = "基础记忆",
                    container = AppColors.Blue.primarySecondary,
                    content = AppColors.Blue.ink,
                    designScale = designScale,
                    modifier = Modifier.weight(1f)
                )
                ChapterQuestionTypeStat(
                    count = understandingCards,
                    label = "理解分析",
                    container = AppColors.Green.primarySecondary,
                    content = AppColors.Green.ink,
                    designScale = designScale,
                    modifier = Modifier.weight(1f)
                )
                ChapterQuestionTypeStat(
                    count = applicationCards,
                    label = "综合应用",
                    container = AppColors.Pink.primarySecondary,
                    content = AppColors.Pink.ink,
                    designScale = designScale,
                    modifier = Modifier.weight(1f)
                )
            }
            MixedLanguageText(
                text = "累计复习${progress.reviewCount}次", color = theme.mutedText,
                chineseFont = AppFonts.MiSansSemibold, latinFont = AppFonts.GoogleSansFlexSemibold,
                fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(21 * designScale),
                style = figmaCardTextStyle()
            )
        }
    }
}

@Composable
private fun ChapterQuestionTypeStat(
    count: Int,
    label: String,
    container: Color,
    content: Color,
    designScale: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape((20 * designScale).dp),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((16 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((4 * designScale).dp, Alignment.CenterVertically)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((4 * designScale).dp)) {
                Text(
                    count.toString(),
                    color = content,
                    fontFamily = AppFonts.GoogleSansFlexBold,
                    fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(16 * designScale),
                    lineHeight = fixedSp(20 * designScale),
                    style = figmaCardTextStyle(),
                    maxLines = 1
                )
                Text(
                    "cards",
                    color = content,
                    fontFamily = AppFonts.GoogleSansFlexBold,
                    fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(16 * designScale),
                    lineHeight = fixedSp(20 * designScale),
                    style = figmaCardTextStyle(),
                    maxLines = 1
                )
            }
            Text(
                label,
                color = content,
                fontFamily = AppFonts.MiSansBold,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(16 * designScale),
                lineHeight = fixedSp(21 * designScale),
                style = figmaCardTextStyle(), maxLines = 1
            )
        }
    }
}

@Composable
private fun ChapterMetric(label: String, value: String, designScale: Float, alignment: TextAlign = TextAlign.Start, theme: DeckTheme) {
    Column(horizontalAlignment = when (alignment) { TextAlign.End -> Alignment.End; TextAlign.Center -> Alignment.CenterHorizontally; else -> Alignment.Start }) {
        Text(
            label, color = theme.text, fontFamily = AppFonts.MiSansSemibold,
            fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale),
            lineHeight = fixedSp(21 * designScale), textAlign = alignment, style = figmaCardTextStyle()
        )
        MixedLanguageText(
            text = value.replace(" ", ""), color = theme.text,
            chineseFont = AppFonts.MiSansBold, latinFont = AppFonts.GoogleSansFlexBold,
            fontSize = fixedSp(20 * designScale),
            lineHeight = fixedSp(27 * designScale), textAlign = alignment, style = figmaCardTextStyle()
        )
    }
}
