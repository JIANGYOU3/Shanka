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
internal fun DataScreen(dueCount: Int, dashboard: DashboardUiState?, weeklyActivity: WeeklyActivityData, nav: ScreenNavigator) {
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    val sideInset = 16 * designScale
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground).statusBarsPadding()) {
            // Content begins 16dp below the fixed 56dp header row.
            Box(Modifier.fillMaxSize().padding(start = sideInset.dp, top = (88 * designScale).dp, end = sideInset.dp)) {
                // The data content viewport is a rounded surface on all four
                // sides; keeping the top corners here restores the Figma crop
                // instead of letting the first card paint into the square edge.
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(AppScrollableContentClipRadius.dp))) {
                    // The common root-navigation tail aligns the final card just above
                    // the floating navigation instead of leaving an oversized blank area.
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = (RootNavigationScrollTail * designScale).dp), verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
                        item { WeeklyActivityCard(designScale, weeklyActivity) }
                        item { DataStreakCards(designScale, dashboard) }
                        item { DataLearningCards(designScale, dashboard, dueCount) }
                        item { MasteryCard(designScale, dashboard) }
                    }
                }
            }
            BottomContentFade(designScale, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun WeeklyActivityCard(designScale: Float, weeklyActivity: WeeklyActivityData) {
    val maxCount = weeklyActivity.dailyCounts.maxOrNull() ?: 0
    val barHeights = weeklyActivity.dailyCounts.map { count ->
        if (maxCount == 0 || count == 0) 0f else 101f * count / maxCount
    }
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Card(
        shape = RoundedCornerShape((AppShapeRadius * designScale).dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Blue.background),
        modifier = Modifier.fillMaxWidth().height((272 * designScale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height((48 * designScale).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    AppText(
                        "每周活动",
                        AppTextRole.SectionTitle,
                        color = AppColors.TextIconDark,
                        designScale = designScale
                    )
                    MixedLanguageText(
                        text = "已巩固${weeklyActivity.total} cards",
                        color = AppColors.TextIconDark.copy(alpha = .5f),
                        chineseFont = AppFonts.MiSansSemibold,
                        latinFont = AppFonts.GoogleSansFlexSemibold,
                        fontSize = fixedSp(16 * designScale),
                        lineHeight = fixedSp(21 * designScale)
                    )
                }
                WeeklyChangeIndicator(weeklyActivity.changePercent, designScale)
            }
            Row(
                modifier = Modifier.fillMaxWidth().height((160 * designScale).dp).padding(horizontal = (8 * designScale).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                labels.zip(barHeights).forEach { (label, filledHeight) ->
                    WeeklyActivityBar(label, filledHeight, designScale)
                }
            }
        }
    }
}

/** Figma 19:774 weekly comparison pill. */
@Composable
private fun WeeklyChangeIndicator(changePercent: Int?, designScale: Float) {
    val improving = changePercent == null || changePercent >= 0
    val hasComparison = changePercent != null
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = when {
            !hasComparison -> AppColors.Blue.primarySecondary
            improving -> AppColors.Green.primaryStrong
            else -> AppColors.Warning
        },
        modifier = Modifier.height((48 * designScale).dp)
    ) {
        Box(Modifier.padding(horizontal = (12 * designScale).dp), contentAlignment = Alignment.Center) {
            Text(
                text = when {
                    !hasComparison -> "暂无对比"
                    improving -> "+${changePercent}%"
                    else -> "${changePercent}%"
                },
                color = if (hasComparison) AppColors.Green.background else AppColors.Blue.ink,
                fontFamily = if (hasComparison) AppFonts.GoogleSansFlexExtraBold else AppFonts.MiSansSemibold,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(if (hasComparison) 24 * designScale else 16 * designScale),
                lineHeight = fixedSp(if (hasComparison) 24 * designScale else 21 * designScale),
                letterSpacing = if (hasComparison) fixedSp(.6f * designScale) else TextUnit.Unspecified
            )
        }
    }
}

@Composable
private fun WeeklyActivityBar(label: String, filledHeight: Float, designScale: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((8 * designScale).dp)
    ) {
        Column(
            modifier = Modifier.width((32 * designScale).dp).height((120 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((4 * designScale).dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(AppColors.Blue.primarySecondary)
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((filledHeight * designScale).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AppColors.Blue.primary)
            )
        }
        Text(
            text = label,
            color = AppColors.TextIconDark,
            fontFamily = AppFonts.GoogleSansFlexExtraBold,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(16 * designScale),
            lineHeight = fixedSp(16 * designScale),
            letterSpacing = fixedSp(.6f * designScale)
        )
    }
}

@Composable
private fun MasteryCard(designScale: Float, dashboard: DashboardUiState?) {
    Card(
        shape = RoundedCornerShape(AppShapeRadius.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Blue.background),
        modifier = Modifier.fillMaxWidth().height((411 * designScale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((24 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WeeklyGoalRing(designScale, dashboard)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
                AppText("这数据不赖，继续努力", AppTextRole.SectionTitle, color = AppColors.TextIconDark, designScale = designScale)
                DataMetricRow("psychology_alt", "回忆正确率", dashboard.percent(dashboard?.recallAccuracy), designScale)
                DataMetricRow("bolt", "首次答对率", dashboard.percent(dashboard?.firstAttemptAccuracy), designScale)
                DataMetricRow("mountain_flag", "记忆保持率", dashboard.percent(dashboard?.retentionRate), designScale)
            }
        }
    }
}

@Composable
private fun WeeklyGoalRing(designScale: Float, dashboard: DashboardUiState?) {
    val progress = dashboard?.let { if (it.weeklyGoal > 0) (it.completed.toFloat() / it.weeklyGoal).coerceIn(0f, 1f) else 0f } ?: 0f
    val ringTrack = AppColors.Blue.primarySecondary
    Box(Modifier.size((192 * designScale).dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension / 8f
            val inset = stroke / 2f
            val bounds = androidx.compose.ui.geometry.Rect(inset, inset, size.width - inset, size.height - inset)
            // A conventional Health-style goal ring: the blue arc always represents the
            // number in the center, beginning at 12 o'clock and ending with round caps.
            drawArc(ringTrack, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = bounds.topLeft, size = bounds.size, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(AppColors.Blue.primary, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, topLeft = bounds.topLeft, size = bounds.size, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(progress * 100).roundToInt()}%", color = AppColors.TextIconDark, fontFamily = AppFonts.GoogleSansFlexBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(40 * designScale), lineHeight = fixedSp(40 * designScale), letterSpacing = fixedSp(-.6f * designScale))
            Text("本周巩固目标", color = AppColors.Blue.ink, fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(21 * designScale))
        }
    }
}

@Composable
private fun DataMetricRow(symbol: String, label: String, value: String, designScale: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy((8 * designScale).dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(999.dp), color = AppColors.Blue.primary, modifier = Modifier.size((24 * designScale).dp)) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol(symbol, null, tint = AppColors.TextIconLight, size = fixedSp(16 * designScale), filled = true)
                }
            }
            AppText(
                text = label,
                role = AppTextRole.CardSubtitle,
                color = AppColors.TextIconDark,
                designScale = designScale,
                maxLines = 1
            )
        }
        Text(value, color = AppColors.Blue.ink, fontFamily = AppFonts.GoogleSansFlexSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(16 * designScale))
    }
}

private fun DashboardUiState?.percent(value: Float?): String = value?.let { "${(it * 100).roundToInt()}%" } ?: "—"

@Composable
private fun DataStreakCards(designScale: Float, dashboard: DashboardUiState?) {
    val longestStreak = dashboard?.streakDays
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
        StatisticsMetricCard(
            modifier = Modifier.weight(1f),
            value = longestStreak?.toString() ?: "—",
            kind = StatisticsMetricKind.LongestStreak,
            surface = StatisticsMetricSurface.Tinted,
            designScale = designScale
        )
        StatisticsMetricCard(
            modifier = Modifier.weight(1f),
            // V2.5 has no app-open metric. Preserve the visual slot without inventing a value.
            value = "—",
            kind = StatisticsMetricKind.OpenCount,
            surface = StatisticsMetricSurface.Tinted,
            designScale = designScale
        )
    }
}

@Composable
private fun DataLearningCards(designScale: Float, dashboard: DashboardUiState?, dueCount: Int) {
    val masteredCount = dashboard?.masteredCards
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
        StatisticsMetricCard(
            modifier = Modifier.weight(1f),
            value = dueCount.toString(),
            kind = StatisticsMetricKind.DueCards,
            surface = StatisticsMetricSurface.Tinted,
            designScale = designScale
        )
        StatisticsMetricCard(
            modifier = Modifier.weight(1f),
            // Real counts are always integers; a `0.042k`-style abbreviation misreads 42 as 42k.
            value = honestCount(masteredCount),
            kind = StatisticsMetricKind.MasteredCards,
            surface = StatisticsMetricSurface.Tinted,
            designScale = designScale
        )
    }
}
