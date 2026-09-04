package com.qiuzhao.flashcards.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.deviceacceptance.RequiresOwnActivityLaunch
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
                    )
                )
            }
        }

        rule.onNodeWithContentDescription("项目，当前页面").assertIsDisplayed().assertIsSelected()
        rule.onNodeWithContentDescription("主页").assertIsDisplayed()
        rule.onNodeWithContentDescription("数据").assertIsDisplayed()
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
}
