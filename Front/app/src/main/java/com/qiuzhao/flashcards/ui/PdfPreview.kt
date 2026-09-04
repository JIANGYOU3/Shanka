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
internal fun PdfPreviewScreen(samples: List<CardDraft>, onBack: () -> Unit, onGenerate: () -> Unit) {
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val types = listOf(
        PdfPreviewType("基础记忆", AppColors.Blue.primarySecondary, AppColors.Blue.ink),
        PdfPreviewType("理解分析", AppColors.Green.primarySecondary, AppColors.Green.ink),
        PdfPreviewType("综合应用", AppColors.Pink.primarySecondary, AppColors.Pink.ink)
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Surface(
                color = AppColors.Blue.background,
                shape = RoundedCornerShape((AppNestedShapeRadius * designScale).dp),
                modifier = Modifier.fillMaxWidth().padding(start = (16 * designScale).dp, top = (136 * designScale).dp, end = (16 * designScale).dp)
                    .height((56 * designScale).dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppText(
                        "点击卡片可以查看答案。",
                        AppTextRole.Supporting,
                        modifier = Modifier.padding(horizontal = (24 * designScale).dp),
                        color = AppColors.TextIconDark.copy(alpha = .75f),
                        designScale = designScale,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Box(
                Modifier.padding(start = (16 * designScale).dp, top = (208 * designScale).dp, end = (16 * designScale).dp)
                    .fillMaxWidth().height((580 * designScale).dp)
                    .clip(RoundedCornerShape((AppScrollableContentClipRadius * designScale).dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * designScale).dp),
                    verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
                ) {
                    items(samples.indices.toList()) { index ->
                        PdfPreviewCard(samples[index], types.getOrElse(index) { types.last() }, designScale)
                    }
                }
            }
            DeckDetailHeader(
                title = "样卡预览",
                designScale = designScale,
                onBack = onBack,
                modifier = Modifier.zIndex(1f)
            )
            BottomContentFade(designScale, Modifier.align(Alignment.BottomCenter))
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(horizontal = (16 * designScale).dp, vertical = (16 * designScale).dp)
                    .fillMaxWidth().height((60 * designScale).dp).zIndex(1f),
                horizontalArrangement = Arrangement.spacedBy((12 * designScale).dp)
            ) {
                CardListActionButton("返回调整", "cycle", false, Modifier.weight(1f), designScale, onClick = onBack)
                CardListActionButton("开始生成", "play_circle", true, Modifier.weight(1f), designScale, onClick = onGenerate)
            }
        }
    }
}

private data class PdfPreviewType(val label: String, val background: Color, val content: Color)

@Composable
private fun PdfPreviewCard(card: CardDraft, type: PdfPreviewType, designScale: Float) {
    var flipped by remember(card.front) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = AppMotion.emphasisSpring(),
        label = "${card.front.take(12)} preview flip"
    )
    val shape = RoundedCornerShape((AppShapeRadius * designScale).dp)
    val density = LocalDensity.current.density
    Box(
        modifier = Modifier.fillMaxWidth().height((208 * designScale).dp).clip(shape)
            .clickable(interactionSource = remember(card.front) { MutableInteractionSource() }, indication = null) { flipped = !flipped }
    ) {
        PdfPreviewFace(card, type, answer = false, rotation = rotation, alpha = if (rotation <= 90f) 1f else 0f, shape = shape, density = density, designScale = designScale)
        PdfPreviewFace(card, type, answer = true, rotation = rotation, alpha = if (rotation > 90f) 1f else 0f, shape = shape, density = density, designScale = designScale)
    }
}

@Composable
private fun PdfPreviewFace(card: CardDraft, type: PdfPreviewType, answer: Boolean, rotation: Float, alpha: Float, shape: RoundedCornerShape, density: Float, designScale: Float) {
    Surface(
        color = AppColors.Blue.background,
        shape = shape,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            rotationY = if (answer) rotation - 180f else rotation
            transformOrigin = TransformOrigin.Center
            cameraDistance = 20f * density
            this.alpha = alpha
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((8 * designScale).dp), verticalAlignment = Alignment.CenterVertically) {
                    MaterialSymbol(if (answer) "wb_incandescent" else "book_5", null, tint = if (answer) MaterialTheme.colorScheme.primary else AppColors.Blue.primary, size = fixedSp(24 * designScale), filled = true)
                    AppText(if (answer) "答案" else "问题", AppTextRole.SectionTitle, color = PageForegroundColor(), designScale = designScale)
                }
                Surface(shape = RoundedCornerShape(999.dp), color = type.background) {
                    AppText(type.label, AppTextRole.Label, modifier = Modifier.padding(horizontal = (16 * designScale).dp, vertical = (8 * designScale).dp), color = type.content, designScale = designScale, maxLines = 1)
                }
            }
            AppText(
                text = if (answer) card.back else card.front,
                role = AppTextRole.Body,
                color = PageForegroundColor(),
                designScale = designScale,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

