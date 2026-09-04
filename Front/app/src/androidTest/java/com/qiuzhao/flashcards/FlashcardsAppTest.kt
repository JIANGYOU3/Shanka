package com.qiuzhao.flashcards

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.qiuzhao.flashcards.deviceacceptance.RequiresOwnActivityLaunch
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class FlashcardsAppTest {
    private val rule = createAndroidComposeRule<MainActivity>()

    // The canary must run before the compose rule's activity launch: on MIUI the app's own
    // launch is aborted under instrumentation and the rule would hang (see the canary KDoc).
    @get:Rule val chain = RuleChain.outerRule(RequiresOwnActivityLaunch()).around(rule)

    @Test fun homeShowsRealHomeState() {
        // Remote-first startup may correctly have no decks yet. Assert fixed home chrome and
        // that the removed fake "计算机网络" fallback deck is gone; the greeting now carries
        // the real account nickname and a deck-less home shows the true empty state instead.
        rule.onNodeWithText("今日目标").assertIsDisplayed()
        rule.onNodeWithText("计算机网络").assertDoesNotExist()
    }
}
