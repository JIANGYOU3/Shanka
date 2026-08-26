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
internal fun PdfSettingsScreen(
    decks: List<DeckSummary>,
    useExistingDeck: Boolean,
    onUseExisting: (Boolean) -> Unit,
    selectedExistingDeckId: String?,
    onSelectedExistingDeck: (String?) -> Unit,
    deckName: String,
    onDeckNameChange: (String) -> Unit,
    coverage: String,
    onCoverageChange: (String) -> Unit,
    requirement: String,
    onRequirementChange: (String) -> Unit,
    onPreview: (basicBoundary: Float, analysisBoundary: Float) -> Unit,
    onBack: () -> Unit
) {
    var basicBoundary by remember { mutableFloatStateOf(40f) }
    var analysisBoundary by remember { mutableFloatStateOf(80f) }
    var deckMenuExpanded by remember { mutableStateOf(false) }
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    PdfFlowLayout("生成设置", onBack, footer = {
        Surface(
            onClick = { onPreview(basicBoundary, analysisBoundary) },
            color = AppColors.Blue.primary,
            contentColor = AppColors.TextIconLight,
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.fillMaxWidth().height((60 * scale).dp)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbol("play_circle", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                AppText(
                    "生成3张样卡",
                    AppTextRole.Label,
                    color = LocalContentColor.current,
                    designScale = scale,
                    maxLines = 1
                )
            }
        }
    }) {
        item {
            PdfSettingsSectionCard("生成牌组到哪儿", "edit_document", scale) {
                PdfDestinationChoice("新建牌组", selected = !useExistingDeck, scale = scale) {
                    onUseExisting(false)
                    deckMenuExpanded = false
                }
                PdfDestinationChoice("加入已有牌组", selected = useExistingDeck, scale = scale) {
                    onUseExisting(true)
                    // Opening the choice always reveals the menu. If the
                    // backend has not returned decks yet, the menu reports that
                    // state instead of silently doing nothing.
                    deckMenuExpanded = true
                }
                PdfDeckNameField(
                    value = if (useExistingDeck) decks.firstOrNull { it.id == selectedExistingDeckId }?.let(::displayDeckTitle).orEmpty() else deckName,
                    isExistingDeck = useExistingDeck,
                    scale = scale,
                    onValueChange = onDeckNameChange,
                    onClick = { if (useExistingDeck) deckMenuExpanded = !deckMenuExpanded },
                    onClear = {
                        if (useExistingDeck) {
                            onSelectedExistingDeck(null)
                            deckMenuExpanded = false
                        } else onDeckNameChange("")
                    }
                )
                AnimatedVisibility(visible = useExistingDeck && deckMenuExpanded) {
                    PdfDeckPickerMenu(
                        decks = decks,
                        scale = scale,
                        onSelect = { deck ->
                            onSelectedExistingDeck(deck.id)
                            deckMenuExpanded = false
                        }
                    )
                }
            }
        }
        item {
            PdfSettingsSectionCard("卡片难度", "instant_mix", scale) {
                PdfDifficultyDistribution(
                    basicBoundary = basicBoundary,
                    analysisBoundary = analysisBoundary,
                    scale = scale,
                    onBoundariesChange = { basic, analysis ->
                        basicBoundary = basic
                        analysisBoundary = analysis
                    }
                )
                AppText(
                    "左右拉动拉杆可改变题库难度比例。\n从左到右对应从易到难。",
                    AppTextRole.CardSubtitle,
                    color = AppColors.TextIconDark.copy(alpha = .55f),
                    designScale = scale
                )
            }
        }
        item {
            PdfSettingsSectionCard("生成数量", "stacks", scale) {
                Row(horizontalArrangement = Arrangement.spacedBy((16 * scale).dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("精简", "均匀", "充分").forEach { label ->
                        val selected = coverage == label
                        Surface(
                            onClick = { onCoverageChange(label) },
                            color = if (selected) AppColors.Blue.primary else AppColors.Card,
                            contentColor = if (selected) AppColors.TextIconLight else AppColors.Blue.ink,
                            shape = RoundedCornerShape((AppButtonShapeRadius * scale).dp),
                            modifier = Modifier.weight(1f).height((56 * scale).dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AppText(
                                    label,
                                    if (selected) AppTextRole.SectionTitle else AppTextRole.Body,
                                    color = LocalContentColor.current,
                                    designScale = scale,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                AppText(
                    "解释说明文字",
                    AppTextRole.CardSubtitle,
                    color = AppColors.TextIconDark.copy(alpha = .55f),
                    designScale = scale
                )
            }
        }
        item {
            PdfSettingsSectionCard("自定义要求", "wand_stars", scale) {
                PdfRequirementField(requirement, onRequirementChange, scale)
            }
        }
    }
}

@Composable
private fun PdfSettingsSectionCard(title: String, icon: String, scale: Float, content: @Composable ColumnScope.() -> Unit) = Surface(
    color = AppColors.Blue.background,
    shape = RoundedCornerShape((AppShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = AppMotion.emphasisSpring())
) {
    Column(
        modifier = Modifier.padding((24 * scale).dp),
        verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol(icon, null, tint = AppColors.Blue.ink, size = fixedSp(24 * scale), filled = true)
            AppText(title, AppTextRole.CardTitle, color = AppColors.Blue.ink, designScale = scale, maxLines = 1)
        }
        content()
    }
}

@Composable
private fun PdfDestinationChoice(label: String, selected: Boolean, scale: Float, onClick: () -> Unit) = Surface(
    onClick = onClick,
    shape = RoundedCornerShape((AppButtonShapeRadius * scale).dp),
    color = if (selected) AppColors.Blue.surface else AppColors.Card,
    modifier = Modifier.fillMaxWidth().height((64 * scale).dp)
) {
    Row(Modifier.padding(horizontal = (16 * scale).dp), horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbol(if (selected) "radio_button_checked" else "radio_button_unchecked", null, tint = if (selected) AppColors.Blue.primary else AppColors.Blue.ink, size = fixedSp(24 * scale), filled = selected)
        AppText(label, AppTextRole.Body, color = AppColors.Blue.ink, designScale = scale, maxLines = 1)
    }
}

@Composable
private fun PdfDeckNameField(value: String, isExistingDeck: Boolean, scale: Float, onValueChange: (String) -> Unit, onClick: () -> Unit, onClear: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height((64 * scale).dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (!isExistingDeck) onValueChange(it) },
            readOnly = isExistingDeck,
            placeholder = { AppText("此处输入牌组名称", AppTextRole.CardSubtitle, designScale = scale) },
            trailingIcon = {
                IconButton(onClick = onClear) { MaterialSymbol("cancel", "清除牌组选择", tint = AppColors.Blue.ink, size = fixedSp(24 * scale), filled = true) }
            },
            textStyle = appInputTextStyle(AppTextRole.CardSubtitle, scale, AppColors.Blue.ink),
            visualTransformation = rememberBilingualInputTransformation(AppTextRole.CardSubtitle, scale),
            shape = RoundedCornerShape((16 * scale).dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Blue.primaryStrong,
                unfocusedBorderColor = AppColors.Blue.primaryStrong,
                cursorColor = AppColors.Blue.primaryStrong
            ),
            singleLine = true,
            modifier = Modifier.fillMaxSize().then(
                if (isExistingDeck) Modifier.clickable(onClick = onClick) else Modifier
            )
        )
        AppText(
            "牌组名称",
            AppTextRole.Label,
            modifier = Modifier.align(Alignment.TopStart).offset(x = (12 * scale).dp, y = (-9 * scale).dp)
                .background(AppColors.Blue.background).padding(horizontal = (4 * scale).dp).zIndex(1f),
            color = AppColors.Blue.primaryStrong,
            designScale = scale,
            maxLines = 1
        )
    }
}

@Composable
private fun PdfDeckPickerMenu(decks: List<DeckSummary>, scale: Float, onSelect: (DeckSummary) -> Unit) {
    Surface(
        // Figma 166:8299 menu surface (the destination rows keep their own
        // selected-state token, while this picker uses the lighter menu blue).
        color = AppColors.Blue.primarySecondary,
        shape = RoundedCornerShape((20 * scale).dp),
        modifier = Modifier.fillMaxWidth().height((231 * scale).dp).clip(RoundedCornerShape((20 * scale).dp))
    ) {
        LazyColumn(
            contentPadding = PaddingValues((16 * scale).dp),
            // Figma 166:8221: title-to-divider and item-to-item spacing are both
            // 16dp. The fixed-height LazyColumn clips and vertically scrolls any
            // additional deck titles instead of compressing the list.
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            if (decks.isEmpty()) {
                item {
                    AppText(
                        "暂无可用牌组",
                        AppTextRole.Body,
                        color = AppColors.Blue.ink.copy(alpha = .75f),
                        designScale = scale
                    )
                }
            }
            items(decks, key = { it.id }) { deck ->
                val index = decks.indexOfFirst { it.id == deck.id }
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(deck) },
                    verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                        MaterialSymbol("style", null, tint = AppColors.Blue.ink.copy(alpha = .75f), size = fixedSp(24 * scale), filled = true)
                        AppText(
                            displayDeckTitle(deck),
                            AppTextRole.Body,
                            color = AppColors.Blue.ink.copy(alpha = .75f),
                            designScale = scale,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider(color = AppColors.Blue.ink.copy(alpha = .1f), thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun PdfRequirementField(value: String, onValueChange: (String) -> Unit, scale: Float) = Surface(
    color = AppColors.Card,
    shape = RoundedCornerShape((AppShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth().height((86 * scale).dp)
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = appInputTextStyle(AppTextRole.Body, scale, AppColors.Blue.ink),
        visualTransformation = rememberBilingualInputTransformation(AppTextRole.Body, scale),
        modifier = Modifier.fillMaxSize().padding((16 * scale).dp),
        decorationBox = { input ->
            if (value.isEmpty()) AppText("此处输入文本", AppTextRole.Body, color = AppColors.TextIconDark.copy(alpha = .625f), designScale = scale)
            input()
        }
    )
}

@Composable
private fun PdfDifficultyDistribution(
    basicBoundary: Float,
    analysisBoundary: Float,
    scale: Float,
    onBoundariesChange: (basicBoundary: Float, analysisBoundary: Float) -> Unit
) {
    val basic = basicBoundary.roundToInt()
    val analysis = (analysisBoundary - basicBoundary).roundToInt()
    val advanced = 100 - analysisBoundary.roundToInt()
    val basicColor = AppColors.Blue.primarySecondary
    val analysisColor = AppColors.Green.primarySecondary
    val advancedColor = AppColors.Pink.primarySecondary

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        PdfDifficultyLabel("基础记忆", basic, basicColor, AppColors.Blue.ink, scale)
        PdfDifficultyLabel("理解分析", analysis, analysisColor, AppColors.Green.ink, scale)
        PdfDifficultyLabel("综合应用", advanced, advancedColor, AppColors.Pink.ink, scale)
    }
    PdfDifficultyRangeSlider(
        basicBoundary = basicBoundary,
        analysisBoundary = analysisBoundary,
        scale = scale,
        basicColor = AppColors.Blue.primary,
        analysisColor = AppColors.Blue.primary,
        advancedColor = AppColors.Blue.primary,
        onBoundariesChange = onBoundariesChange
    )
}

@Composable
private fun PdfDifficultyLabel(label: String, percent: Int, color: Color, contentColor: Color, scale: Float) {
    Surface(color = color, contentColor = contentColor, shape = RoundedCornerShape((20 * scale).dp)) {
        Column(
            modifier = Modifier.padding(horizontal = (16 * scale).dp, vertical = (8 * scale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppText("$percent%", AppTextRole.Label, color = contentColor, designScale = scale, maxLines = 1)
            AppText(label, AppTextRole.Label, color = contentColor, designScale = scale, maxLines = 1)
        }
    }
}

@Composable
private fun PdfDifficultyRangeSlider(
    basicBoundary: Float,
    analysisBoundary: Float,
    scale: Float,
    basicColor: Color,
    analysisColor: Color,
    advancedColor: Color,
    onBoundariesChange: (basicBoundary: Float, analysisBoundary: Float) -> Unit
) {
    val thumbColor = AppColors.Blue.primarySecondary
    val currentBasic by rememberUpdatedState(basicBoundary)
    val currentAnalysis by rememberUpdatedState(analysisBoundary)
    val onCurrentBoundariesChange by rememberUpdatedState(onBoundariesChange)
    var activeThumb by remember { mutableIntStateOf(-1) }
    val minShare = 5f
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height((58 * scale).dp)
            .semantics { contentDescription = "卡片难度分布：基础记忆 ${basicBoundary.roundToInt()}%，理解分析 ${(analysisBoundary - basicBoundary).roundToInt()}%，综合应用 ${100 - analysisBoundary.roundToInt()}%" }
    ) {
        val gap = (5 * scale).dp
        val thumbWidth = (11 * scale).dp
        val thumbHeight = (46 * scale).dp
        val trackHeight = (20 * scale).dp
        val usableTrackWidth = maxWidth - thumbWidth * 2 - gap * 4
        // These offsets reproduce Figma's initial 107 / 128 / 45dp visual
        // segments at 40% / 40% / 20%, while still responding continuously.
        val firstTrackWidth = (usableTrackWidth * (basicBoundary / 100f) - gap).coerceAtLeast(0.dp)
        val secondTrackWidth = (usableTrackWidth * ((analysisBoundary - basicBoundary) / 100f) + (16 * scale).dp).coerceAtLeast(0.dp)
        val firstThumbCenterPx = with(density) { (firstTrackWidth + gap + thumbWidth / 2).toPx() }
        val secondThumbCenterPx = with(density) { (firstTrackWidth + gap + thumbWidth + gap + secondTrackWidth + gap + thumbWidth / 2).toPx() }
        val hitRadiusPx = with(density) { (24 * scale).dp.toPx() }
        // The pointerInput coroutine is intentionally retained during a drag. Its
        // hit-test coordinates must therefore follow recomposition as thumbs move,
        // otherwise subsequent drags target the thumbs' original positions only.
        val currentFirstThumbCenterPx by rememberUpdatedState(firstThumbCenterPx)
        val currentSecondThumbCenterPx by rememberUpdatedState(secondThumbCenterPx)
        val currentHitRadiusPx by rememberUpdatedState(hitRadiusPx)
        Row(
            modifier = Modifier
                .fillMaxSize()
                // Keep this gesture scope alive while the percentages update;
                // the latest values are read through rememberUpdatedState.
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position ->
                            val firstDistance = abs(position.x - currentFirstThumbCenterPx)
                            val secondDistance = abs(position.x - currentSecondThumbCenterPx)
                            activeThumb = when {
                                firstDistance <= currentHitRadiusPx && firstDistance <= secondDistance -> 0
                                secondDistance <= currentHitRadiusPx -> 1
                                else -> -1
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (activeThumb == -1) return@detectDragGestures
                            change.consume()
                            val delta = dragAmount.x / size.width * 100f
                            if (activeThumb == 0) {
                                onCurrentBoundariesChange(
                                    (currentBasic + delta).coerceIn(minShare, currentAnalysis - minShare),
                                    currentAnalysis
                                )
                            } else {
                                onCurrentBoundariesChange(
                                    currentBasic,
                                    (currentAnalysis + delta).coerceIn(currentBasic + minShare, 100f - minShare)
                                )
                            }
                        },
                        onDragEnd = { activeThumb = -1 },
                        onDragCancel = { activeThumb = -1 }
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(firstTrackWidth).height(trackHeight).clip(RoundedCornerShape(999.dp)).background(basicColor))
            Box(Modifier.width(thumbWidth).height(thumbHeight).clip(RoundedCornerShape(999.dp)).background(thumbColor))
            Box(Modifier.width(secondTrackWidth).height(trackHeight).clip(RoundedCornerShape(999.dp)).background(analysisColor))
            Box(Modifier.width(thumbWidth).height(thumbHeight).clip(RoundedCornerShape(999.dp)).background(thumbColor))
            Box(Modifier.weight(1f).height(trackHeight).clip(RoundedCornerShape(999.dp)).background(advancedColor))
        }
    }
}
