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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
    // Figma 494:1447: project cards expose 编辑/删除 through the shared swipe reveal; the
    // confirmation explains that deleting a project takes its decks with it (交接文档 决策②).
    var pendingProjectDeletion by rememberSaveable { mutableStateOf<String?>(null) }
    var projectDeletionInFlight by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground).statusBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            // Figma 494:1447: the former 添加项目/资料管理 button pair is gone;
            // creation lives on the root navigation's round add control.
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp),
                contentPadding = PaddingValues(bottom = (RootNavigationScrollTail * scale).dp)
            ) {
                items(visibleProjects, key = { it.id }) { project ->
                    ProjectSwipeAuto(
                        actions = listOf(
                            ProjectSwipeAction("edit", "编辑项目", AppColors.Card, AppColors.TextIconDark) {
                                nav.navigate(AppRoute.ProjectEdit(project.id))
                            },
                            ProjectSwipeAction("delete", "删除项目", AppColors.Warning, AppColors.TextIconLight) {
                                pendingProjectDeletion = project.id
                            },
                        ),
                        scale = scale
                    ) {
                        ProjectSummaryCard(project, decks.filter { it.projectId == project.id }, scale) {
                            nav.navigate(AppRoute.ProjectDetail(project.id))
                        }
                    }
                }
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
    }
    pendingProjectDeletion?.let { targetId ->
        val target = projects.firstOrNull { it.id == targetId } ?: return@let
        CuteConfirmDialog(
            title = "是否删除项目及所属卡组？",
            busy = projectDeletionInFlight,
            onConfirm = {
                if (projectDeletionInFlight) return@CuteConfirmDialog
                projectDeletionInFlight = true
                viewModel.deleteProject(target.id, retainDecks = false) { succeeded ->
                    projectDeletionInFlight = false
                    if (succeeded) pendingProjectDeletion = null
                }
            },
            onDismiss = { if (!projectDeletionInFlight) pendingProjectDeletion = null }
        )
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
    // 交接文档 4.1：编辑模式读 Room 里该项目的已落地资料（含解析状态）；
    // 创建模式读向导草稿——草稿在完成导入时并入，随项目创建一并上传。
    val wizardMaterials by viewModel.projectCreationMaterials.collectAsState()
    val projectMaterials by viewModel.projectMaterials.collectAsState()
    val materials = editingProject?.let { projectMaterials[it.id].orEmpty() } ?: wizardMaterials
    val pdfUploading by viewModel.pdfUploading.collectAsState()
    val projectCreating by viewModel.projectCreating.collectAsState()
    val projectId = editingProject?.id
    var name by rememberSaveable(projectId) { mutableStateOf(editingProject?.name.orEmpty()) }
    var nameError by rememberSaveable(projectId) { mutableStateOf(false) }
    var selectedTheme by rememberSaveable(projectId) { mutableStateOf(editingProject?.themeKey ?: "violet") }
    var message by remember { mutableStateOf<String?>(null) }
    var editingFile by remember { mutableStateOf<ProjectDraftMaterial?>(null) }
    var showProjectDeletionConfirmation by rememberSaveable(projectId) { mutableStateOf(false) }
    var projectDeletionInFlight by rememberSaveable(projectId) { mutableStateOf(false) }
    var pendingMaterialDeletion by remember { mutableStateOf<ProjectDraftMaterial?>(null) }
    var materialDeletionInFlight by remember { mutableStateOf(false) }
    val theme = DeckThemes.firstOrNull { it.key == selectedTheme } ?: DeckThemes.first()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.addProjectDraftFile(it, projectDocumentName(context, it)) }
    }
    // 编辑页失败 PDF 的「点击重试」= 换文件 replace 重传（V25-D-30）。
    var replaceTarget by remember { mutableStateOf<ProjectDraftMaterial?>(null) }
    val replacePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = replaceTarget
        if (uri != null && target?.materialId != null && projectId != null) {
            viewModel.replaceProjectMaterial(projectId, target.materialId, uri) { _, _ -> }
            viewModel.markMaterialImportParsing(target.id)
        }
        replaceTarget = null
    }
    // 设定替换目标后立即拉起系统文件选择器；取消时回调同样会清空目标。
    LaunchedEffect(replaceTarget) {
        if (replaceTarget != null) replacePicker.launch(arrayOf("application/pdf"))
    }

    // Figma 588:1922 uses a white page canvas.  The project family begins at
    // the 36dp section cards, which keeps each nested radius visually legible.
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar(
            title = if (editingProject == null) "添加项目" else "编辑项目", subtitle = null, onBack = nav::goBack,
            backContainer = theme.secondary, titleColor = theme.text,
            // Figma 1114:6608: the edit page's destructive entry is a #BD3F3F top-bar circle.
            onSecondaryTrailingAction = editingProject?.let { { showProjectDeletionConfirmation = true } },
            secondaryTrailingActionContainer = Color(0xFFBD3F3F),
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
                    ProjectCreationPanel(theme, scale) {
                        ProjectSectionLabel("stylus_note", "项目名称", theme, scale)
                        Surface(color = theme.cardPanel, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.fillMaxWidth().height((59 * scale).dp)) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = name, onValueChange = {
                                    name = it
                                    nameError = false
                                }, singleLine = true,
                                textStyle = appInputTextStyle(AppTextRole.Body, scale, theme.text),
                                visualTransformation = rememberBilingualInputTransformation(AppTextRole.Body, scale),
                                modifier = Modifier.fillMaxSize().padding(horizontal = (24 * scale).dp),
                                decorationBox = { input -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                    // Figma 588:1922 placeholder ink: rgba(36,36,54,0.5).
                                    if (name.isBlank()) AppText("此处输入名称", AppTextRole.Body, color = Color(0x80242436), designScale = scale)
                                    input()
                                } }
                            )
                        }
                        if (nameError) CardHint("未输入名称", designScale = scale, error = true)
                    }
                }
                item {
                    ProjectCreationPanel(theme, scale) {
                        ProjectSectionLabel("colors", "项目主题色", theme, scale)
                        Surface(color = AppColors.Card, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.fillMaxWidth().height((84 * scale).dp)) {
                            Row(Modifier.fillMaxSize().padding((12 * scale).dp), horizontalArrangement = Arrangement.spacedBy((12 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                                DeckThemes.forEach { choice ->
                                    val selected = selectedTheme == choice.key
                                    // 选中/未选中两侧都用连续 weight 过渡：原先「固定
                                    // width ↔ weight」的测量模式互换会让整行瞬间回流，
                                    // 表现为每次切换主题色卡片跳一下。1f+1.75f 使选中
                                    // 色块在 402dp 下约 121dp，与 Figma 一致。
                                    val expand by animateFloatAsState(
                                        if (selected) 1f else 0f,
                                        tween(500, easing = FastOutSlowInEasing),
                                        label = "${choice.key} color expand"
                                    )
                                    val corner by animateDpAsState(
                                        if (selected) (24 * scale).dp else 999.dp,
                                        tween(500, easing = FastOutSlowInEasing),
                                        label = "${choice.key} color corner"
                                    )
                                    val borderWidth by animateDpAsState(
                                        if (selected) 6.dp else 4.dp,
                                        tween(500, easing = FastOutSlowInEasing),
                                        label = "${choice.key} color border"
                                    )
                                    Surface(
                                        onClick = { selectedTheme = choice.key }, color = choice.primary,
                                        shape = RoundedCornerShape(corner),
                                        modifier = Modifier.weight(1f + expand * 1.75f).height((60 * scale).dp),
                                        border = androidx.compose.foundation.BorderStroke(borderWidth, AppColors.Card.copy(alpha = .5f))
                                    ) {
                                        Box(Modifier.alpha(expand), contentAlignment = Alignment.Center) {
                                            MaterialSymbol(
                                                "check",
                                                if (selected) "已选择${choice.label}" else null,
                                                tint = choice.onPrimary, size = fixedSp(24 * scale), filled = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    ProjectCreationMaterialsPanel(
                        title = "文件资料", icon = "files", hint = "右滑卡片可编辑、删除文件",
                        theme = theme, scale = scale,
                        materials = materials.filter { it.type == ProjectDraftMaterialType.FILE },
                        onEditFile = { editingFile = it }, onEditText = {},
                        onRetry = { material -> replaceTarget = material },
                        onDelete = { material ->
                            if (editingProject == null || material.materialId == null) viewModel.deleteProjectDraftMaterial(material.id)
                            else pendingMaterialDeletion = material
                        }
                    )
                }
                item {
                    ProjectCreationMaterialsPanel(
                        title = "文本资料", icon = "description", hint = "右滑卡片可编辑、删除文本",
                        theme = theme, scale = scale,
                        materials = materials.filter { it.type == ProjectDraftMaterialType.TEXT },
                        onEditFile = { editingFile = it },
                        onEditText = { material ->
                            nav.navigate(AppRoute.ProjectTextEditor(material.id, selectedTheme, editingProject?.id, editorTitle = "编辑文本"))
                        },
                        onRetry = {},
                        onDelete = { material ->
                            if (editingProject == null || material.materialId == null) viewModel.deleteProjectDraftMaterial(material.id)
                            else pendingMaterialDeletion = material
                        }
                    )
                }
                message?.let { error -> item { CardHint(error, designScale = scale, error = true) } }
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = AppColors.BaseBackground)
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                .fillMaxWidth().zIndex(1f),
            horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            // Figma 588:1922: the entry action hugs its content on family Surface.
            Surface(
                onClick = {
                    if (editingProject == null) {
                        // Figma 807:4441 导入资料直达：创建流程携带主题族，编辑流程绑定项目。
                        nav.navigate(AppRoute.MaterialImport(themeKey = selectedTheme, projectCreation = true))
                    } else {
                        nav.navigate(AppRoute.MaterialImport(projectId = editingProject.id, themeKey = selectedTheme))
                    }
                },
                color = theme.cardPanel, contentColor = theme.text,
                shape = RoundedCornerShape((24 * scale).dp),
                modifier = Modifier.height((68 * scale).dp)
            ) {
                // Hug the content: a fillMaxWidth child would swallow the row and
                // starve the weighted 完成设置 action beside it.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxHeight().padding(horizontal = (24 * scale).dp)) {
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
                    if (name.isBlank()) nameError = true
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
                    if (name.isBlank()) nameError = true
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
            modifier = Modifier.weight(1f).height((68 * scale).dp)
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
                if (material.projectId == null || material.materialId == null) viewModel.renameProjectDraftFile(material.id, updatedTitle)
                else viewModel.renameProjectFile(material.id, updatedTitle)
                editingFile = null
            },
            onDismiss = { editingFile = null }
        )
    }
    pendingMaterialDeletion?.let { material ->
        MaterialDeletionDialog(
            materialName = material.title,
            theme = theme,
            deleting = materialDeletionInFlight,
            onConfirm = { retainCards ->
                val targetId = material.projectId
                val materialId = material.materialId
                if (materialDeletionInFlight || targetId == null || materialId == null) return@MaterialDeletionDialog
                materialDeletionInFlight = true
                viewModel.deleteMaterial(targetId, materialId, retainCards) { succeeded ->
                    materialDeletionInFlight = false
                    if (succeeded) pendingMaterialDeletion = null
                }
            },
            onDismiss = { if (!materialDeletionInFlight) pendingMaterialDeletion = null }
        )
    }
    if (editingProject != null && showProjectDeletionConfirmation) {
        // Figma 1130:8079 + 交接文档 决策②：删除项目连带其全部卡组，说明文案明示。
        CuteConfirmDialog(
            title = "是否删除项目及所属卡组？",
            busy = projectDeletionInFlight,
            onConfirm = {
                if (projectDeletionInFlight) return@CuteConfirmDialog
                projectDeletionInFlight = true
                viewModel.deleteProject(editingProject.id, retainDecks = false) { succeeded ->
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
    hint: String,
    theme: DeckTheme,
    scale: Float,
    materials: List<ProjectDraftMaterial>,
    onEditFile: (ProjectDraftMaterial) -> Unit,
    onEditText: (ProjectDraftMaterial) -> Unit,
    onRetry: (ProjectDraftMaterial) -> Unit = {},
    onDelete: (ProjectDraftMaterial) -> Unit
) = ProjectCreationPanel(theme, scale) {
    Row(Modifier.padding(horizontal = (8 * scale).dp), horizontalArrangement = Arrangement.spacedBy((10 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbol(icon, null, tint = theme.text, size = fixedSp(24 * scale), filled = true)
        AppText(title, AppTextRole.SectionTitle, color = theme.text, designScale = scale)
    }
    materials.forEach { material ->
        // Figma 588:1922: finished cards wear their type glyph on family primary.
        ProjectCompactMaterialCard(
            material = material,
            theme = theme,
            scale = scale,
            doneIcon = if (material.type == ProjectDraftMaterialType.FILE) "files" else "description",
            onEdit = {
                if (material.type == ProjectDraftMaterialType.FILE) onEditFile(material) else onEditText(material)
            },
            onDelete = { onDelete(material) },
            onRetry = { onRetry(material) }
        )
    }
    CardHint(if (materials.isEmpty()) "暂无资料" else hint, designScale = scale)
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

/** Figma 1100:5634/5644: the two recognition states plus the failure fallback. */
internal enum class ProjectMaterialCardState { RECOGNIZING, DONE, FAILED }

/**
 * Wire status → card state. Creation drafts (null status) are recognized in
 * place: text is final locally, while a staged PDF keeps recognizing until
 * 完成设置 uploads it — the generation screen then owns the live progress.
 */
internal fun materialCardState(material: ProjectDraftMaterial): ProjectMaterialCardState = when (material.serverStatus) {
    "FAILED" -> ProjectMaterialCardState.FAILED
    "PENDING", "PARSING" -> ProjectMaterialCardState.RECOGNIZING
    else -> if (material.serverStatus != null || material.type == ProjectDraftMaterialType.TEXT) {
        ProjectMaterialCardState.DONE
    } else {
        ProjectMaterialCardState.RECOGNIZING
    }
}

/**
 * Figma 1100:5634/5644 compact material card: an 80dp cardPanel body holding the
 * 56dp state tile and the full-width title pill. RECOGNIZING swaps the tile for
 * the official MD3 progress ring; DONE shows the caller's glyph on family
 * primary; FAILED falls back to the warning tile with the error glyph and
 * hands taps to [onRetry]. The picker variant ([selectableOnly], Figma
 * 835:5466/807:4451) drops the swipe reveal: tapping a DONE card fires
 * [onSelect] and a picked card lifts to the green family.
 */
@Composable
internal fun ProjectCompactMaterialCard(
    material: ProjectDraftMaterial,
    theme: DeckTheme,
    scale: Float,
    doneIcon: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit = {},
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    selectableOnly: Boolean = false
) {
    val state = materialCardState(material)
    val card: @Composable () -> Unit = {
        val failed = state == ProjectMaterialCardState.FAILED
        val pickedDone = state == ProjectMaterialCardState.DONE && selected
        Surface(
            onClick = when {
                failed -> onRetry
                state == ProjectMaterialCardState.DONE && onSelect != null -> onSelect
                else -> ({})
            },
            enabled = failed || (state == ProjectMaterialCardState.DONE && onSelect != null),
            // Figma 807:4451 失败态：整卡 #E87F77；Figma 835:5466 选中态：绿色系。
            color = when {
                failed -> AppColors.WarningSecondary
                pickedDone -> AppColors.Green.surface
                else -> theme.cardPanel
            },
            shape = RoundedCornerShape((32 * scale).dp),
            modifier = Modifier.fillMaxWidth().height((80 * scale).dp).clip(RoundedCornerShape((32 * scale).dp))
        ) {
            Row(
                Modifier.fillMaxSize().padding((12 * scale).dp),
                horizontalArrangement = Arrangement.spacedBy((10 * scale).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = when {
                        failed -> AppColors.Warning
                        pickedDone -> AppColors.Green.primary
                        state == ProjectMaterialCardState.RECOGNIZING -> theme.secondary
                        else -> theme.primary
                    },
                    shape = RoundedCornerShape((24 * scale).dp),
                    modifier = Modifier.size((56 * scale).dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            state == ProjectMaterialCardState.RECOGNIZING -> CircularProgressIndicator(
                                color = theme.primary,
                                trackColor = Color.Transparent,
                                strokeCap = StrokeCap.Round,
                                strokeWidth = (3 * scale).dp,
                                modifier = Modifier.size((28 * scale).dp)
                            )
                            failed -> MaterialSymbol("error", null, tint = AppColors.TextIconLight, size = fixedSp(24 * scale), filled = true)
                            pickedDone -> MaterialSymbol("check_circle", null, tint = AppColors.Green.background, size = fixedSp(24 * scale), filled = true)
                            else -> MaterialSymbol(doneIcon, null, tint = theme.onPrimary, size = fixedSp(24 * scale), filled = true)
                        }
                    }
                }
                Surface(
                    color = when {
                        failed -> AppColors.Warning
                        pickedDone -> AppColors.Green.primarySecondary
                        else -> theme.secondary
                    },
                    shape = RoundedCornerShape((32 * scale).dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        AppText(
                            material.title.ifBlank { "未命名资料" },
                            AppTextRole.CardTitle,
                            modifier = Modifier.padding(horizontal = (24 * scale).dp),
                            color = if (failed) AppColors.TextIconLight else theme.text,
                            designScale = scale,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
    if (selectableOnly) {
        Column {
            card()
            // Figma 807:4441: the caption sits tight under the failed card.
            if (state == ProjectMaterialCardState.FAILED) {
                Spacer(Modifier.height((4 * scale).dp))
                MaterialFailureHint(failureReasonText(material.errorCode), scale)
            }
        }
    } else {
        ProjectSwipeCompactContainer(scale = scale, onEdit = onEdit, onDelete = onDelete) {
            card()
        }
        // Figma 807:4441: the caption sits tight under the failed card, inside the same swipe viewport.
        if (state == ProjectMaterialCardState.FAILED) {
            Spacer(Modifier.height((4 * scale).dp))
            MaterialFailureHint(failureReasonText(material.errorCode), scale)
        }
    }
}

/** The compact card's swipe reveal: two side-by-side square actions, the user's existing pattern. */
@Composable
private fun ProjectSwipeCompactContainer(
    scale: Float,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val actionWidth = (80 * scale).dp
    val revealPx = with(LocalDensity.current) { ((actionWidth * 2) - (16 * scale).dp).toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(dragOffset, label = "compact material swipe")
    val shape = RoundedCornerShape((32 * scale).dp)
    val dragState = rememberDraggableState { delta -> dragOffset = (dragOffset + delta).coerceIn(-revealPx, 0f) }
    Box(Modifier.fillMaxWidth().height((80 * scale).dp).clip(shape).clipToBounds()) {
        Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
            Surface(onClick = onEdit, color = AppColors.Card, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.width(actionWidth).fillMaxHeight()) {
                Box(contentAlignment = Alignment.Center) { MaterialSymbol("edit", null, tint = Color(0xCC000000), size = fixedSp(24 * scale), filled = true) }
            }
            Surface(onClick = onDelete, color = AppColors.Warning, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.width(actionWidth).fillMaxHeight()) {
                Box(contentAlignment = Alignment.Center) { MaterialSymbol("delete", null, tint = AppColors.TextIconLight, size = fixedSp(24 * scale), filled = true) }
            }
        }
        Box(
            Modifier.fillMaxSize().offset { IntOffset(offset.roundToInt(), 0) }.clip(shape)
                .draggable(dragState, Orientation.Horizontal, onDragStopped = { dragOffset = if (dragOffset < -revealPx / 2f) -revealPx else 0f })
        ) { content() }
    }
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
    /** Figma 835:5466: the generation screen picks materials in place — no swipe reveal. */
    selectableOnly: Boolean = false,
    onDelete: () -> Unit
) {
    val card: @Composable () -> Unit = {
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
    if (selectableOnly) {
        card()
    } else {
        ProjectSwipeFileContainer(
            scale = scale,
            // The edit reveal is the family's Primary-Secondary semantic, distinct
            // from both the exposed record and the destructive Warning action.
            editBackground = theme.secondary,
            onEdit = onEdit,
            onDelete = onDelete
        ) {
            card()
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
    parentSurface: ProjectMaterialCardParentSurface = ProjectMaterialCardParentSurface.THEME_BACKGROUND,
    /** Figma 835:5466: the generation screen picks materials in place — no swipe reveal. */
    selectableOnly: Boolean = false,
    /** FAILED cards hand taps to the caller's replace/retry action (Figma 807:4451). */
    onRetry: () -> Unit = {}
) {
    val card: @Composable () -> Unit = {
        val isSelectable = kind == ProjectMaterialTextCardKind.SELECTABLE
        val palette = projectMaterialCardPalette(theme, parentSurface, selected)
        // Figma 807:4451: 解析中/失败 replace the ready glyph — 解析中 lifts the tile
        // to the family Primary-Secondary progress treatment, 失败 turns the whole
        // card Warning-Secondary with the Warning tile and collapses to header-only.
        val state = materialCardState(material)
        val failed = state == ProjectMaterialCardState.FAILED
        val recognizing = state == ProjectMaterialCardState.RECOGNIZING
        Surface(
            onClick = when {
                failed -> onRetry
                onSelect != null -> onSelect
                else -> ({})
            },
            enabled = failed || onSelect != null,
            color = if (failed) AppColors.WarningSecondary else palette.card,
            shape = RoundedCornerShape((32 * scale).dp),
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape((32 * scale).dp))
        ) {
            Column(Modifier.fillMaxSize().padding((12 * scale).dp), verticalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
                if (isSelectable) {
                    // Figma 796:6784 / 796:6785: a 56dp-wide icon tile fills the
                    // title pill's 16 + 24 + 16 = 56dp height; the title itself
                    // steps down to the Card-Title level.
                    Row(Modifier.fillMaxWidth().height((56 * scale).dp), horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
                        Surface(
                            color = when {
                                failed -> AppColors.Warning
                                recognizing -> theme.secondary
                                selected -> AppColors.Green.primary
                                else -> theme.primary
                            },
                            shape = RoundedCornerShape((24 * scale).dp),
                            modifier = Modifier.width((56 * scale).dp).fillMaxHeight()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                when {
                                    recognizing -> CircularProgressIndicator(
                                        color = theme.primary,
                                        trackColor = Color.Transparent,
                                        strokeCap = StrokeCap.Round,
                                        strokeWidth = (3 * scale).dp,
                                        modifier = Modifier.size((28 * scale).dp)
                                    )
                                    failed -> MaterialSymbol("error", null, tint = AppColors.TextIconLight, size = fixedSp(24 * scale), filled = true)
                                    selected -> MaterialSymbol("check_circle", null, tint = AppColors.Green.background, size = fixedSp(24 * scale), filled = true)
                                    else -> MaterialSymbol("file_copy", null, tint = theme.background, size = fixedSp(24 * scale), filled = true)
                                }
                            }
                        }
                        Surface(
                            color = if (failed) AppColors.Warning else palette.title,
                            shape = RoundedCornerShape((32 * scale).dp),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                AppText(
                                    material.title,
                                    AppTextRole.CardTitle,
                                    modifier = Modifier.padding(horizontal = (24 * scale).dp),
                                    color = if (failed) AppColors.TextIconLight else theme.text,
                                    designScale = scale,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
                            AppText(material.title, AppTextRole.CardTitle, color = theme.text, designScale = scale, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            materialStatusLine(material)?.let { status ->
                                AppText(status, AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .6f), designScale = scale, maxLines = 1)
                            }
                        }
                    }
                }
                // Figma 807:4451: the parsing/failed variants are header-only cards.
                if (!(isSelectable && state != ProjectMaterialCardState.DONE)) {
                    Surface(color = palette.body, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                        AppText(material.content.ifBlank { "此处最多显示两行可以吗。此处最多显示两行。超出省略号" }, AppTextRole.Body, modifier = Modifier.padding((24 * scale).dp), color = Color.Black.copy(alpha = .5f), designScale = scale, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (selectableOnly) {
        card()
        if (materialCardState(material) == ProjectMaterialCardState.FAILED) {
            Spacer(Modifier.height((8 * scale).dp))
            MaterialFailureHint(failureReasonText(material.errorCode), scale)
        }
    } else {
        ProjectSwipeContainer(
            // Figma 648:2818 = 238dp management preview; Figma 796:6786 = 211dp
            // selectable preview. The viewport stays 36dp while its content is 32dp.
            // Figma 796:6786 typography: the title pill is Card-Title 18/24, so the
            // selectable viewport = 12 + 56 header + 10 + 102 two-line body + 12;
            // management's full-width title pill pads 24 and totals 208.
            height = if (kind == ProjectMaterialTextCardKind.MANAGEMENT) 208f else 192f,
            actions = listOf(
                // Figma 796:6786: delete is Warning Primary; the edit reveal returns to
                // white with the 80% neutral ink.
                ProjectSwipeAction("delete", "删除该卡", AppColors.Warning, theme.onPrimary, onDelete),
                ProjectSwipeAction(
                    "edit", "编辑卡片",
                    AppColors.Card,
                    AppColors.TextIconDark,
                    onEdit
                )
            ),
            scale = scale
        ) {
            card()
        }
        if (materialCardState(material) == ProjectMaterialCardState.FAILED) {
            Spacer(Modifier.height((8 * scale).dp))
            MaterialFailureHint(failureReasonText(material.errorCode), scale)
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
            // Figma 1107:6209 识别内容弹窗: 331dp wide, radius 36, 16dp padding/gaps.
            shape = RoundedCornerShape(36.dp),
            modifier = Modifier.width(331.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
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
                        MaterialSymbol("check_circle", null, tint = LocalContentColor.current, size = fixedSp(24f), filled = true)
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
        // Figma 1107:6361: the save action hugs its content, centred over the fade.
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
            shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(vertical = (16 * scale).dp).height((68 * scale).dp).zIndex(1f)
        ) { Row(Modifier.fillMaxHeight().padding(horizontal = (36 * scale).dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol("list_alt_check", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
            Spacer(Modifier.width((8 * scale).dp)); AppText("完成输入", AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
        } }
    }
}

@Composable
private fun ProjectTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, singleLine: Boolean, theme: DeckTheme, scale: Float) = Column(verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
    AppText(label, AppTextRole.SectionTitle, modifier = Modifier.padding(horizontal = (8 * scale).dp), color = theme.text, designScale = scale)
    // Figma 1107:6361: inputs sit on the family Background at radius 36 with
    // 24dp padding and hug heights — the paste area grows with its content.
    Surface(
        color = theme.background,
        shape = RoundedCornerShape((36 * scale).dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = ((if (singleLine) 75 else 200) * scale).dp)
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = singleLine,
            textStyle = appInputTextStyle(AppTextRole.Body, scale, theme.text), visualTransformation = rememberBilingualInputTransformation(AppTextRole.Body, scale),
            modifier = Modifier.fillMaxWidth().padding((24 * scale).dp), decorationBox = { input -> Box(Modifier.fillMaxSize()) {
                // Figma 1107:6361 placeholder ink is rgba(36,36,54,0.5).
                if (value.isBlank()) AppText(placeholder, AppTextRole.Body, color = Color(0x80242436), designScale = scale)
                input()
            } }
        )
    }
}

private fun projectDocumentName(context: android.content.Context, uri: android.net.Uri): String = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
}.orEmpty().ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "未命名文件" }
