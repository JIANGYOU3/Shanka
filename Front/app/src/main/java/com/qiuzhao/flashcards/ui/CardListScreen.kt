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


internal enum class CardListMode { GENERATED, EDIT }

@Composable
internal fun CardListScreen(
    deckId: String,
    viewModel: AppViewModel,
    nav: ScreenNavigator,
    mode: CardListMode = CardListMode.GENERATED
) {
    LaunchedEffect(deckId) { viewModel.refreshCards(deckId) }
    val cards by viewModel.cards(deckId).collectAsState(initial = emptyList())
    val decks by viewModel.decks.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val deck = decks.firstOrNull { it.id == deckId }
    var optimisticDeck by remember(deckId) { mutableStateOf<DeckSummary?>(null) }
    var optimisticCards by remember(deckId) { mutableStateOf<Map<String, FlashcardEntity>>(emptyMap()) }
    var pendingDeletedCards by remember(deckId) { mutableStateOf<Set<String>>(emptySet()) }
    var deleteFailed by remember(deckId) { mutableStateOf(false) }
    val displayDeck = optimisticDeck ?: deck
    val visibleCards = cards
        .asSequence()
        .filterNot { it.id in pendingDeletedCards }
        .map { optimisticCards[it.id] ?: it }
        .toList()
    val theme = displayDeck?.let { deckTheme(it, projects) } ?: DeckThemes.first()
    var editingCard by remember { mutableStateOf<FlashcardEntity?>(null) }
    var deletingCard by remember { mutableStateOf<FlashcardEntity?>(null) }
    var editingDeckPresentation by remember { mutableStateOf(false) }
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)

    LaunchedEffect(deleteFailed) {
        if (deleteFailed) {
            delay(1_800)
            deleteFailed = false
        }
    }
    LaunchedEffect(cards) {
        optimisticCards = optimisticCards.filter { (id, optimisticCard) ->
            cards.firstOrNull { it.id == id }?.let { serverCard ->
                serverCard.front != optimisticCard.front || serverCard.back != optimisticCard.back
            } == true
        }
        pendingDeletedCards = pendingDeletedCards.intersect(cards.map { it.id }.toSet())
    }

    Surface(Modifier.fillMaxSize(), color = theme.surface) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    // Figma 118:2389: cards begin 194dp from the design canvas top.
                    .padding(start = (16 * designScale).dp, top = (194 * designScale).dp, end = (16 * designScale).dp)
                    .height((693 * designScale).dp)
                    .clip(RoundedCornerShape((AppScrollableContentClipRadius * designScale).dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 40) * designScale).dp),
                    verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
                ) {
                    items(visibleCards, key = { it.id }) { card ->
                        CardListItem(
                            card = card,
                            number = card.position + 1,
                            designScale = designScale,
                            theme = theme,
                            onEdit = { editingCard = card },
                            onDelete = { deletingCard = card }
                        )
                    }
                }
            }
            Text(
                "点击卡片可以查看答案。\n卡片左滑可进行编辑与删除。",
                modifier = Modifier.fillMaxWidth()
                    .padding(start = (26 * designScale).dp, top = (136 * designScale).dp, end = (26 * designScale).dp),
                color = theme.text,
                fontFamily = AppFonts.MiSansMedium,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(16 * designScale),
                lineHeight = fixedSp(20 * designScale),
                textAlign = TextAlign.Center
            )
            DeckDetailHeader(
                // Figma 118:2389 intentionally leaves the centre of this edit
                // list header empty: this is a back-only secondary information bar.
                title = if (mode == CardListMode.EDIT) "" else "卡片列表",
                designScale = designScale,
                onBack = nav::popBackStack,
                theme = if (mode == CardListMode.EDIT) theme else null,
                subtitle = if (mode == CardListMode.GENERATED) "${visibleCards.size} cards" else null,
                modifier = Modifier.zIndex(1f)
            )
            BottomContentFade(designScale, Modifier.align(Alignment.BottomCenter))
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = (16 * designScale).dp, end = (16 * designScale).dp, bottom = (40 * designScale).dp)
                    .fillMaxWidth().height((60 * designScale).dp).zIndex(1f),
                horizontalArrangement = Arrangement.spacedBy(((if (mode == CardListMode.EDIT) 16 else 12) * designScale).dp)
            ) {
                if (mode == CardListMode.EDIT) {
                    CardListActionButton(
                        label = "编辑名称",
                        icon = "edit",
                        primary = false,
                        // Figma 121:2630 keeps this label button at its natural
                        // content width; giving both actions equal weight cuts the
                        // final character on narrower devices.
                        modifier = Modifier,
                        designScale = designScale,
                        theme = theme,
                        onClick = { editingDeckPresentation = true }
                    )
                    CardListActionButton(
                        label = "添加卡片",
                        icon = "add_circle",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        designScale = designScale,
                        theme = theme,
                        onClick = { nav.navigate(AppRoute.AddCard(deckId)) }
                    )
                } else {
                    CardListActionButton(
                        label = "返回调整",
                        icon = "cycle",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        designScale = designScale,
                        onClick = nav::popBackStack
                    )
                    CardListActionButton(
                        label = "完成设置",
                        icon = "celebration",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        designScale = designScale,
                        onClick = { nav.replaceInclusive(AppRoute.CardList(deckId), AppRoute.Deck(deckId)) }
                    )
                }
            }
            DeleteFailureHint(
                visible = deleteFailed,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = (112 * designScale).dp)
            )
        }
    }

    editingCard?.let { card ->
        CardEditDialog(
            card = card,
            onSave = { updated ->
                optimisticCards = optimisticCards + (updated.id to updated)
                viewModel.updateCard(updated) { optimisticCards = optimisticCards - updated.id }
                editingCard = null
            },
            onDismiss = { editingCard = null }
        )
    }
    deletingCard?.let { card ->
        AlertDialog(
            onDismissRequest = { deletingCard = null },
            title = { Text("删除该卡？", fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal) },
            text = { Text("删除后无法恢复。", fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeletedCards = pendingDeletedCards + card.id
                    viewModel.deleteCard(card) {
                        pendingDeletedCards = pendingDeletedCards - card.id
                        deleteFailed = true
                    }
                    deletingCard = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingCard = null }) { Text("取消") } }
        )
    }
    if (editingDeckPresentation && displayDeck != null) {
        DeckPresentationDialog(
            deck = displayDeck,
            theme = theme,
            onDismiss = { editingDeckPresentation = false },
            onSave = { name ->
                // The project's theme is intentionally not editable here.
                val updated = displayDeck.copy(name = name)
                optimisticDeck = updated
                viewModel.updateDeckName(displayDeck.id, name) {
                    optimisticDeck = null
                }
                editingDeckPresentation = false
            }
        )
    }
}

@Composable
internal fun CardListActionButton(label: String, icon: String, primary: Boolean, modifier: Modifier, designScale: Float = 1f, theme: DeckTheme? = null, onClick: () -> Unit) {
    val primaryColor = theme?.primary ?: AppColors.Blue.primary
    val primaryContent = theme?.onPrimary ?: AppColors.TextIconLight
    val secondaryColor = theme?.secondary ?: AppColors.Blue.surface
    val secondaryContent = theme?.strongText ?: AppColors.Blue.ink
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape((24 * designScale).dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (primary) primaryColor else secondaryColor,
            contentColor = if (primary) primaryContent else secondaryContent
        ),
        contentPadding = PaddingValues(horizontal = (24 * designScale).dp)
    ) {
        MaterialSymbol(icon, null, tint = LocalContentColor.current, size = fixedSp(24 * designScale), filled = true)
        Spacer(Modifier.width((8 * designScale).dp))
        Text(label, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(16 * designScale), letterSpacing = fixedSp(.6f * designScale), maxLines = 1)
    }
}

@Composable
private fun CardListItem(card: FlashcardEntity, number: Int, designScale: Float, theme: DeckTheme, onEdit: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape((AppShapeRadius * designScale).dp)
    val actionWidth = (112 * designScale).dp
    // The panel remains 112dp wide. The front card stops 16dp into it, exactly
    // matching Figma 143:3526, so the exposed panel measures 96dp.
    val actionOverlap = (16 * designScale).dp
    val revealWidth = actionWidth - actionOverlap
    val revealWidthPx = with(LocalDensity.current) { revealWidth.toPx() }
    var flipped by remember(card.id) { mutableStateOf(false) }
    var dragOffset by remember(card.id) { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(dragOffset, label = "${card.id} card list swipe")
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = AppMotion.emphasisSpring(),
        label = "${card.id} card list flip"
    )
    val draggable = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceIn(-revealWidthPx, 0f)
    }

    Box(
        // Figma 121:2964 reuses the preview card's shared maximum height so
        // either side of a flipped card occupies identical space.
        modifier = Modifier.fillMaxWidth().height((209 * designScale).dp).clip(shape)
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).width(actionWidth).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy((8 * designScale).dp)
        ) {
            CardListSwipeAction(
                label = "删除该卡",
                icon = "delete",
                color = AppColors.Warning,
                contentColor = AppColors.TextIconLight,
                modifier = Modifier.weight(1f),
                designScale = designScale,
                onClick = onDelete
            )
            CardListSwipeAction(
                label = "编辑卡片",
                icon = "edit",
                color = theme.secondary,
                contentColor = theme.strongText,
                modifier = Modifier.weight(1f),
                designScale = designScale,
                onClick = onEdit
            )
        }
        Box(
            modifier = Modifier.fillMaxSize()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .clickable(interactionSource = remember(card.id) { MutableInteractionSource() }, indication = null) { flipped = !flipped }
                .draggable(
                    state = draggable,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { dragOffset = if (dragOffset < -revealWidthPx / 2f) -revealWidthPx else 0f }
                )
        ) {
            CardListFace(card, number, false, rotation, if (rotation <= 90f) 1f else 0f, shape, designScale, theme)
            CardListFace(card, number, true, rotation, if (rotation > 90f) 1f else 0f, shape, designScale, theme)
        }
    }
}

@Composable
private fun CardListSwipeAction(label: String, icon: String, color: Color, contentColor: Color, modifier: Modifier, designScale: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape((AppButtonShapeRadius * designScale).dp),
        color = color,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            // Figma uses 24dp/16dp asymmetric padding: it places the visual
            // centre of the action content in the exposed part of the panel.
            modifier = Modifier.fillMaxSize().padding(start = (24 * designScale).dp, end = (16 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MaterialSymbol(icon, label, tint = contentColor, size = fixedSp(24 * designScale), filled = true)
            Spacer(Modifier.height((4 * designScale).dp))
            Text(label, color = contentColor, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale), maxLines = 1)
        }
    }
}

/** Figma 118:2389 cycles the card-type pill independently from the deck theme. */
private data class CardListTagStyle(val label: String, val container: Color, val content: Color)

private val CardListTagStyles = listOf(
    CardListTagStyle("基础记忆", AppColors.Blue.primarySecondary, AppColors.Blue.ink),
    CardListTagStyle("理解分析", AppColors.Green.primarySecondary, AppColors.Green.ink),
    CardListTagStyle("综合应用", AppColors.Pink.primarySecondary, AppColors.Pink.ink)
)

private fun cardListTagStyle(number: Int): CardListTagStyle =
    CardListTagStyles[(number - 1).mod(CardListTagStyles.size)]

@Composable
private fun CardListFace(card: FlashcardEntity, number: Int, answer: Boolean, rotation: Float, alpha: Float, shape: RoundedCornerShape, designScale: Float, theme: DeckTheme) {
    val density = LocalDensity.current.density
    val tagStyle = cardListTagStyle(number)
    Surface(
        color = theme.surface,
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
                    MaterialSymbol(
                        if (answer) "wb_incandescent" else "book_5",
                        null,
                        tint = theme.text,
                        size = fixedSp(24 * designScale),
                        filled = true
                    )
                    Text(
                        if (answer) "答案" else "问题",
                        color = theme.text,
                        fontFamily = AppFonts.MiSansSemibold,
                        fontWeight = FontWeight.Normal,
                        fontSize = fixedSp(24 * designScale),
                        lineHeight = fixedSp(28 * designScale)
                    )
                }
                Surface(shape = RoundedCornerShape(999.dp), color = tagStyle.container) {
                    Text(
                        tagStyle.label,
                        modifier = Modifier.padding(horizontal = (16 * designScale).dp, vertical = (8 * designScale).dp),
                        color = tagStyle.content,
                        fontFamily = AppFonts.MiSansBold,
                        fontWeight = FontWeight.Normal,
                        fontSize = fixedSp(16 * designScale),
                        lineHeight = fixedSp(20 * designScale),
                        maxLines = 1
                    )
                }
            }
            Text(
                if (answer) card.back else card.front,
                color = theme.text,
                fontFamily = AppFonts.MiSansMedium,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(20 * designScale),
                lineHeight = fixedSp(28 * designScale),
                textAlign = TextAlign.Start,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun CardEditDialog(card: FlashcardEntity, onSave: (FlashcardEntity) -> Unit, onDismiss: () -> Unit) {
    var front by remember(card.id) { mutableStateOf(card.front) }
    var back by remember(card.id) { mutableStateOf(card.back) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑卡片", fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(front, { front = it }, label = { AppText("问题", AppTextRole.Label) }, modifier = Modifier.fillMaxWidth(), minLines = 2, textStyle = appInputTextStyle(), visualTransformation = rememberBilingualInputTransformation())
                OutlinedTextField(back, { back = it }, label = { AppText("答案", AppTextRole.Label) }, modifier = Modifier.fillMaxWidth(), minLines = 2, textStyle = appInputTextStyle(), visualTransformation = rememberBilingualInputTransformation())
            }
        },
        confirmButton = {
            TextButton(onClick = { if (front.isNotBlank() && back.isNotBlank()) onSave(card.copy(front = front.trim(), back = back.trim())) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun DeckPresentationDialog(
    deck: DeckSummary,
    theme: DeckTheme,
    onDismiss: () -> Unit,
    onSave: (name: String) -> Unit
) {
    var name by remember(deck.id, deck.name) { mutableStateOf(displayDeckTitle(deck)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "编辑卡组名称",
                color = theme.strongText,
                fontFamily = AppFonts.MiSansSemibold,
                fontWeight = FontWeight.Normal
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { AppText("卡片组名称", AppTextRole.Label) },
                    singleLine = true,
                    textStyle = appInputTextStyle(AppTextRole.CardSubtitle),
                    visualTransformation = rememberBilingualInputTransformation(AppTextRole.CardSubtitle)
                )
                Text(
                    "主题色继承自所属项目 · ${theme.label}",
                    color = theme.strongText,
                    fontFamily = AppFonts.MiSansSemibold,
                    fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(16f),
                    lineHeight = fixedSp(21f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) {
                Text("保存", color = theme.primary, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
