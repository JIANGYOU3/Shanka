package com.qiuzhao.flashcards.ui.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectNavigationTest {
    @Test fun rootNavigationUsesTheFigmaHomeProjectDataOrder() {
        assertEquals(
            listOf(AppRoute.Home, AppRoute.Project, AppRoute.Data),
            RootNavigationRoutes
        )
    }

    @Test fun projectDetailRemainsOnTheProjectStackAcrossRootSwitches() {
        val state = TestNavigationStore()
        val navigator = AppNavigator(state)

        navigator.navigate(AppRoute.Project)
        navigator.navigate(AppRoute.ProjectDetail("project-1"))
        navigator.navigate(AppRoute.Data)
        navigator.navigate(AppRoute.Project)

        assertEquals(AppRoute.ProjectDetail("project-1"), state.currentRoute)
        navigator.goBack()
        assertEquals(AppRoute.Project, state.currentRoute)
    }

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
