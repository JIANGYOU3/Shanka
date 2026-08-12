package com.qiuzhao.flashcards.ui

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

private typealias ScreenNavigator = AppNavigator
private val LightHeaderControlBackground = Color(0xFFEBF0F5)
private val LightHeaderControlIcon = Color(0xFF374B61)
private val PageTitleColor = Color(0xFF1F2832)
// Root list content begins with a card. A rounded top crop would remove content
// from that card's 24dp inset, so only the lower viewport corners are rounded.
private val BottomRoundedViewportShape = RoundedCornerShape(
    bottomStart = AppShapeRadius.dp,
    bottomEnd = AppShapeRadius.dp
)

/** White-screen titles are the Figma #1F2832; dark surfaces use the theme's contrast color. */
@Composable
private fun PageForegroundColor(): Color = if (MaterialTheme.colorScheme.background.luminance() > .5f) {
    PageTitleColor
} else {
    MaterialTheme.colorScheme.onSurface
}

@Composable
private fun HeaderControlBackgroundColor(): Color = if (MaterialTheme.colorScheme.background.luminance() > .5f) {
    LightHeaderControlBackground
} else {
    Color(0xFF20303F)
}

@Composable
private fun HeaderControlIconColor(): Color = if (MaterialTheme.colorScheme.background.luminance() > .5f) {
    LightHeaderControlIcon
} else {
    Color(0xFFD2E2F1)
}

/** Keeps Figma's optical type scale stable even when the phone font-size setting changes. */
@Composable
private fun fixedSp(value: Float) = with(LocalDensity.current) { value.dp.toSp() }

/** Figma text frames have no Android ascent/descent padding around their line box. */
private fun figmaCardTextStyle() = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

@Composable
fun FlashcardsApp(viewModel: AppViewModel) {
    val navigationState = rememberAppNavigationState()
    val navigator = remember { AppNavigator(navigationState) }
    val decks by viewModel.decks.collectAsState()
    val dueCount by viewModel.dueCount.collectAsState()
    val dashboard by viewModel.dashboard.collectAsState()
    val weeklyActivity by viewModel.weeklyActivity.collectAsState()
    var studySearchQuery by remember { mutableStateOf("") }
    val selectedRootTab = when (navigationState.selectedTopLevel) {
        AppRoute.Home -> RootTab.HOME
        AppRoute.Library -> RootTab.STUDY
        AppRoute.Data -> RootTab.DATA
        else -> error("Top-level navigation state must be a root route")
    }

    val entryProvider = entryProvider {
        entry<AppRoute.Home> { HomeScreen(decks, dueCount, navigator) }
        entry<AppRoute.Library> { LibraryScreen(decks, viewModel, studySearchQuery, navigator) }
        entry<AppRoute.Data> { DataScreen(dueCount, dashboard, weeklyActivity, navigator) }
        entry<AppRoute.Deck> { route ->
            val deck = decks.firstOrNull { it.id == route.id }
            if (deck == null) LoadingScreen() else DeckScreen(deck, viewModel, navigator)
        }
        entry<AppRoute.Study> { route ->
            StudyScreen(viewModel, navigator, route.deckId, route.reviewMode)
        }
        entry<AppRoute.Import> { ImportScreen(viewModel, navigator) }
        entry<AppRoute.AddCard> { route -> AddCardScreen(route.deckId, viewModel, navigator) }
        entry<AppRoute.CardList> { route -> CardListScreen(route.deckId, viewModel, navigator) }
        entry<AppRoute.EditCardList> { route ->
            CardListScreen(route.deckId, viewModel, navigator, mode = CardListMode.EDIT)
        }
        entry<AppRoute.ImportToDeck> { route ->
            ImportScreen(viewModel, navigator, existingDeckId = route.deckId)
        }
        entry<AppRoute.PdfMaker> { PdfSmartCardsFlow(decks, viewModel, navigator) }
        entry<AppRoute.Settings> { SettingsScreen(viewModel, navigator) }
        entry<AppRoute.SettingsIdentity> { SettingsIdentityScreen(navigator) }
        entry<AppRoute.SettingsUnbuilt> { route -> SettingsUnbuiltScreen(route.title, navigator) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavDisplay(
            entries = navigationState.decoratedEntries(entryProvider),
            onBack = navigator::goBack,
            transitionSpec = { fadeIn(AppMotion.enter()) togetherWith fadeOut(AppMotion.exit()) },
            popTransitionSpec = { fadeIn(AppMotion.enter()) togetherWith fadeOut(AppMotion.exit()) },
            predictivePopTransitionSpec = { fadeIn(AppMotion.enter()) togetherWith fadeOut(AppMotion.exit()) }
        )
        // Root chrome remains mounted for a selected tab and is hidden on child routes.
        AnimatedVisibility(
            visible = navigationState.currentRoute == navigationState.selectedTopLevel,
            enter = fadeIn(AppMotion.enter()),
            exit = fadeOut(AppMotion.exit()),
            modifier = Modifier.fillMaxSize(),
            label = "rootPersistentChrome"
        ) {
            Box(Modifier.fillMaxSize()) {
                RootPersistentHeader(
                    selected = selectedRootTab,
                    searchQuery = studySearchQuery,
                    onSearchQueryChange = { studySearchQuery = it },
                    onSettings = { navigator.navigate(AppRoute.Settings) }
                )
                BottomNavBar(
                    selected = selectedRootTab,
                    onHome = { navigator.navigate(AppRoute.Home) },
                    onStudy = { navigator.navigate(AppRoute.Library) },
                    onData = { navigator.navigate(AppRoute.Data) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun AppBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    // The legacy Material app bar is deliberately replaced so secondary screens
    // share the 209:2733 safe-area position and control geometry.
    ScreenTopInformationBar(title = title, subtitle = null, onBack = onBack ?: {}, modifier = Modifier.zIndex(1f))
}

@Composable
private fun HomeScreen(decks: List<DeckSummary>, dueCount: Int, nav: ScreenNavigator) {
    val activeDeck = decks.firstOrNull { it.dueCount > 0 } ?: decks.firstOrNull()
    val dark = MaterialTheme.colorScheme.background.luminance() <= .5f
    // One Figma design canvas: 402dp wide. On a narrower phone, every visual value
    // uses this one scale rather than responding independently to display/font settings.
    val compactScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    val sideInset = 16 * compactScale
    // The persistent root shell owns the navigation. Home owns only its scrollable body.
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
            // This is the fixed, rounded viewport from Figma node 19:611. The list may
            // scroll inside it, but nothing can paint into the fixed settings/header area.
            // The app content area already starts beneath the device status inset.
            // Header starts at 16dp and is 56dp tall. The 88dp viewport inset keeps
            // Figma's explicit 16dp gap between it and the first content card.
            Box(Modifier.fillMaxSize().padding(start = sideInset.dp, top = (88 * compactScale).dp, end = sideInset.dp)) {
                // The positioning box establishes the viewport bounds. Only its inner
                // child is clipped, so the crop begins below the fixed settings layer.
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(AppShapeRadius.dp))) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Leave a scroll tail so the two quick cards can move fully
                        // above the floating navigation instead of becoming trapped by it.
                        contentPadding = PaddingValues(bottom = (180 * compactScale).dp),
                        verticalArrangement = Arrangement.spacedBy((12 * compactScale).dp)
                    ) {
                    item { DailyGoalCard(compactScale) }
                    item {
                        // Node 19:620 has a 12dp title-to-card-group gap; only the
                        // two cards *inside* the group retain the 16dp spacing.
                        Column(verticalArrangement = Arrangement.spacedBy((12 * compactScale).dp)) {
                            Text(
                                "用户名，快来学习", modifier = Modifier.padding(horizontal = (8 * compactScale).dp), color = PageForegroundColor(), fontFamily = AppFonts.MiSansBold,
                                fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * compactScale), lineHeight = fixedSp(28 * compactScale)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy((16 * compactScale).dp)) {
                                ContinueLearningCard(
                                    deck = activeDeck,
                                    compactScale = compactScale,
                                    onOpenDeck = { activeDeck?.let { nav.navigate(AppRoute.Deck(it.id)) } },
                                    onContinue = { activeDeck?.let { nav.navigate(AppRoute.Study(it.id, true)) } }
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * compactScale).dp)) {
                                    QuickLearningCard(
                                        modifier = Modifier.weight(1f), background = if (dark) Color(0xFF392725) else Color(0xFFFFF4F3),
                                        button = if (dark) Color(0xFFAA5B55) else Color(0xFFE9887F), textColor = if (dark) Color(0xFFFFE7E3) else Color(0xFF4A0600),
                                        iconBackground = if (dark) Color(0xFF5A302D) else Color(0xFFFFDBD8),
                                        icon = "brightness_alert", iconTint = if (dark) Color(0xFFFFB4AB) else Color(0xFF8D2118),
                                        label = "昨日错题",
                                        compactScale = compactScale, onClick = { activeDeck?.let { nav.navigate(AppRoute.Study(it.id, true)) } }
                                    )
                                    QuickLearningCard(
                                        modifier = Modifier.weight(1f), background = if (dark) Color(0xFF392F21) else Color(0xFFFFFAEF),
                                        button = if (dark) Color(0xFF9B7746) else Color(0xFFE1BA5E), textColor = if (dark) Color(0xFFFFE9C7) else Color(0xFF51411B),
                                        iconBackground = if (dark) Color(0xFF5A472A) else Color(0xFFFAEED2),
                                        icon = "star_shine", iconTint = if (dark) Color(0xFFFFDFA6) else Color(0xFF765900),
                                        label = "随机复习",
                                        compactScale = compactScale, onClick = { activeDeck?.let { nav.navigate(AppRoute.Study(it.id, false)) } }
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
            BottomContentFade(compactScale, Modifier.align(Alignment.BottomCenter))
    }
}

/** Figma node 19:1446: a visual, non-interactive finish above the floating nav. */
@Composable
private fun BottomContentFade(designScale: Float, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth()
            .height((97 * designScale).dp)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to MaterialTheme.colorScheme.background.copy(alpha = .75f)
                )
            )
            .zIndex(.5f)
    )
}

/**
 * A deliberately quiet failure acknowledgement for optimistic delete actions.
 * It is only shown when the item has to be restored, so normal editing and
 * successful deletion do not add any extra copy to the screen.
 */
@Composable
private fun DeleteFailureHint(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(140)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = modifier.zIndex(3f)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Text(
                "删除失败",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = AppFonts.MiSansMedium,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(14f)
            )
        }
    }
}

/** Shared, always-mounted header for the three root destinations. */
@Composable
private fun RootPersistentHeader(
    selected: RootTab,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSettings: () -> Unit
) {
    if (selected == RootTab.STUDY) {
        // Figma 15:3030: the Study variant retains the same 16dp-safe-area
        // information bar, with a 226dp search field between its two controls.
        val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = (16 * scale).dp, top = (16 * scale).dp, end = (16 * scale).dp)
                .height((56 * scale).dp).zIndex(2f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsHeaderButton(onSettings, (56 * scale).dp)
            Box(
                modifier = Modifier.width((258 * scale).dp).fillMaxHeight()
                    .padding(horizontal = (16 * scale).dp),
                contentAlignment = Alignment.Center
            ) {
                StudySearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ImageAvatar((56 * scale).dp)
        }
    } else {
        ScreenTopInformationBar(
            title = null,
            subtitle = null,
            onBack = null,
            onSettings = onSettings,
            modifier = Modifier.zIndex(2f)
        )
    }
}

/**
 * Shared Figma component 209:2733. Every screen gets exactly 16dp between the
 * status-bar safe area and this 56dp information bar; callers only supply its
 * variant-specific content.
 */
@Composable
private fun ScreenTopInformationBar(
    title: String?,
    subtitle: String?,
    onBack: (() -> Unit)?,
    onSettings: (() -> Unit)? = null,
    backContainer: Color? = null,
    titleColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    TopInformationBarContent(
        title = title,
        subtitle = subtitle,
        onBack = onBack,
        onSettings = onSettings,
        backContainer = backContainer,
        titleColor = titleColor,
        modifier = modifier.fillMaxWidth().statusBarsPadding()
            .padding(start = (16 * scale).dp, top = (16 * scale).dp, end = (16 * scale).dp)
    )
}

@Composable
private fun TopInformationBarContent(
    title: String?,
    subtitle: String?,
    onBack: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    backContainer: Color?,
    titleColor: Color?,
    modifier: Modifier = Modifier
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Box(modifier.height((56 * scale).dp)) {
        if (onBack == null) {
            SettingsHeaderButton(onSettings ?: {}, (56 * scale).dp)
            Box(Modifier.align(Alignment.CenterEnd)) { ImageAvatar((56 * scale).dp) }
        } else {
            Surface(
                onClick = onBack,
                color = backContainer ?: HeaderControlBackgroundColor(),
                contentColor = titleColor ?: HeaderControlIconColor(),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.size((56 * scale).dp).align(Alignment.CenterStart)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol("arrow_back", "返回", tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                }
            }
            Row(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = (60 * scale).dp),
                horizontalArrangement = Arrangement.spacedBy((16 * scale).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MixedLanguageText(
                    text = title.orEmpty(), color = titleColor ?: PageForegroundColor(),
                    // Figma 209:2733: top-information titles are MiSans 520 for
                    // Chinese and Google Sans Flex 700/ROND 100 for all Latin/digits.
                    chineseFont = AppFonts.MiSansTopInformation, latinFont = AppFonts.GoogleSansFlexBold,
                    fontSize = fixedSp(24 * scale), lineHeight = fixedSp(32 * scale),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                subtitle?.let {
                    Text(it, color = titleColor ?: PageForegroundColor(), fontFamily = AppFonts.GoogleSansFlexBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(24 * scale), lineHeight = fixedSp(32 * scale), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DailyGoalCard(compactScale: Float) {
    Card(
        shape = RoundedCornerShape(AppShapeRadius.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF48A0FF)),
        modifier = Modifier.fillMaxWidth().height((196 * compactScale).dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding((24 * compactScale).dp),
            verticalArrangement = Arrangement.spacedBy((24 * compactScale).dp)
        ) {
            Row(Modifier.fillMaxWidth().height((32 * compactScale).dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(
                    modifier = Modifier.width((115 * compactScale).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialSymbol("local_fire_department", null, tint = Color(0xFFEFF6FF), size = fixedSp(28 * compactScale), filled = true)
                    Spacer(Modifier.width((8 * compactScale).dp))
                    Text("今日目标", color = Color(0xFFEFF6FF), fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * compactScale), lineHeight = fixedSp(28 * compactScale), letterSpacing = fixedSp(-.5f * compactScale))
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFEFF6FF),
                    modifier = Modifier.width((134 * compactScale).dp).height((32 * compactScale).dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "连续天数：12",
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF2160A6),
                            fontFamily = AppFonts.MiSansBold,
                            fontWeight = FontWeight.Normal,
                            fontSize = fixedSp(16 * compactScale),
                            lineHeight = fixedSp(16 * compactScale),
                            letterSpacing = fixedSp(.6f * compactScale),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height((48 * compactScale).dp), verticalAlignment = Alignment.Bottom) {
                Text("12", modifier = Modifier.alignByBaseline(), fontFamily = AppFonts.GoogleSansFlexBold, fontSize = fixedSp(48 * compactScale), lineHeight = fixedSp(48 * compactScale), fontWeight = FontWeight.Normal, color = Color(0xFFEFF6FF), letterSpacing = fixedSp(-2.4f * compactScale))
                Text("/ 50", modifier = Modifier.padding(start = (4 * compactScale).dp).alignByBaseline(), fontFamily = AppFonts.GoogleSansFlexBold, fontSize = fixedSp(20 * compactScale), lineHeight = fixedSp(28 * compactScale), fontWeight = FontWeight.Normal, color = Color(0xFFCCDDF0))
                Text("卡片已复习", modifier = Modifier.padding(start = (4 * compactScale).dp).alignByBaseline(), color = Color(0xFFCCDDF0), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * compactScale), lineHeight = fixedSp(28 * compactScale))
                Spacer(Modifier.weight(1f))
                Text("24%", modifier = Modifier.alignByBaseline(), fontFamily = AppFonts.GoogleSansFlexBold, fontSize = fixedSp(47 * compactScale), lineHeight = fixedSp(36 * compactScale), fontWeight = FontWeight.Normal, color = Color(0xFFEFF6FF))
            }
            Row(Modifier.fillMaxWidth().height((20 * compactScale).dp), horizontalArrangement = Arrangement.spacedBy((5 * compactScale).dp)) {
                Box(Modifier.width((97 * compactScale).dp).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(Color.White))
                Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = .5f)))
            }
        }
    }
}

@Composable
private fun ContinueLearningCard(
    deck: DeckSummary?,
    compactScale: Float,
    onOpenDeck: () -> Unit,
    onContinue: () -> Unit
) {
    // Figma 184:738 is the Study deck card plus its primary action. Keep every
    // colour token shared with StudyDeckCard so the same deck never changes theme
    // merely because it is surfaced on the home page.
    val fallbackDeck = deck ?: DeckSummary("", "计算机网络", 2, "builtin", "violet", 20, 14)
    val visual = studyDeckVisual(fallbackDeck, 0)
    val cardCount = deck?.cardCount ?: 20
    val dueCount = deck?.dueCount ?: 14
    val progress = if (cardCount == 0) 0 else ((cardCount - dueCount).coerceAtLeast(0) * 100 / cardCount).coerceIn(0, 100)
    Surface(
        onClick = onOpenDeck,
        shape = RoundedCornerShape(AppShapeRadius.dp),
        color = visual.background,
        // The card opens its deck overview; the nested primary button consumes its
        // own tap and continues directly into the review flow.
        // 287:8214 latest typography is intrinsically sized inside the
        // updated 257:6634 card, so the parent keeps a small vertical buffer.
        modifier = Modifier.fillMaxWidth().height((284 * compactScale).dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding((24 * compactScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * compactScale).dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy((12 * compactScale).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy((8 * compactScale).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape((16 * compactScale).dp),
                        color = visual.iconBackground,
                        modifier = Modifier.size((56 * compactScale).dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            MaterialSymbol(visual.icon, null, tint = visual.iconTint, size = fixedSp(24 * compactScale), filled = true)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4 * compactScale).dp)) {
                        MixedLanguageText(
                            displayDeckTitle(fallbackDeck), modifier = Modifier.fillMaxWidth(), color = visual.titleColor,
                            chineseFont = AppFonts.MiSansCardBold, latinFont = AppFonts.GoogleSansFlexBold,
                            fontSize = fixedSp(20 * compactScale), lineHeight = fixedSp(24 * compactScale),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, includeFontPadding = false
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((4 * compactScale).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Figma 258:6722 uses the filled 18dp brightness-alert
                            // symbol here, not a decorative status dot.
                            MaterialSymbol("brightness_alert", null, tint = Color(0xFFD23535), size = fixedSp(18 * compactScale), filled = true)
                            Text("高优先级", color = Color(0xFFD23535), fontFamily = AppFonts.MiSansCardSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * compactScale), lineHeight = fixedSp(20 * compactScale), style = figmaCardTextStyle())
                        }
                    }
                }
                ReviewCountBadge(
                    count = cardCount,
                    background = visual.panel,
                    contentColor = visual.badgeText,
                    compactScale = compactScale
                )
            }
            Surface(
                color = visual.panel, shape = RoundedCornerShape((20 * compactScale).dp),
                modifier = Modifier.fillMaxWidth().height((80 * compactScale).dp)
            ) {
                Column(
                    Modifier.fillMaxSize().padding((12 * compactScale).dp),
                    verticalArrangement = Arrangement.spacedBy((8 * compactScale).dp)
                ) {
                    Row(Modifier.fillMaxWidth().height((28 * compactScale).dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("进度", color = visual.progressLabel, fontFamily = AppFonts.MiSansCardSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * compactScale), lineHeight = fixedSp(20 * compactScale), style = figmaCardTextStyle())
                        Text("$progress%", color = visual.progress, fontFamily = AppFonts.GoogleSansFlexBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(24 * compactScale), lineHeight = fixedSp(28 * compactScale), style = figmaCardTextStyle())
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().height((20 * compactScale).dp),
                        horizontalArrangement = Arrangement.spacedBy((5 * compactScale).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (progress > 0) {
                            Box(
                                // Figma 248:6231 specifies a 97dp filled segment for
                                // this component. Keep the percentage label data-driven,
                                // but preserve the component's designed geometry.
                                Modifier.width((97 * compactScale).dp).fillMaxHeight()
                                    .clip(RoundedCornerShape(999.dp)).background(visual.progressFill)
                            )
                        }
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(999.dp)).background(visual.progressTrack)
                        )
                    }
                }
            }
            Surface(
                onClick = onContinue,
                color = visual.action,
                contentColor = Color.White.copy(alpha = .9f),
                shape = RoundedCornerShape(AppShapeRadius.dp),
                modifier = Modifier.fillMaxWidth().height((61 * compactScale).dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy((6 * compactScale).dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("继续复习", fontFamily = AppFonts.MiSansCardBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * compactScale), lineHeight = fixedSp(20 * compactScale), style = figmaCardTextStyle())
                    MaterialSymbol("arrow_forward", null, tint = LocalContentColor.current, size = fixedSp(24 * compactScale), filled = true)
                }
            }
        }
    }
}

@Composable
private fun QuickLearningCard(
    modifier: Modifier,
    background: Color,
    button: Color,
    textColor: Color,
    iconBackground: Color,
    icon: String,
    iconTint: Color,
    label: String,
    compactScale: Float,
    onClick: () -> Unit
) {
    // Figma 287:8015: each quick-review card is 177×172dp, with two
    // equal 56dp tiles above a 52dp text-only action button.
    Card(shape = RoundedCornerShape(AppShapeRadius.dp), colors = CardDefaults.cardColors(containerColor = background), modifier = modifier.height((172 * compactScale).dp)) {
        Column(
            Modifier.fillMaxSize().padding((24 * compactScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * compactScale).dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height((56 * compactScale).dp),
                horizontalArrangement = Arrangement.spacedBy((16 * compactScale).dp)
            ) {
                Surface(
                    shape = RoundedCornerShape((18 * compactScale).dp),
                    color = iconBackground,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol(icon, null, tint = iconTint, size = fixedSp(24 * compactScale), filled = true)
                    }
                }
                Surface(
                    shape = RoundedCornerShape((18 * compactScale).dp),
                    color = iconBackground,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol("arrow_forward", null, tint = iconTint, size = fixedSp(20 * compactScale), filled = true)
                    }
                }
            }
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(AppShapeRadius.dp),
                color = button,
                contentColor = textColor,
                modifier = Modifier.fillMaxWidth().height((52 * compactScale).dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        color = textColor,
                        // Figma 287:8015: both Chinese action labels use MiSans VF 630.
                        // Use the card-specific 630 face explicitly so this cannot
                        // regress to Android's semantic Bold mapping.
                        fontFamily = AppFonts.MiSansCardBold,
                        fontWeight = FontWeight.Normal,
                        fontSize = fixedSp(16 * compactScale),
                        lineHeight = fixedSp(20 * compactScale),
                        style = figmaCardTextStyle()
                    )
                }
            }
        }
    }
}

/** Figma 287:8214 — the reusable English two-line total-card badge. */
@Composable
private fun ReviewCountBadge(
    count: Int,
    background: Color,
    contentColor: Color,
    compactScale: Float
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(999.dp),
        // 287:8214: intrinsic Figma sizing — the 24dp icon and the two-line
        // text stack determine the height; the component itself supplies the
        // specified 12dp vertical padding without an Android-imposed height.
        modifier = Modifier
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = (16 * compactScale).dp,
                vertical = (12 * compactScale).dp
            ),
            horizontalArrangement = Arrangement.spacedBy((8 * compactScale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbol(
                "playing_cards",
                null,
                tint = contentColor,
                size = fixedSp(24 * compactScale),
                filled = false
            )
            // 287:8214 latest: the first text row overlaps the second by 2dp
            // (Figma's negative bottom margin), rather than using a positive gap.
            Column(verticalArrangement = Arrangement.spacedBy((-2 * compactScale).dp)) {
                Text(
                    count.toString(),
                    color = contentColor,
                    fontFamily = AppFonts.GoogleSansFlexExtraBold,
                    fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(16 * compactScale),
                    // Figma's wrapper is 16dp, but its paragraph uses the
                    // font's natural line metrics; leaving this unspecified
                    // preserves the visible glyphs instead of Compose-clipping
                    // the second line.
                    lineHeight = TextUnit.Unspecified,
                    letterSpacing = fixedSp(.6f * compactScale),
                    style = figmaCardTextStyle()
                )
                Text(
                    "cards",
                    color = contentColor,
                    fontFamily = AppFonts.GoogleSansFlexExtraBold,
                    fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(16 * compactScale),
                    lineHeight = TextUnit.Unspecified,
                    letterSpacing = fixedSp(.6f * compactScale),
                    style = figmaCardTextStyle()
                )
            }
        }
    }
}

private enum class RootTab { HOME, STUDY, DATA }

@Composable
private fun BottomNavBar(selected: RootTab, onHome: () -> Unit, onStudy: () -> Unit, onData: () -> Unit, modifier: Modifier = Modifier) {
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    val selectedIndex = when (selected) {
        RootTab.HOME -> 0
        RootTab.STUDY -> 1
        RootTab.DATA -> 2
    }
    Surface(
        color = if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFF4A545F) else Color(0xFF253644), shape = RoundedCornerShape((AppShapeRadius * designScale).dp),
        shadowElevation = 14.dp,
        // Figma 15:3716 / 15:3715: outer nav is 370×85 on the 402dp canvas.
        // Its 12dp padding leaves an exact 61dp-high selected range.
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(start = (16 * designScale).dp, end = (16 * designScale).dp, bottom = (16 * designScale).dp).height((85 * designScale).dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding((12 * designScale).dp)) {
            val itemGap = (32 * designScale).dp
            val itemWidth = (maxWidth - itemGap * 2) / 3
            val indicatorOffset by animateDpAsState(
                targetValue = (itemWidth + itemGap) * selectedIndex,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                label = "bottom navigation indicator"
            )

            // The Figma variants share a single light selection capsule. Keeping it as
            // one composable lets it travel between tabs instead of flashing in place.
            Surface(
                color = if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFFECF5FF) else Color(0xFF36546C),
                shape = RoundedCornerShape((24 * designScale).dp),
                modifier = Modifier.width(itemWidth).fillMaxSize().offset(x = indicatorOffset)
            ) {}
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(itemGap)) {
                BottomNavItem("主页", "home", selected == RootTab.HOME, onHome, Modifier.weight(1f), designScale)
                BottomNavItem("学习", "playing_cards", selected == RootTab.STUDY, onStudy, Modifier.weight(1f), designScale)
                BottomNavItem("数据", "query_stats", selected == RootTab.DATA, onData, Modifier.weight(1f), designScale)
            }
        }
    }
}

@Composable
private fun BottomNavItem(label: String, symbol: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, designScale: Float) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFF374B61) else Color(0xFFE5F1FF)
        } else {
            if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFFECECEC).copy(alpha = .75f) else Color(0xFFB8C7D6)
        },
        animationSpec = tween(durationMillis = 180),
        label = "$label navigation color"
    )
    Surface(
        onClick = onClick, color = Color.Transparent,
        contentColor = contentColor,
        shape = RoundedCornerShape((24 * designScale).dp), modifier = modifier.fillMaxSize()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                label = "$label navigation icon"
            ) { isSelected ->
                MaterialSymbol(symbol, label, tint = contentColor, size = fixedSp(25.263f * designScale), filled = isSelected)
            }
            Spacer(Modifier.height((4 * designScale).dp))
            AnimatedContent(
                targetState = selected,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                label = "$label navigation label"
            ) { isSelected ->
                Text(
                    label,
                    // Figma navigation labels: 520 when idle, Heavy 700 when selected.
                    fontFamily = if (isSelected) AppFonts.MiSansHeavy else AppFonts.MiSansNavigation,
                    fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(14 * designScale),
                    lineHeight = fixedSp(16 * designScale),
                    letterSpacing = fixedSp(.6f * designScale),
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun RoundIconButton(symbol: String, description: String, color: Color, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 52.dp, tint: Color, filled: Boolean = true) {
    Surface(onClick = onClick, shape = RoundedCornerShape(999.dp), color = color, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            MaterialSymbol(symbol, description, tint = tint, size = fixedSp(size.value * 25.263f / 52f), filled = filled)
        }
    }
}

/** Figma 15:3032: the shared main-screen settings control. */
@Composable
private fun SettingsHeaderButton(onClick: () -> Unit, size: androidx.compose.ui.unit.Dp) {
    RoundIconButton(
        symbol = "settings", description = "设置", color = HeaderControlBackgroundColor(),
        onClick = onClick, size = size, tint = HeaderControlIconColor(), filled = false
    )
}

@Composable
private fun ImageAvatar(size: androidx.compose.ui.unit.Dp = 56.dp) {
    // The avatar is deliberately informational only. Settings remains available from
    // the left control; tapping this photo must not navigate away from the main page.
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(999.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF99C9FF), Color(0xFF489FFF))
                )
            )
            .padding((4f / 56f * size.value).dp)
    ) {
        Image(
            painter = painterResource(R.drawable.avatar_profile_figma),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(999.dp))
        )
    }
}

@Composable
private fun MaterialSymbol(name: String, description: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: androidx.compose.ui.unit.TextUnit = 24.sp, filled: Boolean = true) {
    val accessibleModifier = if (description == null) modifier.clearAndSetSemantics { }
    else modifier.semantics { contentDescription = description }
    Text(
        text = name, modifier = accessibleModifier,
        // Material Symbols are Rounded + FILL on + Grade Emphasis by default.
        // The shared main-screen settings button is the sole FILL-off exception.
        color = tint,
        fontFamily = if (filled) AppFonts.MaterialSymbolsRounded else AppFonts.MaterialSymbolsRoundedOff,
        fontSize = size, lineHeight = size,
        style = TextStyle(fontFeatureSettings = "liga"),
        maxLines = 1
    )
}

/**
 * MiSans carries the Chinese copy while every non-Chinese run (Latin, numbers and
 * punctuation) uses the Figma Google Sans Flex face with ROND=100.
 */
@Composable
private fun MixedLanguageText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    chineseFont: FontFamily,
    latinFont: FontFamily,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    includeFontPadding: Boolean = true
) {
    val styled = remember(text, latinFont) {
        buildAnnotatedString {
            Regex("[\\u4E00-\\u9FFF]+|[^\\u4E00-\\u9FFF]+")
                .findAll(text)
                .forEach { match ->
                    val piece = match.value
                    if (piece.any { it in '\u4E00'..'\u9FFF' }) append(piece)
                    else withStyle(SpanStyle(fontFamily = latinFont, fontWeight = FontWeight.Normal)) { append(piece) }
                }
        }
    }
    Text(
        text = styled, modifier = modifier, color = color,
        fontFamily = chineseFont, fontWeight = FontWeight.Normal,
        fontSize = fontSize, lineHeight = lineHeight, textAlign = textAlign,
        style = if (includeFontPadding) TextStyle.Default else figmaCardTextStyle(),
        maxLines = maxLines, overflow = overflow
    )
}

@Composable
private fun LibraryScreen(decks: List<DeckSummary>, viewModel: AppViewModel, searchQuery: String, nav: ScreenNavigator) {
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    val sideInset = 16 * designScale
    var pendingDeletedDecks by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteFailed by remember { mutableStateOf(false) }
    val visibleDecks = decks.filter { it.id !in pendingDeletedDecks && it.name.contains(searchQuery, ignoreCase = true) }
    var deckPendingDeletion by remember { mutableStateOf<DeckSummary?>(null) }

    LaunchedEffect(deleteFailed) {
        if (deleteFailed) {
            delay(1_800)
            deleteFailed = false
        }
    }
    LaunchedEffect(decks) {
        pendingDeletedDecks = pendingDeletedDecks.intersect(decks.map { it.id }.toSet())
    }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(start = sideInset.dp, top = (88 * designScale).dp, end = sideInset.dp),
                verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
            ) {
                StudyAddDeckButton(designScale) { nav.navigate(AppRoute.Import) }
                Box(
                    // This viewport fills all space down to the navigation safe area. Only
                    // its own deck flow scrolls; the fixed header and add button do not.
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .clip(RoundedCornerShape(AppShapeRadius.dp))
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // The navigation bar overlays this clipped viewport. This tail lets
                        // the last card travel fully above the fade/navigation area.
                        contentPadding = PaddingValues(bottom = (180 * designScale).dp),
                        verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
                    ) {
                        items(visibleDecks, key = { it.id }) { deck ->
                            val progress by viewModel.deckProgress(deck.id).collectAsState(
                                initial = DeckProgress(deck.cardCount, deck.dueCount, masteredCards = 0, reviewCount = 0)
                            )
                            val visual = studyDeckVisual(deck, decks.indexOfFirst { it.id == deck.id })
                            StudyDeckCard(
                                deck = deck,
                                progress = progress,
                                visual = visual,
                                designScale = designScale,
                                onClick = { nav.navigate(AppRoute.Deck(deck.id)) },
                                onDelete = { deckPendingDeletion = deck }
                            )
                        }
                    }
                }
            }
            BottomContentFade(designScale, Modifier.align(Alignment.BottomCenter))
            DeleteFailureHint(
                visible = deleteFailed,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = (88 * designScale).dp)
            )
    }
    deckPendingDeletion?.let { deck ->
        AlertDialog(
            onDismissRequest = { deckPendingDeletion = null },
            title = { Text("删除卡片组", fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal) },
            text = { Text("“${displayDeckTitle(deck)}”及其中的卡片将从本机删除。", fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeletedDecks = pendingDeletedDecks + deck.id
                    viewModel.deleteDeck(deck.id, onFailure = {
                        pendingDeletedDecks = pendingDeletedDecks - deck.id
                        deleteFailed = true
                    })
                    deckPendingDeletion = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deckPendingDeletion = null }) { Text("取消") } }
        )
    }
}

private data class StudyDeckVisual(
    val background: Color,
    val iconBackground: Color,
    val icon: String,
    val iconTint: Color,
    val titleColor: Color,
    val panel: Color,
    val badgeText: Color,
    val progress: Color,
    val progressFill: Color,
    val progressLabel: Color,
    val progressTrack: Color,
    val action: Color,
)

/** Theme tokens travel with a deck, rather than being inferred from where a screen is opened. */
private data class DeckTheme(
    val key: String,
    val label: String,
    val primary: Color,
    val action: Color,
    val progress: Color,
    val progressFill: Color,
    val onPrimary: Color,
    val surface: Color,
    val cardPanel: Color,
    val secondary: Color,
    val strongText: Color,
    val text: Color,
    val mutedText: Color,
    val progressTrack: Color
)

private val DeckThemes = listOf(
    // Figma 257:6634: card panel is intentionally distinct from the secondary
    // button/container token documented in Figma 258:7544.
    DeckTheme("azure", "蓝色", Color(0xFF489FFF), Color(0xFF489FFF), Color(0xFF005AB8), Color(0xFF489FFF), Color(0xFFEFF6FF), Color(0xFFF0F8FF), Color(0xFFD0E7FF), Color(0xFFC3E1FF), Color(0xFF003C7A), Color(0xCC000000), Color(0xFF003C7A), Color(0xFFC3E1FF)),
    DeckTheme("violet", "紫色", Color(0xFF716FDD), Color(0xFF716FDD), Color(0xFF3836B7), Color(0xFF716FDD), Color(0xFFEFF6FF), Color(0xFFF3F3FF), Color(0xFFE4E4FF), Color(0xFFDDDDFF), Color(0xFF38387A), Color(0xCC000000), Color(0xFF38387A), Color(0xFFDDDDFF)),
    DeckTheme("mint", "绿色", Color(0xFF7AC583), Color(0xFF7AC583), Color(0xFF138120), Color(0xFF7AC583), Color(0xFFEFF6FF), Color(0xFFEAFBEB), Color(0xFFCDEFD1), Color(0xFFBFEEC4), Color(0xFF1F5225), Color(0xCC000000), Color(0xFF1F5225), Color(0xFFBFEEC4)),
    DeckTheme("coral", "粉色", Color(0xFFEF9BBE), Color(0xFFEF9BBE), Color(0xFFAA0047), Color(0xFFEF9BBE), Color(0xFFEFF6FF), Color(0xFFFFF5F9), Color(0xFFFFE2EE), Color(0xFFF7CEDF), Color(0xFF4E1B30), Color(0xCC000000), Color(0xFF4E1B30), Color(0xFFF7CEDF)),
    DeckTheme("amber", "黄色", Color(0xFFE1BA5E), Color(0xFFE1BA5E), Color(0xFF906500), Color(0xFFE1BA5E), Color(0xFFEFF6FF), Color(0xFFFFFAEF), Color(0xFFFBEED2), Color(0xFFF8E9C5), Color(0xFF51411B), Color(0xCC000000), Color(0xFF51411B), Color(0xFFF8E9C5))
)

private fun deckTheme(deck: DeckSummary): DeckTheme = DeckThemes.firstOrNull { it.key == deck.themeKey } ?: DeckThemes.first()

@Composable
private fun studyDeckVisual(deck: DeckSummary, @Suppress("UNUSED_PARAMETER") index: Int): StudyDeckVisual {
    val theme = deckTheme(deck)
    return StudyDeckVisual(
        background = theme.surface,
        iconBackground = theme.primary,
        icon = studyDeckIcon(deck),
        iconTint = theme.onPrimary,
        titleColor = theme.text,
        // Figma 257:6634: card panels use their own token, while detail-page
        // secondary buttons continue to use Figma 258:7544's secondary token.
        panel = theme.cardPanel,
        badgeText = theme.strongText,
        progress = theme.progress,
        progressFill = theme.progressFill,
        progressLabel = theme.strongText,
        // The progress component's unfilled track uses the deck surface token.
        // For violet this is Figma 248:6231's exact #F3F3FF.
        progressTrack = theme.surface,
        action = theme.action
    )
}

/** A small, semantic icon vocabulary keeps bundled and imported decks visually coherent. */
private fun studyDeckIcon(deck: DeckSummary): String = when (deck.chapter) {
    1 -> "smart_toy"
    2 -> "account_tree"
    3 -> "memory"
    4 -> "extension"
    5 -> "code"
    6 -> "fact_check"
    7 -> "school"
    8 -> "update"
    9 -> "image"
    10 -> "groups"
    else -> "note_stack"
}

private fun studyDeckKeywords(deck: DeckSummary): String = when (deck.chapter) {
    1 -> "模型 · 上下文 · 工具"
    2 -> "筛选 · 压缩 · 长任务"
    3 -> "记忆 · 知识库 · RAG"
    4 -> "工具调用 · 协议 · MCP"
    5 -> "代码生成 · 测试 · 验证"
    6 -> "基准 · 指标 · 反馈"
    7 -> "SFT · RL · 工具轨迹"
    8 -> "迭代 · 评估 · 回滚"
    9 -> "语音 · 视觉 · 实时"
    10 -> "协作 · 分工 · 交接"
    else -> "自定义 · 问答卡片"
}

/** The short chapter name is real deck metadata, shared by the list and detail title. */
private fun displayDeckTitle(deck: DeckSummary): String = if (deck.source != "builtin" || !deck.name.startsWith("第 ")) {
    deck.name
} else when (deck.chapter) {
    1 -> "Agent 基础"
    2 -> "上下文工程"
    3 -> "记忆与知识库"
    4 -> "工具与 MCP"
    5 -> "Coding Agent"
    6 -> "Agent 评估"
    7 -> "模型后训练"
    8 -> "持续进化"
    9 -> "多模态与实时"
    10 -> "多 Agent 协作"
    else -> deck.name
}

@Composable
private fun StudySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier,
    designScale: Float = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
) {
    Surface(
        color = HeaderControlBackgroundColor(), shape = RoundedCornerShape((24 * designScale).dp),
        modifier = modifier.height((56 * designScale).dp)
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = PageForegroundColor(), fontFamily = AppFonts.MiSansTopInformation,
                fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * designScale), textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxSize().padding(horizontal = (16 * designScale).dp),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (query.isEmpty()) {
                        Text("搜索", color = Color(0xFF8C97A3), fontFamily = AppFonts.MiSansTopInformation, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * designScale), letterSpacing = fixedSp(-.2f * designScale))
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun StudyAddDeckButton(designScale: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = Color(0xFF489FFF), contentColor = Color(0xFFEBF5FF),
        shape = RoundedCornerShape((24 * designScale).dp),
        modifier = Modifier.width((163 * designScale).dp).height((60 * designScale).dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = (24 * designScale).dp),
            horizontalArrangement = Arrangement.spacedBy((8 * designScale).dp), verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbol("note_stack_add", "添加卡片组", tint = LocalContentColor.current, size = fixedSp(24 * designScale), filled = true)
            Text("添加卡片组", fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), letterSpacing = fixedSp(.6f * designScale))
        }
    }
}

@Composable
private fun StudyDeckCard(deck: DeckSummary, progress: DeckProgress, visual: StudyDeckVisual, designScale: Float, onClick: () -> Unit, onDelete: () -> Unit) {
    val masteryRatio = if (progress.cardCount == 0) 0f else progress.masteredCards.toFloat() / progress.cardCount
    val progressPercent = (masteryRatio * 100).roundToInt()
    val deleteWidth = (112 * designScale).dp
    // Figma 143:3526 defines a shared 16dp overlap between the swiped card and
    // its action panel. Keeping the action anchored in the clipped viewport
    // makes the same geometry work throughout the swipe.
    val deleteOverlap = (16 * designScale).dp
    val revealWidth = deleteWidth - deleteOverlap
    val revealWidthPx = with(LocalDensity.current) { revealWidth.toPx() }
    var dragOffset by remember(deck.id) { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(dragOffset, label = "${deck.id} delete swipe")
    val dragState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceIn(-revealWidthPx, 0f)
    }
    val containerShape = RoundedCornerShape(AppShapeRadius.dp)
    Box(
        // The updated 287:8214 text stack is allowed its full line box; the
        // no-action card keeps the resulting 206dp total height.
        modifier = Modifier.fillMaxWidth().height((206 * designScale).dp).clip(containerShape)
    ) {
        Surface(
            onClick = onDelete,
            shape = containerShape,
            color = Color(0xFFBD3F3F),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(deleteWidth)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(start = (24 * designScale).dp, end = (16 * designScale).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MaterialSymbol("delete", "删除卡组", tint = Color(0xFFFFEDED), size = fixedSp(24 * designScale), filled = true)
                Spacer(Modifier.height((4 * designScale).dp))
                Text("删除卡组", color = Color(0xFFFFEDED), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale))
            }
        }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(AppShapeRadius.dp),
        colors = CardDefaults.cardColors(containerColor = visual.background),
        modifier = Modifier.fillMaxWidth().height((206 * designScale).dp)
            .offset { IntOffset(animatedOffset.roundToInt(), 0) }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { dragOffset = if (dragOffset < -revealWidthPx / 2f) -revealWidthPx else 0f }
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((12 * designScale).dp), verticalAlignment = Alignment.Top) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy((8 * designScale).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape((16 * designScale).dp), color = visual.iconBackground, modifier = Modifier.size((56 * designScale).dp)) {
                        Box(contentAlignment = Alignment.Center) { MaterialSymbol(visual.icon, null, tint = visual.iconTint, size = fixedSp(24 * designScale), filled = true) }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4 * designScale).dp)) {
                        MixedLanguageText(displayDeckTitle(deck), modifier = Modifier.fillMaxWidth(), color = visual.titleColor, chineseFont = AppFonts.MiSansCardBold, latinFont = AppFonts.GoogleSansFlexBold, fontSize = fixedSp(20 * designScale), lineHeight = fixedSp(24 * designScale), maxLines = 1, overflow = TextOverflow.Ellipsis, includeFontPadding = false)
                        Row(horizontalArrangement = Arrangement.spacedBy((4 * designScale).dp), verticalAlignment = Alignment.CenterVertically) {
                            MaterialSymbol("brightness_alert", null, tint = Color(0xFFD23535), size = fixedSp(18 * designScale), filled = true)
                            Text("高优先级", color = Color(0xFFD23535), fontFamily = AppFonts.MiSansCardSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale), style = figmaCardTextStyle())
                        }
                    }
                }
                ReviewCountBadge(
                    count = deck.cardCount,
                    background = visual.panel,
                    contentColor = visual.badgeText,
                    compactScale = designScale
                )
            }
            Surface(shape = RoundedCornerShape((20 * designScale).dp), color = visual.panel, modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding((12 * designScale).dp),
                    verticalArrangement = Arrangement.spacedBy((8 * designScale).dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("进度", color = visual.progressLabel, fontFamily = AppFonts.MiSansCardSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale), style = figmaCardTextStyle())
                        Text("${progressPercent}%", color = visual.progress, fontFamily = AppFonts.GoogleSansFlexBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(24 * designScale), lineHeight = fixedSp(28 * designScale), style = figmaCardTextStyle())
                    }
                    Box(Modifier.fillMaxWidth().height((20 * designScale).dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = .5f))) {
                        if (masteryRatio > 0f) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(masteryRatio).background(visual.progressFill))
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun DataScreen(dueCount: Int, dashboard: Dashboard?, weeklyActivity: WeeklyActivityData, nav: ScreenNavigator) {
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
                        item { WeeklyActivityCard(designScale, weeklyActivity) }
                        item { MasteryCard(designScale, dashboard) }
                        item { DataBentoCards(designScale, dashboard) }
                    }
                }
            }
            BottomContentFade(designScale, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun DataHeader(modifier: Modifier, designScale: Float, onSettings: () -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        SettingsHeaderButton(onSettings, (56 * designScale).dp)
        ImageAvatar((56 * designScale).dp)
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
        shape = RoundedCornerShape(AppShapeRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFFF0F8FF) else Color(0xFF233D55)
        ),
        modifier = Modifier.fillMaxWidth().height((273 * designScale).dp)
    ) {
        Column(
            // The header needs 50dp for MiSans' real vertical metrics. Keep the outer
            // card at the Figma height by reclaiming the otherwise invisible 1dp bottom
            // inset; this prevents the subtitle's descenders from being clipped.
            modifier = Modifier.fillMaxSize().padding(
                start = (24 * designScale).dp,
                top = (24 * designScale).dp,
                end = (24 * designScale).dp,
                bottom = (23 * designScale).dp
            ),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
        ) {
            Row(Modifier.fillMaxWidth().height((50 * designScale).dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    // Keep the two-line header at Figma's 48dp total height, but give the
                    // variable-font title two extra dp so its ascenders are never clipped.
                    Text("每周活动", color = PageForegroundColor(), fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(24 * designScale), lineHeight = fixedSp(28 * designScale))
                    MixedLanguageText("已复习${weeklyActivity.total} cards", color = MaterialTheme.colorScheme.onSurfaceVariant, chineseFont = AppFonts.MiSansSemibold, latinFont = AppFonts.GoogleSansFlexSemibold, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale))
                }
                WeeklyChangeIndicator(changePercent = weeklyActivity.changePercent, designScale = designScale)
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

/** Figma variants 19:1146 / 19:1149 — positive and negative weekly deltas. */
@Composable
private fun WeeklyChangeIndicator(changePercent: Int?, designScale: Float) {
    val improving = changePercent == null || changePercent >= 0
    val hasComparison = changePercent != null
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = when {
            !hasComparison -> if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFFD5E0EA) else Color(0xFF496277)
            improving -> Color(0xFF3FAE4A)
            else -> Color(0xFFBD3F3F)
        },
        modifier = Modifier.height((48 * designScale).dp)
    ) {
        Box(Modifier.padding(horizontal = (12 * designScale).dp), contentAlignment = Alignment.Center) {
            if (hasComparison) {
                Text(
                    text = if (improving) "+${changePercent}%" else "${changePercent}%",
                    color = Color(0xFFEAFFEC), fontFamily = AppFonts.GoogleSansFlexBold,
                    fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * designScale),
                    lineHeight = fixedSp(16 * designScale), letterSpacing = fixedSp(.6f * designScale)
                )
            } else {
                Text(
                    text = "暂无对比", color = if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFF3F5368) else Color(0xFFE7F1FA),
                    fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(16 * designScale)
                )
            }
        }
    }
}

@Composable
private fun WeeklyActivityBar(label: String, filledHeight: Float, designScale: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((8 * designScale).dp)) {
        Column(
            modifier = Modifier.width((32 * designScale).dp).height((120 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((4 * designScale).dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFFD0E7FF) else Color(0xFF29465E)))
            Box(Modifier.fillMaxWidth().height((filledHeight * designScale).dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFF489FFF)))
        }
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = AppFonts.GoogleSansFlexExtraBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(16 * designScale), letterSpacing = fixedSp(.6f * designScale))
    }
}

@Composable
private fun MasteryCard(designScale: Float, dashboard: Dashboard?) {
    Card(
        shape = RoundedCornerShape(AppShapeRadius.dp),
        // Figma 19:621: the weekly-goal card now shares the soft blue data surface.
        // surfaceVariant maps to the matching low-luminance surface in dark mode.
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
    val ringTrack = if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFFEAF1F7) else Color(0xFF29465E)
    Box(Modifier.size((192 * designScale).dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension / 8f
            val inset = stroke / 2f
            val bounds = androidx.compose.ui.geometry.Rect(inset, inset, size.width - inset, size.height - inset)
            // A conventional Health-style goal ring: the blue arc always represents the
            // number in the center, beginning at 12 o'clock and ending with round caps.
            drawArc(ringTrack, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = bounds.topLeft, size = bounds.size, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color(0xFF489FFF), startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, topLeft = bounds.topLeft, size = bounds.size, style = Stroke(stroke, cap = StrokeCap.Round))
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
            Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFF489FFF), modifier = Modifier.size((24 * designScale).dp)) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol(symbol, null, tint = Color.White, size = fixedSp(16 * designScale), filled = true)
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
    val dark = MaterialTheme.colorScheme.background.luminance() <= .5f
    val masteredCount = dashboard?.raw?.let { raw -> raw.optInt("mastered_card_count", raw.optInt("mastered_cards", 0)) } ?: 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
        DataBentoCard(
            modifier = Modifier.weight(1f),
            background = if (dark) Color(0xFF392725) else Color(0xFFFFF4F3),
            iconBackground = if (dark) Color(0xFF603A35) else Color(0xFFFF998E),
            icon = "local_fire_department",
            iconTint = if (dark) Color(0xFFFFE7E3) else Color(0xFF650800),
            value = (dashboard?.raw?.optInt("streak_days", 0) ?: 0).toString(),
            label = "连胜！",
            valueColor = if (dark) Color(0xFFFFE7E3) else Color(0xFF352826),
            labelColor = if (dark) Color(0xFFFFB3AA) else Color(0xFFFF4430),
            designScale = designScale
        )
        DataBentoCard(
            modifier = Modifier.weight(1f),
            background = if (dark) Color(0xFF20372A) else Color(0xFFEAFBEB),
            iconBackground = if (dark) Color(0xFF2A6940) else Color(0xFF7AC583),
            icon = "editor_choice",
            iconTint = if (dark) Color(0xFFE2F7E6) else Color(0xFF004904),
            value = formatMasteredCount(masteredCount),
            label = "已掌握卡片",
            valueColor = if (dark) Color(0xFFE2F7E6) else Color(0xCC000000),
            labelColor = if (dark) Color(0xFFB4E7BB) else Color(0xFF138120),
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
                Text(label, color = labelColor, fontFamily = AppFonts.MiSansCardBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = TextUnit.Unspecified, letterSpacing = fixedSp(.6f * designScale), style = figmaCardTextStyle())
            }
        }
    }
}

private enum class CardListMode { GENERATED, EDIT }

@Composable
private fun CardListScreen(
    deckId: String,
    viewModel: AppViewModel,
    nav: ScreenNavigator,
    mode: CardListMode = CardListMode.GENERATED
) {
    LaunchedEffect(deckId) { viewModel.refreshCards(deckId) }
    val cards by viewModel.cards(deckId).collectAsState(initial = emptyList())
    val decks by viewModel.decks.collectAsState()
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
    val theme = displayDeck?.let(::deckTheme) ?: DeckThemes.first()
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    // Figma 118:2389: cards begin 194dp from the design canvas top.
                    .padding(start = (16 * designScale).dp, top = (194 * designScale).dp, end = (16 * designScale).dp)
                    .height((693 * designScale).dp)
                    .clip(RoundedCornerShape((AppShapeRadius * designScale).dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (144 * designScale).dp),
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
                "点击卡片查看答案，此次不会进入学习记录。\n卡片左滑可进行单卡编辑与删除。",
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
                        label = "名称与主题色",
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
            onDismiss = { editingDeckPresentation = false },
            onSave = { name, themeKey ->
                val updated = displayDeck.copy(name = name, themeKey = themeKey)
                optimisticDeck = updated
                viewModel.updateDeckPresentation(displayDeck.id, name, themeKey) {
                    optimisticDeck = null
                }
                editingDeckPresentation = false
            }
        )
    }
}

@Composable
private fun CardListActionButton(label: String, icon: String, primary: Boolean, modifier: Modifier, designScale: Float = 1f, theme: DeckTheme? = null, onClick: () -> Unit) {
    val primaryColor = theme?.primary ?: Color(0xFF489FFF)
    val primaryContent = theme?.onPrimary ?: Color(0xFFEFF6FF)
    val secondaryColor = theme?.secondary ?: Color(0xFFEBF4FF)
    val secondaryContent = theme?.strongText ?: Color(0xFF001631)
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
    val shape = RoundedCornerShape((32 * designScale).dp)
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
        animationSpec = tween(420, easing = LinearOutSlowInEasing),
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
                color = Color(0xFFBD3F3F),
                contentColor = Color(0xFFFFEDED),
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
        shape = RoundedCornerShape((32 * designScale).dp),
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
            Text(label, color = contentColor, fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale), maxLines = 1)
        }
    }
}

/** Figma 118:2389 cycles the card-type pill independently from the deck theme. */
private data class CardListTagStyle(val label: String, val container: Color, val content: Color)

private val CardListTagStyles = listOf(
    CardListTagStyle("基础记忆", Color(0xFF84BFFF), Color(0xFF00254F)),
    CardListTagStyle("理解分析", Color(0xFF7DCC85), Color(0xFF07550F)),
    CardListTagStyle("综合应用", Color(0xFFE87F77), Color(0xFF591B16))
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
private fun CardEditDialog(card: FlashcardEntity, onSave: (FlashcardEntity) -> Unit, onDismiss: () -> Unit) {
    var front by remember(card.id) { mutableStateOf(card.front) }
    var back by remember(card.id) { mutableStateOf(card.back) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑卡片", fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(front, { front = it }, label = { Text("问题") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(back, { back = it }, label = { Text("答案") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
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
    onDismiss: () -> Unit,
    onSave: (name: String, themeKey: String) -> Unit
) {
    var name by remember(deck.id, deck.name) { mutableStateOf(displayDeckTitle(deck)) }
    var selectedThemeKey by remember(deck.id, deck.themeKey) { mutableStateOf(deckTheme(deck).key) }
    val selectedTheme = DeckThemes.first { it.key == selectedThemeKey }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "名称与主题色",
                color = selectedTheme.strongText,
                fontFamily = AppFonts.MiSansSemibold,
                fontWeight = FontWeight.Normal
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("卡片组名称") },
                    singleLine = true
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "主色调 · ${selectedTheme.label}",
                        color = selectedTheme.text,
                        fontFamily = AppFonts.MiSansSemibold,
                        fontWeight = FontWeight.Normal,
                        fontSize = fixedSp(16f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DeckThemes.forEach { option ->
                            val selected = option.key == selectedThemeKey
                            Surface(
                                onClick = { selectedThemeKey = option.key },
                                color = option.primary,
                                contentColor = option.onPrimary,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                                    .semantics { contentDescription = "${option.label}主题色" }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (selected) {
                                        MaterialSymbol("check", "已选择${option.label}", tint = option.onPrimary, size = fixedSp(24f), filled = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), selectedThemeKey) }) {
                Text("保存", color = selectedTheme.primary, fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun DeckScreen(deck: DeckSummary, viewModel: AppViewModel, nav: ScreenNavigator) {
    val progress by viewModel.deckProgress(deck.id).collectAsState(
        initial = DeckProgress(deck.cardCount, deck.dueCount, masteredCards = 0, reviewCount = 0)
    )
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    val theme = deckTheme(deck)
    val masteryRatio = if (progress.cardCount == 0) 0f else progress.masteredCards.toFloat() / progress.cardCount
    var deleteConfirmationVisible by remember { mutableStateOf(false) }
    var deleteFailed by remember { mutableStateOf(false) }
    LaunchedEffect(deleteFailed) {
        if (deleteFailed) {
            delay(1_800)
            deleteFailed = false
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            // Figma 41:1623: this is the only scrollable region. It is clipped so
            // a long overview or future metrics never travel into the fixed header.
            Box(
                Modifier.fillMaxSize()
                    .padding(start = (16 * designScale).dp, top = (148 * designScale).dp, end = (16 * designScale).dp)
                    .clip(RoundedCornerShape((AppShapeRadius * designScale).dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (188 * designScale).dp),
                    verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
                ) {
                    item { DeckOverviewCard(deckOverview(deck), designScale, theme) }
                    item { ChapterProgressCard(progress, masteryRatio, designScale, theme) }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy((8 * designScale).dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { nav.navigate(AppRoute.EditCardList(deck.id)) },
                                modifier = Modifier.height((60 * designScale).dp),
                                shape = RoundedCornerShape((24 * designScale).dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = theme.secondary,
                                    contentColor = theme.strongText
                                ),
                                contentPadding = PaddingValues(horizontal = (24 * designScale).dp)
                            ) {
                                MaterialSymbol("edit", "编辑卡片", tint = LocalContentColor.current, size = fixedSp(24 * designScale), filled = true)
                                Spacer(Modifier.width((8 * designScale).dp))
                                Text("编辑", fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(16 * designScale), letterSpacing = fixedSp(.6f * designScale))
                            }
                            Surface(
                                onClick = { deleteConfirmationVisible = true },
                                shape = RoundedCornerShape((24 * designScale).dp),
                                color = Color(0xFFBD3F3F),
                                modifier = Modifier.size((60 * designScale).dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    MaterialSymbol("delete", "删除牌组", tint = Color(0xFFFFEDED), size = fixedSp(24 * designScale), filled = true)
                                }
                            }
                        }
                    }
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
                CardListActionButton("自由刷题", "style", false, Modifier.weight(1f), designScale, theme) { nav.navigate(AppRoute.Study(deck.id, false)) }
                CardListActionButton("开始复习", "play_circle", true, Modifier.weight(1f), designScale, theme) { nav.navigate(AppRoute.Study(deck.id, true)) }
            }
            DeleteFailureHint(
                visible = deleteFailed,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = (112 * designScale).dp)
            )
        }
    }
    if (deleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { deleteConfirmationVisible = false },
            title = { Text("删除这个牌组？", fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal) },
            text = { Text("牌组中的所有卡片和学习记录都会被删除，且无法恢复。", fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal) },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmationVisible = false
                    viewModel.deleteDeck(
                        deck.id,
                        onSuccess = nav::popBackStack,
                        onFailure = { deleteFailed = true }
                    )
                }) { Text("删除", color = Color(0xFFBD3F3F)) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmationVisible = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun AiRewriteDialog(onDismiss: () -> Unit) {
    var state by remember { mutableStateOf("确认") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state == "成功") "已生成新版本" else "AI 重新生成", fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal) },
        text = {
            when (state) {
                "成功" -> Text("示例卡已改写为更清晰的问答形式。原卡没有被修改。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal)
                "失败" -> Text("重新生成失败，原卡没有变化。", color = MaterialTheme.colorScheme.error, fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal)
                else -> Text("这会模拟生成一张新的卡片版本，不会调用 AI 服务。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal)
            }
        },
        confirmButton = {
            if (state == "确认") TextButton(onClick = { state = "成功" }) { Text("开始生成") }
            else TextButton(onClick = onDismiss) { Text("完成") }
        },
        dismissButton = {
            if (state == "确认") TextButton(onClick = { state = "失败" }) { Text("演示失败") }
            else null
        }
    )
}

@Composable
private fun DeckDetailHeader(title: String, designScale: Float, onBack: () -> Unit, theme: DeckTheme? = null, modifier: Modifier = Modifier) {
    // Theme decks use their card surface for the back control, per Figma 41:1623;
    // neutral pages use the default secondary-header treatment from 209:2733.
    ScreenTopInformationBar(
        title = title,
        subtitle = null,
        onBack = onBack,
        backContainer = theme?.surface,
        titleColor = theme?.text,
        modifier = modifier
    )
}

/** The fixed primary/secondary action style shared by Figma nodes 41:1623 and 48:4562. */
@Composable
private fun DetailPrimaryButton(
    text: String,
    icon: String,
    primary: Boolean,
    designScale: Float,
    onClick: () -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() <= .5f
    val container = if (primary) Color(0xFF489FFF) else if (dark) Color(0xFF203A52) else Color(0xFFEBF4FF)
    val content = if (primary) Color(0xFFEBF5FF) else if (dark) Color(0xFFD7EBFF) else Color(0xFF001631)
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height((60 * designScale).dp),
        shape = RoundedCornerShape((24 * designScale).dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = (24 * designScale).dp)
    ) {
        MaterialSymbol(icon, null, tint = content, size = fixedSp(24 * designScale), filled = true)
        Spacer(Modifier.width((8 * designScale).dp))
        Text(
            text,
            fontFamily = AppFonts.MiSansBold,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(16 * designScale),
            lineHeight = fixedSp(16 * designScale),
            letterSpacing = fixedSp(.6f * designScale)
        )
    }
}

@Composable
private fun DeckOverviewCard(summary: String, designScale: Float, theme: DeckTheme) {
    Card(
        shape = RoundedCornerShape(AppShapeRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = theme.surface
        ),
        modifier = Modifier.fillMaxWidth().heightIn(
            min = (102 * designScale).dp,
            max = (156 * designScale).dp
        )
    ) {
        Text(
            text = summary,
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            color = theme.text,
            // Figma specifies MiSans VF Medium (500) for the chapter synopsis.
            fontFamily = AppFonts.MiSansMedium,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(20 * designScale),
            lineHeight = fixedSp(27 * designScale),
            minLines = 2,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
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
        // Figma 41:1623 includes metrics, three question-type chips and the
        // review total in this single 304dp card.
        modifier = Modifier.fillMaxWidth().height((304 * designScale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("本章进度", color = theme.text, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * designScale), lineHeight = fixedSp(27 * designScale))
                Text("${(masteryRatio * 100).roundToInt()}%已掌握", color = theme.strongText, fontFamily = AppFonts.GoogleSansFlexBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * designScale), lineHeight = fixedSp(27 * designScale))
            }
            Box(
                modifier = Modifier.fillMaxWidth().height((20 * designScale).dp)
                    .clip(RoundedCornerShape(999.dp)).background(theme.progressTrack)
            ) {
                if (masteryRatio > 0f) {
                    Box(Modifier.fillMaxWidth(masteryRatio.coerceIn(0f, 1f)).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(theme.primary))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ChapterMetric("共", "${progress.cardCount}张", designScale, theme = theme)
                ChapterMetric("已掌握", "${progress.masteredCards}张", designScale, TextAlign.Center, theme)
                ChapterMetric("待复习", "${progress.dueCount}张", designScale, TextAlign.End, theme)
            }
            Row(
                modifier = Modifier.fillMaxWidth().height((76 * designScale).dp),
                horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp)
            ) {
                ChapterQuestionTypeStat(
                    count = foundationCards,
                    label = "基础记忆",
                    container = Color(0xFF84BFFF),
                    content = Color(0xFF183A5E),
                    designScale = designScale,
                    modifier = Modifier.weight(1f)
                )
                ChapterQuestionTypeStat(
                    count = understandingCards,
                    label = "理解分析",
                    container = Color(0xFF7DCC85),
                    content = Color(0xFF05460C),
                    designScale = designScale,
                    modifier = Modifier.weight(1f)
                )
                ChapterQuestionTypeStat(
                    count = applicationCards,
                    label = "综合应用",
                    container = Color(0xFFE87F77),
                    content = Color(0xFF591B16),
                    designScale = designScale,
                    modifier = Modifier.weight(1f)
                )
            }
            Text("累计复习${progress.reviewCount}次", color = theme.mutedText, fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale))
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
            Text(
                "$count cards",
                color = content,
                fontFamily = AppFonts.GoogleSansFlexBold,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(16 * designScale),
                lineHeight = fixedSp(20 * designScale),
                maxLines = 1
            )
            Text(
                label,
                color = content,
                fontFamily = AppFonts.MiSansBold,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(16 * designScale),
                lineHeight = fixedSp(20 * designScale),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChapterMetric(label: String, value: String, designScale: Float, alignment: TextAlign = TextAlign.Start, theme: DeckTheme) {
    Column(horizontalAlignment = when (alignment) { TextAlign.End -> Alignment.End; TextAlign.Center -> Alignment.CenterHorizontally; else -> Alignment.Start }) {
        Text(label, color = theme.text, fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale), textAlign = alignment)
        Text(value.replace(" ", ""), color = theme.text, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * designScale), lineHeight = fixedSp(24 * designScale), textAlign = alignment)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudyScreen(viewModel: AppViewModel, nav: ScreenNavigator, deckId: String, reviewMode: Boolean) {
    val cards by viewModel.studyCards.collectAsState()
    // Keep a local queue for this session. A card disappears from it immediately
    // after it is rated, so the previous/next controls can never reopen a card
    // that has already been swiped away.
    var remainingCardIds by remember(deckId, reviewMode) { mutableStateOf<List<String>?>(null) }
    var currentIndex by remember(deckId, reviewMode) { mutableIntStateOf(0) }
    var rememberedCount by remember(deckId, reviewMode) { mutableIntStateOf(0) }
    var forgottenCount by remember(deckId, reviewMode) { mutableIntStateOf(0) }
    LaunchedEffect(deckId, reviewMode) { viewModel.startStudy(deckId, reviewMode) }

    LaunchedEffect(cards) {
        if (remainingCardIds == null && cards.isNotEmpty()) {
            remainingCardIds = cards.map { it.id }
        }
    }

    val initialCardIds = remainingCardIds
    val cardsById = cards.associateBy { it.id }
    val remainingCards = initialCardIds.orEmpty().mapNotNull(cardsById::get)
    if (reviewMode && remainingCards.isNotEmpty()) {
        val safeIndex = currentIndex.coerceIn(0, remainingCards.lastIndex)
        val card = remainingCards[safeIndex]
        ReviewStudy(
            card = card,
            position = initialCardIds.orEmpty().indexOf(card.id) + 1,
            total = initialCardIds.orEmpty().size,
            canGoPrevious = safeIndex > 0,
            canGoNext = safeIndex < remainingCards.lastIndex,
            rememberedCount = rememberedCount,
            forgottenCount = forgottenCount,
            modifier = Modifier.fillMaxSize(),
            onBack = nav::popBackStack,
            onPrevious = { currentIndex = (safeIndex - 1).coerceAtLeast(0) },
            onNext = { currentIndex = (safeIndex + 1).coerceAtMost(remainingCards.lastIndex) },
            onRate = { rating ->
                viewModel.rate(card.id, rating)
                if (rating == Rating.GOOD) rememberedCount++ else forgottenCount++
                val updatedIds = initialCardIds.orEmpty().filterNot { it == card.id }
                remainingCardIds = updatedIds
                currentIndex = safeIndex.coerceAtMost((updatedIds.size - 1).coerceAtLeast(0))
            }
        )
        return
    }
    if (!reviewMode && cards.isNotEmpty()) {
        FreeStudy(cards = cards, onBack = nav::popBackStack, onUpdateCard = viewModel::updateCard)
        return
    }
    Scaffold(topBar = { AppBar(if (reviewMode) "间隔复习" else "自由刷题", nav::popBackStack) }) { padding ->
        when {
            cards.isEmpty() -> EmptyStudy(Modifier.padding(padding), reviewMode, nav)
            reviewMode && remainingCardIds?.isEmpty() == true -> CompleteStudy(Modifier.padding(padding), nav)
            else -> Unit
        }
    }
}

@Composable
private fun EmptyStudy(modifier: Modifier, reviewMode: Boolean, nav: ScreenNavigator) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MaterialSymbol("star", null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary, size = 44.sp)
            Text(if (reviewMode) "没有到期卡片" else "这个卡组还是空的", style = MaterialTheme.typography.headlineSmall, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal)
            Text(if (reviewMode) "休息一下，或者自由刷题巩固印象。" else "先导入几张问答卡吧。", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = nav::popBackStack) { Text("返回") }
        }
    }
}

@Composable
private fun CompleteStudy(modifier: Modifier, nav: ScreenNavigator) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MaterialSymbol("star", null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary, size = 52.sp)
            Text("本轮完成", style = MaterialTheme.typography.headlineSmall, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal)
            Text("做得好，下一次复习会按你的记忆情况自动安排。", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = nav::popBackStack) { Text("回到卡组") }
        }
    }
}

@Composable
private fun ReviewStudy(
    card: FlashcardEntity,
    position: Int,
    total: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    rememberedCount: Int,
    forgottenCount: Int,
    modifier: Modifier,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRate: (Rating) -> Unit
) {
    var flipped by remember(card.id) { mutableStateOf(false) }
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenTopInformationBar(
            title = "间隔复习", subtitle = "$position/$total", onBack = onBack,
            modifier = Modifier.zIndex(1f)
        )
        LinearProgressIndicator(
            progress = { position.toFloat() / total },
            color = Color(0xFF489FFF), trackColor = Color(0xFFD1E8FF),
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = (16 * designScale).dp)
                .padding(top = (88 * designScale).dp).height((4 * designScale).dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = (16 * designScale).dp)
                .padding(top = (132 * designScale).dp).height((600 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FigmaReviewCard(
                card = card,
                flipped = flipped,
                onFlip = { flipped = !flipped },
                onRate = onRate,
                modifier = Modifier.fillMaxWidth().weight(1f),
                designScale = designScale
            )
            if (flipped) ReviewAnswerControls(canGoPrevious, canGoNext, onPrevious, onNext) { onRate(Rating.HARD) }
            else ReviewQuestionControls(canGoPrevious, canGoNext, rememberedCount, forgottenCount, onPrevious, onNext)
        }
        if (flipped) ReviewSwipeHint(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = (756 * designScale).dp), designScale) else Text(
            "点击卡片查看答案",
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = (769 * designScale).dp),
            color = PageForegroundColor(), fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal,
            fontSize = fixedSp(20 * designScale), lineHeight = fixedSp(28 * designScale), textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReviewQuestionControls(
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    rememberedCount: Int,
    forgottenCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Row(Modifier.fillMaxWidth().height((60 * scale).dp), horizontalArrangement = Arrangement.spacedBy((15 * scale).dp)) {
        ReviewNavigationButton("arrow_back", canGoPrevious, Modifier.weight(1f), scale, onPrevious)
        ReviewCountBadge("check", rememberedCount, Color(0xFFDBFCDE), Color(0xFF0C8617), Modifier.weight(1f), scale)
        ReviewCountBadge("close", forgottenCount, Color(0xFFFFF4F3), Color(0xFFBD3F3F), Modifier.weight(1f), scale)
        ReviewNavigationButton("arrow_forward", canGoNext, Modifier.weight(1f), scale, onNext)
    }
}

@Composable
private fun ReviewAnswerControls(canGoPrevious: Boolean, canGoNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit, onHard: () -> Unit) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Row(Modifier.fillMaxWidth().height((60 * scale).dp), horizontalArrangement = Arrangement.spacedBy((15 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        ReviewNavigationButton("arrow_back", enabled = canGoPrevious, Modifier.weight(1f), scale, onPrevious)
        Surface(
            onClick = onHard,
            color = Color(0xFFF0F8FF),
            contentColor = Color(0xFF002D5F),
            border = androidx.compose.foundation.BorderStroke((2 * scale).dp, Color(0xFF002D5F)),
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.height((59 * scale).dp)
        ) {
            Row(Modifier.padding(horizontal = (24 * scale).dp), horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbol("comedy_mask", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Text("印象模糊，明天再刷", fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), lineHeight = fixedSp(16 * scale), letterSpacing = fixedSp(.6f * scale))
            }
        }
        ReviewNavigationButton("arrow_forward", enabled = canGoNext, Modifier.weight(1f), scale, onNext)
    }
}

@Composable
private fun ReviewNavigationButton(symbol: String, enabled: Boolean, modifier: Modifier, scale: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color(0xFF489FFF),
        contentColor = Color.White,
        shape = RoundedCornerShape((24 * scale).dp),
        modifier = modifier.fillMaxHeight()
    ) {
        Box(contentAlignment = Alignment.Center) {
            MaterialSymbol(symbol, if (symbol == "arrow_back") "上一张未完成卡片" else "下一张未完成卡片", tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
        }
    }
}

@Composable
private fun ReviewCountBadge(symbol: String, count: Int, color: Color, contentColor: Color, modifier: Modifier, scale: Float) {
    Surface(
        color = color,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke((2 * scale).dp, contentColor),
        shape = RoundedCornerShape((24 * scale).dp),
        modifier = modifier.fillMaxHeight()
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize(),) {
            Spacer(Modifier.weight(1f))
            MaterialSymbol(symbol, null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
            Text("$count", fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), lineHeight = fixedSp(16 * scale), letterSpacing = fixedSp(.6f * scale))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReviewSwipeHint(modifier: Modifier, scale: Float) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((4 * scale).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MaterialSymbol("swipe_left", null, tint = PageForegroundColor(), size = fixedSp(24 * scale), filled = true)
        Text("左滑是记得，", color = PageForegroundColor(), fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * scale), lineHeight = fixedSp(28 * scale))
        MaterialSymbol("swipe_right", null, tint = PageForegroundColor(), size = fixedSp(24 * scale), filled = true)
        Text("右滑是不记得", color = PageForegroundColor(), fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * scale), lineHeight = fixedSp(28 * scale))
    }
}

@Composable
private fun StudyBackButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 52.dp) {
    Surface(onClick = onClick, color = HeaderControlBackgroundColor(), shape = RoundedCornerShape(999.dp), modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            MaterialSymbol("arrow_back", "返回", tint = HeaderControlIconColor(), size = fixedSp(24f), filled = true)
        }
    }
}

@Composable
private fun FigmaReviewCard(
    card: FlashcardEntity,
    flipped: Boolean,
    onFlip: () -> Unit,
    onRate: (Rating) -> Unit,
    modifier: Modifier,
    designScale: Float
) {
    var offsetX by remember(card.id) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val draggable = rememberDraggableState { offsetX += it }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 420, easing = LinearOutSlowInEasing),
        label = "figma review flip"
    )
    val frontAlpha = if (rotation <= 90f) 1f else 0f
    val backAlpha = if (rotation > 90f) 1f else 0f
    val faceShape = RoundedCornerShape((32 * designScale).dp)
    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .graphicsLayer(rotationZ = offsetX / 55f)
            .clip(faceShape)
            // Use the Material ripple that homepage cards use. Clipping first keeps
            // the native press state within the same 32dp container shape.
            .clickable(onClick = onFlip)
            .draggable(
                state = draggable,
                orientation = Orientation.Horizontal,
                enabled = flipped,
                onDragStopped = {
                    when {
                        // Figma 44:2464: left = remembered; right = forgot.
                        offsetX > 140f -> onRate(Rating.AGAIN)
                        offsetX < -140f -> onRate(Rating.GOOD)
                        else -> scope.launch { offsetX = 0f }
                    }
                }
            )
    ) {
        ReviewCardFace(
            title = "问题", content = card.front, symbol = "book_5", visible = frontAlpha,
            rotation = rotation, shape = faceShape, designScale = designScale, backFace = false
        )
        ReviewCardFace(
            title = "答案", content = card.back, symbol = "wb_incandescent", visible = backAlpha,
            rotation = rotation, shape = faceShape, designScale = designScale, backFace = true
        )
    }
}

@Composable
private fun ReviewCardFace(
    title: String,
    content: String,
    symbol: String,
    visible: Float,
    rotation: Float,
    shape: RoundedCornerShape,
    designScale: Float,
    backFace: Boolean
) {
    val lightSurface = MaterialTheme.colorScheme.background.luminance() > .5f
    val faceGradient = if (lightSurface) {
        if (backFace) Brush.verticalGradient(listOf(Color(0xFFD1E8FF), Color(0xFF88C1FF)))
        else Brush.verticalGradient(listOf(Color(0xFFF0F8FF), Color(0xFFD1E8FF)))
    } else Brush.verticalGradient(listOf(Color(0xFF233D55), Color(0xFF182D40)))
    Box(
        // The layer must wrap both the gradient and its text. Keeping it before
        // background prevents the invisible reverse face from painting over the
        // visible face during the 3D transition.
        modifier = Modifier.fillMaxSize().clip(shape).graphicsLayer {
            rotationY = if (backFace) rotation - 180f else rotation
            transformOrigin = TransformOrigin.Center
            cameraDistance = 20f * density
            alpha = visible
        }.background(faceGradient)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MaterialSymbol(symbol, null, tint = if (lightSurface) Color(0xFF00152A) else Color(0xFFE0EEFC), size = fixedSp(44 * designScale), filled = true)
            Spacer(Modifier.height((16 * designScale).dp))
            // Figma 44:2446 / 44:2452 / 48:4553: heading is the project's MiSans
            // Semibold token (520) and body is its Medium token (380). Use fixed faces rather
            // than a requested system weight so every phone renders identically.
            Text(title, color = if (lightSurface) Color(0xFF00152A) else Color(0xFFE0EEFC), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(24 * designScale), lineHeight = fixedSp(32 * designScale), textAlign = TextAlign.Center)
            Spacer(Modifier.height((8 * designScale).dp))
            Text(content, color = if (lightSurface) Color(0xFF00152A) else Color(0xFFE0EEFC), fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(24 * designScale), lineHeight = fixedSp(32 * designScale), textAlign = TextAlign.Center, overflow = TextOverflow.Clip)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FreeStudy(cards: List<FlashcardEntity>, onBack: () -> Unit, onUpdateCard: (FlashcardEntity) -> Unit) {
    var displayedCards by remember(cards) { mutableStateOf(cards) }
    var editingCard by remember { mutableStateOf<FlashcardEntity?>(null) }
    val pager = rememberPagerState(pageCount = { displayedCards.size })
    val scope = rememberCoroutineScope()
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenTopInformationBar(
            title = "自由刷题", subtitle = "${pager.currentPage + 1}/${displayedCards.size}", onBack = onBack,
            modifier = Modifier.zIndex(1f)
        )
        LinearProgressIndicator(
            progress = { (pager.currentPage + 1).toFloat() / displayedCards.size },
            color = Color(0xFF489FFF),
            trackColor = Color(0xFFD1E8FF),
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = (16 * designScale).dp)
                .padding(top = (88 * designScale).dp).height((4 * designScale).dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(top = (132 * designScale).dp).height((600 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
        ) {
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed((346 * designScale).dp),
                // Keep the pager viewport edge-to-edge. The first card starts at 16dp,
                // while the next one can peek through the physical screen edge instead
                // of being clipped a second time by an inset parent.
                contentPadding = PaddingValues(start = (16 * designScale).dp, end = (16 * designScale).dp),
                pageSpacing = (18 * designScale).dp,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { page ->
                var flipped by remember(displayedCards[page].id) { mutableStateOf(false) }
                FreeStudyCard(displayedCards[page], flipped, { flipped = !flipped }, designScale, Modifier.fillMaxSize())
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = (16 * designScale).dp).height((60 * designScale).dp),
                horizontalArrangement = Arrangement.spacedBy((15 * designScale).dp)
            ) {
                Surface(
                    onClick = { editingCard = displayedCards.getOrNull(pager.currentPage) },
                    color = Color(0xFFEBF4FF),
                    contentColor = Color(0xFF001631),
                    shape = RoundedCornerShape((24 * designScale).dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        MaterialSymbol("edit", null, tint = LocalContentColor.current, size = fixedSp(24 * designScale), filled = true)
                        Spacer(Modifier.width((8 * designScale).dp))
                        Text("编辑该卡", fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(16 * designScale), letterSpacing = fixedSp(.6f * designScale))
                    }
                }
                Surface(
                    onClick = {
                        val shuffledCards = displayedCards.shuffled()
                        // A random shuffle can occasionally preserve the same order. In that
                        // case rotate once so this action always gives the user visible feedback.
                        displayedCards = if (shuffledCards == displayedCards && displayedCards.size > 1) {
                            displayedCards.drop(1) + displayedCards.first()
                        } else {
                            shuffledCards
                        }
                        scope.launch { pager.scrollToPage(0) }
                    },
                    color = Color(0xFF489FFF),
                    contentColor = Color(0xFFEBF5FF),
                    shape = RoundedCornerShape((24 * designScale).dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        MaterialSymbol("shuffle", null, tint = LocalContentColor.current, size = fixedSp(24 * designScale), filled = true)
                        Spacer(Modifier.width((8 * designScale).dp))
                        Text("打乱顺序", fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(16 * designScale), letterSpacing = fixedSp(.6f * designScale))
                    }
                }
            }
        }
        Text(
            text = "点击卡片查看答案",
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = (756 * designScale).dp),
            color = PageForegroundColor(),
            fontFamily = AppFonts.MiSansMedium,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(20 * designScale),
            lineHeight = fixedSp(28 * designScale),
            textAlign = TextAlign.Center
        )
    }
    editingCard?.let { card ->
        CardEditDialog(
            card = card,
            onSave = { updated ->
                displayedCards = displayedCards.map { if (it.id == updated.id) updated else it }
                onUpdateCard(updated)
                editingCard = null
            },
            onDismiss = { editingCard = null }
        )
    }
}

@Composable
private fun FreeStudyCard(card: FlashcardEntity, flipped: Boolean, onFlip: () -> Unit, designScale: Float, modifier: Modifier) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 420, easing = LinearOutSlowInEasing),
        label = "free study flip"
    )
    val shape = RoundedCornerShape((32 * designScale).dp)
    Box(modifier = modifier.clip(shape).clickable(onClick = onFlip)) {
        ReviewCardFace(
            title = "问题",
            content = card.front,
            symbol = "book_5",
            visible = if (rotation <= 90f) 1f else 0f,
            rotation = rotation,
            shape = shape,
            designScale = designScale,
            backFace = false
        )
        ReviewCardFace(
            title = "答案",
            content = listOfNotNull(card.back, card.code?.takeIf { it.isNotBlank() }).joinToString("\n\n"),
            symbol = "wb_incandescent",
            visible = if (rotation > 90f) 1f else 0f,
            rotation = rotation,
            shape = shape,
            designScale = designScale,
            backFace = true
        )
    }
}

@Composable
private fun SwipeCard(card: FlashcardEntity, flipped: Boolean, onFlip: () -> Unit, onRate: (Rating) -> Unit, modifier: Modifier) {
    var offsetX by remember(card.id) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val draggable = rememberDraggableState { offsetX += it }
    FlippableCard(
        card, flipped, onFlip,
        modifier.offset { IntOffset(offsetX.roundToInt(), 0) }
            .graphicsLayer(rotationZ = offsetX / 55f)
            .draggable(
                state = draggable, orientation = Orientation.Horizontal, enabled = flipped,
                onDragStopped = {
                    when {
                        offsetX > 140f -> onRate(Rating.GOOD)
                        offsetX < -140f -> onRate(Rating.AGAIN)
                        else -> scope.launch { offsetX = 0f }
                    }
                }
            )
    )
}

@Composable
private fun FlippableCard(card: FlashcardEntity, flipped: Boolean, onFlip: () -> Unit, modifier: Modifier) {
    // Keep both faces in the composition during the whole transition.  Rotating a
    // single container swaps its content too early and makes the answer flash or
    // render mirrored around the 90° point.
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 420, easing = LinearOutSlowInEasing),
        label = "flashcard flip rotation"
    )
    val frontAlpha = if (rotation <= 90f) 1f else 0f
    val backAlpha = if (rotation > 90f) 1f else 0f
    val faceShape = RoundedCornerShape(AppShapeRadius.dp)
    Box(modifier = modifier.clip(faceShape).clickable(onClick = onFlip)) {
        ReviewCardFace(
            title = "问题", content = card.front, symbol = "book_5", visible = frontAlpha,
            rotation = rotation, shape = faceShape, designScale = 1f, backFace = false
        )
        ReviewCardFace(
            title = "答案", content = listOfNotNull(card.back, card.code?.takeIf { it.isNotBlank() }).joinToString("\n\n"),
            symbol = "wb_incandescent", visible = backAlpha, rotation = rotation,
            shape = faceShape, designScale = 1f, backFace = true
        )
    }
}

@Composable
private fun AddCardScreen(deckId: String, viewModel: AppViewModel, nav: ScreenNavigator) {
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            // Only field content scrolls. The title, back control and two actions retain
            // their Figma positions while long questions/answers stay reachable.
            Box(
                Modifier.fillMaxSize()
                    .padding(start = (16 * designScale).dp, top = (148 * designScale).dp, end = (16 * designScale).dp)
                    .clip(RoundedCornerShape(AppShapeRadius.dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (164 * designScale).dp),
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
                DetailPrimaryButton("添加单个卡片", "add_circle", true, designScale) {
                    viewModel.addCardsToDeck(deckId, listOf(CardDraft(front = front, back = back))) { nav.goBack() }
                }
                DetailPrimaryButton("批量导入", "note_stack_add", false, designScale) { nav.navigate(AppRoute.ImportToDeck(deckId)) }
            }
        }
    }
}

@Composable
private fun AddCardLabel(text: String, designScale: Float) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = (8 * designScale).dp),
        color = PageForegroundColor(),
        fontFamily = AppFonts.MiSansSemibold,
        fontWeight = FontWeight.Normal,
        fontSize = fixedSp(20 * designScale),
        lineHeight = fixedSp(24 * designScale)
    )
}

@Composable
private fun AddCardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    designScale: Float,
    height: Float = 177f,
    singleLine: Boolean = false
) {
    Box(
        modifier = Modifier.fillMaxWidth().height((height * designScale).dp)
            .clip(RoundedCornerShape(AppShapeRadius.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding((24 * designScale).dp)
    ) {
        if (value.isBlank()) {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
                fontFamily = AppFonts.MiSansMedium,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(20 * designScale),
                lineHeight = fixedSp(28 * designScale)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = TextStyle(
                color = PageForegroundColor(),
                fontFamily = AppFonts.MiSansMedium,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(20 * designScale),
                lineHeight = fixedSp(28 * designScale)
            ),
            singleLine = singleLine
        )
    }
}

@Composable
private fun ImportScreen(viewModel: AppViewModel, nav: ScreenNavigator, existingDeckId: String? = null) {
    val context = LocalContext.current
    var deckName by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf("") }
    val drafts = remember { mutableStateListOf<CardDraft>() }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        rawText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    if (drafts.isEmpty()) {
        ImportEntryScreen(
            title = if (existingDeckId == null) "导入卡片组" else "批量导入",
            isNewDeck = existingDeckId == null,
            deckName = deckName,
            onDeckNameChange = { deckName = it },
            rawText = rawText,
            onRawTextChange = { rawText = it },
            errors = errors,
            onBack = nav::popBackStack,
            onChooseFile = { filePicker.launch("text/*") },
            onSmartPdf = { nav.navigate(AppRoute.PdfMaker) },
            onPreview = {
                val result = ImportParser.parse(rawText)
                drafts.clear()
                drafts.addAll(result.cards)
                errors = result.errors
            }
        )
    } else {
        Scaffold(topBar = { AppBar(if (existingDeckId == null) "导入卡片组" else "批量导入", nav::popBackStack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (errors.isNotEmpty()) item { Text(errors.joinToString("\n"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            item { Text("已识别 ${drafts.size} 张卡", style = MaterialTheme.typography.titleMedium, fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal) }
            items(drafts.indices.toList()) { itemIndex ->
                var front by remember(drafts[itemIndex].front) { mutableStateOf(drafts[itemIndex].front) }
                var back by remember(drafts[itemIndex].back) { mutableStateOf(drafts[itemIndex].back) }
                Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(front, { front = it; drafts[itemIndex] = drafts[itemIndex].copy(front = it) }, label = { Text("问题") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(back, { back = it; drafts[itemIndex] = drafts[itemIndex].copy(back = it) }, label = { Text("答案") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
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
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Text(if (existingDeckId == null) "保存 ${drafts.size} 张卡" else "加入当前卡组（${drafts.size} 张）") }
            }
        }
    }
    }
}

@Composable
private fun ImportEntryScreen(
    title: String,
    isNewDeck: Boolean,
    deckName: String,
    onDeckNameChange: (String) -> Unit,
    rawText: String,
    onRawTextChange: (String) -> Unit,
    errors: List<String>,
    onBack: () -> Unit,
    onChooseFile: () -> Unit,
    onSmartPdf: () -> Unit,
    onPreview: () -> Unit
) {
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            // Figma 60:4695 / 62:4755: both import variants share a clipped,
            // independently scrollable input area beginning at y=132.
            Box(
                Modifier.fillMaxSize()
                    .padding(start = (16 * designScale).dp, top = (132 * designScale).dp, end = (16 * designScale).dp)
                    .clip(RoundedCornerShape(AppShapeRadius.dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (140 * designScale).dp),
                    verticalArrangement = Arrangement.spacedBy((15 * designScale).dp)
                ) {
                    item { PdfImportShortcut(designScale, onSmartPdf) }
                    item { ImportInfoCard(designScale) }
                    if (isNewDeck) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
                                AddCardLabel("卡组名称", designScale)
                                AddCardTextField(
                                    value = deckName,
                                    onValueChange = onDeckNameChange,
                                    placeholder = "此处输入",
                                    designScale = designScale,
                                    height = 110f,
                                    singleLine = true
                                )
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
                            AddCardLabel("问题与答案", designScale)
                            AddCardTextField(rawText, onRawTextChange, "此处粘贴", designScale)
                            if (errors.isNotEmpty()) {
                                Text(
                                    errors.first(),
                                    modifier = Modifier.padding(start = (8 * designScale).dp),
                                    color = Color(0xFFC83232),
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
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = (16 * designScale).dp, end = (16 * designScale).dp, bottom = (32 * designScale).dp)
                    .height((60 * designScale).dp).zIndex(1f),
                horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp)
            ) {
                ImportActionButton("选择文件", null, false, Modifier.weight(1f), designScale, onChooseFile)
                ImportActionButton("识别并预览", null, true, Modifier.weight(1f), designScale, onPreview)
            }
        }
    }
}

@Composable
private fun ImportInfoCard(designScale: Float) {
    DescriptionInfoCard(
        text = "选择的文件支持（PDF/ .txt/ .md）\n格式。识别后可逐章修改并保存",
        scale = designScale
    )
}

@Composable
private fun ImportInfoLine(text: String, designScale: Float) {
    Text(
        text = text,
        color = PageForegroundColor(),
        fontFamily = AppFonts.MiSansMedium,
        fontWeight = FontWeight.Normal,
        fontSize = fixedSp(20 * designScale),
        lineHeight = fixedSp(27 * designScale),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ImportActionButton(
    text: String,
    icon: String?,
    primary: Boolean,
    modifier: Modifier,
    designScale: Float,
    onClick: () -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() <= .5f
    val container = if (primary) Color(0xFF489FFF) else if (dark) Color(0xFF203A52) else Color(0xFFEBF4FF)
    val content = if (primary) Color(0xFFEBF5FF) else if (dark) Color(0xFFD7EBFF) else Color(0xFF001631)
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
        Text(
            text,
            fontFamily = AppFonts.MiSansBold,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(16 * designScale),
            lineHeight = fixedSp(16 * designScale),
            letterSpacing = fixedSp(.3f * designScale),
            maxLines = 1
        )
    }
}

/** Links the normal card-group import page to the shared file-to-flashcard flow. */
@Composable
private fun PdfImportShortcut(designScale: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(AppShapeRadius.dp),
        color = Color(0xFF489FFF),
        contentColor = Color(0xFFF0F8FF),
        modifier = Modifier.fillMaxWidth().height((76 * designScale).dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding((12 * designScale).dp),
            horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(1000.dp),
                color = Color(0xFFF0F8FF),
                modifier = Modifier.size((52 * designScale).dp)
            ) { Box(contentAlignment = Alignment.Center) { MaterialSymbol("picture_as_pdf", null, tint = Color(0xFF489FFF), size = fixedSp(24 * designScale), filled = true) } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("选择文件智能制卡", color = Color(0xFFF0F8FF), fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * designScale))
                Text("从教材或课件生成闪卡", color = Color(0xBFEFF6FF), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(16 * designScale), letterSpacing = fixedSp(.6f * designScale))
            }
            MaterialSymbol("arrow_forward", "选择文件智能制卡", tint = Color(0xFFF0F8FF), size = fixedSp(24 * designScale))
        }
    }
}

private enum class PdfMakerStep { HOME, READING, READ_ERROR, CHAPTERS, SETTINGS, PREVIEW, TASK }
private enum class PdfTaskState { GENERATING, PAUSED, COMPLETE }
private data class PdfGenerationBlock(val title: String, val detail: String, val canOpenSettings: Boolean = false)

private fun apiKeyGenerationBlock(status: String): PdfGenerationBlock = when (status.uppercase()) {
    "INVALID" -> PdfGenerationBlock("API Key 不可用", "请在设置中更新有效的 DeepSeek API Key。", canOpenSettings = true)
    "INSUFFICIENT_BALANCE" -> PdfGenerationBlock("API Key 余额不足", "请在设置中更新可用的 DeepSeek API Key。", canOpenSettings = true)
    else -> PdfGenerationBlock("需要 API Key", "请先在设置中保存可用的 DeepSeek API Key。", canOpenSettings = true)
}

private fun taskGenerationBlock(code: String?): PdfGenerationBlock = when (code) {
    "API_KEY_NOT_SET", "API_KEY_INVALID", "API_KEY_INSUFFICIENT_BALANCE" -> apiKeyGenerationBlock(code.removePrefix("API_KEY_"))
    "PDF_NOT_READY" -> PdfGenerationBlock("PDF 状态异常", "请返回上一步重新选择并解析 PDF。")
    else -> PdfGenerationBlock("暂时无法开始生成", "服务暂时无法创建任务，请稍后重试。")
}

private fun sampleGenerationBlock(code: String?): PdfGenerationBlock = when (code) {
    "API_KEY_NOT_SET", "API_KEY_INVALID", "API_KEY_INSUFFICIENT_BALANCE" -> apiKeyGenerationBlock(code.removePrefix("API_KEY_"))
    "PDF_NOT_READY" -> PdfGenerationBlock("PDF 状态异常", "请返回上一步重新选择并解析 PDF。")
    "VALIDATION_ERROR" -> PdfGenerationBlock("无法生成样卡", "当前生成参数未被服务端接受，请调整后重试。")
    else -> PdfGenerationBlock("暂时无法生成样卡", "服务暂时无法生成样卡，请稍后重试。")
}

private data class PdfChapter(val remoteId: String? = null, val title: String, val start: Int, val end: Int, val selected: Boolean = true)
private data class SmartImportFile(
    val id: String,
    val uri: Uri,
    val name: String,
    val format: String,
    val selected: Boolean = false
)

private fun displayNameFor(uri: Uri, context: android.content.Context): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        }
        ?: uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "未命名文件" }
}

private fun formatForFileName(name: String): String? {
    val suffix = name.substringAfterLast('.', "").lowercase()
    return when (suffix) {
        "pdf" -> "pdf"
        "txt", "md" -> ".${suffix}"
        else -> null
    }
}

private val PdfSampleCards = listOf(
    CardDraft("什么是以人为本的设计？", "它从人的需要、能力与行为出发，而不是只关注物品本身。"),
    CardDraft("为什么日常物品需要清晰的反馈？", "反馈能让人知道操作是否发生、结果是什么，从而减少不确定感。"),
    CardDraft("判断：可见性越高，用户越容易理解物品的可操作方式。", "正确。可见性帮助用户建立正确的操作预期。")
)

@Composable
private fun PdfSmartCardsFlow(decks: List<DeckSummary>, viewModel: AppViewModel, nav: ScreenNavigator) {
    val context = LocalContext.current
    val remotePdf by viewModel.pdfFile.collectAsState()
    val remoteSamples by viewModel.pdfSamples.collectAsState()
    val remoteTask by viewModel.pdfTask.collectAsState()
    val remoteTaskDeckId by viewModel.pdfTaskDeckId.collectAsState()
    var step by remember { mutableStateOf(PdfMakerStep.HOME) }
    val importedFiles = remember { mutableStateListOf<SmartImportFile>() }
    val chapters = remember { mutableStateListOf<PdfChapter>() }
    var editingChapter by remember { mutableStateOf<Int?>(null) }
    var useExistingDeck by remember { mutableStateOf(false) }
    var selectedExistingDeckId by remember { mutableStateOf<String?>(null) }
    var deckName by remember { mutableStateOf("") }
    var coverage by remember { mutableStateOf("均匀") }
    var requirement by remember { mutableStateOf("") }
    var taskState by remember { mutableStateOf(PdfTaskState.GENERATING) }
    var pdfReadFailure by remember { mutableStateOf<PdfReadFailure?>(null) }
    var chapterDeleteFailed by remember { mutableStateOf(false) }
    var generationBlocked by remember { mutableStateOf<PdfGenerationBlock?>(null) }
    var sampleRequestInFlight by remember { mutableStateOf(false) }
    var generationCheckInFlight by remember { mutableStateOf(false) }
    var generationConfig by remember { mutableStateOf(PdfGenerationConfig()) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            val name = displayNameFor(uri, context)
            val format = formatForFileName(name)
            if (format != null && importedFiles.none { it.uri == uri }) {
                importedFiles += SmartImportFile(uri.toString(), uri, name, format)
            }
        }
    }

    LaunchedEffect(remoteTask?.status) {
        when (remoteTask?.status?.uppercase()) {
            "COMPLETED" -> taskState = PdfTaskState.COMPLETE
            "PAUSED" -> taskState = PdfTaskState.PAUSED
            "PENDING", "RUNNING" -> taskState = PdfTaskState.GENERATING
        }
    }
    LaunchedEffect(chapterDeleteFailed) {
        if (chapterDeleteFailed) {
            delay(1_800)
            chapterDeleteFailed = false
        }
    }

    when (step) {
        PdfMakerStep.HOME -> SmartFileImportScreen(
            files = importedFiles,
            onChoose = { filePicker.launch(arrayOf("application/pdf")) },
            onToggle = { id ->
                val index = importedFiles.indexOfFirst { it.id == id }
                if (index >= 0) importedFiles[index] = importedFiles[index].copy(selected = !importedFiles[index].selected)
            },
            onDelete = { id -> importedFiles.removeAll { it.id == id } },
            onPreview = {
                importedFiles.firstOrNull { it.selected }?.let { file ->
                    step = PdfMakerStep.READING
                    pdfReadFailure = null
                    viewModel.uploadPdf(file.uri, onParsed = { parsed ->
                        chapters.clear()
                        chapters.addAll(parsed.map { PdfChapter(it.id, it.name, it.startPage, it.endPage) })
                        step = PdfMakerStep.CHAPTERS
                    }, onFailure = { failure -> pdfReadFailure = failure; step = PdfMakerStep.READ_ERROR })
                }
            },
            onBack = nav::popBackStack
        )
        PdfMakerStep.READING -> PdfReadingScreen(onBack = { step = PdfMakerStep.HOME })
        PdfMakerStep.READ_ERROR -> PdfReadErrorScreen(failure = pdfReadFailure, onBack = { step = PdfMakerStep.HOME }, onRetry = { step = PdfMakerStep.HOME })
        PdfMakerStep.CHAPTERS -> PdfChapterScreen(
            chapters = chapters,
            onToggle = { index -> chapters[index] = chapters[index].copy(selected = !chapters[index].selected) },
            onEdit = { editingChapter = it },
            onDelete = { index ->
                val removed = chapters.removeAt(index)
                removed.remoteId?.let { chapterId ->
                    viewModel.deletePdfChapter(
                        com.qiuzhao.flashcards.data.remote.PdfChapter(
                            id = chapterId,
                            name = removed.title,
                            startPage = removed.start,
                            endPage = removed.end
                        )
                    ) {
                        chapters.add(index.coerceIn(0, chapters.size), removed)
                        chapterDeleteFailed = true
                    }
                }
            },
            deleteFailed = chapterDeleteFailed,
            onNext = { step = PdfMakerStep.SETTINGS },
            onBack = { step = PdfMakerStep.HOME }
        )
        PdfMakerStep.SETTINGS -> PdfSettingsScreen(
            decks = decks, useExistingDeck = useExistingDeck, onUseExisting = { useExistingDeck = it },
            selectedExistingDeckId = selectedExistingDeckId,
            onSelectedExistingDeck = { selectedExistingDeckId = it },
            deckName = deckName, onDeckNameChange = { deckName = it }, coverage = coverage,
            onCoverageChange = { coverage = it }, requirement = requirement, onRequirementChange = { requirement = it },
            onPreview = { basic, analysis ->
                generationConfig = PdfGenerationConfig(
                    quantity = when (coverage) {
                        "精简" -> "COMPACT"
                        "充分" -> "EXTENSIVE"
                        else -> "BALANCED"
                    },
                    basic = basic / 100f,
                    understanding = (analysis - basic) / 100f,
                    application = (100f - analysis) / 100f,
                    requirement = requirement
                )
                val selected = chapters.filter { it.selected }.mapNotNull { it.remoteId }
                if (selected.isEmpty()) {
                    generationBlocked = PdfGenerationBlock("未选择章节", "请返回上一步选择至少一个章节。")
                } else if (!sampleRequestInFlight) {
                    sampleRequestInFlight = true
                    viewModel.generatePdfSamples(
                        chapterIds = selected,
                        config = generationConfig,
                        onReady = {
                            sampleRequestInFlight = false
                            step = PdfMakerStep.PREVIEW
                        },
                        onFailure = { code ->
                            sampleRequestInFlight = false
                            generationBlocked = sampleGenerationBlock(code)
                        }
                    )
                }
            }, onBack = { step = PdfMakerStep.CHAPTERS }
        )
        PdfMakerStep.PREVIEW -> PdfPreviewScreen(
            samples = remoteSamples,
            onBack = { step = PdfMakerStep.SETTINGS },
            onGenerate = {
                val selected = chapters.filter { it.selected }.mapNotNull { it.remoteId }
                if (selected.isEmpty()) {
                    generationBlocked = PdfGenerationBlock("未选择章节", "请返回上一步选择至少一个章节。")
                } else if (!generationCheckInFlight) {
                    generationCheckInFlight = true
                    viewModel.checkApiKeyForGeneration(
                        onAvailable = {
                            viewModel.createPdfTask(
                                existingDeckId = if (useExistingDeck) selectedExistingDeckId else null,
                                deckName = deckName,
                                chapterIds = selected,
                                config = generationConfig,
                                onStarted = {
                                    generationCheckInFlight = false
                                    taskState = PdfTaskState.GENERATING
                                    step = PdfMakerStep.TASK
                                },
                                onFailure = { code ->
                                    generationCheckInFlight = false
                                    generationBlocked = taskGenerationBlock(code)
                                }
                            )
                        },
                        onUnavailable = { status ->
                            generationCheckInFlight = false
                            generationBlocked = apiKeyGenerationBlock(status)
                        },
                        onFailure = {
                            generationCheckInFlight = false
                            generationBlocked = PdfGenerationBlock("无法确认 API Key", "请检查网络后重试。")
                        }
                    )
                }
            }
        )
        PdfMakerStep.TASK -> PdfTaskScreen(
            state = taskState,
            onPause = { taskState = PdfTaskState.PAUSED },
            onResume = { taskState = PdfTaskState.GENERATING; viewModel.resumePdfTask() },
            onBack = { step = PdfMakerStep.PREVIEW },
            onViewDeck = {
                remoteTaskDeckId?.let { deckId -> nav.replaceInclusive(AppRoute.PdfMaker, AppRoute.CardList(deckId)) }
            }
        )
    }

    editingChapter?.let { index ->
        PdfChapterEditDialog(
            chapter = chapters[index],
            onSave = {
                chapters[index] = it
                it.remoteId?.let { id -> remotePdf?.id?.let { fileId -> viewModel.updatePdfChapter(com.qiuzhao.flashcards.data.remote.PdfChapter(id, it.title, it.start, it.end)) } }
                editingChapter = null
            },
            onDismiss = { editingChapter = null }
        )
    }
    generationBlocked?.let { block ->
        PdfGenerationBlockedDialog(
            block = block,
            onDismiss = { generationBlocked = null },
            onOpenSettings = {
                generationBlocked = null
                nav.navigate(AppRoute.Settings)
            }
        )
    }
}

@Composable
private fun PdfFlowLayout(title: String, onBack: () -> Unit, footer: @Composable (() -> Unit)? = null, content: LazyListScope.() -> Unit) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(start = (16 * scale).dp, top = (132 * scale).dp, end = (16 * scale).dp).clip(BottomRoundedViewportShape),
                contentPadding = PaddingValues(bottom = if (footer == null) 36.dp else (148 * scale).dp),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp), content = content
            )
            DeckDetailHeader(title, scale, onBack, modifier = Modifier.zIndex(1f))
            if (footer != null) {
                BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
                Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = (16 * scale).dp, end = (16 * scale).dp, bottom = (24 * scale).dp).zIndex(1f)) { footer() }
            }
        }
    }
}

@Composable
private fun SmartFileImportScreen(
    files: List<SmartImportFile>,
    onChoose: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPreview: () -> Unit,
    onBack: () -> Unit
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .padding(start = (16 * scale).dp, top = (132 * scale).dp, end = (16 * scale).dp)
                    .clip(BottomRoundedViewportShape),
                contentPadding = PaddingValues(bottom = (148 * scale).dp),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
            ) {
                item { SmartInfoCard("上传教材、课件或其他学习资料。\n暂不支持扫描版PDF。", scale) }
                item { SmartSectionLabel("导入的资料", scale) }
                items(files, key = { it.id }) { file ->
                    SmartImportFileCard(file, scale, { onToggle(file.id) }, { onDelete(file.id) })
                }
            }
            DeckDetailHeader("智能制卡", scale, onBack, modifier = Modifier.zIndex(1f))
            BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = (16 * scale).dp, end = (16 * scale).dp, bottom = (32 * scale).dp)
                    .height((60 * scale).dp).zIndex(1f),
                horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
            ) {
                ImportActionButton("选择文件", "folder_open", false, Modifier.weight(1f), scale, onChoose)
                ImportActionButton("识别并预览", "screen_search_desktop", true, Modifier.weight(1f), scale, onPreview)
            }
        }
    }
}

@Composable
private fun SmartInfoCard(text: String, scale: Float) {
    DescriptionInfoCard(text = text, scale = scale)
}

/** Figma 307:1419 — shared, text-only explanatory card used across import flows. */
@Composable
private fun DescriptionInfoCard(text: String, scale: Float) {
    Surface(
        shape = RoundedCornerShape((32 * scale).dp),
        color = Color(0xFFF3F3FF),
        modifier = Modifier.fillMaxWidth().heightIn(min = (102 * scale).dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding((24 * scale).dp),
            color = Color(0xCC000000),
            fontFamily = AppFonts.MiSansMedium,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(20 * scale),
            lineHeight = fixedSp(24 * scale),
            style = figmaCardTextStyle()
        )
    }
}

@Composable
private fun SmartSectionLabel(text: String, scale: Float) {
    Text(
        text = text,
        modifier = Modifier.padding(start = (8 * scale).dp),
        color = Color(0xFF1F2832),
        fontFamily = AppFonts.MiSansSemibold,
        fontWeight = FontWeight.Normal,
        fontSize = fixedSp(20 * scale),
        lineHeight = fixedSp(28 * scale)
    )
}

@Composable
private fun SmartImportFileCard(file: SmartImportFile, scale: Float, onToggle: () -> Unit, onDelete: () -> Unit) {
    SmartSwipeDeleteContainer(file.id, scale, "删除文件", onDelete) { cardModifier ->
        SmartSelectableCard(
            title = file.name,
            subtitle = "26/8/11 导入",
            badge = file.format,
            selected = file.selected,
            selectedIcon = "check_circle",
            unselectedIcon = "picture_as_pdf",
            action = onToggle,
            scale = scale,
            modifier = cardModifier,
            onClick = onToggle
        )
    }
}

/** Shared Figma swipe geometry: 112dp action panel, 16dp overlap, 96dp reveal. */
@Composable
private fun SmartSwipeDeleteContainer(
    key: Any,
    scale: Float,
    deleteLabel: String,
    onDelete: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val shape = RoundedCornerShape((32 * scale).dp)
    val actionWidth = (112 * scale).dp
    val revealWidthPx = with(LocalDensity.current) { ((112 - 16) * scale).dp.toPx() }
    var dragOffset by remember(key) { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(dragOffset, label = "$key smart delete swipe")
    val dragState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceIn(-revealWidthPx, 0f)
    }
    Box(Modifier.fillMaxWidth().height((104 * scale).dp).clip(shape)) {
        // Keep the action mounted behind the card at rest. This matches the
        // deck/card-list implementation and prevents a one-frame pop-in as a
        // drag first crosses the reveal threshold.
        Surface(
            onClick = onDelete,
            shape = shape,
            color = Color(0xFFBD3F3F),
            contentColor = Color(0xFFFFEDED),
            modifier = Modifier.align(Alignment.CenterEnd).width(actionWidth).fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(start = (24 * scale).dp, end = (16 * scale).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MaterialSymbol("delete", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.height((4 * scale).dp))
                Text(deleteLabel, fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), lineHeight = fixedSp(20 * scale))
            }
        }
        content(
            Modifier.fillMaxSize().offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        dragOffset = if (dragOffset < -revealWidthPx / 2f) -revealWidthPx else 0f
                    }
                )
        )
    }
}

@Composable
private fun SmartSelectableCard(
    title: String,
    subtitle: String,
    badge: String,
    selected: Boolean,
    selectedIcon: String,
    unselectedIcon: String,
    action: (() -> Unit)?,
    scale: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Figma 167:9679 / 222:4713.  These two selectable components share the
    // exact blue/green state pair; keeping it here prevents the file and
    // chapter flows from drifting apart again.
    val surface = if (selected) Color(0xFFEAFBEB) else Color(0xFFF0F8FF)
    val accent = if (selected) Color(0xFF7AC583) else Color(0xFF489FFF)
    val primary = Color(0xCC000000)
    val onAccent = if (selected) Color(0xFFEAFBEB) else Color(0xFFF0F8FF)
    // The unselected file state in 167:9679 uses a 20sp import line.  The
    // selected file and both chapter states use the 16sp component token.
    val isFileCard = badge != "编辑"
    val subtitleTextSize = if (isFileCard && !selected) 20f else 16f
    val subtitleLineHeight = if (subtitleTextSize == 20f) 24f else 20f
    val badgeTextSize = 16f
    val badgeFont = if (badge == "编辑") AppFonts.MiSans630 else AppFonts.GoogleSansFlexBold
    val badgeWeight = FontWeight.Normal
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape((32 * scale).dp),
        color = surface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding((24 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            Surface(shape = RoundedCornerShape((16 * scale).dp), color = accent, modifier = Modifier.size((56 * scale).dp)) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol(if (selected) selectedIcon else unselectedIcon, null, tint = onAccent, size = fixedSp(24 * scale), filled = true)
                }
            }
            Column(
                Modifier.weight(1f).height((56 * scale).dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    title, color = primary, fontFamily = AppFonts.MiSansBold,
                    fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * scale),
                    lineHeight = fixedSp(24 * scale), maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle, color = Color(0x80000000), fontFamily = AppFonts.MiSansSemibold,
                    fontWeight = FontWeight.Normal, fontSize = fixedSp(subtitleTextSize * scale),
                    lineHeight = fixedSp(subtitleLineHeight * scale), maxLines = 1
                )
            }
            Surface(
                onClick = { action?.invoke() },
                shape = RoundedCornerShape((20 * scale).dp),
                color = accent,
                modifier = Modifier.height((56 * scale).dp)
            ) {
                Box(Modifier.padding(horizontal = (16 * scale).dp), contentAlignment = Alignment.Center) {
                    Text(badge, color = onAccent, fontFamily = badgeFont, fontWeight = badgeWeight, fontSize = fixedSp(badgeTextSize * scale), lineHeight = fixedSp(20 * scale), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PdfHomeScreen(recentVisible: Boolean, onChoose: () -> Unit, onShowError: () -> Unit, onUseRecent: () -> Unit, onDeleteRecent: () -> Unit, onBack: () -> Unit) {
    PdfFlowLayout("PDF 智能制卡", onBack) {
        item {
            Card(shape = RoundedCornerShape(AppShapeRadius.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(onClick = onChoose, shape = RoundedCornerShape(24.dp), color = Color(0xFF489FFF), modifier = Modifier.fillMaxWidth().height(60.dp)) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            MaterialSymbol("upload_file", "选择 PDF", tint = Color.White, filled = true); Spacer(Modifier.width(8.dp))
                            Text("选择 PDF", color = Color.White, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(17f))
                        }
                    }
                    Text("上传教材、课件或其他学习资料", color = PageForegroundColor(), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(17f))
                    Text("暂不支持扫描版 PDF", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(14f))
                    TextButton(onClick = onShowError) { Text("演示无法读取", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item { Text("最近资料", color = PageForegroundColor(), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(20f), modifier = Modifier.padding(start = 8.dp)) }
        if (recentVisible) item {
            Surface(onClick = onUseRecent, shape = RoundedCornerShape(AppShapeRadius.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { MaterialSymbol("picture_as_pdf", null, tint = MaterialTheme.colorScheme.primary, filled = true) } }
                    Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("设计心理学.pdf", color = PageForegroundColor(), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(17f))
                        Text("126 页 · 今天使用", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(14f))
                    }
                    TextButton(onClick = onDeleteRecent) { MaterialSymbol("delete", "删除最近资料", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        } else item { PdfEmptyState("还没有使用过 PDF", "选择一份学习资料，AI 会帮你整理成闪卡。") }
    }
}

@Composable
private fun PdfReadingScreen(onBack: () -> Unit) = PdfFlowLayout("PDF 智能制卡", onBack) {
    item { PdfStatusCard("正在读取 PDF", "正在识别文字与章节目录，请稍候。", loading = true) }
}

@Composable
private fun PdfReadErrorScreen(failure: PdfReadFailure?, onBack: () -> Unit, onRetry: () -> Unit) = PdfFlowLayout("PDF 智能制卡", onBack) {
    item { PdfStatusCard(failure?.title ?: "PDF 处理失败", failure?.detail ?: "请重新选择文件后再试。", icon = "error") }
    item { Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("重新选择") } }
}

@Composable
private fun PdfEmptyState(title: String, subtitle: String) = Card(shape = RoundedCornerShape(AppShapeRadius.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MaterialSymbol("folder_open", null, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = fixedSp(36f))
        Text(title, color = PageForegroundColor(), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(17f))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(14f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun PdfStatusCard(title: String, subtitle: String, loading: Boolean = false, icon: String = "picture_as_pdf") = Card(shape = RoundedCornerShape(AppShapeRadius.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 4.dp) else MaterialSymbol(icon, null, tint = MaterialTheme.colorScheme.error, size = fixedSp(40f), filled = true)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = PageForegroundColor(), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(19f))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(14f))
        }
    }
}

@Composable
private fun PdfChapterScreen(
    chapters: List<PdfChapter>,
    onToggle: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    deleteFailed: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .padding(start = (16 * scale).dp, top = (132 * scale).dp, end = (16 * scale).dp)
                    .clip(BottomRoundedViewportShape),
                contentPadding = PaddingValues(bottom = (148 * scale).dp),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
            ) {
                item { SmartInfoCard("选择要制作闪卡的章节。", scale) }
                item { SmartSectionLabel("章节", scale) }
                items(chapters.indices.toList()) { index ->
                    val chapter = chapters[index]
                    SmartSwipeDeleteContainer("chapter-${chapter.title}-${chapter.start}", scale, "删除章节", { onDelete(index) }) { cardModifier ->
                        SmartSelectableCard(
                            title = chapter.title,
                            subtitle = "${chapter.start}-${chapter.end} 页",
                            badge = "编辑",
                            selected = chapter.selected,
                            selectedIcon = "check_circle",
                            unselectedIcon = "book_ribbon",
                            action = { onEdit(index) },
                            scale = scale,
                            modifier = cardModifier,
                            onClick = { onToggle(index) }
                        )
                    }
                }
            }
            DeckDetailHeader("智能制卡", scale, onBack, modifier = Modifier.zIndex(1f))
            BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
            Surface(
                onClick = onNext,
                color = Color(0xFF489FFF),
                contentColor = Color(0xFFEBF5FF),
                shape = RoundedCornerShape((24 * scale).dp),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = (16 * scale).dp, end = (16 * scale).dp, bottom = (32 * scale).dp)
                    .height((60 * scale).dp).fillMaxWidth().zIndex(1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("下一步", fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), lineHeight = fixedSp(16 * scale), letterSpacing = fixedSp(.6f * scale))
                }
            }
            DeleteFailureHint(
                visible = deleteFailed,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = (108 * scale).dp)
            )
        }
    }
}

@Composable
private fun PdfChapterEditDialog(chapter: PdfChapter, onSave: (PdfChapter) -> Unit, onDismiss: () -> Unit) {
    var title by remember(chapter) { mutableStateOf(chapter.title) }
    var start by remember(chapter) { mutableStateOf(chapter.start.toString()) }
    var end by remember(chapter) { mutableStateOf(chapter.end.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("编辑章节", fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("章节名称") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(start, { start = it.filter(Char::isDigit) }, label = { Text("起始页") }, modifier = Modifier.weight(1f))
                OutlinedTextField(end, { end = it.filter(Char::isDigit) }, label = { Text("结束页") }, modifier = Modifier.weight(1f))
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(chapter.copy(title = title.ifBlank { chapter.title }, start = start.toIntOrNull() ?: chapter.start, end = end.toIntOrNull() ?: chapter.end)) }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun PdfSettingsScreen(
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
            color = Color(0xFF489FFF),
            contentColor = Color(0xFFEBF5FF),
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.fillMaxWidth().height((60 * scale).dp)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbol("play_circle", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                Text("生成3张样卡", fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), letterSpacing = fixedSp(.6f * scale))
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
                Text("左右拉动拉杆可改变题库难度比例。\n从左到右对应从易到难。", color = Color(0xFF8C939A), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), lineHeight = fixedSp(20 * scale))
            }
        }
        item {
            PdfSettingsSectionCard("生成数量", "stacks", scale) {
                Row(horizontalArrangement = Arrangement.spacedBy((16 * scale).dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("精简", "均匀", "充分").forEach { label ->
                        val selected = coverage == label
                        Surface(
                            onClick = { onCoverageChange(label) },
                            color = if (selected) Color(0xFF489FFF) else Color.White,
                            contentColor = if (selected) Color(0xFFEBF5FF) else Color(0xFF001832),
                            shape = RoundedCornerShape((32 * scale).dp),
                            modifier = Modifier.weight(1f).height((56 * scale).dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, fontFamily = if (selected) AppFonts.MiSansSemibold else AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * scale), maxLines = 1)
                            }
                        }
                    }
                }
                Text("解释说明文字", color = Color(0xFF8C939A), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), lineHeight = fixedSp(20 * scale))
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
    color = Color(0xFFF0F8FF),
    shape = RoundedCornerShape((32 * scale).dp),
    modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(250, easing = FastOutSlowInEasing))
) {
    Column(
        modifier = Modifier.padding((24 * scale).dp),
        verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol(icon, null, tint = Color(0xFF001832), size = fixedSp(24 * scale), filled = true)
            Text(title, color = Color(0xFF001832), fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * scale))
        }
        content()
    }
}

@Composable
private fun PdfDestinationChoice(label: String, selected: Boolean, scale: Float, onClick: () -> Unit) = Surface(
    onClick = onClick,
    shape = RoundedCornerShape((32 * scale).dp),
    color = if (selected) Color(0xFFC3E3FF) else Color.White,
    modifier = Modifier.fillMaxWidth().height((64 * scale).dp)
) {
    Row(Modifier.padding(horizontal = (16 * scale).dp), horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbol(if (selected) "radio_button_checked" else "radio_button_unchecked", null, tint = if (selected) Color(0xFF489FFF) else Color(0xFF001832), size = fixedSp(24 * scale), filled = selected)
        Text(label, color = Color(0xFF001832), fontFamily = if (selected) AppFonts.MiSansSemibold else AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * scale))
    }
}

@Composable
private fun PdfDeckNameField(value: String, isExistingDeck: Boolean, scale: Float, onValueChange: (String) -> Unit, onClick: () -> Unit, onClear: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height((64 * scale).dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (!isExistingDeck) onValueChange(it) },
            readOnly = isExistingDeck,
            placeholder = { Text("此处输入牌组名称", fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale)) },
            trailingIcon = {
                IconButton(onClick = onClear) { MaterialSymbol("cancel", "清除牌组选择", tint = Color(0xFF4C5964), size = fixedSp(24 * scale), filled = true) }
            },
            textStyle = TextStyle(color = Color(0xFF001832), fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale)),
            shape = RoundedCornerShape((16 * scale).dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF17518A),
                unfocusedBorderColor = Color(0xFF17518A),
                cursorColor = Color(0xFF17518A)
            ),
            singleLine = true,
            modifier = Modifier.fillMaxSize().then(
                if (isExistingDeck) Modifier.clickable(onClick = onClick) else Modifier
            )
        )
        Text(
            "牌组名称",
            modifier = Modifier.align(Alignment.TopStart).offset(x = (12 * scale).dp, y = (-9 * scale).dp)
                .background(Color(0xFFF0F8FF)).padding(horizontal = (4 * scale).dp).zIndex(1f),
            color = Color(0xFF17518A),
            fontFamily = AppFonts.MiSansSemibold,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(16 * scale)
        )
    }
}

@Composable
private fun PdfDeckPickerMenu(decks: List<DeckSummary>, scale: Float, onSelect: (DeckSummary) -> Unit) {
    Surface(
        // Figma 166:8299 menu surface (the destination rows keep their own
        // selected-state token, while this picker uses the lighter menu blue).
        color = Color(0xFFD1E8FF),
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
                    Text(
                        "暂无可用牌组",
                        color = Color(0xBF001D36),
                        fontFamily = AppFonts.MiSansSemibold,
                        fontWeight = FontWeight.Normal,
                        fontSize = fixedSp(20 * scale),
                        lineHeight = fixedSp(28 * scale)
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
                        MaterialSymbol("style", null, tint = Color(0xBF001D36), size = fixedSp(24 * scale), filled = true)
                        Text(
                            displayDeckTitle(deck),
                            color = Color(0xBF001D36),
                            fontFamily = AppFonts.MiSansSemibold,
                            fontWeight = FontWeight.Normal,
                            fontSize = fixedSp(20 * scale),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider(color = Color(0x1A002A4D), thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun PdfRequirementField(value: String, onValueChange: (String) -> Unit, scale: Float) = Surface(
    color = Color.White,
    shape = RoundedCornerShape((32 * scale).dp),
    modifier = Modifier.fillMaxWidth().height((86 * scale).dp)
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = Color(0xFF001832), fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * scale)),
        modifier = Modifier.fillMaxSize().padding((16 * scale).dp),
        decorationBox = { input ->
            if (value.isEmpty()) Text("此处输入文本", color = Color(0x80000000), fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * scale))
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
    val basicColor = Color(0xFF84BFFF)
    val analysisColor = Color(0xFF7DCC85)
    val advancedColor = Color(0xFFE87F77)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        PdfDifficultyLabel("基础记忆", basic, basicColor, Color(0xFF00254F), scale)
        PdfDifficultyLabel("理解分析", analysis, analysisColor, Color(0xFF004E08), scale)
        PdfDifficultyLabel("综合应用", advanced, advancedColor, Color(0xFF670700), scale)
    }
    PdfDifficultyRangeSlider(
        basicBoundary = basicBoundary,
        analysisBoundary = analysisBoundary,
        scale = scale,
        basicColor = Color(0xFF489FFF),
        analysisColor = Color(0xFF489FFF),
        advancedColor = Color(0xFF489FFF),
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
            Text("$percent%", color = contentColor, fontFamily = AppFonts.GoogleSansFlexBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), lineHeight = fixedSp(20 * scale))
            Text(label, color = contentColor, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), lineHeight = fixedSp(20 * scale), maxLines = 1)
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
    val thumbColor = Color(0xFFA9D2FF)
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

@Composable
private fun PdfPreviewScreen(samples: List<CardDraft>, onBack: () -> Unit, onGenerate: () -> Unit) {
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val types = listOf(
        PdfPreviewType("基础记忆", Color(0xFF84BFFF), Color(0xFF183A5E)),
        PdfPreviewType("理解分析", Color(0xFF7DCC85), Color(0xFF05460C)),
        PdfPreviewType("综合应用", Color(0xFFE87F77), Color(0xFF591B16))
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.padding(start = (16 * designScale).dp, top = (162 * designScale).dp, end = (16 * designScale).dp)
                    .fillMaxWidth().height((580 * designScale).dp)
                    .clip(RoundedCornerShape((AppShapeRadius * designScale).dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (152 * designScale).dp),
                    verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
                ) {
                    items(samples.indices.toList()) { index ->
                        PdfPreviewCard(samples[index], types.getOrElse(index) { types.last() }, designScale)
                    }
                }
            }
            Text(
                "点击卡片查看答案。样卡不会进入学习记录",
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = (128 * designScale).dp, end = 16.dp),
                color = PageForegroundColor(),
                fontFamily = AppFonts.MiSansMedium,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(16 * designScale),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            DeckDetailHeader(
                title = "样卡预览",
                designScale = designScale,
                onBack = onBack,
                modifier = Modifier.zIndex(1f)
            )
            BottomContentFade(designScale, Modifier.align(Alignment.BottomCenter))
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = (16 * designScale).dp, end = (16 * designScale).dp, bottom = (40 * designScale).dp)
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
        animationSpec = tween(420, easing = LinearOutSlowInEasing),
        label = "${card.front.take(12)} preview flip"
    )
    val shape = RoundedCornerShape((AppShapeRadius * designScale).dp)
    val density = LocalDensity.current.density
    Box(
        // Four 28dp text lines plus the fixed title, gap and padding require 209dp.
        // Both faces share this constraint so a flip never changes the card's layout.
        modifier = Modifier.fillMaxWidth().height((209 * designScale).dp).clip(shape)
            .clickable(interactionSource = remember(card.front) { MutableInteractionSource() }, indication = null) { flipped = !flipped }
    ) {
        PdfPreviewFace(card, type, answer = false, rotation = rotation, alpha = if (rotation <= 90f) 1f else 0f, shape = shape, density = density, designScale = designScale)
        PdfPreviewFace(card, type, answer = true, rotation = rotation, alpha = if (rotation > 90f) 1f else 0f, shape = shape, density = density, designScale = designScale)
    }
}

@Composable
private fun PdfPreviewFace(card: CardDraft, type: PdfPreviewType, answer: Boolean, rotation: Float, alpha: Float, shape: RoundedCornerShape, density: Float, designScale: Float) {
    Surface(
        color = if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFFF0F8FF) else Color(0xFF233D55),
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
                    MaterialSymbol(if (answer) "wb_incandescent" else "book_5", null, tint = if (answer) MaterialTheme.colorScheme.primary else Color(0xFF489FFF), size = fixedSp(24 * designScale), filled = true)
                    Text(if (answer) "答案" else "问题", color = PageForegroundColor(), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(24 * designScale), lineHeight = fixedSp(28 * designScale))
                }
                Surface(shape = RoundedCornerShape(999.dp), color = type.background) {
                    Text(type.label, modifier = Modifier.padding(horizontal = (16 * designScale).dp, vertical = (8 * designScale).dp), color = type.content, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale), maxLines = 1)
                }
            }
            Text(
                if (answer) card.back else card.front,
                color = PageForegroundColor(),
                fontFamily = AppFonts.MiSansMedium,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(20 * designScale),
                lineHeight = fixedSp(28 * designScale),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PdfGenerationBlockedDialog(block: PdfGenerationBlock, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(block.title, fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal) },
        text = { Text(block.detail, fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal) },
        confirmButton = {
            if (block.canOpenSettings) {
                TextButton(onClick = onOpenSettings) { Text("去设置") }
            } else {
                TextButton(onClick = onDismiss) { Text("知道了") }
            }
        },
        dismissButton = {
            if (block.canOpenSettings) TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun PdfTaskScreen(state: PdfTaskState, onPause: () -> Unit, onResume: () -> Unit, onBack: () -> Unit, onViewDeck: () -> Unit) {
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
    val surface = if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFFF0F8FF) else Color(0xFF233D55)
    Surface(
        shape = RoundedCornerShape((32 * designScale).dp),
        color = surface,
        modifier = modifier.fillMaxWidth().height((265 * designScale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((20 * designScale).dp)
        ) {
            if (paused) {
                MaterialSymbol("pause_circle", null, tint = Color(0xFF489FFF), size = fixedSp(80 * designScale), filled = true)
            } else {
                Md3ExpressiveIndeterminateRing(designScale)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((8 * designScale).dp)) {
                Text(
                    if (paused) "生成已暂停" else "正在生成闪卡",
                    color = PageForegroundColor(),
                    fontFamily = AppFonts.MiSansSemibold,
                    fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(28 * designScale),
                    lineHeight = fixedSp(34 * designScale),
                    letterSpacing = fixedSp(-.68f * designScale)
                )
                if (paused) {
                    Text(
                        "已保留当前生成进度",
                        color = Color(0xFF8C939A),
                        fontFamily = AppFonts.MiSansMedium,
                        fontWeight = FontWeight.Normal,
                        fontSize = fixedSp(20 * designScale),
                        lineHeight = fixedSp(24 * designScale),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy((4 * designScale).dp)
                    ) {
                        listOf("已整理学习内容", "正在生成卡片", "正在检查结果").forEach { label ->
                            Text(
                                label,
                                color = Color(0xFF8C939A),
                                fontFamily = AppFonts.MiSansMedium,
                                fontWeight = FontWeight.Normal,
                                fontSize = fixedSp(16 * designScale),
                                lineHeight = fixedSp(20 * designScale),
                                textAlign = TextAlign.Center
                            )
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
            color = Color(0xFFD1E8FF),
            startAngle = rotation - 220f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = bounds.topLeft,
            size = bounds.size,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFF489FFF),
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
    val surface = if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFFF0F8FF) else Color(0xFF233D55)
    Surface(
        shape = RoundedCornerShape((32 * designScale).dp),
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
            MaterialSymbol("check_circle", null, tint = Color(0xFF43B64B), size = fixedSp(80 * designScale), filled = true)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((8 * designScale).dp)) {
                Text(
                    "卡片组生成完成",
                    color = Color(0xFF1F2832),
                    fontFamily = AppFonts.MiSansSemibold,
                    fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(28 * designScale),
                    lineHeight = fixedSp(34 * designScale),
                    letterSpacing = fixedSp(-.68f * designScale)
                )
                Text(
                    "共生成42张闪卡",
                    color = Color(0xFF8C939A),
                    fontFamily = AppFonts.MiSansMedium,
                    fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(16 * designScale),
                    lineHeight = fixedSp(20 * designScale),
                    letterSpacing = fixedSp(-.68f * designScale)
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp)
            ) {
                TaskTypeChip("基础记忆", "17", Color(0xFF84BFFF), Color(0xFF183A5E), designScale, Modifier.weight(1f))
                TaskTypeChip("理解分析", "9", Color(0xFF7DCC85), Color(0xFF05460C), designScale, Modifier.weight(1f))
                TaskTypeChip("综合应用", "16", Color(0xFFE87F77), Color(0xFF591B16), designScale, Modifier.weight(1f))
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
                Text(count, color = content, fontFamily = AppFonts.GoogleSansFlexBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale), maxLines = 1)
                Text("cards", color = content, fontFamily = AppFonts.GoogleSansFlexBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale), maxLines = 1)
            }
            Text(label, color = content, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale), maxLines = 1)
        }
    }
}
@Composable
private fun SettingsScreen(viewModel: AppViewModel, nav: ScreenNavigator) {
    val theme by viewModel.darkTheme.collectAsState()
    val remoteApiStatus by viewModel.apiKeyStatus.collectAsState()
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    var showThemePicker by remember { mutableStateOf(false) }
    var showAiKeyDialog by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var savingApiKey by remember { mutableStateOf(false) }
    LaunchedEffect(showAiKeyDialog) { if (showAiKeyDialog) viewModel.refreshApiKeyStatus() }
    LaunchedEffect(remoteApiStatus) { if (remoteApiStatus != null) savingApiKey = false }
    val aiStatus = if (savingApiKey) "验证中" else when (remoteApiStatus?.status?.uppercase()) {
        "AVAILABLE" -> "可用"
        "INVALID" -> "无效"
        "INSUFFICIENT_BALANCE" -> "余额不足"
        else -> "未设置"
    }
    val openUnbuilt: (String) -> Unit = { title ->
        nav.navigate(AppRoute.SettingsUnbuilt(title))
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF0F8FF)) {
        Box(Modifier.fillMaxSize()) {
            // Figma 66:4804: this is one deliberately tight menu, with 4dp inside
            // a group and a 20dp break between groups. It scrolls below the fixed header.
            Box(
                Modifier.fillMaxSize()
                    .padding(
                        start = (16 * designScale).dp,
                        top = (148 * designScale).dp,
                        end = (16 * designScale).dp
                    )
                    .clip(RoundedCornerShape(AppShapeRadius.dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (128 * designScale).dp),
                    verticalArrangement = Arrangement.spacedBy((20 * designScale).dp)
                ) {
                    item {
                        SettingsMenuGroup(designScale) {
                            SettingsMenuRow("头像与昵称", "badge", Color(0xFF69C56B), Color(0xFF095222), designScale) {
                                nav.navigate(AppRoute.SettingsIdentity)
                            }
                            SettingsMenuRow("学习档案", "article_person", Color(0xFF69C56B), Color(0xFF095222), designScale) {
                                openUnbuilt("学习档案")
                            }
                        }
                    }
                    item {
                        SettingsMenuGroup(designScale) {
                            SettingsMenuRow("主题与外观", "palette", Color(0xFF64AEFF), Color(0xFF064B8C), designScale) {
                                showThemePicker = true
                            }
                            SettingsMenuRow("深色模式", "routine", Color(0xFF64AEFF), Color(0xFF064B8C), designScale) {
                                openUnbuilt("深色模式")
                            }
                        }
                    }
                    item {
                        SettingsMenuGroup(designScale) {
                            SettingsMenuRow("API设置", "experiment", Color(0xFFD9BAFD), Color(0xFF5422A0), designScale) {
                                showAiKeyDialog = true
                            }
                            SettingsMenuRow("（后续ai相关的设置）", "article_person", Color(0xFFD9BAFD), Color(0xFF5422A0), designScale) {
                                openUnbuilt("AI 设置")
                            }
                        }
                    }
                    item {
                        SettingsMenuGroup(designScale) {
                            SettingsMenuRow("数据隐私与安全条款", "admin_panel_settings", Color(0xFFFFB683), Color(0xFF74350E), designScale) {
                                openUnbuilt("数据隐私与安全条款")
                            }
                            SettingsMenuRow("用户协议", "person_raised_hand", Color(0xFFFFB683), Color(0xFF74350E), designScale) {
                                openUnbuilt("用户协议")
                            }
                            SettingsMenuRow("关于应用", "info", Color(0xFFFFB683), Color(0xFF74350E), designScale) {
                                openUnbuilt("关于应用")
                            }
                        }
                    }
                }
            }
            SettingsPageHeader(
                title = "设置",
                designScale = designScale,
                onBack = nav::popBackStack,
                modifier = Modifier
                    .padding(
                        start = (16 * designScale).dp,
                        top = (64 * designScale).dp,
                        end = (16 * designScale).dp
                    )
                    .zIndex(1f)
            )
            BottomContentFade(designScale, Modifier.align(Alignment.BottomCenter))
        }
    }

    if (showThemePicker) {
        ThemeModeDialog(
            selectedTheme = theme,
            onSelect = {
                viewModel.setDarkTheme(it)
                showThemePicker = false
            },
            onDismiss = { showThemePicker = false }
        )
    }
    if (showAiKeyDialog) {
        AiServiceDialog(
            currentKey = apiKey,
            status = aiStatus,
            onSave = { key ->
                apiKey = key
                savingApiKey = true
                viewModel.saveApiKey(key) { savingApiKey = false }
            },
            onDismiss = { showAiKeyDialog = false }
        )
    }
}

@Composable
private fun SettingsIdentityScreen(nav: ScreenNavigator) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF0F8FF)) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .padding(start = (16 * scale).dp, top = (148 * scale).dp, end = (16 * scale).dp)
                    .clip(RoundedCornerShape((32 * scale).dp))
            ) {
                SettingsMenuGroup(scale) {
                    SettingsIdentityRow("头像", "account_circle", avatar = true, scale = scale)
                    SettingsIdentityRow("昵称", "id_card", value = "酱油四", scale = scale)
                }
            }
            SettingsPageHeader(
                title = "头像与昵称",
                designScale = scale,
                onBack = nav::popBackStack,
                modifier = Modifier
                    .padding(start = (16 * scale).dp, top = (64 * scale).dp, end = (16 * scale).dp)
                    .zIndex(1f)
            )
            BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun SettingsUnbuiltScreen(title: String, nav: ScreenNavigator) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF0F8FF)) {
        Box(Modifier.fillMaxSize()) {
            Text(
                "未构建",
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF8C939A),
                fontFamily = AppFonts.MiSansMedium,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(16 * scale),
                lineHeight = fixedSp(20 * scale)
            )
            SettingsPageHeader(
                title = title,
                designScale = scale,
                onBack = nav::popBackStack,
                modifier = Modifier
                    .padding(start = (16 * scale).dp, top = (64 * scale).dp, end = (16 * scale).dp)
                    .zIndex(1f)
            )
        }
    }
}

@Composable
private fun AiServiceDialog(currentKey: String, status: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by remember(currentKey) { mutableStateOf(currentKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DeepSeek API", fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(key, { key = it }, label = { Text("DeepSeek API Key") }, placeholder = { Text("••••••••••••••") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    when (status) {
                        "验证中" -> "正在验证…"
                        "可用" -> "连接可用。"
                        "无效" -> "DeepSeek API Key 已失效。"
                        "余额不足" -> "DeepSeek API 余额不足。"
                        else -> "保存后将由服务端验证。"
                    },
                    color = if (status == "无效" || status == "余额不足") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(14f)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key) }) { Text("验证并保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun SettingsPageHeader(
    title: String,
    designScale: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().height((56 * designScale).dp)) {
        RoundIconButton(
            symbol = "arrow_back",
            description = "返回",
            color = HeaderControlBackgroundColor(),
            onClick = onBack,
            size = (56 * designScale).dp,
            tint = HeaderControlIconColor()
        )
        MixedLanguageText(
            text = title,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = (60 * designScale).dp),
            color = PageForegroundColor(),
            chineseFont = AppFonts.MiSansSemibold,
            latinFont = AppFonts.GoogleSansFlexBold,
            fontSize = fixedSp(24 * designScale),
            lineHeight = fixedSp(32 * designScale),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Figma 66:4804: 76dp menu rows, 52dp icon discs, 4dp row rhythm. */
@Composable
private fun SettingsMenuGroup(designScale: Float, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy((4 * designScale).dp), content = content)
}

@Composable
private fun SettingsMenuRow(
    title: String,
    symbol: String,
    iconBackground: Color,
    iconTint: Color,
    designScale: Float,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape((24 * designScale).dp),
        modifier = Modifier.fillMaxWidth().height((76 * designScale).dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding((12 * designScale).dp),
            horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = iconBackground,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.size((52 * designScale).dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol(symbol, null, tint = iconTint, size = fixedSp(24 * designScale), filled = true)
                }
            }
            MixedLanguageText(
                text = title,
                modifier = Modifier.weight(1f),
                color = Color(0xFF242436),
                chineseFont = AppFonts.MiSansSemibold,
                latinFont = AppFonts.GoogleSansFlexSemibold,
                fontSize = fixedSp(20 * designScale),
                lineHeight = fixedSp(24 * designScale),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier.size((52 * designScale).dp),
                contentAlignment = Alignment.Center
            ) {
                MaterialSymbol("arrow_forward", "进入$title", tint = Color(0xFF1F2832), size = fixedSp(24 * designScale))
            }
        }
    }
}

@Composable
private fun SettingsIdentityRow(
    title: String,
    symbol: String,
    avatar: Boolean = false,
    value: String? = null,
    scale: Float
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape((32 * scale).dp),
        modifier = Modifier.fillMaxWidth().height((76 * scale).dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding((12 * scale).dp),
            horizontalArrangement = Arrangement.spacedBy((16 * scale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF69C56B),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.size((52 * scale).dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol(symbol, null, tint = Color(0xFF095222), size = fixedSp(24 * scale), filled = true)
                }
            }
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = Color(0xFF242436),
                fontFamily = AppFonts.MiSansSemibold,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp((if (avatar) 18 else 20) * scale),
                lineHeight = fixedSp(24 * scale),
                maxLines = 1
            )
            if (avatar) {
                Image(
                    painter = painterResource(R.drawable.avatar_settings_figma),
                    contentDescription = "头像",
                    modifier = Modifier.size((48 * scale).dp).clip(RoundedCornerShape(999.dp))
                )
            } else if (value != null) {
                Text(
                    value,
                    color = Color(0xFF242436),
                    fontFamily = AppFonts.MiSansSemibold,
                    fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(20 * scale),
                    lineHeight = fixedSp(24 * scale),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    designScale: Float,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = (8 * designScale).dp),
            color = PageForegroundColor(),
            fontFamily = AppFonts.MiSansSemibold,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(20 * designScale),
            lineHeight = fixedSp(27 * designScale)
        )
        Column(verticalArrangement = Arrangement.spacedBy((8 * designScale).dp)) { content() }
    }
}

@Composable
private fun SettingsListItem(
    title: String,
    value: String,
    designScale: Float,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = Modifier.fillMaxWidth().height((76 * designScale).dp)
    val rowShape = RoundedCornerShape(AppShapeRadius.dp)
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = (24 * designScale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AppFonts.MiSansSemibold,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(17 * designScale),
                lineHeight = fixedSp(24 * designScale),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width((12 * designScale).dp))
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = AppFonts.MiSansMedium,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(14 * designScale),
                lineHeight = fixedSp(20 * designScale),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onClick != null) {
                Spacer(Modifier.width((6 * designScale).dp))
                MaterialSymbol(
                    name = "chevron_right",
                    description = "选择$title",
                    tint = HeaderControlIconColor(),
                    size = fixedSp(22 * designScale)
                )
            }
        }
    }
    if (onClick == null) {
        Surface(
            modifier = rowModifier,
            shape = rowShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            content = content
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = rowModifier,
            shape = rowShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            content = content
        )
    }
}

@Composable
private fun ThemeModeDialog(
    selectedTheme: Boolean?,
    onSelect: (Boolean?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "主题外观",
                color = PageForegroundColor(),
                fontFamily = AppFonts.MiSansSemibold,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(20f)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeChoice("跟随系统", selectedTheme == null) { onSelect(null) }
                ThemeChoice("浅色", selectedTheme == false) { onSelect(false) }
                ThemeChoice("深色", selectedTheme == true) { onSelect(true) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal)
            }
        }
    )
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = AppFonts.MiSansMedium,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(16f)
            )
            if (selected) {
                MaterialSymbol("check", "当前选择", tint = Color(0xFF489FFF), size = fixedSp(20f), filled = true)
            }
        }
    }
}

@Composable
private fun Attribution() {
    val uriHandler = LocalUriHandler.current
    Text(
        "预置题库根据《深入理解 AI Agent：设计原理与工程实践》整理，内容为原创问答重述；原书采用 Apache-2.0 许可。字体使用 MiSans VF。",
        modifier = Modifier.clickable { uriHandler.openUri("https://github.com/bojieli/ai-agent-book") },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable private fun LoadingScreen() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在加载卡组…") }
