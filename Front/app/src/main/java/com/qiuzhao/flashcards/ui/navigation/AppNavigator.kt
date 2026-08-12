package com.qiuzhao.flashcards.ui.navigation

/** Mutates Navigation 3 state; screens receive this narrow, type-safe API only. */
class AppNavigator(private val state: AppNavigationState) {
    fun navigate(route: AppRoute) {
        if (route in TopLevelRoutes) {
            state.selectedTopLevel = route
        } else {
            state.backStacks.getValue(state.selectedTopLevel).add(route)
        }
    }

    fun replaceTop(route: AppRoute) {
        state.backStacks.getValue(state.selectedTopLevel).removeLastOrNull()
        state.backStacks.getValue(state.selectedTopLevel).add(route)
    }

    fun replaceInclusive(route: AppRoute, replacement: AppRoute) {
        val stack = state.backStacks.getValue(state.selectedTopLevel)
        while (stack.size > 1 && stack.last() != route) stack.removeLastOrNull()
        if (stack.lastOrNull() == route) stack.removeLastOrNull()
        stack.add(replacement)
    }

    fun goBack() {
        val stack = state.backStacks.getValue(state.selectedTopLevel)
        if (stack.size > 1) {
            stack.removeLastOrNull()
        } else if (state.selectedTopLevel != AppRoute.Home) {
            state.selectedTopLevel = AppRoute.Home
        }
    }

    /** Transitional semantic name retained for existing screen callbacks. */
    fun popBackStack() = goBack()
}
