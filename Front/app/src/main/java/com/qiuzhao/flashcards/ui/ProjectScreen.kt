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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt
import com.qiuzhao.flashcards.data.remote.DeckSummary
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
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp),
                contentPadding = PaddingValues(bottom = (RootNavigationScrollTail * scale).dp)
            ) {
                items(visibleProjects, key = { it.id }) { project ->
                    ProjectSummaryCard(project, decks.filter { it.projectId == project.id }, scale) {
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
        variant = ProjectThemedCardVariant.BASE_PAGE,
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
    val pdfUploading by viewModel.pdfUploading.collectAsState()
    val projectCreating by viewModel.projectCreating.collectAsState()
    val projectId = editingProject?.id
    var name by rememberSaveable(projectId) { mutableStateOf(editingProject?.name.orEmpty()) }
    var selectedTheme by rememberSaveable(projectId) { mutableStateOf(editingProject?.themeKey ?: "violet") }
    var message by remember { mutableStateOf<String?>(null) }
    var editingFile by remember { mutableStateOf<ProjectDraftMaterial?>(null) }
    var showProjectDeletionConfirmation by rememberSaveable(projectId) { mutableStateOf(false) }
    var projectDeletionInFlight by rememberSaveable(projectId) { mutableStateOf(false) }
    val theme = DeckThemes.firstOrNull { it.key == selectedTheme } ?: DeckThemes.first()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.addProjectDraftFile(it, projectDocumentName(context, it)) }
    }

    // Figma 588:1922 uses a white page canvas.  The project family begins at
    // the 36dp section cards, which keeps each nested radius visually legible.
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar(
            title = if (editingProject == null) "添加项目" else "编辑项目", subtitle = null, onBack = nav::goBack,
            backContainer = theme.cardPanel, titleColor = theme.text
        )
        Box(
            Modifier.fillMaxSize().statusBarsPadding()
                .padding(top = (88 * scale).dp, start = (16 * scale).dp, end = (16 * scale).dp)
                .clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp),
                contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * scale).dp)
            ) {
                item {
                    Surface(color = theme.cardPanel, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.padding((24 * scale).dp), contentAlignment = Alignment.CenterStart) {
                            AppText(
                                if (editingProject == null) "填写名称创建项目，再添加 PDF 或文本资料" else "可编辑主题色、名称，以及管理资料",
                                AppTextRole.CardSubtitle, color = theme.text, designScale = scale
                            )
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
                    ProjectCreationMaterialsPanel(
                        title = "管理添加的文件资料", icon = "files", theme = theme, scale = scale,
                        materials = materials.filter { it.type == ProjectDraftMaterialType.FILE },
                        onEditFile = { editingFile = it }, onEditText = {},
                        onDelete = { viewModel.deleteProjectDraftMaterial(it.id) }
                    )
                }
                item {
                    ProjectCreationMaterialsPanel(
                        title = "管理添加的文本资料", icon = "description", theme = theme, scale = scale,
                        materials = materials.filter { it.type == ProjectDraftMaterialType.TEXT },
                        onEditFile = { editingFile = it },
                        onEditText = { material ->
                            nav.navigate(AppRoute.ProjectTextEditor(material.id, selectedTheme, editorTitle = "编辑文本资料"))
                        },
                        onAddText = {
                            val draftId = viewModel.stageProjectDraftText()
                            nav.navigate(AppRoute.ProjectTextEditor(draftId, selectedTheme, editorTitle = "导入文本"))
                        },
                        onDelete = { viewModel.deleteProjectDraftMaterial(it.id) }
                    )
                }
                if (editingProject != null) {
                    item {
                        ProjectDeletionEntry(
                            scale = scale,
                            onRequestDeletion = { showProjectDeletionConfirmation = true }
                        )
                    }
                }
                message?.let { error -> item { AppText(error, AppTextRole.CardSubtitle, color = AppColors.WarningStrong, designScale = scale) } }
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = AppColors.BaseBackground)
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                .fillMaxWidth().zIndex(1f),
            horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)
        ) {
            Surface(
                onClick = {
                    if (editingProject == null) {
                        nav.navigate(AppRoute.ProjectMaterialPicker(selectedTheme))
                    } else {
                        nav.navigate(AppRoute.ProjectMaterialManagement(editingProject.id))
                    }
                },
                color = theme.secondary, contentColor = theme.text,
                shape = RoundedCornerShape((24 * scale).dp),
                modifier = Modifier.weight(1f).height((60 * scale).dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    MaterialSymbol("folder_open", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                    Spacer(Modifier.width((8 * scale).dp))
                    AppText("导入资料", AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
                }
            }
            Surface(
            onClick = {
                if (editingProject == null) {
                    // Two-step creation (V25-D-29): the wizard's single "完成设置" runs both
                    // network steps — POST /projects (JSON name), then materials/* per draft.
                    val hadMaterials = materials.isNotEmpty()
                    viewModel.createProjectFromDraft(name, selectedTheme) { projectId, error ->
                        message = error
                        when {
                            error != null -> Unit
                            // Materials on board → straight to the parse-wait/chapter flow.
                            projectId != null && hadMaterials -> nav.replaceTop(AppRoute.DeckGeneration(projectId))
                            // An EMPTY project lands on its own guide (add material / delete).
                            projectId != null -> nav.replaceTop(AppRoute.ProjectDetail(projectId))
                            else -> nav.goBack()
                        }
                    }
                } else {
                    viewModel.renameProjectFromEditor(editingProject.id, name, selectedTheme) { error ->
                        message = error
                        if (error == null) nav.goBack()
                    }
                }
            },
            // A submission in flight is already idempotently owned; a second tap would
            // turn one upload into two requests with fresh keys.
            enabled = !pdfUploading && !projectCreating,
            color = theme.primary, contentColor = theme.onPrimary,
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.weight(1f).height((60 * scale).dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                MaterialSymbol("list_alt_check", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                AppText("完成设置", AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
            }
        }
        }
    }
    editingFile?.let { material ->
        FileNameEditorDialog(
            theme = theme, initialTitle = material.title,
            onConfirm = { updatedTitle ->
                viewModel.renameProjectDraftFile(material.id, updatedTitle)
                editingFile = null
            },
            onDismiss = { editingFile = null }
        )
    }
    if (editingProject != null && showProjectDeletionConfirmation) {
        ProjectDeletionDialog(
            projectName = editingProject.name,
            theme = theme,
            deleting = projectDeletionInFlight,
            onConfirm = { retainDecks ->
                if (projectDeletionInFlight) return@ProjectDeletionDialog
                projectDeletionInFlight = true
                viewModel.deleteProject(editingProject.id, retainDecks) { succeeded ->
                    projectDeletionInFlight = false
                    if (succeeded) {
                        showProjectDeletionConfirmation = false
                        nav.returnToTopLevel()
                    }
                }
            },
            onDismiss = { if (!projectDeletionInFlight) showProjectDeletionConfirmation = false }
        )
    }
}

/**
 * The edit form owns the destructive entry so it remains visually separate from normal project
 * settings. The second, modal confirmation chooses the backend's retain_decks contract value.
 */
@Composable
internal fun ProjectDeletionEntry(scale: Float, onRequestDeletion: () -> Unit) = Surface(
    color = AppColors.WarningSecondary,
    shape = RoundedCornerShape((AppShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth()
) {
    Column(
        modifier = Modifier.padding((20 * scale).dp),
        verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((12 * scale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbol("delete", null, tint = AppColors.WarningInk, size = fixedSp(24 * scale), filled = true)
            AppText("删除项目", AppTextRole.SectionTitle, color = AppColors.WarningInk, designScale = scale)
        }
        AppText(
            "删除后会移除项目的 PDF、章节、制卡任务历史和项目设置。",
            AppTextRole.CardSubtitle,
            color = AppColors.WarningInk,
            designScale = scale
        )
        Surface(
            onClick = onRequestDeletion,
            color = AppColors.WarningStrong,
            contentColor = AppColors.TextIconLight,
            shape = RoundedCornerShape((AppButtonShapeRadius * scale).dp),
            modifier = Modifier.fillMaxWidth().height((60 * scale).dp)
                .semantics { contentDescription = "删除项目" }
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MaterialSymbol("delete", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                AppText("删除项目", AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
            }
        }
    }
}

/** Compact project confirmation: the server handles task cancellation automatically. */
@Composable
internal fun ProjectDeletionDialog(
    projectName: String,
    theme: DeckTheme,
    deleting: Boolean = false,
    /** Advisory impact line (decks/cards/tasks) from the deletion preflight; null hides it. */
    impactText: String? = null,
    onConfirm: (retainDecks: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!deleting) onDismiss() }) {
        Surface(
            color = theme.background,
            shape = RoundedCornerShape(36.dp),
            modifier = Modifier.width(331.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText("删除“$projectName”吗？", AppTextRole.SectionTitle, color = theme.text, maxLines = 2)
                if (!impactText.isNullOrBlank()) {
                    AppText(impactText, AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .6f))
                }
                CompactDeletionButton("删除项目，保留卡组", theme, deleting) { onConfirm(true) }
                CompactDeletionButton("删除项目及卡组", theme, deleting, destructive = true) { onConfirm(false) }
            }
        }
    }
}

@Composable
internal fun CompactDeletionButton(
    label: String,
    theme: DeckTheme,
    deleting: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !deleting,
        color = if (destructive) AppColors.WarningStrong else theme.cardPanel,
        contentColor = if (destructive) AppColors.TextIconLight else theme.text,
        shape = RoundedCornerShape(AppButtonShapeRadius.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { AppText(label, AppTextRole.Label, color = LocalContentColor.current) }
    }
}

@Composable
private fun ProjectCreationPanel(theme: DeckTheme, scale: Float, content: @Composable ColumnScope.() -> Unit) = Surface(
    // Figma 588:1922: group is the family Background; the nested fields lift
    // to Surface.  Keeping this inversion prevents the lost card hierarchy.
    color = theme.background, shape = RoundedCornerShape((36 * scale).dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape((36 * scale).dp))
) { Column(Modifier.padding((20 * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp), content = content) }

@Composable
private fun ProjectSectionLabel(icon: String, label: String, theme: DeckTheme, scale: Float) = Row(
    modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((12 * scale).dp), verticalAlignment = Alignment.CenterVertically
) {
    MaterialSymbol(icon, null, tint = theme.text, size = fixedSp(24 * scale), filled = true)
    AppText(label, AppTextRole.SectionTitle, color = theme.text, designScale = scale)
}

@Composable
internal fun ProjectMaterialActionCard(icon: String, title: String, subtitle: String, theme: DeckTheme, scale: Float, onClick: () -> Unit) = Surface(
    onClick = onClick, color = theme.primary, contentColor = theme.onPrimary,
    shape = RoundedCornerShape((AppButtonShapeRadius * scale).dp), modifier = Modifier.fillMaxWidth().height((80 * scale).dp)
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
        // Match the import action-card's 56dp trailing alignment container.
        Box(Modifier.size((56 * scale).dp), contentAlignment = Alignment.Center) {
            MaterialSymbol("arrow_forward", title, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
        }
    }
}

@Composable
private fun ProjectCreationMaterialsPanel(
    title: String,
    icon: String,
    theme: DeckTheme,
    scale: Float,
    materials: List<ProjectDraftMaterial>,
    onEditFile: (ProjectDraftMaterial) -> Unit,
    onEditText: (ProjectDraftMaterial) -> Unit,
    onDelete: (ProjectDraftMaterial) -> Unit,
    onAddText: (() -> Unit)? = null,
) = ProjectCreationPanel(theme, scale) {
    Row(Modifier.padding(horizontal = (8 * scale).dp), horizontalArrangement = Arrangement.spacedBy((10 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbol(icon, null, tint = theme.text, size = fixedSp(24 * scale), filled = true)
        AppText(title, AppTextRole.SectionTitle, color = theme.text, designScale = scale)
    }
    Surface(color = AppColors.Card, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.fillMaxWidth()) {
        AppText(
            if (materials.isEmpty()) "暂无资料。可通过“导入资料”添加，或点击“添加文本资料”" else "右滑卡片可编辑名称/删除资料",
            AppTextRole.Supporting,
            modifier = Modifier.padding((24 * scale).dp), color = theme.text, designScale = scale
        )
    }
    materials.forEach { material ->
        if (material.type == ProjectDraftMaterialType.FILE) {
            ProjectDraftFileCard(material, theme, scale, onEdit = { onEditFile(material) }, onDelete = { onDelete(material) })
        } else {
            ProjectDraftTextCard(material, theme, scale, onEdit = { onEditText(material) }, onDelete = { onDelete(material) })
        }
    }
    onAddText?.let { action ->
        Surface(
            onClick = action,
            color = theme.secondary, contentColor = theme.text,
            shape = RoundedCornerShape((AppButtonShapeRadius * scale).dp),
            modifier = Modifier.fillMaxWidth().height((60 * scale).dp)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbol("add_note", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                AppText("添加文本资料", AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
            }
        }
    }
}

/**
 * Contract status line for server-backed materials (解析中 / 解析失败 / 就绪, TEXT shows the
 * character count); null for creation-flow drafts that have no server status yet.
 */
internal fun materialStatusLine(material: ProjectDraftMaterial): String? = when {
    material.serverStatus == null -> null
    material.type == ProjectDraftMaterialType.TEXT ->
        if (material.charCount != null) "就绪 · ${material.charCount}字" else "就绪"
    material.serverStatus == "FAILED" ->
        "解析失败" + (material.errorCode?.let { " · $it" } ?: "")
    material.serverStatus == "PENDING" || material.serverStatus == "PARSING" -> "解析中"
    else -> "就绪"
}

@Composable
internal fun ProjectDraftFileCard(
    material: ProjectDraftMaterial,
    theme: DeckTheme,
    scale: Float,
    onEdit: () -> Unit = {},
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    parentSurface: ProjectMaterialCardParentSurface = ProjectMaterialCardParentSurface.THEME_BACKGROUND,
    onDelete: () -> Unit
) = ProjectSwipeFileContainer(
    scale = scale,
    // The edit reveal is the family's Primary-Secondary semantic, distinct
    // from both the exposed record and the destructive Warning action.
    editBackground = theme.secondary,
    onEdit = onEdit,
    onDelete = onDelete
) {
    val palette = projectMaterialCardPalette(theme, parentSurface, selected)
    // Figma 167:9679: the visible card is a 370 x 88 family-Surface. The
    // parent swipe viewport owns the 36dp clip so both revealed actions remain
    // inside that same silhouette.
    Surface(
        color = palette.card,
        shape = RoundedCornerShape((36 * scale).dp),
        onClick = onSelect ?: {},
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape((36 * scale).dp))
    ) {
        Row(Modifier.fillMaxSize().padding((16 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            val accent = if (selected) AppColors.Green.primary else theme.primary
            val accentOn = if (selected) AppColors.Green.background else theme.background
            Surface(color = accent, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.size((56 * scale).dp)) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol(if (selected) "check_circle" else "picture_as_pdf", null, tint = accentOn, size = fixedSp(24 * scale), filled = true)
                }
            }
            Spacer(Modifier.width((16 * scale).dp))
            // Figma 167:9679 puts the type and import date below the filename;
            // there is no trailing file-type pill in this card variant.
            Column(
                modifier = Modifier.weight(1f).height((56 * scale).dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                AppText(material.title, AppTextRole.CardTitle, color = theme.text, designScale = scale, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(
                    horizontalArrangement = Arrangement.spacedBy((4 * scale).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusLine = materialStatusLine(material)
                    if (statusLine != null) {
                        AppText(statusLine, AppTextRole.CardSubtitle, color = theme.text, designScale = scale)
                    } else {
                        AppText(
                            material.extension.orEmpty().trimStart('.').uppercase(),
                            AppTextRole.CardSubtitle,
                            color = theme.text,
                            designScale = scale
                        )
                        AppText(formatImportDate(material.importedAt), AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .5f), designScale = scale)
                        AppText("导入", AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .5f), designScale = scale)
                    }
                }
            }
        }
    }
}

/**
 * The two Figma text cards use the same geometry but intentionally different
 * colour hierarchy. Management is a white record card; the picker is already
 * inside a family-Background section and therefore starts at family-Surface.
 */
internal enum class ProjectMaterialTextCardKind { MANAGEMENT, SELECTABLE }

/** What directly surrounds a reusable material card. */
internal enum class ProjectMaterialCardParentSurface { BASE, THEME_BACKGROUND }

/**
 * Figma 775:3940 / 786:4657 / 796:6785 semantic material-card tiers.
 *
 * On a white canvas the visible card starts at family Background and its body
 * returns to white. Inside a family-Background section, the card lifts to
 * Surface and its body returns to Background. Green selection always uses the
 * equivalent Surface / Primary-Secondary / Background sequence.
 */
internal data class ProjectMaterialCardPalette(
    val card: Color,
    val title: Color,
    val body: Color
)

internal fun projectMaterialCardPalette(
    theme: DeckTheme,
    parentSurface: ProjectMaterialCardParentSurface,
    selected: Boolean
): ProjectMaterialCardPalette = if (selected) {
    ProjectMaterialCardPalette(
        card = AppColors.Green.surface,
        title = AppColors.Green.primarySecondary,
        body = AppColors.Green.background
    )
} else when (parentSurface) {
    ProjectMaterialCardParentSurface.BASE -> ProjectMaterialCardPalette(
        card = theme.background,
        title = theme.secondary,
        body = AppColors.Card
    )
    ProjectMaterialCardParentSurface.THEME_BACKGROUND -> ProjectMaterialCardPalette(
        card = theme.cardPanel,
        title = theme.secondary,
        body = theme.background
    )
}

@Composable
internal fun ProjectDraftTextCard(
    material: ProjectDraftMaterial,
    theme: DeckTheme,
    scale: Float,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    kind: ProjectMaterialTextCardKind = ProjectMaterialTextCardKind.MANAGEMENT,
    parentSurface: ProjectMaterialCardParentSurface = ProjectMaterialCardParentSurface.THEME_BACKGROUND
) = ProjectSwipeContainer(
    // Figma 648:2818 = 238dp management preview; Figma 796:6786 = 211dp
    // selectable preview. The viewport stays 36dp while its content is 32dp.
    height = if (kind == ProjectMaterialTextCardKind.MANAGEMENT) 238f else 211f,
    actions = listOf(
        // Delete is Warning Primary; every edit action uses Primary-Secondary.
        ProjectSwipeAction("delete", "删除该卡", AppColors.Warning, theme.onPrimary, onDelete),
        ProjectSwipeAction(
            "edit", "编辑卡片",
            theme.secondary,
            theme.strongText,
            onEdit
        )
    ),
    scale = scale
) {
    val isSelectable = kind == ProjectMaterialTextCardKind.SELECTABLE
    val palette = projectMaterialCardPalette(theme, parentSurface, selected)
    Surface(
        color = palette.card,
        shape = RoundedCornerShape((32 * scale).dp),
        onClick = onSelect ?: {},
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape((32 * scale).dp))
    ) {
        Column(Modifier.fillMaxSize().padding((12 * scale).dp), verticalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
            if (isSelectable) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
                    Surface(
                        color = if (selected) AppColors.Green.primary else theme.primary,
                        shape = RoundedCornerShape((24 * scale).dp),
                        // Figma 796:6784 / 796:6785: this is not the compact
                        // 56dp file-icon tile. It self-stretches to the title
                        // panel's 24 + 27 + 24 = 75dp height and stays square.
                        modifier = Modifier.size((75 * scale).dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            MaterialSymbol(if (selected) "check_circle" else "file_copy", null, tint = if (selected) AppColors.Green.background else theme.background, size = fixedSp(24 * scale), filled = true)
                        }
                    }
                    Surface(
                        color = palette.title,
                        shape = RoundedCornerShape((32 * scale).dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        AppText(material.title, AppTextRole.Body, modifier = Modifier.padding((24 * scale).dp), color = theme.text, designScale = scale, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Surface(
                    color = palette.title,
                    shape = RoundedCornerShape((32 * scale).dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding((24 * scale).dp),
                        horizontalArrangement = Arrangement.spacedBy((8 * scale).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(material.title, AppTextRole.Body, color = theme.text, designScale = scale, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        materialStatusLine(material)?.let { status ->
                            AppText(status, AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .6f), designScale = scale, maxLines = 1)
                        }
                    }
                }
            }
            Surface(color = palette.body, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                AppText(material.content.ifBlank { "此处最多显示两行可以吗。此处最多显示两行。超出省略号" }, AppTextRole.Body, modifier = Modifier.padding((24 * scale).dp), color = theme.text.copy(alpha = .5f), designScale = scale, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Figma 167:9679 exposes edit and delete beside a compact 88dp file card. */
@Composable
private fun ProjectSwipeFileContainer(
    scale: Float,
    editBackground: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val actionWidth = (88 * scale).dp
    val revealPx = with(LocalDensity.current) { ((actionWidth * 2) - (16 * scale).dp).toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(dragOffset, label = "file material swipe")
    val shape = RoundedCornerShape((36 * scale).dp)
    val dragState = rememberDraggableState { delta -> dragOffset = (dragOffset + delta).coerceIn(-revealPx, 0f) }
    Box(Modifier.fillMaxWidth().height((88 * scale).dp).clip(shape).clipToBounds()) {
        Row(Modifier.align(Alignment.CenterEnd).height((88 * scale).dp), horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
            Surface(onClick = onEdit, color = editBackground, shape = RoundedCornerShape((36 * scale).dp), modifier = Modifier.width(actionWidth).fillMaxHeight()) {
                Box(contentAlignment = Alignment.Center) { MaterialSymbol("edit", null, tint = Color(0xCC000000), size = fixedSp(24 * scale), filled = true) }
            }
            Surface(onClick = onDelete, color = AppColors.Warning, shape = RoundedCornerShape((36 * scale).dp), modifier = Modifier.width(actionWidth).fillMaxHeight()) {
                Box(contentAlignment = Alignment.Center) { MaterialSymbol("delete", null, tint = AppColors.TextIconLight, size = fixedSp(24 * scale), filled = true) }
            }
        }
        Box(
            Modifier.fillMaxSize().offset { IntOffset(offset.roundToInt(), 0) }.clip(shape)
                .draggable(dragState, Orientation.Horizontal, onDragStopped = { dragOffset = if (dragOffset < -revealPx / 2f) -revealPx else 0f })
        ) { content() }
    }
}

internal data class ProjectSwipeAction(val icon: String, val label: String, val background: Color, val content: Color, val onClick: () -> Unit)

@Composable
internal fun ProjectSwipeContainer(height: Float, actions: List<ProjectSwipeAction>, scale: Float, content: @Composable () -> Unit) {
    val actionWidth = (112 * scale).dp
    val revealPx = with(LocalDensity.current) { (actionWidth - (16 * scale).dp).toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(dragOffset, label = "project material swipe")
    val dragState = rememberDraggableState { delta -> dragOffset = (dragOffset + delta).coerceIn(-revealPx, 0f) }
    val cardShape = RoundedCornerShape((AppShapeRadius * scale).dp)
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

/** Wrap-content swipe reveal (deck cards have intrinsic heights). */
@Composable
internal fun ProjectSwipeAuto(actions: List<ProjectSwipeAction>, scale: Float, content: @Composable () -> Unit) {
    val actionWidth = (112 * scale).dp
    val revealPx = with(LocalDensity.current) { (actionWidth - (16 * scale).dp).toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(dragOffset, label = "wrap swipe")
    val dragState = rememberDraggableState { delta -> dragOffset = (dragOffset + delta).coerceIn(-revealPx, 0f) }
    val cardShape = RoundedCornerShape((AppShapeRadius * scale).dp)
    Box(Modifier.fillMaxWidth().clip(cardShape).clipToBounds()) {
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).width(actionWidth),
            verticalArrangement = Arrangement.spacedBy((8 * scale).dp)
        ) {
            actions.forEach { action ->
                Surface(
                    onClick = action.onClick, color = action.background, contentColor = action.content,
                    shape = RoundedCornerShape((36 * scale).dp),
                    modifier = Modifier.fillMaxWidth().height((60 * scale).dp)
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        MaterialSymbol(action.icon, null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                        Spacer(Modifier.height((4 * scale).dp))
                        AppText(action.label, AppTextRole.Label, modifier = Modifier.fillMaxWidth(), color = LocalContentColor.current, textAlign = TextAlign.Center, designScale = scale, maxLines = 1)
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
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

/** Figma 821:5124.  The filename dialog is shared by every file-card edit reveal. */
@Composable
internal fun FileNameEditorDialog(
    theme: DeckTheme,
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    LaunchedEffect(dialogWindow) {
        // Figma specifies Android's 20% black dim treatment behind the dialog.
        dialogWindow?.setDimAmount(.2f)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = theme.background,
            shape = RoundedCornerShape(36.dp),
            modifier = Modifier.width(331.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = theme.secondary,
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = appInputTextStyle(AppTextRole.Body, 1f, theme.text),
                        visualTransformation = rememberBilingualInputTransformation(AppTextRole.Body, 1f),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        decorationBox = { input ->
                            Box(Modifier.fillMaxWidth()) { input() }
                        }
                    )
                }
                Surface(
                    onClick = { onConfirm(title) },
                    color = theme.primary,
                    contentColor = theme.onPrimary,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MaterialSymbol("check", null, tint = LocalContentColor.current, size = fixedSp(24f), filled = true)
                        Spacer(Modifier.width(8.dp))
                        AppText("完成修改", AppTextRole.Label, color = LocalContentColor.current)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProjectTextEditorScreen(route: AppRoute.ProjectTextEditor, viewModel: AppViewModel, nav: ScreenNavigator) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val materials by viewModel.projectCreationMaterials.collectAsState()
    val projectMats by viewModel.projectMaterials.collectAsState()
    val importMats by viewModel.materialImportDrafts.collectAsState()
    val existing = (route.projectId?.let { projectMats[it] })?.firstOrNull { it.id == route.materialId }
        ?: materials.firstOrNull { it.id == route.materialId }
        ?: importMats.firstOrNull { it.id == route.materialId }
    // The text editor inherits the import page that opened it: Azure for global
    // material management, and the in-progress project family during creation.
    val theme = DeckThemes.firstOrNull { it.key == route.themeKey }
        ?: DeckThemes.first { it.key == "azure" }
    var title by rememberSaveable(route.materialId) { mutableStateOf(existing?.title.orEmpty()) }
    var content by rememberSaveable(route.materialId) { mutableStateOf(existing?.content.orEmpty()) }
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar(route.editorTitle, null, nav::goBack, backContainer = theme.cardPanel, titleColor = theme.text)
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp)
                .clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)),
            contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * scale).dp), verticalArrangement = Arrangement.spacedBy((12 * scale).dp)
        ) {
            item { ProjectTextField("文本标题", title, { title = it }, "标题标题", singleLine = true, theme = theme, scale = scale) }
            item { ProjectTextField("文本输入", content, { content = it }, "此处粘贴文本", singleLine = false, theme = theme, scale = scale) }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = AppColors.BaseBackground)
        Surface(
            onClick = {
                if (title.isBlank()) {
                    return@Surface
                }
                if (route.stageForMaterialImport) {
                    viewModel.upsertMaterialImportText(route.materialId, title, content)
                } else if (route.projectId == null) {
                    viewModel.upsertProjectDraftText(route.materialId, title, content)
                } else {
                    // A living project receives the text directly: POST materials/text, and the
                    // dialog-less result reports through the shared UI message.
                    viewModel.addTextMaterialToProject(route.projectId, route.materialId, title, content)
                }
                nav.goBack()
            }, color = theme.primary, contentColor = theme.onPrimary,
            shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp).fillMaxWidth().height((60 * scale).dp).zIndex(1f)
        ) { Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol("list_alt_check", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
            Spacer(Modifier.width((8 * scale).dp)); AppText("完成输入", AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
        } }
    }
}

@Composable
private fun ProjectTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, singleLine: Boolean, theme: DeckTheme, scale: Float) = Column(verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
    AppText(label, AppTextRole.SectionTitle, modifier = Modifier.padding(horizontal = (8 * scale).dp), color = theme.text, designScale = scale)
    // Figma 493:1386: inputs sit on the family Background (#EEF4FA) and the
    // content box is 453dp (not the smaller create-form default).
    Surface(color = theme.background, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth().height(if (singleLine) (75 * scale).dp else (453 * scale).dp)) {
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = singleLine,
            textStyle = appInputTextStyle(AppTextRole.Body, scale, theme.text), visualTransformation = rememberBilingualInputTransformation(AppTextRole.Body, scale),
            modifier = Modifier.fillMaxSize().padding((24 * scale).dp), decorationBox = { input -> Box(Modifier.fillMaxSize()) {
                // Figma 493:1386 placeholder ink is a solid dark slate (#242436).
                if (value.isBlank()) AppText(placeholder, AppTextRole.Body, color = Color(0xFF242436), designScale = scale)
                input()
            } }
        )
    }
}

private fun projectDocumentName(context: android.content.Context, uri: android.net.Uri): String = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
}.orEmpty().ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "未命名文件" }
