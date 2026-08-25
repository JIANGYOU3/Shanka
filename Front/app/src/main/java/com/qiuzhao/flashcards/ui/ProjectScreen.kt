package com.qiuzhao.flashcards.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.data.remote.LEGACY_UNASSIGNED_PROJECT_ID
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.ui.navigation.AppRoute

/** Figma 494:1447 project root. Project data is derived from the contract layer. */
@Composable
internal fun ProjectScreen(
    projects: List<ProjectSummary>,
    decks: List<DeckSummary>,
    searchQuery: String,
    viewModel: AppViewModel,
    nav: ScreenNavigator
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val visibleProjects = projects.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground).statusBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
                ProjectRootAction(
                    modifier = Modifier.weight(1f), icon = "create_new_folder", label = "添加项目",
                    background = AppColors.Blue.primary, content = AppColors.TextIconLight, scale = scale
                ) {
                    viewModel.resetProjectCreationDraft()
                    nav.navigate(AppRoute.ProjectCreate)
                }
                ProjectRootAction(
                    modifier = Modifier.weight(1f), icon = "edit_document", label = "资料管理",
                    background = AppColors.Blue.background, content = AppColors.TextIconDark, scale = scale
                ) { nav.navigate(AppRoute.MaterialManagement) }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp),
                contentPadding = PaddingValues(bottom = (RootNavigationScrollTail * scale).dp)
            ) {
                items(visibleProjects, key = { it.id }) { project ->
                    ProjectSummaryCard(project, decks.filter { (it.projectId ?: LEGACY_UNASSIGNED_PROJECT_ID) == project.id }, scale) {
                        // Project detail is introduced as the next project work item.
                        nav.navigate(AppRoute.ProjectDetail(project.id))
                    }
                }
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ProjectRootAction(
    modifier: Modifier,
    icon: String,
    label: String,
    background: Color,
    content: Color,
    scale: Float,
    onClick: () -> Unit
) = Surface(
    onClick = onClick, color = background, contentColor = content,
    shape = RoundedCornerShape((24 * scale).dp), modifier = modifier.height((60 * scale).dp)
) {
    Row(
        // Figma 494:1449 / 506:1896: both actions share a 24dp horizontal inset.
        modifier = Modifier.fillMaxSize().padding(horizontal = (24 * scale).dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        MaterialSymbol(icon, label, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
        Spacer(Modifier.width((8 * scale).dp))
        AppText(label, AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
    }
}

@Composable
private fun ProjectSummaryCard(project: ProjectSummary, decks: List<DeckSummary>, scale: Float, onClick: () -> Unit) {
    val theme = deckTheme(project)
    val totalCards = decks.sumOf { it.cardCount }
    val masteredCards = decks.sumOf { it.masteredCards }
    val ratio = if (totalCards == 0) 0f else masteredCards.toFloat() / totalCards
    ProjectThemedCard(
        title = project.name,
        count = project.deckCount,
        countLabel = "group",
        progress = ratio,
        theme = theme,
        icon = "heap_snapshot_multiple",
        variant = ProjectThemedCardVariant.TINTED,
        designScale = scale,
        onClick = onClick
    )
}

/** Figma 588:1922. The same visual form is used for creating and editing a project. */
@Composable
internal fun ProjectCreateScreen(
    viewModel: AppViewModel,
    nav: ScreenNavigator,
    editingProject: ProjectSummary? = null
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val materials by viewModel.projectCreationMaterials.collectAsState()
    val projectId = editingProject?.id
    var name by rememberSaveable(projectId) { mutableStateOf(editingProject?.name.orEmpty()) }
    var selectedTheme by rememberSaveable(projectId) { mutableStateOf(editingProject?.themeKey ?: "violet") }
    var message by remember { mutableStateOf<String?>(null) }
    val theme = DeckThemes.firstOrNull { it.key == selectedTheme } ?: DeckThemes.first()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.addProjectDraftFile(projectDocumentName(context, it)) }
    }

    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar(
            title = if (editingProject == null) "添加项目" else "编辑项目", subtitle = null, onBack = nav::goBack,
            backContainer = theme.secondary, titleColor = AppColors.TextIconDark
        )
        Box(
            Modifier.fillMaxSize().statusBarsPadding()
                .padding(top = (88 * scale).dp, start = (16 * scale).dp, end = (16 * scale).dp)
                .clipToBounds()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp),
                contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * scale).dp)
            ) {
                item {
                    Surface(color = theme.cardPanel, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth().height((72 * scale).dp)) {
                        Box(Modifier.padding(horizontal = (24 * scale).dp), contentAlignment = Alignment.CenterStart) {
                            AppText("可编辑主题色、名称，以及添加文件", AppTextRole.CardSubtitle, color = theme.text, designScale = scale)
                        }
                    }
                }
                item {
                    ProjectCreationPanel(theme, scale) {
                        ProjectSectionLabel("stylus_note", "项目名称", theme, scale)
                        Surface(color = theme.cardPanel, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.fillMaxWidth().height((59 * scale).dp)) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = name, onValueChange = { name = it }, singleLine = true,
                                textStyle = appInputTextStyle(AppTextRole.Body, scale, theme.text),
                                visualTransformation = rememberBilingualInputTransformation(AppTextRole.Body, scale),
                                modifier = Modifier.fillMaxSize().padding(horizontal = (24 * scale).dp),
                                decorationBox = { input -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                    if (name.isBlank()) AppText("此处输入名称", AppTextRole.Body, color = theme.text.copy(alpha = .5f), designScale = scale)
                                    input()
                                } }
                            )
                        }
                    }
                }
                item {
                    ProjectCreationPanel(theme, scale) {
                        ProjectSectionLabel("colors", "项目主题色", theme, scale)
                        Surface(color = AppColors.Card, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.fillMaxWidth().height((84 * scale).dp)) {
                            Row(Modifier.fillMaxSize().padding((12 * scale).dp), horizontalArrangement = Arrangement.spacedBy((12 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                                DeckThemes.forEach { choice ->
                                    val selected = selectedTheme == choice.key
                                    val choiceWidth by animateDpAsState(if (selected) (121 * scale).dp else 0.dp, tween(500, easing = FastOutSlowInEasing), label = "${choice.key} color width")
                                    val border = if (selected) 6.dp else 4.dp
                                    Surface(
                                        onClick = { selectedTheme = choice.key }, color = choice.primary,
                                        shape = RoundedCornerShape(if (selected) (24 * scale).dp else 999.dp),
                                        modifier = (if (selected) Modifier.width(choiceWidth) else Modifier.weight(1f)).height((60 * scale).dp),
                                        border = androidx.compose.foundation.BorderStroke(border, AppColors.Card.copy(alpha = .5f))
                                    ) {
                                        if (selected) Box(contentAlignment = Alignment.Center) {
                                            MaterialSymbol("check", "已选择${choice.label}", tint = choice.onPrimary, size = fixedSp(24 * scale), filled = true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    ProjectCreationPanel(theme, scale) {
                        ProjectSectionLabel("note_stack_add", "添加学习资料", theme, scale)
                        ProjectMaterialActionCard("file_copy", "输入文本", "粘贴文本或手动输入", theme, scale) {
                            nav.navigate(AppRoute.ProjectTextEditor(themeKey = selectedTheme))
                        }
                        ProjectMaterialActionCard("picture_as_pdf", "选择文件", "PDF/ .txt/ .md格式", theme, scale) {
                            filePicker.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "text/x-markdown"))
                        }
                    }
                }
                item {
                    ProjectCreationPanel(theme, scale) {
                        ProjectSectionLabel("bookmark_stacks", "管理已添加的学习资料", theme, scale)
                        Surface(color = AppColors.Card, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth()) {
                            AppText("右滑卡片可进行编辑与删除", AppTextRole.Supporting, modifier = Modifier.padding((24 * scale).dp), color = theme.text, designScale = scale)
                        }
                        val textItems = materials.filter { it.type == ProjectDraftMaterialType.TEXT }
                        val fileItems = materials.filter { it.type == ProjectDraftMaterialType.FILE }
                        if (textItems.isNotEmpty()) {
                            ProjectMaterialGroupTitle("文本资料", theme, scale)
                            textItems.forEach { material ->
                                ProjectDraftTextCard(
                                    material = material, theme = theme, scale = scale,
                                    onEdit = { nav.navigate(AppRoute.ProjectTextEditor(material.id, selectedTheme)) },
                                    onDelete = { viewModel.deleteProjectDraftMaterial(material.id) }
                                )
                            }
                        }
                        if (fileItems.isNotEmpty()) {
                            ProjectMaterialGroupTitle("文件资料", theme, scale)
                            fileItems.forEach { material ->
                                ProjectDraftFileCard(material, theme, scale) { viewModel.deleteProjectDraftMaterial(material.id) }
                            }
                        }
                    }
                }
                message?.let { error -> item { AppText(error, AppTextRole.CardSubtitle, color = AppColors.WarningStrong, designScale = scale) } }
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
        Surface(
            onClick = {
                val onResult: (String?) -> Unit = { error ->
                    message = error
                    if (error == null) nav.goBack()
                }
                if (editingProject == null) {
                    viewModel.createFrontendTestProject(name, selectedTheme, onResult)
                } else {
                    viewModel.updateFrontendTestProject(editingProject.id, name, selectedTheme, onResult)
                }
            }, color = theme.primary, contentColor = theme.onPrimary,
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp).fillMaxWidth().height((60 * scale).dp).zIndex(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                MaterialSymbol("list_alt_check", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                AppText("完成设置", AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
            }
        }
    }
}

@Composable
private fun ProjectCreationPanel(theme: DeckTheme, scale: Float, content: @Composable ColumnScope.() -> Unit) = Surface(
    color = theme.surface, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth()
) { Column(Modifier.padding((20 * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp), content = content) }

@Composable
private fun ProjectSectionLabel(icon: String, label: String, theme: DeckTheme, scale: Float) = Row(
    modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((12 * scale).dp), verticalAlignment = Alignment.CenterVertically
) {
    MaterialSymbol(icon, null, tint = theme.text, size = fixedSp(28 * scale), filled = true)
    AppText(label, AppTextRole.SectionTitle, color = theme.text, designScale = scale)
}

@Composable
private fun ProjectMaterialActionCard(icon: String, title: String, subtitle: String, theme: DeckTheme, scale: Float, onClick: () -> Unit) = Surface(
    onClick = onClick, color = theme.primary, contentColor = theme.onPrimary,
    shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth().height((80 * scale).dp)
) {
    Row(Modifier.fillMaxSize().padding((12 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = theme.cardPanel, shape = RoundedCornerShape(999.dp), modifier = Modifier.size((56 * scale).dp)) {
            Box(contentAlignment = Alignment.Center) { MaterialSymbol(icon, null, tint = theme.strongText, size = fixedSp(24 * scale), filled = true) }
        }
        Spacer(Modifier.width((16 * scale).dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
            AppText(title, AppTextRole.CardTitle, color = LocalContentColor.current, designScale = scale)
            AppText(subtitle, AppTextRole.CardSubtitle, color = theme.cardPanel, designScale = scale)
        }
        MaterialSymbol("arrow_forward", title, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
    }
}

@Composable
private fun ProjectMaterialGroupTitle(title: String, theme: DeckTheme, scale: Float) = Surface(
    color = theme.cardPanel, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth()
) { AppText(title, AppTextRole.SectionTitle, modifier = Modifier.padding(horizontal = (28 * scale).dp, vertical = (16 * scale).dp), color = theme.text, designScale = scale) }

@Composable
private fun ProjectDraftFileCard(material: ProjectDraftMaterial, theme: DeckTheme, scale: Float, onDelete: () -> Unit) = ProjectSwipeContainer(
    height = 104f, actions = listOf(ProjectSwipeAction("delete", "删除卡组", AppColors.WarningStrong, theme.onPrimary, onDelete)), scale = scale
) {
    Surface(
        color = theme.secondary,
        shape = RoundedCornerShape((32 * scale).dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Row(Modifier.fillMaxSize().padding((20 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = theme.primary, shape = RoundedCornerShape((16 * scale).dp), modifier = Modifier.size((56 * scale).dp)) {
                Box(contentAlignment = Alignment.Center) { MaterialSymbol("picture_as_pdf", null, tint = theme.onPrimary, size = fixedSp(24 * scale), filled = true) }
            }
            Spacer(Modifier.width((16 * scale).dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
                AppText(material.title, AppTextRole.CardTitle, color = theme.text, designScale = scale, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AppText("— 页", AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .5f), designScale = scale)
            }
            Surface(color = theme.primary, shape = RoundedCornerShape((20 * scale).dp)) {
                AppText(material.extension.orEmpty(), AppTextRole.CardSubtitle, modifier = Modifier.padding(horizontal = (16 * scale).dp, vertical = (4 * scale).dp), color = theme.onPrimary, designScale = scale)
            }
        }
    }
}

@Composable
private fun ProjectDraftTextCard(material: ProjectDraftMaterial, theme: DeckTheme, scale: Float, onEdit: () -> Unit, onDelete: () -> Unit) = ProjectSwipeContainer(
    height = 238f,
    actions = listOf(
        ProjectSwipeAction("delete", "删除该卡", AppColors.WarningStrong, theme.onPrimary, onDelete),
        ProjectSwipeAction("edit", "编辑卡片", theme.cardPanel, theme.text, onEdit)
    ),
    scale = scale
) {
    Surface(
        color = AppColors.Card,
        shape = RoundedCornerShape((32 * scale).dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize().padding((12 * scale).dp), verticalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
            Surface(color = theme.secondary, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth()) {
                AppText(material.title, AppTextRole.Body, modifier = Modifier.padding((24 * scale).dp), color = theme.text, designScale = scale)
            }
            Surface(color = theme.cardPanel, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                AppText(material.content.ifBlank { "此处粘贴文本" }, AppTextRole.Body, modifier = Modifier.padding((24 * scale).dp), color = theme.text.copy(alpha = .5f), designScale = scale, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private data class ProjectSwipeAction(val icon: String, val label: String, val background: Color, val content: Color, val onClick: () -> Unit)

@Composable
private fun ProjectSwipeContainer(height: Float, actions: List<ProjectSwipeAction>, scale: Float, content: @Composable () -> Unit) {
    val actionWidth = (112 * scale).dp
    val revealPx = with(LocalDensity.current) { (actionWidth - (16 * scale).dp).toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(dragOffset, label = "project material swipe")
    val dragState = rememberDraggableState { delta -> dragOffset = (dragOffset + delta).coerceIn(-revealPx, 0f) }
    val cardShape = RoundedCornerShape((32 * scale).dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height((height * scale).dp)
            .clip(cardShape)
            .clipToBounds()
    ) {
        Column(Modifier.align(Alignment.CenterEnd).width(actionWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
            actions.forEach { action ->
                Surface(onClick = action.onClick, color = action.background, contentColor = action.content, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        MaterialSymbol(action.icon, null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                        Spacer(Modifier.height((4 * scale).dp))
                        AppText(
                            action.label,
                            AppTextRole.Label,
                            modifier = Modifier.fillMaxWidth(),
                            color = LocalContentColor.current,
                            textAlign = TextAlign.Center,
                            designScale = scale,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(offset.roundToInt(), 0) }
                .clip(cardShape)
                .draggable(
                    dragState,
                    Orientation.Horizontal,
                    onDragStopped = { dragOffset = if (dragOffset < -revealPx / 2f) -revealPx else 0f }
                )
        ) { content() }
    }
}

@Composable
internal fun ProjectTextEditorScreen(route: AppRoute.ProjectTextEditor, viewModel: AppViewModel, nav: ScreenNavigator) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val materials by viewModel.projectCreationMaterials.collectAsState()
    val existing = materials.firstOrNull { it.id == route.materialId }
    val theme = DeckThemes.firstOrNull { it.key == route.themeKey } ?: DeckThemes.first()
    var title by rememberSaveable(route.materialId) { mutableStateOf(existing?.title.orEmpty()) }
    var content by rememberSaveable(route.materialId) { mutableStateOf(existing?.content.orEmpty()) }
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar("导入文本", null, nav::goBack, backContainer = theme.cardPanel, titleColor = theme.text)
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp).clipToBounds(),
            contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * scale).dp), verticalArrangement = Arrangement.spacedBy((12 * scale).dp)
        ) {
            item { ProjectTextField("文件标题", title, { title = it }, "标题标题", singleLine = true, theme = theme, scale = scale) }
            item { ProjectTextField("文本输入", content, { content = it }, "此处粘贴文本", singleLine = false, theme = theme, scale = scale) }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
        Surface(
            onClick = { viewModel.upsertProjectDraftText(route.materialId, title, content); nav.goBack() }, color = theme.primary, contentColor = theme.onPrimary,
            shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp).fillMaxWidth().height((60 * scale).dp).zIndex(1f)
        ) { Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol("list_alt_check", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
            Spacer(Modifier.width((8 * scale).dp)); AppText("完成导入", AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
        } }
    }
}

@Composable
private fun ProjectTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, singleLine: Boolean, theme: DeckTheme, scale: Float) = Column(verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
    AppText(label, AppTextRole.SectionTitle, modifier = Modifier.padding(horizontal = (8 * scale).dp), color = theme.text, designScale = scale)
    Surface(color = theme.surface, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth().height(if (singleLine) (75 * scale).dp else (260 * scale).dp)) {
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = singleLine,
            textStyle = appInputTextStyle(AppTextRole.Body, scale, theme.text), visualTransformation = rememberBilingualInputTransformation(AppTextRole.Body, scale),
            modifier = Modifier.fillMaxSize().padding((24 * scale).dp), decorationBox = { input -> Box(Modifier.fillMaxSize()) {
                if (value.isBlank()) AppText(placeholder, AppTextRole.Body, color = theme.text.copy(alpha = .5f), designScale = scale)
                input()
            } }
        )
    }
}

private fun projectDocumentName(context: android.content.Context, uri: android.net.Uri): String = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
}.orEmpty().ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "未命名文件" }
