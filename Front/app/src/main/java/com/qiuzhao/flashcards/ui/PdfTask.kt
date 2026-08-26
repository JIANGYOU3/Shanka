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
internal fun PdfTaskScreen(state: PdfTaskState, onPause: () -> Unit, onResume: () -> Unit, onBack: () -> Unit, onViewDeck: () -> Unit) {
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (state) {
                PdfTaskState.GENERATING, PdfTaskState.PAUSED -> TaskGenerationCard(
                    paused = state == PdfTaskState.PAUSED,
                    designScale = designScale,
                    modifier = Modifier.padding(start = (16 * designScale).dp, top = (132 * designScale).dp, end = (16 * designScale).dp)
                )
                PdfTaskState.COMPLETE -> TaskCompletedCard(
                    designScale = designScale,
                    modifier = Modifier.padding(start = (16 * designScale).dp, top = (132 * designScale).dp, end = (16 * designScale).dp)
                )
            }
            DeckDetailHeader(
                title = "生成任务",
                designScale = designScale,
                onBack = onBack,
                modifier = Modifier.zIndex(1f)
            )
            BottomContentFade(designScale, Modifier.align(Alignment.BottomCenter))
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = (16 * designScale).dp, end = (16 * designScale).dp, bottom = (32 * designScale).dp)
                    .zIndex(1f)
            ) {
                when (state) {
                    PdfTaskState.GENERATING -> DetailPrimaryButton("暂停生成", "pause_circle", true, designScale, onPause)
                    PdfTaskState.PAUSED -> DetailPrimaryButton("继续生成", "play_circle", true, designScale, onResume)
                    PdfTaskState.COMPLETE -> DetailPrimaryButton("查看牌组", "style", true, designScale, onViewDeck)
                }
            }
        }
    }
}

@Composable
private fun TaskGenerationCard(paused: Boolean, designScale: Float, modifier: Modifier = Modifier) {
    val surface = AppColors.Blue.background
    Surface(
        shape = RoundedCornerShape((AppShapeRadius * designScale).dp),
        color = surface,
        modifier = modifier.fillMaxWidth().height((265 * designScale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((20 * designScale).dp)
        ) {
            if (paused) {
                MaterialSymbol("pause_circle", null, tint = AppColors.Blue.primary, size = fixedSp(80 * designScale), filled = true)
            } else {
                Md3ExpressiveIndeterminateRing(designScale)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((8 * designScale).dp)) {
                AppText(if (paused) "生成已暂停" else "正在生成闪卡", AppTextRole.PageTitle, color = PageForegroundColor(), designScale = designScale)
                if (paused) {
                    AppText("已保留当前生成进度", AppTextRole.CardSubtitle, color = AppColors.TextIconDark.copy(alpha = .55f), designScale = designScale, textAlign = TextAlign.Center)
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy((4 * designScale).dp)
                    ) {
                        listOf("已整理学习内容", "正在生成卡片", "正在检查结果").forEach { label ->
                            AppText(label, AppTextRole.CardSubtitle, color = AppColors.TextIconDark.copy(alpha = .55f), designScale = designScale, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Md3ExpressiveIndeterminateRing(designScale: Float) {
    val transition = rememberInfiniteTransition(label = "MD3 expressive generation ring")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing)),
        label = "MD3 expressive generation ring rotation"
    )
    Canvas(Modifier.size((80 * designScale).dp)) {
        val stroke = with(this) { 8.dp.toPx() }
        val inset = stroke / 2f
        val bounds = androidx.compose.ui.geometry.Rect(inset, inset, size.width - inset, size.height - inset)
        drawArc(
            color = AppColors.Blue.primarySecondary,
            startAngle = rotation - 220f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = bounds.topLeft,
            size = bounds.size,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = AppColors.Blue.primary,
            startAngle = rotation + 50f,
            sweepAngle = 44f,
            useCenter = false,
            topLeft = bounds.topLeft,
            size = bounds.size,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun TaskCompletedCard(designScale: Float, modifier: Modifier = Modifier) {
    val surface = AppColors.Blue.background
    Surface(
        shape = RoundedCornerShape((AppShapeRadius * designScale).dp),
        color = surface,
        // Figma 140:3415 is a hug-content card.  Its height must be derived
        // from the icon, text block, chip row and the prescribed gaps.
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding((24 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((20 * designScale).dp)
        ) {
            MaterialSymbol("check_circle", null, tint = AppColors.Green.primaryStrong, size = fixedSp(80 * designScale), filled = true)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((8 * designScale).dp)) {
                AppText("卡片组生成完成", AppTextRole.PageTitle, color = AppColors.TextIconDark, designScale = designScale)
                AppText("共生成42张闪卡", AppTextRole.CardSubtitle, color = AppColors.TextIconDark.copy(alpha = .55f), designScale = designScale)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp)
            ) {
                TaskTypeChip("基础记忆", "17", AppColors.Blue.primarySecondary, AppColors.Blue.ink, designScale, Modifier.weight(1f))
                TaskTypeChip("理解分析", "9", AppColors.Green.primarySecondary, AppColors.Green.ink, designScale, Modifier.weight(1f))
                TaskTypeChip("综合应用", "16", AppColors.Pink.primarySecondary, AppColors.Pink.ink, designScale, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TaskTypeChip(label: String, count: String, background: Color, content: Color, designScale: Float, modifier: Modifier = Modifier) {
    Surface(
        // Figma 140:3450/203:2546/203:2552: a 20dp card with two
        // centred rows, rather than the old horizontal pill.
        shape = RoundedCornerShape((20 * designScale).dp),
        color = background,
        modifier = modifier.height((77 * designScale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((16 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((4 * designScale).dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((4 * designScale).dp), verticalAlignment = Alignment.CenterVertically) {
                AppText(count, AppTextRole.Label, color = content, designScale = designScale, maxLines = 1)
                AppText("cards", AppTextRole.Label, color = content, designScale = designScale, maxLines = 1)
            }
            AppText(label, AppTextRole.Label, color = content, designScale = designScale, maxLines = 1)
        }
    }
}
