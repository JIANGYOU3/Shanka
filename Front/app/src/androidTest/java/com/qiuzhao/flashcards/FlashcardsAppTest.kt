package com.qiuzhao.flashcards

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class FlashcardsAppTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun homeShowsRemoteSafeEntryPoint() {
        // Remote-first startup may correctly have no decks yet. Assert fixed home chrome,
        // not the old Room-seeded content.
        rule.onNodeWithText("今日目标").assertIsDisplayed()
        rule.onNodeWithText("用户名，快来学习").assertIsDisplayed()
    }
}
