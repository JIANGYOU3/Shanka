package com.qiuzhao.flashcards.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test

class ProjectComponentsTest {
    @get:Rule val rule = createComposeRule()

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
                ProjectSectionSwitcher(ProjectDetailSection.DECKS, onSelect = {})
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
}
