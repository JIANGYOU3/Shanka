package com.qiuzhao.flashcards.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.deviceacceptance.RequiresOwnActivityLaunch
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class ProjectComponentsTest {
    private val rule = createComposeRule()

    // Canary before the compose rule's activity launch; see RequiresOwnActivityLaunch.
    @get:Rule val chain = RuleChain.outerRule(RequiresOwnActivityLaunch()).around(rule)

    @Test fun bottomNavigationExposesSelectedDestinationToAssistiveTechnology() {
        rule.setContent {
            AutumnFlashcardsTheme {
                AppBottomNavigation(
                    selectedIndex = 1,
                    items = listOf(
                        AppBottomNavigationItem("主页", "home") {},
                        AppBottomNavigationItem("项目", "playing_cards") {},
                        AppBottomNavigationItem("数据", "query_stats") {}
                    ),
                    hazeState = HazeState(),
                    onAddClick = {}
                )
            }
        }

        rule.onNodeWithContentDescription("项目，当前页面").assertIsDisplayed().assertIsSelected()
        rule.onNodeWithContentDescription("主页").assertIsDisplayed()
        rule.onNodeWithContentDescription("数据").assertIsDisplayed()
        rule.onNodeWithContentDescription("添加项目").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun projectSwitcherExposesSelectedSectionToAssistiveTechnology() {
        rule.setContent {
            AutumnFlashcardsTheme {
                ProjectSectionSwitcher(
                    selected = ProjectDetailSection.DECKS,
                    onSelect = {},
                    theme = deckTheme(ProjectSummary(id = "test", name = "test")),
                )
            }
        }

        rule.onNodeWithContentDescription("项目内容切换：卡组管理，当前选中")
            .assertIsDisplayed()
            .assertIsSelected()
        rule.onNodeWithContentDescription("项目内容切换：数据统计").assertIsDisplayed()
    }

    @Test fun secondaryHeaderSupportsBackAndEditActions() {
        rule.setContent {
            AutumnFlashcardsTheme {
                ScreenTopInformationBar(
                    title = "项目标题",
                    subtitle = null,
                    onBack = {},
                    onTrailingAction = {}
                )
            }
        }

        rule.onNodeWithContentDescription("返回").assertIsDisplayed()
        rule.onNodeWithContentDescription("编辑").assertIsDisplayed()
    }

    @Test fun primaryHeaderAvatarIsIdentityDisplayWithoutNavigationAction() {
        rule.setContent {
            AutumnFlashcardsTheme {
                ScreenTopInformationBar(
                    title = null,
                    subtitle = null,
                    onBack = null,
                    onSettings = {},
                    account = LocalAccount("酱油四", "979492620@qq.com"),
                )
            }
        }

        rule.onNodeWithContentDescription("酱油四的头像")
            .assertIsDisplayed()
            .assertHasNoClickAction()
    }

    @Test fun primaryHeaderAvatarInvokesNavigationHandlerWhenProvided() {
        var opened = false
        rule.setContent {
            AutumnFlashcardsTheme {
                ScreenTopInformationBar(
                    title = null,
                    subtitle = null,
                    onBack = null,
                    onSettings = {},
                    account = LocalAccount("酱油四", "979492620@qq.com"),
                    onAvatar = { opened = true },
                )
            }
        }

        rule.onNodeWithContentDescription("酱油四的头像")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue(opened)
    }

    // --- 项目制卡全流程（交接文档 §4/§5）组件验收 ---

    @Test fun cuteConfirmDialogUsesCuteCopyAndConfirmsOnce() {
        var confirmed = false
        rule.setContent {
            AutumnFlashcardsTheme {
                CuteConfirmDialog(title = "是否删除项目及所属卡组？", onConfirm = { confirmed = true }, onDismiss = {})
            }
        }

        rule.onNodeWithText("是否删除项目及所属卡组？").assertIsDisplayed()
        rule.onNodeWithText("不是喵").assertIsDisplayed()
        rule.onNodeWithText("是的喵").assertIsDisplayed().performClick()
        assertTrue(confirmed)
    }

    @Test fun statusProgressCardShowsFailureReasonInFailedState() {
        rule.setContent {
            AutumnFlashcardsTheme {
                StatusProgressCard(
                    title = "生成失败，点击重试",
                    subtitle = "",
                    failed = true,
                    failureReason = "PDF 解析失败，请换一份文件重试",
                )
            }
        }

        rule.onNodeWithText("生成失败，点击重试").assertIsDisplayed()
        rule.onNodeWithText("原因：PDF 解析失败，请换一份文件重试").assertIsDisplayed()
    }

    @Test fun deckTaskStateTilesRenderGeneratingFailedAndAwaitingStates() {
        rule.setContent {
            AutumnFlashcardsTheme {
                Column {
                    GeneratingDeckCard("卡组一", deckTheme(ProjectSummary(id = "t", name = "t", themeKey = "violet")), 1f)
                    FailedDeckCard("卡组二", "服务内部错误", deckTheme(ProjectSummary(id = "t", name = "t", themeKey = "violet")), 1f, onRetry = {})
                    AwaitConfirmationDeckCard("卡组三", deckTheme(ProjectSummary(id = "t", name = "t", themeKey = "violet")), 1f, onView = {})
                }
            }
        }

        rule.onNodeWithText("正在生成").assertIsDisplayed()
        rule.onNodeWithText("生成失败：服务内部错误").assertIsDisplayed()
        rule.onNodeWithText("点击重试").assertIsDisplayed()
        rule.onNodeWithText("点击查看").assertIsDisplayed()
    }

    @Test fun scopeProjectCardRendersPendingDeckVisibleButDisabled() {
        val pending = DeckSummary(id = "deck-pending", name = "卡组三", source = "V25")
        rule.setContent {
            AutumnFlashcardsTheme {
                ScopeProjectCard(
                    projectName = "项目1",
                    decks = emptyList(),
                    pendingDecks = listOf(pending),
                    checked = false,
                    expanded = true,
                    onToggleProject = {},
                    onToggleExpand = {},
                    onToggleDeck = {},
                    deckChecked = { false },
                )
            }
        }

        rule.onNodeWithText("卡组三").assertIsDisplayed()
        rule.onNodeWithText("卡组三未设置完成，无法选择").assertIsDisplayed()
    }

    @Test fun materialCardStateMappingCoversWireStatuses() {
        fun draft(status: String?) = ProjectDraftMaterial(
            id = "m", type = ProjectDraftMaterialType.FILE, title = "f", serverStatus = status,
        )
        assertTrue(materialCardState(draft(null)) == ProjectMaterialCardState.RECOGNIZING)
        assertTrue(materialCardState(draft("PARSING")) == ProjectMaterialCardState.RECOGNIZING)
        assertTrue(materialCardState(draft("FAILED")) == ProjectMaterialCardState.FAILED)
        assertTrue(materialCardState(draft("PARSED")) == ProjectMaterialCardState.DONE)
    }

    @Test fun compactMaterialCardRendersPickerSelectionAndFailureStates() {
        var toggled = false
        var retried = false
        val picked = ProjectDraftMaterial(
            id = "m1", type = ProjectDraftMaterialType.FILE, title = "就绪资料", serverStatus = "PARSED",
        )
        val failed = ProjectDraftMaterial(
            id = "m2", type = ProjectDraftMaterialType.FILE, title = "失败资料",
            serverStatus = "FAILED", errorCode = "PDF_TOC_MISSING", materialId = "mat-2",
        )
        val violet = deckTheme(ProjectSummary(id = "t", name = "t", themeKey = "violet"))
        rule.setContent {
            AutumnFlashcardsTheme {
                Column {
                    ProjectCompactMaterialCard(
                        material = picked, theme = violet, scale = 1f, doneIcon = "picture_as_pdf",
                        onEdit = {}, onDelete = {},
                        selected = true, onSelect = { toggled = true }, selectableOnly = true,
                    )
                    ProjectCompactMaterialCard(
                        material = failed, theme = violet, scale = 1f, doneIcon = "picture_as_pdf",
                        onEdit = {}, onDelete = {},
                        selectableOnly = true, onRetry = { retried = true },
                    )
                }
            }
        }

        rule.onNodeWithText("就绪资料").assertIsDisplayed().performClick()
        assertTrue(toggled)
        rule.onNodeWithText("解析失败：PDF 缺少目录结构，无法生成\n点击重试").assertIsDisplayed()
        rule.onNodeWithText("失败资料").assertIsDisplayed().performClick()
        assertTrue(retried)
    }
}
