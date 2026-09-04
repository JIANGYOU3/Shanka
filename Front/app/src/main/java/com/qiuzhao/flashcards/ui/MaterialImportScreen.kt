package com.qiuzhao.flashcards.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.qiuzhao.flashcards.ui.navigation.AppNavigator
import com.qiuzhao.flashcards.ui.navigation.AppRoute

/** Figma 775:3842 / 783:4135.  Material is committed only after recognition ends. */
@Composable
internal fun MaterialImportScreen(
    route: AppRoute.MaterialImport,
    viewModel: AppViewModel,
    navigator: AppNavigator
) {
    // The global material-management flow is Azure brand colour. The project
    // creation variant receives the in-progress project's theme explicitly.
    val theme = remember(route.themeKey) {
        DeckThemes.firstOrNull { it.key == route.themeKey }
            ?: DeckThemes.first { it.key == "azure" }
    }
    val materials by viewModel.materialImportDrafts.collectAsState()
    val pdfUploading by viewModel.pdfUploading.collectAsState()
    var isRecognizing by rememberSaveable { mutableStateOf(false) }
    var editingFile by remember { mutableStateOf<ProjectDraftMaterial?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.stageMaterialImportFiles(uris)
    }

    LaunchedEffect(isRecognizing) {
        if (!isRecognizing) return@LaunchedEffect
        viewModel.commitMaterialImport(route.projectId) { success, _ ->
            isRecognizing = false
            if (success) navigator.goBack()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        LazyColumn(
            // Figma 783:4135 owns a 370dp-wide, 24dp-rounded scroll viewport.
            // The clip is deliberately on the viewport, not only on each child,
            // so long cards fade/crop cleanly beneath the fixed action.
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = 136.dp, end = 16.dp)
                .clip(RoundedCornerShape(24.dp)),
            contentPadding = PaddingValues(bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ImportAddPanel(
                    theme = theme,
                    onChooseFile = { filePicker.launch(arrayOf("application/pdf")) },
                    onEnterText = {
                        val draftId = viewModel.stageMaterialImportText()
                        navigator.navigate(
                            AppRoute.ProjectTextEditor(
                                materialId = draftId,
                                themeKey = theme.key,
                                projectId = route.projectId,
                                stageForMaterialImport = true,
                                editorTitle = "导入文本",
                            )
                        )
                    }
                )
            }
            item {
                ImportPreviewGroup(
                    theme = theme, title = "文件资料", icon = "files",
                    materials = materials.filter { it.type == ProjectDraftMaterialType.FILE },
                    onEditFile = { editingFile = it },
                    onEditText = {}, onDelete = viewModel::removeMaterialImportDraft
                )
            }
            item {
                ImportPreviewGroup(
                    theme = theme, title = "文本资料", icon = "description",
                    materials = materials.filter { it.type == ProjectDraftMaterialType.TEXT },
                    emptyHint = "暂无添加",
                    onEditFile = { editingFile = it },
                    onEditText = { material ->
                        navigator.navigate(
                            AppRoute.ProjectTextEditor(
                                materialId = material.id, themeKey = theme.key, projectId = route.projectId,
                                stageForMaterialImport = true, editorTitle = "编辑文本资料"
                            )
                        )
                    },
                    onDelete = viewModel::removeMaterialImportDraft
                )
            }
        }

        ScreenTopInformationBar(
            title = if (route.projectCreation) "添加新资料" else "导入资料", subtitle = null, onBack = navigator::goBack,
            backContainer = theme.cardPanel, titleColor = theme.text
        )
        // Shared fixed-action fade: clips the scrolling card region visually
        // before it meets the bottom control, as in Figma 720:2251.
        BottomContentFade(1f, Modifier.align(Alignment.BottomCenter), color = Color.White)
        Surface(
            color = theme.primary, contentColor = theme.onPrimary, shape = RoundedCornerShape(24.dp),
            onClick = { if (materials.isNotEmpty()) isRecognizing = true },
            enabled = !pdfUploading,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().zIndex(1f)
                .padding(horizontal = 16.dp, vertical = 16.dp).height(60.dp)
        ) {
            Row(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterVertically) {
                MaterialSymbol("scan", null, tint = theme.onPrimary, size = fixedSp(24f))
                Spacer(Modifier.width(10.dp))
                AppText("识别并导入", AppTextRole.Label, color = theme.onPrimary)
            }
        }
    }
    editingFile?.let { material ->
        FileNameEditorDialog(
            theme = theme, initialTitle = material.title,
            onConfirm = { updatedTitle ->
                viewModel.renameMaterialImportFile(material.id, updatedTitle)
                editingFile = null
            },
            onDismiss = { editingFile = null }
        )
    }
    if (isRecognizing) RecognitionDialog(theme)
}

@Composable
private fun ImportAddPanel(
    theme: DeckTheme,
    onChooseFile: () -> Unit,
    onEnterText: () -> Unit
) = Surface(
    color = theme.background, shape = RoundedCornerShape(36.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp))
) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol(
                "note_stack_add", null, tint = theme.text, size = fixedSp(24f), filled = true,
                includeFontPadding = false
            )
            Spacer(Modifier.width(10.dp))
            AppText("添加学习资料", AppTextRole.SectionTitle, color = theme.text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ImportChoice("picture_as_pdf", "选择 PDF", "支持多份 PDF 文件，异步解析", theme, onChooseFile)
            ImportChoice("file_copy", "输入文本", "粘贴文本资料，30000 字以内", theme, onEnterText)
        }
    }
}

@Composable
private fun ImportChoice(icon: String, title: String, subtitle: String, theme: DeckTheme, onClick: () -> Unit) = Surface(
    color = theme.primary, contentColor = theme.onPrimary, shape = RoundedCornerShape(32.dp), onClick = onClick,
    modifier = Modifier.fillMaxWidth().height(80.dp)
) {
    Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // Figma 783:4136 / 783:4145: the 56dp icon tile is a 24dp nested
        // surface, not the previous 28dp treatment that made the actions look heavy.
        Surface(color = theme.cardPanel, shape = RoundedCornerShape(24.dp), modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) {
                MaterialSymbol(icon, null, tint = theme.strongText, size = fixedSp(24f), filled = true, includeFontPadding = false)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(title, AppTextRole.CardTitle, color = theme.onPrimary)
            AppText(subtitle, AppTextRole.CardSubtitle, color = theme.onPrimary.copy(alpha = .86f))
        }
        // Figma 783:4145 reserves a full 56dp trailing alignment container;
        // placing the 24dp glyph directly in the Row shifts it 16dp too far.
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            MaterialSymbol("arrow_forward", null, tint = theme.onPrimary, size = fixedSp(24f), includeFontPadding = false)
        }
    }
}

@Composable
private fun ImportPreviewGroup(
    theme: DeckTheme,
    title: String,
    icon: String,
    materials: List<ProjectDraftMaterial>,
    emptyHint: String = "暂无添加",
    onEditFile: (ProjectDraftMaterial) -> Unit,
    onEditText: (ProjectDraftMaterial) -> Unit,
    onDelete: (String) -> Unit
) = Surface(color = theme.background, shape = RoundedCornerShape(36.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp))) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol(icon, null, tint = theme.text, size = fixedSp(24f), filled = true, includeFontPadding = false)
            Spacer(Modifier.width(10.dp))
            AppText(if (materials.isEmpty()) title else "刚添加的$title", AppTextRole.SectionTitle, color = theme.text)
        }
        if (materials.isEmpty()) {
            Surface(color = Color.White, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AppText(emptyHint, AppTextRole.Supporting, color = theme.text.copy(alpha = .5f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                materials.forEach { material ->
                    if (material.type == ProjectDraftMaterialType.FILE) {
                        ProjectDraftFileCard(material, theme, 1f, onEdit = { onEditFile(material) }) { onDelete(material.id) }
                    } else {
                        ProjectDraftTextCard(material, theme, 1f, onEdit = { onEditText(material) }, onDelete = { onDelete(material.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionDialog(theme: DeckTheme) {
    Dialog(onDismissRequest = {}) {
        Surface(color = Color(0xFFF0F8FF), shape = RoundedCornerShape(32.dp), modifier = Modifier.width(331.dp)) {
            Column(
                modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                GenerationProgressRing(color = theme.primary, trackColor = theme.secondary, designScale = 1f)
                AppText("正在提交 PDF 文件", AppTextRole.PageTitle, color = theme.text, textAlign = TextAlign.Center)
                AppText("正在发送到服务端", AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .5f), textAlign = TextAlign.Center)
            }
        }
    }
}

private fun Uri.displayName(context: Context): String {
    context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))?.let { return it }
    }
    return lastPathSegment?.substringAfterLast('/') ?: "未命名文件"
}
