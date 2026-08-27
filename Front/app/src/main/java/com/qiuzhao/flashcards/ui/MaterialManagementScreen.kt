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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.ui.navigation.AppRoute

/**
 * Figma 531:3013 资料管理. A global Blue/white screen (independent of any project
 * theme). Reuses the shared swipe cards: text cards reveal edit + delete, file
 * cards reveal delete only. The "导入资料" action is a display control pending the
 * confirmed material import contract.
 */
@Composable
internal fun MaterialManagementScreen(project: ProjectSummary?, viewModel: AppViewModel, nav: ScreenNavigator) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    // A project-scoped 资料管理 follows the project theme; the unscoped root
    // entry stays on the global Blue/white sheet per Figma 531:3013.
    val theme = project?.let(::deckTheme) ?: DeckThemes.first { it.key == "azure" }
    val drafts by viewModel.projectCreationMaterials.collectAsState()
    val projectMats by viewModel.projectMaterials.collectAsState()
    val list = project?.let { projectMats[it.id] } ?: drafts
    val textItems = list.filter { it.type == ProjectDraftMaterialType.TEXT }
    val fileItems = list.filter { it.type == ProjectDraftMaterialType.FILE }

    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar(
            title = "资料管理", subtitle = null, onBack = nav::goBack,
            backContainer = theme.cardPanel, titleColor = theme.text
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
                .padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp)
                .clip(RoundedCornerShape((24 * scale).dp)),
            contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            item {
                // Figma 531:3013 导入说明: #CCE6FF, radius 24, height 72, 18/24 #242436.
                Surface(
                    color = theme.cardPanel, shape = RoundedCornerShape((24 * scale).dp),
                    modifier = Modifier.fillMaxWidth().height((72 * scale).dp)
                ) {
                    Box(Modifier.padding(horizontal = (24 * scale).dp), contentAlignment = Alignment.CenterStart) {
                        AppText("右滑卡片可进行编辑与删除", AppTextRole.Supporting, color = Color(0xFF242436), designScale = scale)
                    }
                }
            }
            if (textItems.isNotEmpty()) {
                item { MaterialGroupLabel("文本资料", scale) }
                items(textItems, key = { it.id }) { material ->
                    ProjectDraftTextCard(
                        material = material, theme = theme, scale = scale,
                        onEdit = {
                            nav.navigate(AppRoute.ProjectTextEditor(material.id, theme.key, project?.id))
                        },
                        onDelete = {
                            if (project == null) viewModel.deleteProjectDraftMaterial(material.id)
                            else viewModel.deleteProjectMaterial(project.id, material.id)
                        }
                    )
                }
            }
            if (fileItems.isNotEmpty()) {
                item { MaterialGroupLabel("文件资料", scale) }
                items(fileItems, key = { it.id }) { material ->
                    ProjectDraftFileCard(
                        material = material, theme = theme, scale = scale,
                        onDelete = {
                            if (project == null) viewModel.deleteProjectDraftMaterial(material.id)
                            else viewModel.deleteProjectMaterial(project.id, material.id)
                        }
                    )
                }
            }
            if (textItems.isEmpty() && fileItems.isEmpty()) {
                item {
                    Surface(
                        color = theme.surface, shape = RoundedCornerShape((24 * scale).dp),
                        modifier = Modifier.fillMaxWidth().height((150 * scale).dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AppText("暂无资料，点击下方“导入资料”添加", AppTextRole.Supporting, color = theme.text.copy(alpha = .6f), designScale = scale)
                        }
                    }
                }
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = AppColors.BaseBackground)
        Surface(
            onClick = { /* material import is a display control pending the confirmed contract */ },
            color = theme.primary, contentColor = theme.onPrimary,
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                .fillMaxWidth().height((60 * scale).dp).zIndex(1f)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbol("folder_open", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                AppText("导入资料", AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
            }
        }
    }
}

/** Figma 531:3013 groups "文本资料" / "文件资料" as plain 20/27·630 black labels on the white sheet. */
@Composable
private fun MaterialGroupLabel(title: String, scale: Float) = AppText(
    title,
    AppTextRole.SectionTitle,
    modifier = Modifier.padding(horizontal = (4 * scale).dp),
    color = Color(0xFF000000),
    designScale = scale
)
