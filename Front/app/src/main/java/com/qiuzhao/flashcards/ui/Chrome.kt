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
import androidx.compose.runtime.saveable.rememberSaveable
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
fun FlashcardsApp(viewModel: AppViewModel) {
    val navigationState = rememberAppNavigationState()
    val navigator = remember { AppNavigator(navigationState) }
    val activity = LocalContext.current as? Activity
    val decks by viewModel.decks.collectAsState()
    val dueCount by viewModel.dueCount.collectAsState()
    val dashboard by viewModel.dashboard.collectAsState()
    val weeklyActivity by viewModel.weeklyActivity.collectAsState()
    val accountBootstrap by viewModel.accountBootstrap.collectAsState()
    val account = accountBootstrap.account
    var studySearchQuery by remember { mutableStateOf("") }
    var initialAuthNavigationHandled by rememberSaveable { mutableStateOf(false) }
    val shouldOpenFirstLogin = accountBootstrap.loaded && account == null && !initialAuthNavigationHandled
    LaunchedEffect(shouldOpenFirstLogin) {
        if (shouldOpenFirstLogin) {
            navigator.navigate(AppRoute.FirstLogin)
            initialAuthNavigationHandled = true
        }
    }
    // Do not briefly render Home while the local account record is loading or the
    // first-login destination is being placed on the stack.
    if (!accountBootstrap.loaded || shouldOpenFirstLogin) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF0F8FF)))
        return
    }
    val selectedRootTab = when (navigationState.selectedTopLevel) {
        AppRoute.Home -> RootTab.HOME
        AppRoute.Library -> RootTab.STUDY
        AppRoute.Data -> RootTab.DATA
        else -> error("Top-level navigation state must be a root route")
    }

    val typedEntryProvider = entryProvider {
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
        entry<AppRoute.FirstLogin> {
            LoginScreen(viewModel, navigator, showBack = false, firstLaunch = true)
        }
        entry<AppRoute.Login> { LoginScreen(viewModel, navigator, showBack = true) }
        entry<AppRoute.Register> { RegisterScreen(viewModel, navigator) }
        entry<AppRoute.Settings> { SettingsScreen(viewModel, navigator) }
        entry<AppRoute.SettingsIdentity> { SettingsIdentityScreen(navigator) }
        entry<AppRoute.SettingsUnbuilt> { route -> SettingsUnbuiltScreen(route.title, navigator) }
    }
    val entryProvider: (NavKey) -> NavEntry<NavKey> = { key ->
        @Suppress("UNCHECKED_CAST")
        (typedEntryProvider(key as AppRoute) as NavEntry<NavKey>)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavDisplay(
            entries = navigationState.decoratedEntries(entryProvider),
            onBack = {
                // The first-login gate must never reveal Home through Back. Put
                // the task in the background instead; returning to it restores
                // the same gate until the user enters directly or logs in.
                if (navigationState.currentRoute == AppRoute.FirstLogin) {
                    activity?.moveTaskToBack(true)
                } else if (navigator.isAtExitRoot) {
                    activity?.finish()
                } else {
                    navigator.goBack()
                }
            },
            transitionSpec = { fadeIn(AppMotion.enter()) togetherWith fadeOut(AppMotion.exit()) },
            popTransitionSpec = { fadeIn(AppMotion.enter()) togetherWith fadeOut(AppMotion.exit()) },
            predictivePopTransitionSpec = { fadeIn(AppMotion.enter()) togetherWith fadeOut(AppMotion.exit()) },
            modifier = Modifier.imePadding()
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
                    onSettings = { navigator.navigate(AppRoute.Settings) },
                    account = account,
                    onAvatar = { navigator.navigate(AppRoute.Login) }
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
internal fun AppBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    // The legacy Material app bar is deliberately replaced so secondary screens
    // share the 209:2733 safe-area position and control geometry.
    ScreenTopInformationBar(title = title, subtitle = null, onBack = onBack ?: {}, modifier = Modifier.zIndex(1f))
}

/** Shared, always-mounted header for the three root destinations. */
@Composable
private fun RootPersistentHeader(
    selected: RootTab,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSettings: () -> Unit,
    account: LocalAccount?,
    onAvatar: () -> Unit
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
            ImageAvatar((56 * scale).dp, account, onAvatar)
        }
    } else {
        ScreenTopInformationBar(
            title = null,
            subtitle = null,
            onBack = null,
            onSettings = onSettings,
            account = account,
            onAvatar = onAvatar,
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
internal fun ScreenTopInformationBar(
    title: String?,
    subtitle: String?,
    onBack: (() -> Unit)?,
    onSettings: (() -> Unit)? = null,
    account: LocalAccount? = null,
    onAvatar: (() -> Unit)? = null,
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
        account = account,
        onAvatar = onAvatar,
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
    account: LocalAccount?,
    onAvatar: (() -> Unit)?,
    backContainer: Color?,
    titleColor: Color?,
    modifier: Modifier = Modifier
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Box(modifier.height((56 * scale).dp)) {
        if (onBack == null) {
            SettingsHeaderButton(onSettings ?: {}, (56 * scale).dp)
            Box(Modifier.align(Alignment.CenterEnd)) { ImageAvatar((56 * scale).dp, account, onAvatar ?: {}) }
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
                AppText(
                    text = title.orEmpty(), role = AppTextRole.PageTitle,
                    color = titleColor ?: PageForegroundColor(), designScale = scale,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                subtitle?.let {
                    AppText(it, AppTextRole.PageTitle, color = titleColor ?: PageForegroundColor(), designScale = scale, maxLines = 1)
                }
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
                    fontFamily = if (isSelected) AppFonts.MiSansHeavy else AppFonts.MiSansSemibold,
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

/** Figma 15:3032: the shared main-screen settings control. */
@Composable
private fun SettingsHeaderButton(onClick: () -> Unit, size: androidx.compose.ui.unit.Dp) {
    RoundIconButton(
        symbol = "settings", description = "设置", color = HeaderControlBackgroundColor(),
        onClick = onClick, size = size, tint = HeaderControlIconColor(), filled = false
    )
}

@Composable
private fun ImageAvatar(
    size: androidx.compose.ui.unit.Dp = 56.dp,
    account: LocalAccount? = null,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(999.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF99C9FF), Color(0xFF489FFF))
                )
            )
            .padding((4f / 56f * size.value).dp)
            .clickable(onClick = onClick)
    ) {
        if (account == null) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol("login", "登录", tint = Color(0xFF489FFF), size = fixedSp(24f / 56f * size.value), filled = true)
                }
            }
        } else {
            Image(
                painter = painterResource(R.drawable.avatar_profile_figma),
                contentDescription = "${account.nickname}的头像",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(999.dp))
            )
        }
    }
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
            textStyle = appInputTextStyle(AppTextRole.CardTitle, designScale, PageForegroundColor()).copy(textAlign = TextAlign.Center),
            visualTransformation = rememberBilingualInputTransformation(AppTextRole.CardTitle, designScale),
            modifier = Modifier.fillMaxSize().padding(horizontal = (16 * designScale).dp),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (query.isEmpty()) {
                        AppText("搜索", AppTextRole.CardTitle, color = Color(0xFF8C97A3), designScale = designScale)
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable private fun LoadingScreen() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在加载卡组…") }
