package com.qiuzhao.flashcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Figma 835:5466 "生成设置". A project-owned deck generator. The whole page
 * follows the owning project's colour family: section cards use the family
 * Background, inputs/controls lift to Surface and the fixed generate button
 * uses the family Primary. The existing file/text material cards are reused
 * in their selectable variants so a user can pick which materials feed the
 * generated sample cards.
 */
@Composable
internal fun DeckGenerationScreen(
    project: ProjectSummary,
    nav: ScreenNavigator,
    viewModel: AppViewModel
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = deckTheme(project)
    val projectMats by viewModel.projectMaterials.collectAsState()
    val materials = projectMats[project.id].orEmpty()
    val fileItems = materials.filter { it.type == ProjectDraftMaterialType.FILE }
    val textItems = materials.filter { it.type == ProjectDraftMaterialType.TEXT }

    var name by remember { mutableStateOf("") }
    var basicBoundary by remember { mutableFloatStateOf(40f) }
    var analysisBoundary by remember { mutableFloatStateOf(80f) }
    var coverage by remember { mutableStateOf("均匀") }
    var requirement by remember { mutableStateOf("") }
    var selectedFileIds by remember { mutableStateOf(setOf<String>()) }
    var selectedTextIds by remember { mutableStateOf(setOf<String>()) }
    // Same contract as the material-management page: the bound PDF has no standalone delete,
    // so its swipe-delete requests the project deletion flow with the advisory impact line.
    var showProjectDeletion by remember { mutableStateOf(false) }
    var projectDeletionInFlight by remember { mutableStateOf(false) }
    var deletionPreflight by remember { mutableStateOf<com.qiuzhao.flashcards.domain.v25.V25DeletionPreflight?>(null) }
    LaunchedEffect(showProjectDeletion) {
        deletionPreflight = null
        if (!showProjectDeletion) return@LaunchedEffect
        viewModel.refreshProjectDeletionPreflight(project.id, retainDecks = true, allowCancel = false) { result ->
            deletionPreflight = result
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar(
            title = "添加卡片组", subtitle = null, onBack = nav::goBack,
            backContainer = theme.cardPanel, titleColor = theme.text
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
                .padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp)
                .clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)),
            contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            item {
                DeckGenerationSectionCard("卡组名称", "stylus_note", theme, scale) {
                    DeckGenerationNameField(name, { name = it }, theme, scale)
                }
            }
            item {
                DeckGenerationSectionCard("卡片难度", "instant_mix", theme, scale) {
                    DeckGenerationDifficulty(basicBoundary, analysisBoundary, theme, scale) { basic, analysis ->
                        basicBoundary = basic
                        analysisBoundary = analysis
                    }
                }
            }
            item {
                DeckGenerationSectionCard("生成数量", "stacks", theme, scale) {
                    DeckGenerationCount(coverage, theme, scale) { coverage = it }
                }
            }
            item {
                DeckGenerationSectionCard("自定义要求", "wand_stars", theme, scale) {
                    DeckGenerationRequirement(requirement, { requirement = it }, theme, scale)
                }
            }
            item {
                DeckGenerationMaterialSection(
                    title = "添加文件资料", icon = "files", materials = fileItems, theme = theme, scale = scale,
                    selectedIds = selectedFileIds,
                    onToggle = { id -> selectedFileIds = if (id in selectedFileIds) selectedFileIds - id else selectedFileIds + id },
                    onEditText = {},
                    onDelete = { id ->
                        val material = fileItems.firstOrNull { it.id == id }
                        if (material?.projectId == null) viewModel.deleteProjectDraftMaterial(id)
                        else showProjectDeletion = true
                    }
                )
            }
            item {
                DeckGenerationMaterialSection(
                    title = "添加文本资料", icon = "description", materials = textItems, theme = theme, scale = scale,
                    selectedIds = selectedTextIds,
                    onToggle = { id -> selectedTextIds = if (id in selectedTextIds) selectedTextIds - id else selectedTextIds + id },
                    onEditText = { material -> nav.navigate(AppRoute.ProjectTextEditor(material.id, theme.key, project.id, editorTitle = "编辑文本资料")) },
                    onDelete = { id ->
                        val material = textItems.firstOrNull { it.id == id }
                        if (material?.projectId == null) viewModel.deleteProjectDraftMaterial(id)
                        else showProjectDeletion = true
                    }
                )
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = AppColors.BaseBackground)
        Surface(
            onClick = {
                viewModel.prepareProjectGeneration(
                    projectId = project.id,
                    deckName = name,
                    config = PdfGenerationConfig(
                        quantity = when (coverage) {
                            "精简" -> "COMPACT"
                            "充分" -> "EXTENSIVE"
                            else -> "BALANCED"
                        },
                        basic = basicBoundary / 100f,
                        understanding = (analysisBoundary - basicBoundary) / 100f,
                        application = (100f - analysisBoundary) / 100f,
                        requirement = requirement,
                    ),
                    onReady = { ready -> if (ready) nav.navigate(AppRoute.SmartCardChapter(project.id)) },
                )
            },
            color = theme.primary, contentColor = theme.onPrimary,
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                .fillMaxWidth().height((60 * scale).dp).zIndex(1f)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                AppText("下一步", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
            }
        }
        if (showProjectDeletion) {
            val impact = deletionPreflight?.impact
            ProjectDeletionDialog(
                projectName = project.name,
                theme = theme,
                deleting = projectDeletionInFlight,
                impactText = impact?.let {
                    "影响范围：${it.deckCount} 个牌组、${it.cardCount} 张卡片、${it.taskCount} 条制卡任务记录"
                },
                onConfirm = { retainDecks ->
                    if (projectDeletionInFlight) return@ProjectDeletionDialog
                    projectDeletionInFlight = true
                    viewModel.deleteProject(project.id, retainDecks) { succeeded ->
                        projectDeletionInFlight = false
                        showProjectDeletion = false
                        if (succeeded) nav.returnToTopLevel()
                    }
                },
                onDismiss = { if (!projectDeletionInFlight) showProjectDeletion = false }
            )
        }
    }
}

/** Figma 835:5466 shared section card: family Background surface, 36dp clip. */
@Composable
private fun DeckGenerationSectionCard(
    title: String,
    icon: String,
    theme: DeckTheme,
    scale: Float,
    content: @Composable ColumnScope.() -> Unit
) = Surface(
    color = theme.background,
    shape = RoundedCornerShape((AppShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth()
) {
    Column(
        modifier = Modifier.padding((24 * scale).dp),
        verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol(icon, null, tint = theme.text, size = fixedSp(24 * scale), filled = true)
            AppText(title, AppTextRole.SectionTitle, color = theme.text, designScale = scale, maxLines = 1)
        }
        content()
    }
}

/** Figma 835:5466 deck-name field: family Surface pill, 24dp clip. */
@Composable
private fun DeckGenerationNameField(value: String, onValueChange: (String) -> Unit, theme: DeckTheme, scale: Float) = Surface(
    color = theme.cardPanel,
    shape = RoundedCornerShape((AppNestedShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth().height((59 * scale).dp)
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = appInputTextStyle(AppTextRole.Body, scale, theme.strongText),
        visualTransformation = rememberBilingualInputTransformation(AppTextRole.Body, scale),
        modifier = Modifier.fillMaxSize().padding(horizontal = (24 * scale).dp, vertical = (16 * scale).dp),
        decorationBox = { input ->
            if (value.isEmpty()) AppText("此处输入名称", AppTextRole.Body, color = theme.text.copy(alpha = .5f), designScale = scale)
            input()
        }
    )
}

/** Figma 835:5469 / 835:5492 difficulty distribution, themed by the project. */
@Composable
private fun DeckGenerationDifficulty(
    basicBoundary: Float,
    analysisBoundary: Float,
    theme: DeckTheme,
    scale: Float,
    onBoundariesChange: (basic: Float, analysis: Float) -> Unit
) {
    val basic = basicBoundary.roundToInt()
    val analysis = (analysisBoundary - basicBoundary).roundToInt()
    val advanced = 100 - analysisBoundary.roundToInt()
    // Basic inherits the project family; analysis and advanced are fixed
    // semantic difficulty colours (green = medium, red = hard), matching Figma.
    val basicColor = theme.secondary
    val analysisColor = AppColors.Green.primarySecondary
    val advancedColor = AppColors.WarningSecondary
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        DeckGenerationDifficultyLabel("基础记忆", basic, basicColor, theme.strongText, scale)
        DeckGenerationDifficultyLabel("理解分析", analysis, analysisColor, AppColors.Green.ink, scale)
        DeckGenerationDifficultyLabel("综合应用", advanced, advancedColor, AppColors.WarningInk, scale)
    }
    DeckGenerationDifficultySlider(
        basicBoundary = basicBoundary,
        analysisBoundary = analysisBoundary,
        scale = scale,
        basicColor = theme.primary,
        analysisColor = theme.primary,
        advancedColor = theme.primary,
        thumbColor = theme.secondary,
        onBoundariesChange = onBoundariesChange
    )
    AppText(
        "左右拉动拉杆可改变题库难度比例。\n从左到右对应从易到难。",
        AppTextRole.CardSubtitle,
        color = theme.text.copy(alpha = .5f),
        designScale = scale
    )
}

@Composable
private fun DeckGenerationDifficultyLabel(label: String, percent: Int, color: Color, contentColor: Color, scale: Float) {
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

/**
 * Figma 835:5469 segmented difficulty range slider. The track segments are all
 * the family Primary while each draggable thumb lifts to Primary-Secondary,
 * mirroring the existing [PdfDifficultyRangeSlider] geometry.
 */
@Composable
private fun DeckGenerationDifficultySlider(
    basicBoundary: Float,
    analysisBoundary: Float,
    scale: Float,
    basicColor: Color,
    analysisColor: Color,
    advancedColor: Color,
    thumbColor: Color,
    onBoundariesChange: (basic: Float, analysis: Float) -> Unit
) {
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
            .semantics {
                contentDescription = "卡片难度分布：基础记忆 ${basicBoundary.roundToInt()}%，理解分析 ${(analysisBoundary - basicBoundary).roundToInt()}%，综合应用 ${100 - analysisBoundary.roundToInt()}%"
            }
    ) {
        val gap = (5 * scale).dp
        val thumbWidth = (11 * scale).dp
        val thumbHeight = (46 * scale).dp
        val trackHeight = (20 * scale).dp
        val usableTrackWidth = maxWidth - thumbWidth * 2 - gap * 4
        val firstTrackWidth = (usableTrackWidth * (basicBoundary / 100f) - gap).coerceAtLeast(0.dp)
        val secondTrackWidth = (usableTrackWidth * ((analysisBoundary - basicBoundary) / 100f) + (16 * scale).dp).coerceAtLeast(0.dp)
        val firstThumbCenterPx = with(density) { (firstTrackWidth + gap + thumbWidth / 2).toPx() }
        val secondThumbCenterPx = with(density) { (firstTrackWidth + gap + thumbWidth + gap + secondTrackWidth + gap + thumbWidth / 2).toPx() }
        val hitRadiusPx = with(density) { (24 * scale).dp.toPx() }
        val currentFirstThumbCenterPx by rememberUpdatedState(firstThumbCenterPx)
        val currentSecondThumbCenterPx by rememberUpdatedState(secondThumbCenterPx)
        val currentHitRadiusPx by rememberUpdatedState(hitRadiusPx)
        Row(
            modifier = Modifier
                .fillMaxSize()
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

/** Figma 835:5492 generation count: three family-Primary selectable pillars. */
@Composable
private fun DeckGenerationCount(coverage: String, theme: DeckTheme, scale: Float, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy((16 * scale).dp), modifier = Modifier.fillMaxWidth()) {
            listOf("精简", "均匀", "充分").forEach { label ->
                val selected = coverage == label
                Surface(
                    onClick = { onChange(label) },
                    color = if (selected) theme.primary else AppColors.Card,
                    contentColor = if (selected) theme.onPrimary else theme.text,
                    shape = RoundedCornerShape((32 * scale).dp),
                    modifier = Modifier.weight(1f).height((59 * scale).dp)
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
        AppText("从左到右数量递增", AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .5f), designScale = scale)
    }
}

/** Figma 835:5506 custom requirement text area on a white Surface. */
@Composable
private fun DeckGenerationRequirement(value: String, onValueChange: (String) -> Unit, theme: DeckTheme, scale: Float) = Surface(
    color = AppColors.Card,
    shape = RoundedCornerShape((32 * scale).dp),
    modifier = Modifier.fillMaxWidth().height((86 * scale).dp)
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = appInputTextStyle(AppTextRole.Body, scale, theme.strongText),
        visualTransformation = rememberBilingualInputTransformation(AppTextRole.Body, scale),
        modifier = Modifier.fillMaxSize().padding((24 * scale).dp),
        decorationBox = { input ->
            if (value.isEmpty()) AppText("此处输入文本", AppTextRole.Body, color = theme.text.copy(alpha = .5f), designScale = scale)
            input()
        }
    )
}

/** Figma 835:5644 / 835:5656 reusable selectable file/text material section. */
@Composable
private fun DeckGenerationMaterialSection(
    title: String,
    icon: String,
    materials: List<ProjectDraftMaterial>,
    theme: DeckTheme,
    scale: Float,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onEditText: (ProjectDraftMaterial) -> Unit,
    onDelete: (String) -> Unit
) = Surface(
    color = theme.background,
    shape = RoundedCornerShape((AppShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth()
) {
    Column(
        modifier = Modifier.padding((24 * scale).dp),
        verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol(icon, null, tint = theme.text, size = fixedSp(24 * scale), filled = true)
            AppText(title, AppTextRole.SectionTitle, color = theme.text, designScale = scale, maxLines = 1)
        }
        HintBox(
            text = if (title == "添加文件资料") "选择该项目已添加的文件资料\n右滑卡片可编辑文件名称/删除文件"
            else "选择该项目已添加的文件资料\n右滑卡片可编辑内容/删除文件",
            parentIsWhite = false,
            theme = theme,
            designScale = scale
        )
        if (materials.isEmpty()) {
            AppText(
                "暂无资料",
                AppTextRole.Supporting,
                color = theme.mutedText.copy(alpha = .6f),
                designScale = scale,
                modifier = Modifier.padding(horizontal = (8 * scale).dp)
            )
        }
        materials.forEach { material ->
            if (material.type == ProjectDraftMaterialType.FILE) {
                ProjectDraftFileCard(
                    material = material, theme = theme, scale = scale,
                    onEdit = {}, selected = material.id in selectedIds,
                    onSelect = { onToggle(material.id) },
                    onDelete = { onDelete(material.id) }
                )
            } else {
                ProjectDraftTextCard(
                    material = material, theme = theme, scale = scale,
                    onEdit = { onEditText(material) }, onDelete = { onDelete(material.id) },
                    selected = material.id in selectedIds,
                    onSelect = { onToggle(material.id) },
                    kind = ProjectMaterialTextCardKind.SELECTABLE,
                    parentSurface = ProjectMaterialCardParentSurface.THEME_BACKGROUND
                )
            }
        }
    }
}
