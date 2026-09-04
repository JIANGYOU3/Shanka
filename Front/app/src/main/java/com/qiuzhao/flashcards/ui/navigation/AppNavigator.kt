package com.qiuzhao.flashcards.ui.navigation

/** Mutates Navigation 3 state; screens receive this narrow, type-safe API only. */
class AppNavigator(private val state: AppNavigationStore) {
    val isAtExitRoot: Boolean
        get() = state.selectedTopLevel == AppRoute.Home && state.stackFor(AppRoute.Home).size == 1

    fun navigate(route: AppRoute) {
        if (route in TopLevelRoutes) {
            state.selectedTopLevel = route
        } else {
            state.stackFor(state.selectedTopLevel).add(route)
        }
    }

    fun replaceTop(route: AppRoute) {
        state.stackFor(state.selectedTopLevel).removeLastOrNull()
        state.stackFor(state.selectedTopLevel).add(route)
    }

    fun replaceInclusive(route: AppRoute, replacement: AppRoute) {
        val stack = state.stackFor(state.selectedTopLevel)
        while (stack.size > 1 && stack.last() != route) stack.removeLastOrNull()
        if (stack.lastOrNull() == route) stack.removeLastOrNull()
        stack.add(replacement)
    }

    fun goBack() {
        val stack = state.stackFor(state.selectedTopLevel)
        if (stack.size > 1) {
            stack.removeLastOrNull()
        } else if (state.selectedTopLevel != AppRoute.Home) {
            state.selectedTopLevel = AppRoute.Home
        }
    }

    /** Removes transient detail/edit entries after an aggregate was deleted. */
    fun returnToTopLevel() {
        val stack = state.stackFor(state.selectedTopLevel)
        while (stack.size > 1) stack.removeLastOrNull()
    }

    /** Transitional semantic name retained for existing screen callbacks. */
    fun popBackStack() = goBack()
}
