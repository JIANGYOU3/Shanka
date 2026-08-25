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
internal fun DataScreen(dueCount: Int, dashboard: Dashboard?, weeklyActivity: WeeklyActivityData, nav: ScreenNavigator) {
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    val sideInset = 16 * designScale
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
            // Content begins 16dp below the fixed 56dp header row.
            Box(Modifier.fillMaxSize().padding(start = sideInset.dp, top = (88 * designScale).dp, end = sideInset.dp)) {
                // The data content viewport is a rounded surface on all four
                // sides; keeping the top corners here restores the Figma crop
                // instead of letting the first card paint into the square edge.
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(AppShapeRadius.dp))) {
                    // The content runs under the floating nav; reserve enough scroll tail
                    // to lift the final Bento row fully into the readable viewport.
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = (180 * designScale).dp), verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
                        item { WeeklyActivityCard(designScale) }
                        item { MasteryCard(designScale, dashboard) }
                        item { DataBentoCards(designScale, dashboard) }
                    }
                }
            }
            BottomContentFade(designScale, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun WeeklyActivityCard(designScale: Float) {
    // Figma 577:2466 defines this as a fixed five-state review-progress component.
    // These colors deliberately remain local to the component until the product's
    // review-status color model is available from the backend.
    val statuses = listOf(
        ReviewProgressStatus("熟识", Color(0xFF579B00), 12),
        ReviewProgressStatus("认识", Color(0xFFAFCD82), 27),
        ReviewProgressStatus("模糊", Color(0xFFFFC000), 87),
        ReviewProgressStatus("陌生", Color(0xFFFF3D00), 19),
        ReviewProgressStatus("没学", Color(0xFFDDDDDD), 97)
    )
    Card(
        shape = RoundedCornerShape((32 * designScale).dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth().height((277 * designScale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height((32 * designScale).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MaterialSymbol(
                    name = "local_fire_department",
                    description = null,
                    tint = Color.Black,
                    size = fixedSp(28 * designScale),
                    filled = true
                )
                Text(
                    text = "复习进度",
                    modifier = Modifier.padding(start = (8 * designScale).dp),
                    color = Color.Black,
                    fontFamily = AppFonts.MiSansBold,
                    fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(20 * designScale),
                    lineHeight = fixedSp(27 * designScale)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                statuses.forEach { status ->
                    ReviewProgressLegend(status, designScale)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy((12 * designScale).dp),
                verticalAlignment = Alignment.Top
            ) {
                statuses.forEach { status ->
                    ReviewProgressBar(status, designScale, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReviewProgressLegend(status: ReviewProgressStatus, designScale: Float) {
    Row(
        horizontalArrangement = Arrangement.spacedBy((8 * designScale).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size((16 * designScale).dp)
                .clip(RoundedCornerShape(999.dp))
                .background(status.color)
        )
        Text(
            text = status.label,
            color = Color.Black.copy(alpha = .75f),
            fontFamily = AppFonts.MiSansSemibold,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(16 * designScale),
            lineHeight = fixedSp(21 * designScale),
            maxLines = 1
        )
    }
}

@Composable
private fun ReviewProgressBar(status: ReviewProgressStatus, designScale: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((8 * designScale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height((120 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((4 * designScale).dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(status.color.copy(alpha = .25f))
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((status.barHeight * designScale).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(status.color)
            )
        }
        Text(
            text = "M",
            color = Color.Black.copy(alpha = .8f),
            fontFamily = AppFonts.GoogleSansFlexExtraBold,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(16 * designScale),
            lineHeight = fixedSp(16 * designScale),
            letterSpacing = fixedSp(.6f * designScale)
        )
    }
}

private data class ReviewProgressStatus(val label: String, val color: Color, val barHeight: Int)

@Composable
private fun MasteryCard(designScale: Float, dashboard: Dashboard?) {
    Card(
        shape = RoundedCornerShape(AppShapeRadius.dp),
        // Figma 19:621: the weekly-goal card now shares the soft blue data surface.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().height((420 * designScale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((24 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WeeklyGoalRing(designScale, dashboard)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
                Text("这数据不赖", modifier = Modifier.padding(bottom = (8 * designScale).dp), color = PageForegroundColor(), fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(24 * designScale), lineHeight = fixedSp(28 * designScale))
                DataMetricRow("psychology_alt", "回忆正确率", dashboard.percent("recall_accuracy", "review_accuracy"), designScale)
                DataMetricRow("bolt", "首次答对率", dashboard.percent("first_answer_accuracy", "first_pass_rate", "first_correct_rate"), designScale)
                DataMetricRow("mountain_flag", "记忆保持率", dashboard.percent("retention_rate", "memory_retention_rate"), designScale)
            }
        }
    }
}

@Composable
private fun WeeklyGoalRing(designScale: Float, dashboard: Dashboard?) {
    val progress = dashboard?.let { if (it.weeklyGoal != null && it.weeklyGoal > 0) (it.completed.toFloat() / it.weeklyGoal).coerceIn(0f, 1f) else 0f } ?: 0f
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
            Text("${(progress * 100).roundToInt()}%", color = PageForegroundColor(), fontFamily = AppFonts.GoogleSansFlexBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(40 * designScale), lineHeight = fixedSp(41 * designScale), letterSpacing = fixedSp(-.68f * designScale))
            Text("本周复习目标", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), letterSpacing = fixedSp(.6f * designScale))
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
            Text(label, color = PageForegroundColor(), fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale))
        }
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = AppFonts.GoogleSansFlexSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale))
    }
}

private fun Dashboard?.number(vararg keys: String): Double? = this?.let { dashboard ->
    keys.firstOrNull { dashboard.raw.has(it) && !dashboard.raw.isNull(it) }?.let { dashboard.raw.optDouble(it) }
}

private fun Dashboard?.percent(vararg keys: String): String = number(*keys)?.let { "${(it * 100).roundToInt()}%" } ?: "—"

@Composable
private fun DataBentoCards(designScale: Float, dashboard: Dashboard?) {
    val masteredCount = dashboard?.raw?.let { raw -> raw.optInt("mastered_card_count", raw.optInt("mastered_cards", 0)) } ?: 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
        DataBentoCard(
            modifier = Modifier.weight(1f),
            background = AppColors.Pink.background,
            iconBackground = AppColors.Pink.primary,
            icon = "local_fire_department",
            iconTint = AppColors.Pink.ink,
            value = (dashboard?.raw?.optInt("streak_days", 0) ?: 0).toString(),
            label = "连胜！",
            valueColor = AppColors.TextIconDark,
            labelColor = AppColors.WarningStrong,
            designScale = designScale
        )
        DataBentoCard(
            modifier = Modifier.weight(1f),
            background = AppColors.Green.background,
            iconBackground = AppColors.Green.primary,
            icon = "editor_choice",
            iconTint = AppColors.Green.ink,
            value = formatMasteredCount(masteredCount),
            label = "已掌握卡片",
            valueColor = AppColors.TextIconDark,
            labelColor = AppColors.Green.primaryStrong,
            designScale = designScale
        )
    }
}

/** The Figma component presents mastered cards in thousands (0k, 1.2k, ...). */
private fun formatMasteredCount(count: Int): String {
    val tenths = (count.coerceAtLeast(0) / 100).coerceAtLeast(0)
    return when {
        tenths == 0 -> "0k"
        tenths % 10 == 0 -> "${tenths / 10}k"
        else -> "${tenths / 10}.${tenths % 10}k"
    }
}

@Composable
private fun DataBentoCard(modifier: Modifier, background: Color, iconBackground: Color, icon: String, iconTint: Color, value: String, label: String, valueColor: Color, labelColor: Color, designScale: Float) {
    Card(
        shape = RoundedCornerShape(AppShapeRadius.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        // Figma 19:1276 / 19:1286: the bento card is 174dp tall. The
        // previous 169dp bound clipped the MiSans label descenders at the
        // bottom of both "连胜！" and "已掌握卡片".
        modifier = modifier.height((174 * designScale).dp)
    ) {
        Column(Modifier.fillMaxSize().padding((24 * designScale).dp), verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
            Surface(shape = RoundedCornerShape(999.dp), color = iconBackground, modifier = Modifier.size((40 * designScale).dp)) {
                Box(contentAlignment = Alignment.Center) { MaterialSymbol(icon, null, tint = iconTint, size = fixedSp(24 * designScale), filled = true) }
            }
            Column(verticalArrangement = Arrangement.spacedBy((4 * designScale).dp)) {
                Text(value, color = valueColor, fontFamily = AppFonts.GoogleSansFlexBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(36 * designScale), lineHeight = TextUnit.Unspecified, letterSpacing = fixedSp(-.68f * designScale), style = figmaCardTextStyle())
                Text(label, color = labelColor, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = TextUnit.Unspecified, letterSpacing = fixedSp(.6f * designScale), style = figmaCardTextStyle())
            }
        }
    }
}
