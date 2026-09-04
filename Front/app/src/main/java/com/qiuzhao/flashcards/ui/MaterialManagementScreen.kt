package com.qiuzhao.flashcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.ui.navigation.AppRoute

/**
 * Figma 781:4012 / 781:3846. This is a Blue/white materials workflow even
 * when it was opened from a coloured project: material type is its own visual
 * semantic, while the project id only scopes the stored entries. Server-backed
 * materials delete through DELETE /materials/{material_id} with the user's
 * retain-cards decision; creation drafts still delete locally in place.
 */
@Composable
internal fun MaterialManagementScreen(project: ProjectSummary?, viewModel: AppViewModel, nav: ScreenNavigator) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = DeckThemes.first { it.key == "azure" }
    val drafts by viewModel.projectCreationMaterials.collectAsState()
    val projectMats by viewModel.projectMaterials.collectAsState()
    // Global entry (project == null) is the app-wide materials library: every project's
    // server-backed materials plus any unbound creation drafts.
    val list = project?.let { projectMats[it.id] }
        ?: (projectMats.values.flatten() + drafts)
    var query by rememberSaveable { mutableStateOf("") }
    var editingFile by remember { mutableStateOf<ProjectDraftMaterial?>(null) }
    /** Server-backed material awaiting its deletion confirmation (three-tier delete). */
    var pendingMaterialDeletion by remember { mutableStateOf<ProjectDraftMaterial?>(null) }
    var materialDeletionInFlight by remember { mutableStateOf(false) }
    val filtered = list.filter { material -> query.isBlank() || material.title.contains(query, true) || material.content.contains(query, true) }
    val textItems = filtered.filter { it.type == ProjectDraftMaterialType.TEXT }
    val fileItems = filtered.filter { it.type == ProjectDraftMaterialType.FILE }
    val hasMaterials = list.isNotEmpty()

    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar(
            title = "资料管理", subtitle = null, onBack = nav::goBack,
            backContainer = theme.cardPanel, titleColor = theme.text
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
                .padding(start = (16 * scale).dp, top = ((if (hasMaterials) 187 else 88) * scale).dp, end = (16 * scale).dp)
                .clip(RoundedCornerShape((24 * scale).dp)),
            contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            item {
                MaterialManagementGroup(
                    title = "文件资料", icon = "files", materials = fileItems, theme = theme, scale = scale,
                    onEditFile = { editingFile = it }, onEditText = {},
                    onDelete = { material ->
                        if (material.projectId == null || material.materialId == null) viewModel.deleteProjectDraftMaterial(material.id)
                        else pendingMaterialDeletion = material
                    }
                )
            }
            item {
                MaterialManagementGroup(
                    title = "文本资料", icon = "description", materials = textItems, theme = theme, scale = scale,
                    onEditFile = { editingFile = it },
                    onEditText = { material -> nav.navigate(AppRoute.ProjectTextEditor(material.id, theme.key, project?.id, editorTitle = "编辑文本资料")) },
                    onDelete = { material ->
                        if (material.projectId == null || material.materialId == null) viewModel.deleteProjectDraftMaterial(material.id)
                        else pendingMaterialDeletion = material
                    }
                )
            }
        }
        if (hasMaterials) {
            MaterialSearchField(
                query = query,
                onQueryChange = { query = it },
                theme = theme,
                scale = scale,
                modifier = Modifier.statusBarsPadding()
                    .padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp)
                    .fillMaxWidth()
            )
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = AppColors.BaseBackground)
        Surface(
            onClick = {
                viewModel.beginMaterialImport()
                nav.navigate(AppRoute.MaterialImport(project?.id))
            },
            color = theme.primary, contentColor = theme.onPrimary,
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                .fillMaxWidth().height((60 * scale).dp).zIndex(1f)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbol("folder_open", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                AppText("添加资料", AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
            }
        }
    }
    editingFile?.let { material ->
        FileNameEditorDialog(
            theme = theme, initialTitle = material.title,
            onConfirm = { updatedTitle ->
                if (project == null) viewModel.renameProjectDraftFile(material.id, updatedTitle)
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
}

/**
 * The three-tier material deletion confirmation (V25-D-30): the destructive action plus the
 * card-retention decision. No per-material preflight endpoint exists, so the impact line states
 * the contract honestly — the generated cards are kept or removed with the material, and tasks
 * still referencing it are cancelled silently by the server.
 */
@Composable
internal fun MaterialDeletionDialog(
    materialName: String,
    theme: DeckTheme,
    deleting: Boolean = false,
    onConfirm: (retainCards: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!deleting) onDismiss() }) {
        Surface(
            color = theme.background,
            shape = RoundedCornerShape(36.dp),
            modifier = Modifier.width(331.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText("删除资料“$materialName”吗？", AppTextRole.SectionTitle, color = theme.text, maxLines = 2)
                AppText(
                    "该资料将从项目中移除；引用它的进行中任务会被取消。该资料生成的卡片可以选择保留或一并删除。",
                    AppTextRole.CardSubtitle,
                    color = theme.text.copy(alpha = .6f),
                )
                CompactDeletionButton("删除资料，保留卡片", theme, deleting) { onConfirm(true) }
                CompactDeletionButton("删除资料及卡片", theme, deleting, destructive = true) { onConfirm(false) }
            }
        }
    }
}

@Composable
private fun MaterialManagementGroup(
    title: String,
    icon: String,
    materials: List<ProjectDraftMaterial>,
    theme: DeckTheme,
    scale: Float,
    onEditFile: (ProjectDraftMaterial) -> Unit,
    onEditText: (ProjectDraftMaterial) -> Unit,
    onDelete: (ProjectDraftMaterial) -> Unit
) = Surface(
    color = theme.background,
    shape = RoundedCornerShape((36 * scale).dp),
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape((36 * scale).dp))
) {
    Column(
        modifier = Modifier.padding((20 * scale).dp),
        verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = (8 * scale).dp),
            horizontalArrangement = Arrangement.spacedBy((10 * scale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbol(icon, null, tint = theme.text, size = fixedSp(24 * scale), filled = true)
            AppText(title, AppTextRole.SectionTitle, color = theme.text, designScale = scale)
        }
        Surface(
            color = AppColors.Card,
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(Modifier.padding((24 * scale).dp), contentAlignment = Alignment.CenterStart) {
                val instruction = if (materials.isEmpty()) {
                    "暂无资料。点击下方“导入资料”按钮来添加资料"
                } else if (title == "文件资料") {
                    "右滑卡片可编辑文件名称/删除文件"
                } else {
                    "右滑卡片可编辑内容/删除文件"
                }
                AppText(instruction, AppTextRole.Supporting, color = Color(0xCC000000), designScale = scale)
            }
        }
        materials.forEach { material ->
            if (material.type == ProjectDraftMaterialType.FILE) {
                ProjectDraftFileCard(material, theme, scale, onEdit = { onEditFile(material) }) { onDelete(material) }
            } else {
                ProjectDraftTextCard(material, theme, scale, onEdit = { onEditText(material) }, onDelete = { onDelete(material) })
            }
        }
    }
}

/**
 * Figma 796:6925. Shared by the global materials page and the project-creation
 * picker so only its colour family changes; all geometry remains 402dp exact.
 */
@Composable
internal fun MaterialSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    theme: DeckTheme,
    scale: Float,
    modifier: Modifier = Modifier
) = Surface(
    color = theme.secondary, shape = RoundedCornerShape((32 * scale).dp),
    modifier = modifier.height((80 * scale).dp)
) {
    Row(Modifier.fillMaxSize().padding((12 * scale).dp), horizontalArrangement = Arrangement.spacedBy((10 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = theme.primary, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.size((56 * scale).dp)) {
            Box(contentAlignment = Alignment.Center) { MaterialSymbol("search", null, tint = theme.onPrimary, size = fixedSp(28 * scale), filled = true) }
        }
        Surface(color = theme.background, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.weight(1f).fillMaxSize()) {
            androidx.compose.foundation.text.BasicTextField(
                value = query, onValueChange = onQueryChange, singleLine = true,
                textStyle = appInputTextStyle(AppTextRole.Body, scale, theme.text), visualTransformation = rememberBilingualInputTransformation(AppTextRole.Body, scale),
                modifier = Modifier.fillMaxSize().padding(horizontal = (24 * scale).dp), decorationBox = { input ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) AppText("搜索", AppTextRole.Body, color = theme.text.copy(alpha = .7f), designScale = scale)
                        input()
                    }
                }
            )
        }
    }
}
