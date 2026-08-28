package com.qiuzhao.flashcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qiuzhao.flashcards.ui.navigation.AppRoute

/** Figma 796:6935 / 796:6589 / 796:6786 / 796:6936.
 *
 * This is an attachment picker inside project creation, so every non-selection
 * colour comes from the selected project family.  Green is reserved for the
 * explicit selected state and never borrowed as a project accent.
 */
@Composable
internal fun ProjectMaterialPickerScreen(route: AppRoute.ProjectMaterialPicker, viewModel: AppViewModel, nav: ScreenNavigator) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = DeckThemes.firstOrNull { it.key == route.themeKey } ?: DeckThemes.first { it.key == "violet" }
    val materials by viewModel.projectCreationMaterials.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var editingFile by remember { mutableStateOf<ProjectDraftMaterial?>(null) }
    val shown = materials.filter { material ->
        query.isBlank() || material.title.contains(query, ignoreCase = true) || material.content.contains(query, ignoreCase = true)
    }
    val files = shown.filter { it.type == ProjectDraftMaterialType.FILE }
    val texts = shown.filter { it.type == ProjectDraftMaterialType.TEXT }
    val toggle: (String) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        ScreenTopInformationBar(
            title = "导入资料", subtitle = null, onBack = nav::goBack,
            backContainer = theme.secondary, titleColor = theme.text
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
                .padding(start = (16 * scale).dp, top = (187 * scale).dp, end = (16 * scale).dp)
                .clip(RoundedCornerShape((24 * scale).dp)),
            contentPadding = PaddingValues(bottom = (116 * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            item {
                ProjectPickerGroup(
                    title = "文件资料", icon = "files", theme = theme, scale = scale,
                    hasMaterials = files.isNotEmpty()
                ) {
                    files.forEach { material ->
                        val selected = material.id in selectedIds
                        ProjectDraftFileCard(
                            material = material, theme = theme, scale = scale,
                            onEdit = { editingFile = material }, selected = selected, onSelect = { toggle(material.id) }
                        ) { viewModel.deleteProjectDraftMaterial(material.id) }
                    }
                }
            }
            item {
                ProjectPickerGroup(
                    title = "文本资料", icon = "description", theme = theme, scale = scale,
                    hasMaterials = texts.isNotEmpty()
                ) {
                    texts.forEach { material ->
                        val selected = material.id in selectedIds
                        ProjectDraftTextCard(
                            material = material, theme = theme, scale = scale,
                            onEdit = {
                                nav.navigate(AppRoute.ProjectTextEditor(material.id, theme.key, editorTitle = "编辑文本资料"))
                            }, onDelete = { viewModel.deleteProjectDraftMaterial(material.id) },
                            selected = selected, onSelect = { toggle(material.id) },
                            kind = ProjectMaterialTextCardKind.SELECTABLE
                        )
                    }
                }
            }
        }
        MaterialSearchField(
            query = query,
            onQueryChange = { query = it },
            theme = theme,
            scale = scale,
            modifier = Modifier.statusBarsPadding()
                .padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp)
                .fillMaxWidth()
        )
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = Color.White)
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                .fillMaxWidth().zIndex(1f),
            horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)
        ) {
            Surface(
                onClick = {
                    viewModel.beginMaterialImport()
                    nav.navigate(AppRoute.MaterialImport(themeKey = theme.key, projectCreation = true))
                },
                color = theme.secondary, contentColor = theme.text, shape = RoundedCornerShape((24 * scale).dp),
                modifier = Modifier.weight(1f).height((60 * scale).dp)
            ) {
                PickerButtonContent("note_stack_add", "添加新资料", scale)
            }
            Surface(
                onClick = { nav.goBack() }, color = theme.primary, contentColor = theme.onPrimary,
                shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.weight(1f).height((60 * scale).dp)
            ) {
                PickerButtonContent("folder_open", "完成导入", scale)
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
}

@Composable
private fun ProjectPickerGroup(
    title: String,
    icon: String,
    theme: DeckTheme,
    scale: Float,
    hasMaterials: Boolean,
    content: @Composable ColumnScope.() -> Unit
) = Surface(
    color = theme.background, shape = RoundedCornerShape((36 * scale).dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape((36 * scale).dp))
) {
    Column(Modifier.padding((20 * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
        Row(Modifier.padding(horizontal = (8 * scale).dp), horizontalArrangement = Arrangement.spacedBy((10 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol(icon, null, tint = theme.text, size = fixedSp(24 * scale), filled = true)
            AppText(title, AppTextRole.SectionTitle, color = theme.text, designScale = scale)
        }
        Surface(color = Color.White, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.fillMaxWidth()) {
            AppText(
                if (hasMaterials) "右滑卡片可编辑文件名称/删除文件。点击卡片完成选择"
                else "暂无资料。点击下方“添加新资料”按钮来添加资料",
                AppTextRole.Supporting,
                modifier = Modifier.padding((24 * scale).dp),
                color = theme.text,
                designScale = scale
            )
        }
        if (hasMaterials) Column(verticalArrangement = Arrangement.spacedBy((12 * scale).dp), content = content)
    }
}

@Composable
private fun PickerButtonContent(icon: String, label: String, scale: Float) = Row(
    Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
) {
    MaterialSymbol(icon, null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
    Spacer(Modifier.width((8 * scale).dp))
    AppText(label, AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
}
