package com.qiuzhao.flashcards.ui.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorTest {
    @Test
    fun childRouteReturnsToItsOriginTab() {
        val state = state()
        val navigator = AppNavigator(state)

        navigator.navigate(AppRoute.Deck("deck-1"))
        assertEquals(AppRoute.Deck("deck-1"), state.currentRoute)
        navigator.goBack()

        assertEquals(AppRoute.Home, state.selectedTopLevel)
        assertEquals(AppRoute.Home, state.currentRoute)
    }

    @Test
    fun tabStacksAreRetainedAndNonHomeRootReturnsHome() {
        val state = state()
        val navigator = AppNavigator(state)

        navigator.navigate(AppRoute.Project)
        navigator.navigate(AppRoute.Deck("deck-2"))
        navigator.navigate(AppRoute.Data)
        navigator.navigate(AppRoute.Project)

        assertEquals(AppRoute.Deck("deck-2"), state.currentRoute)
        navigator.goBack()
        assertEquals(AppRoute.Project, state.currentRoute)
        navigator.goBack()
        assertEquals(AppRoute.Home, state.selectedTopLevel)
    }

    @Test
    fun onlyHomeRootExitsTheApplicationFlow() {
        val state = state()
        val navigator = AppNavigator(state)

        assertTrue(navigator.isAtExitRoot)
        navigator.navigate(AppRoute.Settings)
        assertFalse(navigator.isAtExitRoot)
        navigator.goBack()
        assertTrue(navigator.isAtExitRoot)
    }

    @Test
    fun registrationReturnsToTheLoginScreenThatOpenedIt() {
        val state = state()
        val navigator = AppNavigator(state)

        navigator.navigate(AppRoute.Login)
        navigator.navigate(AppRoute.Register)
        navigator.goBack()
        assertEquals(AppRoute.Login, state.currentRoute)
    }

    private fun state(): TestNavigationStore = TestNavigationStore()

    private class TestNavigationStore : AppNavigationStore {
        override var selectedTopLevel: AppRoute = AppRoute.Home
        private val stacks = mapOf<AppRoute, MutableList<NavKey>>(
            AppRoute.Home to mutableListOf(AppRoute.Home),
            AppRoute.Project to mutableListOf(AppRoute.Project),
            AppRoute.Data to mutableListOf(AppRoute.Data)
        )

        val currentRoute: AppRoute get() = stackFor(selectedTopLevel).last() as AppRoute
        override fun stackFor(route: AppRoute): MutableList<NavKey> = stacks.getValue(route)
    }
}
