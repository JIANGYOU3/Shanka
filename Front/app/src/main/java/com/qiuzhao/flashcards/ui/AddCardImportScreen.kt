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
internal fun AddCardScreen(deckId: String, viewModel: AppViewModel, nav: ScreenNavigator) {
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    val submitting by viewModel.importSubmitting.collectAsState()
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            // Only field content scrolls. The title, back control and two actions retain
            // their Figma positions while long questions/answers stay reachable.
            Box(
                Modifier.fillMaxSize()
                    .padding(start = (16 * designScale).dp, top = (148 * designScale).dp, end = (16 * designScale).dp)
                    .clip(RoundedCornerShape(AppScrollableContentClipRadius.dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(controlCount = 2, gapBetweenControls = 12) * designScale).dp),
                    verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy((15 * designScale).dp)) {
                            AddCardLabel("问题", designScale)
                            AddCardTextField(front, { front = it }, "此处输入", designScale)
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
                            AddCardLabel("答案", designScale)
                            AddCardTextField(back, { back = it }, "此处输入", designScale)
                        }
                    }
                }
            }
            DeckDetailHeader(
                title = "添加卡片", designScale = designScale, onBack = nav::popBackStack,
                modifier = Modifier.zIndex(1f)
            )
            BottomContentFade(designScale, Modifier.align(Alignment.BottomCenter))
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = (16 * designScale).dp, end = (16 * designScale).dp, bottom = (32 * designScale).dp).zIndex(1f),
                verticalArrangement = Arrangement.spacedBy((12 * designScale).dp)
            ) {
                DetailPrimaryButton(
                    if (submitting) "正在添加…" else "添加单个卡片",
                    if (submitting) "hourglass_top" else "add_circle",
                    true,
                    designScale,
                    enabled = !submitting,
                ) {
                    viewModel.addCardsToDeck(deckId, listOf(CardDraft(front = front, back = back))) { nav.goBack() }
                }
                DetailPrimaryButton("批量导入", "note_stack_add", false, designScale, enabled = !submitting) { nav.navigate(AppRoute.ImportToDeck(deckId)) }
            }
        }
    }
}

@Composable
private fun AddCardLabel(text: String, designScale: Float) {
    AppText(
        text = text,
        role = AppTextRole.SectionTitle,
        modifier = Modifier.fillMaxWidth().padding(horizontal = (8 * designScale).dp),
        color = PageForegroundColor(),
        designScale = designScale
    )
}

@Composable
private fun AddCardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    designScale: Float,
    height: Float = 177f,
    singleLine: Boolean = false,
    cornerRadius: Float = AppShapeRadius.toFloat()
) {
    Box(
        modifier = Modifier.fillMaxWidth().height((height * designScale).dp)
            .clip(RoundedCornerShape((cornerRadius * designScale).dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding((24 * designScale).dp)
    ) {
        if (value.isBlank()) {
            AppText(
                text = placeholder,
                role = AppTextRole.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
                designScale = designScale
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = appInputTextStyle(AppTextRole.Body, designScale, PageForegroundColor()),
            visualTransformation = rememberBilingualInputTransformation(AppTextRole.Body, designScale),
            singleLine = singleLine
        )
    }
}

private enum class ImportStage { CHOICE, PASTE }

@Composable
internal fun ImportScreen(viewModel: AppViewModel, nav: ScreenNavigator, existingDeckId: String? = null) {
    var deckName by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf("") }
    val drafts = remember { mutableStateListOf<CardDraft>() }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    var stage by remember(existingDeckId) { mutableStateOf(if (existingDeckId == null) ImportStage.CHOICE else ImportStage.PASTE) }
    // The submit coordinator's in-flight flag gates the save button: no double submits, and a
    // failed batch keeps the drafts on screen so the retry replays only the failed step.
    val submitting by viewModel.importSubmitting.collectAsState()

    if (drafts.isEmpty()) {
        when (stage) {
            ImportStage.CHOICE -> ImportMethodChoiceScreen(
                onBack = nav::popBackStack,
                onPasteText = { stage = ImportStage.PASTE }
            )
            ImportStage.PASTE -> PasteTextImportScreen(
                title = if (existingDeckId == null) "导入卡片组" else "批量导入",
                rawText = rawText,
                onRawTextChange = { rawText = it },
                errors = errors,
                onBack = { if (existingDeckId == null) stage = ImportStage.CHOICE else nav.popBackStack() },
                onPreview = {
                    val result = ImportParser.parse(rawText)
                    drafts.clear()
                    drafts.addAll(result.cards)
                    errors = result.errors
                }
            )
        }
    } else {
        Scaffold(topBar = { AppBar(if (existingDeckId == null) "导入卡片组" else "批量导入", nav::popBackStack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)
                .clip(RoundedCornerShape(AppScrollableContentClipRadius.dp)),
            contentPadding = PaddingValues(bottom = NaturalScrollTail.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (errors.isNotEmpty()) item { Text(errors.joinToString("\n"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            item {
                MixedLanguageText(
                    text = "已识别 ${drafts.size} 张卡",
                    color = MaterialTheme.colorScheme.onSurface,
                    chineseFont = AppFonts.MiSansSemibold,
                    latinFont = AppFonts.GoogleSansFlexSemibold,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            }
            items(drafts.indices.toList()) { itemIndex ->
                var front by remember(drafts[itemIndex].front) { mutableStateOf(drafts[itemIndex].front) }
                var back by remember(drafts[itemIndex].back) { mutableStateOf(drafts[itemIndex].back) }
                Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(front, { front = it; drafts[itemIndex] = drafts[itemIndex].copy(front = it) }, label = { AppText("问题", AppTextRole.Label) }, modifier = Modifier.fillMaxWidth(), textStyle = appInputTextStyle(), visualTransformation = rememberBilingualInputTransformation())
                    OutlinedTextField(back, { back = it; drafts[itemIndex] = drafts[itemIndex].copy(back = it) }, label = { AppText("答案", AppTextRole.Label) }, modifier = Modifier.fillMaxWidth(), minLines = 2, textStyle = appInputTextStyle(), visualTransformation = rememberBilingualInputTransformation())
                } }
            }
            item {
                Button(
                    onClick = {
                        if (existingDeckId == null) {
                            viewModel.importDeck(deckName.ifBlank { "导入卡片组" }, drafts.toList()) { nav.replaceTop(AppRoute.Deck(it)) }
                        } else {
                            viewModel.addCardsToDeck(existingDeckId, drafts.toList()) { nav.goBack() }
                        }
                    },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    MixedLanguageText(
                        text = when {
                            submitting -> "正在保存…"
                            existingDeckId == null -> "保存 ${drafts.size} 张卡"
                            else -> "加入当前卡组（${drafts.size} 张）"
                        },
                        color = LocalContentColor.current,
                        chineseFont = AppFonts.MiSansBold,
                        latinFont = AppFonts.GoogleSansFlexBold,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun ImportMethodChoiceScreen(
    onBack: () -> Unit,
    onPasteText: () -> Unit
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(start = (16 * scale).dp, top = (136 * scale).dp, end = (16 * scale).dp)
                    .clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)),
                contentPadding = PaddingValues(bottom = (NaturalScrollTail * scale).dp),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
            ) {
                item {
                    ImportMethodOption(
                        icon = "file_copy",
                        title = "粘贴文本进行制卡",
                        subtitle = "批量粘贴问题与答案",
                        detail = "支持一次粘贴多组问答，识别后可继续编辑并导入。",
                        onClick = onPasteText,
                        scale = scale
                    )
                }
            }
            DeckDetailHeader("导入卡片组", scale, onBack, modifier = Modifier.zIndex(1f))
        }
    }
}

@Composable
private fun ImportMethodOption(
    icon: String,
    title: String,
    subtitle: String,
    detail: String,
    onClick: () -> Unit,
    scale: Float
) = Column(verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
    Surface(
        onClick = onClick,
        color = AppColors.Blue.surface,
        shape = RoundedCornerShape((AppShapeRadius * scale).dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding((12 * scale).dp),
            horizontalArrangement = Arrangement.spacedBy((16 * scale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = AppColors.Blue.primary, shape = RoundedCornerShape(999.dp), modifier = Modifier.size((56 * scale).dp)) {
                Box(contentAlignment = Alignment.Center) { MaterialSymbol(icon, null, tint = AppColors.Blue.background, size = fixedSp(24 * scale)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
                AppText(title, AppTextRole.CardTitle, color = AppColors.TextIconDark, designScale = scale, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AppText(subtitle, AppTextRole.CardSubtitle, color = AppColors.TextIconDark.copy(alpha = .625f), designScale = scale, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MaterialSymbol("arrow_forward", null, tint = AppColors.Blue.ink, size = fixedSp(24 * scale))
        }
    }
    Surface(color = AppColors.Blue.background, shape = RoundedCornerShape((AppNestedShapeRadius * scale).dp), modifier = Modifier.fillMaxWidth()) {
        AppText(detail, AppTextRole.Supporting, modifier = Modifier.padding((24 * scale).dp), color = AppColors.TextIconDark, designScale = scale)
    }
}

@Composable
private fun PasteTextImportScreen(
    title: String,
    rawText: String,
    onRawTextChange: (String) -> Unit,
    errors: List<String>,
    onBack: () -> Unit,
    onPreview: () -> Unit
) {
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .padding(start = (16 * designScale).dp, top = (136 * designScale).dp, end = (16 * designScale).dp)
                    .clip(RoundedCornerShape((AppScrollableContentClipRadius * designScale).dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail() * designScale).dp),
                    verticalArrangement = Arrangement.spacedBy((12 * designScale).dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy((12 * designScale).dp)) {
                            AddCardLabel("问题与答案（可批量粘贴）", designScale)
                            AddCardTextField(rawText, onRawTextChange, "此处粘贴", designScale)
                            if (errors.isNotEmpty()) {
                                Text(
                                    errors.first(),
                                    modifier = Modifier.padding(start = (8 * designScale).dp),
                                    color = AppColors.WarningStrong,
                                    fontFamily = AppFonts.MiSansMedium,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = fixedSp(13 * designScale),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            DeckDetailHeader(
                title = title, designScale = designScale, onBack = onBack,
                modifier = Modifier.zIndex(1f)
            )
            BottomContentFade(designScale, Modifier.align(Alignment.BottomCenter))
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = (16 * designScale).dp, end = (16 * designScale).dp, bottom = (32 * designScale).dp)
                    .height((60 * designScale).dp).fillMaxWidth().zIndex(1f)
            ) {
                ImportActionButton("识别并预览", "scan", true, Modifier.fillMaxWidth(), designScale, onPreview)
            }
        }
    }
}

@Composable
internal fun ImportActionButton(
    text: String,
    icon: String?,
    primary: Boolean,
    modifier: Modifier,
    designScale: Float,
    onClick: () -> Unit
) {
    val container = if (primary) AppColors.Blue.primary else AppColors.Blue.primarySecondary
    val content = if (primary) AppColors.TextIconLight else AppColors.Blue.ink
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape((24 * designScale).dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = (12 * designScale).dp)
    ) {
        if (icon != null) {
            MaterialSymbol(icon, null, tint = content, size = fixedSp(22 * designScale), filled = true)
            Spacer(Modifier.width((6 * designScale).dp))
        }
        AppText(text, AppTextRole.Label, color = content, designScale = designScale, maxLines = 1)
    }
}
