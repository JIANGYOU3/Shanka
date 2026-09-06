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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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
fun FlashcardsApp(viewModel: AppViewModel) {
    val navigationState = rememberAppNavigationState()
    val navigator = remember { AppNavigator(navigationState) }
    val activity = LocalContext.current as? Activity
    val decks by viewModel.decks.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val dueCount by viewModel.dueCount.collectAsState()
    val dashboard by viewModel.dashboard.collectAsState()
    val weeklyActivity by viewModel.weeklyActivity.collectAsState()
    val todayPlan by viewModel.todayPlan.collectAsState()
    val projectProgress by viewModel.projectProgress.collectAsState()
    val accountBootstrap by viewModel.accountBootstrap.collectAsState()
    val account = accountBootstrap.account
    var projectSearchQuery by remember { mutableStateOf("") }
    // Do not briefly render Home while the local account record is loading.
    if (!accountBootstrap.loaded) {
        Box(Modifier.fillMaxSize().background(AppColors.BaseBackground))
        return
    }
    val selectedRootTab = when (navigationState.selectedTopLevel) {
        AppRoute.Home -> RootTab.HOME
        AppRoute.Project -> RootTab.PROJECT
        AppRoute.Data -> RootTab.DATA
        else -> error("Top-level navigation state must be a root route")
    }

    val typedEntryProvider = entryProvider {
        entry<AppRoute.Home> { HomeScreen(decks, projects, account?.nickname, todayPlan, dashboard?.streakDays, navigator) }
        entry<AppRoute.StudyGoal> { StudyGoalScreen(viewModel, navigator) }
        entry<AppRoute.Project> { ProjectScreen(projects, decks, projectSearchQuery, viewModel, navigator) }
        entry<AppRoute.ProjectCreate> { ProjectCreateScreen(viewModel, navigator) }
        entry<AppRoute.ProjectEdit> { route ->
            val project = projects.firstOrNull { it.id == route.id }
            if (project == null) LoadingScreen() else ProjectCreateScreen(
                viewModel = viewModel,
                nav = navigator,
                editingProject = project
            )
        }
        entry<AppRoute.ProjectTextEditor> { route ->
            ProjectTextEditorScreen(route, viewModel, navigator)
        }
        entry<AppRoute.ProjectDetail> { route ->
            LaunchedEffect(route.id) {
                viewModel.refreshProjectTasks(route.id)
                viewModel.refreshProjectProgress(route.id)
            }
            val project = projects.firstOrNull { it.id == route.id }
            if (project == null) LoadingScreen() else {
                // Live task statuses from the Room projection (V25-D-34): a generating task
                // advances to its terminal state here without any screen-driven polling.
                val tasks by viewModel.projectTasks(route.id)
                    .collectAsState(initial = emptyList())
                ProjectDetailScreen(
                    project,
                    decks.filter { it.projectId == project.id },
                    navigator,
                    onDeleteDeck = { deckId, onResult ->
                        viewModel.deleteDeck(
                            deckId,
                            onSuccess = { onResult(true) },
                            onFailure = { onResult(false) },
                        )
                    },
                    onDeleteProject = { retainDecks, onResult ->
                        viewModel.deleteProject(project.id, retainDecks, onResult)
                    },
                    tasks = tasks,
                    progress = projectProgress[route.id],
                // Contract 3.16: status EMPTY means no materials yet — the guide replaces the
                // generation surface until the first PDF/text material lands.
                isEmptyProject = project.status == "EMPTY",
                onAddPdfMaterial = {
                    viewModel.beginMaterialImport()
                    navigator.navigate(AppRoute.MaterialImport(projectId = project.id))
                },
                onAddTextMaterial = {
                    navigator.navigate(
                        AppRoute.ProjectTextEditor(
                            materialId = null,
                            themeKey = project.themeKey,
                            projectId = project.id,
                            editorTitle = "导入文本",
                        )
                    )
                },
                )
            }
        }
        entry<AppRoute.DeckGeneration> { route ->
            val project = projects.firstOrNull { it.id == route.projectId }
            if (project == null) LoadingScreen() else DeckGenerationScreen(project, navigator, viewModel)
        }
        entry<AppRoute.SmartCardChapter> { route ->
            val project = projects.firstOrNull { it.id == route.projectId }
            if (project == null) LoadingScreen() else SmartCardChapterScreen(project, navigator, viewModel)
        }
        entry<AppRoute.SmartCardPreview> { route ->
            val project = projects.firstOrNull { it.id == route.projectId }
            if (project == null) LoadingScreen() else SmartCardPreviewScreen(project, navigator, viewModel)
        }
        entry<AppRoute.SmartCardGenerating> { route ->
            val project = projects.firstOrNull { it.id == route.projectId }
            if (project == null) LoadingScreen() else SmartCardGeneratingScreen(project, navigator, viewModel)
        }
        entry<AppRoute.MaterialManagement> { MaterialManagementScreen(project = null, viewModel, navigator) }
        entry<AppRoute.ProjectMaterialManagement> { route ->
            val project = projects.firstOrNull { it.id == route.projectId }
            if (project == null) LoadingScreen() else MaterialManagementScreen(project, viewModel, navigator)
        }
        entry<AppRoute.MaterialImport> { route -> MaterialImportScreen(route, viewModel, navigator) }
        entry<AppRoute.ProjectMaterialPicker> { route -> ProjectMaterialPickerScreen(route, viewModel, navigator) }
        entry<AppRoute.Data> { DataScreen(dueCount, dashboard, weeklyActivity, navigator) }
        entry<AppRoute.Deck> { route ->
            val deck = decks.firstOrNull { it.id == route.id }
            if (deck == null) LoadingScreen() else DeckScreen(deck, viewModel, navigator)
        }
        entry<AppRoute.Study> { route ->
            StudyScreen(viewModel, navigator, route.deckId, route.reviewMode)
        }
        entry<AppRoute.StudyToday> {
            StudyScreen(viewModel, navigator, deckId = "", reviewMode = true, todayMode = true)
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
        entry<AppRoute.Login> { LoginScreen(viewModel, navigator, showBack = true) }
        entry<AppRoute.Register> { RegisterScreen(viewModel, navigator) }
        entry<AppRoute.Settings> { SettingsScreen(viewModel, navigator) }
    }
    val entryProvider: (NavKey) -> NavEntry<NavKey> = { key ->
        @Suppress("UNCHECKED_CAST")
        (typedEntryProvider(key as AppRoute) as NavEntry<NavKey>)
    }

    // 毛玻璃 backdrop blur 的采样源：所有路由内容都在这棵子树里绘制。
    val bottomBarHazeState = remember { HazeState() }
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        Box(
            Modifier.fillMaxSize().background(AppColors.BaseBackground)
                .hazeSource(bottomBarHazeState)
        ) {
            NavDisplay(
                entries = navigationState.decoratedEntries(entryProvider),
                onBack = {
                    if (navigator.isAtExitRoot) {
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
        }
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
                    onSettings = { navigator.navigate(AppRoute.Settings) },
                    account = account,
                    projectSearchQuery = projectSearchQuery,
                    onProjectSearchChange = { projectSearchQuery = it }
                )
                BottomNavBar(
                    selected = selectedRootTab,
                    hazeState = bottomBarHazeState,
                    onHome = { navigator.navigate(AppRoute.Home) },
                    onProject = { navigator.navigate(AppRoute.Project) },
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
    onSettings: () -> Unit,
    account: LocalAccount?,
    projectSearchQuery: String,
    onProjectSearchChange: (String) -> Unit
) {
    if (selected == RootTab.PROJECT) {
        val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = (16 * scale).dp, top = (16 * scale).dp, end = (16 * scale).dp).height((56 * scale).dp),
            horizontalArrangement = Arrangement.spacedBy((16 * scale).dp), verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsHeaderButton(onSettings, (56 * scale).dp)
            StudySearchField(projectSearchQuery, onProjectSearchChange, Modifier.weight(1f), scale)
            ImageAvatar((56 * scale).dp, account)
        }
    } else {
        ScreenTopInformationBar(
            title = null, subtitle = null, onBack = null, onSettings = onSettings,
            account = account, modifier = Modifier.zIndex(2f)
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
    onTrailingAction: (() -> Unit)? = null,
    trailingActionSymbol: String = "edit",
    trailingActionDescription: String = "编辑",
    trailingActionContainer: Color? = null,
    onSecondaryTrailingAction: (() -> Unit)? = null,
    secondaryTrailingActionSymbol: String = "delete",
    secondaryTrailingActionDescription: String = "删除",
    secondaryTrailingActionContainer: Color = AppColors.WarningStrong,
    secondaryTrailingActionColor: Color = AppColors.TextIconLight,
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
        onTrailingAction = onTrailingAction,
        trailingActionSymbol = trailingActionSymbol,
        trailingActionDescription = trailingActionDescription,
        trailingActionContainer = trailingActionContainer,
        onSecondaryTrailingAction = onSecondaryTrailingAction,
        secondaryTrailingActionSymbol = secondaryTrailingActionSymbol,
        secondaryTrailingActionDescription = secondaryTrailingActionDescription,
        secondaryTrailingActionContainer = secondaryTrailingActionContainer,
        secondaryTrailingActionColor = secondaryTrailingActionColor,
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
    onTrailingAction: (() -> Unit)?,
    trailingActionSymbol: String,
    trailingActionDescription: String,
    trailingActionContainer: Color?,
    onSecondaryTrailingAction: (() -> Unit)?,
    secondaryTrailingActionSymbol: String,
    secondaryTrailingActionDescription: String,
    secondaryTrailingActionContainer: Color,
    secondaryTrailingActionColor: Color,
    modifier: Modifier = Modifier
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Box(modifier.height((56 * scale).dp)) {
        if (onBack == null) {
            SettingsHeaderButton(onSettings ?: {}, (56 * scale).dp)
            Box(Modifier.align(Alignment.CenterEnd)) { ImageAvatar((56 * scale).dp, account, onAvatar) }
        } else {
            Surface(
                onClick = onBack,
                color = backContainer ?: SecondaryHeaderActionBackgroundColor(),
                contentColor = titleColor ?: HeaderControlIconColor(),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.size((56 * scale).dp).align(Alignment.CenterStart)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol("arrow_back", "返回", tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                }
            }
            Row(
                modifier = Modifier.align(Alignment.Center).padding(
                    start = (60 * scale).dp,
                    end = (if (onSecondaryTrailingAction == null) 60 * scale else 124 * scale).dp
                ),
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
            onTrailingAction?.let { action ->
                Surface(
                    onClick = action,
                    color = trailingActionContainer ?: backContainer ?: SecondaryHeaderActionBackgroundColor(),
                    contentColor = titleColor ?: HeaderControlIconColor(),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.size((56 * scale).dp).align(Alignment.CenterEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol(
                            trailingActionSymbol,
                            trailingActionDescription,
                            tint = LocalContentColor.current,
                            size = fixedSp(24 * scale),
                            filled = true
                        )
                    }
                }
            }
            onSecondaryTrailingAction?.let { action ->
                Surface(
                    onClick = action,
                    color = secondaryTrailingActionContainer,
                    contentColor = secondaryTrailingActionColor,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .padding(end = (64 * scale).dp)
                        .size((56 * scale).dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol(
                            secondaryTrailingActionSymbol,
                            secondaryTrailingActionDescription,
                            tint = LocalContentColor.current,
                            size = fixedSp(24 * scale),
                            filled = true
                        )
                    }
                }
            }
        }
    }
}

private enum class RootTab { HOME, PROJECT, DATA }

@Composable
private fun BottomNavBar(
    selected: RootTab,
    hazeState: HazeState,
    onHome: () -> Unit,
    onProject: () -> Unit,
    onData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = when (selected) {
        RootTab.HOME -> 0
        RootTab.PROJECT -> 1
        RootTab.DATA -> 2
    }
    AppBottomNavigation(
        selectedIndex = selectedIndex,
        items = listOf(
            AppBottomNavigationItem("主页", "home", onHome),
            AppBottomNavigationItem("项目", "playing_cards", onProject),
            AppBottomNavigationItem("数据", "query_stats", onData)
        ),
        hazeState = hazeState,
        modifier = modifier
    )
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
    onClick: (() -> Unit)? = null,
) {
    val avatarModifier = Modifier.size(size).clip(RoundedCornerShape(999.dp))
        .background(
            Brush.linearGradient(
                colors = listOf(AppColors.Blue.primarySecondary, AppColors.Blue.primary)
            )
        )
        .padding((4f / 56f * size.value).dp)
    // The avatar is interactive only when the caller wires a click handler: the
    // root header opens the login dialog, secondary headers keep it display-only.
    Box(
        modifier = if (onClick == null) avatarModifier else avatarModifier.clickable(onClick = onClick)
    ) {
        if (account == null) {
            Surface(
                color = AppColors.Card,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol("login", "登录", tint = AppColors.Blue.primary, size = fixedSp(24f / 56f * size.value), filled = true)
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
                        AppText("搜索", AppTextRole.CardTitle, color = AppColors.TextIconDark.copy(alpha = .55f), designScale = designScale)
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable private fun LoadingScreen() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppText("正在加载卡组…", AppTextRole.Body) }
