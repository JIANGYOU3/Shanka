package com.qiuzhao.flashcards.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

@Composable
fun rememberAppNavigationState(): AppNavigationState {
    val selectedTopLevel = rememberSerializable(
        AppRoute.Home,
        TopLevelRoutes,
        serializer = MutableStateSerializer(NavKeySerializer())
    ) {
        mutableStateOf<AppRoute>(AppRoute.Home)
    }
    val backStacks = TopLevelRoutes.associateWith { route -> rememberNavBackStack(route) }
    return remember { AppNavigationState(selectedTopLevel, backStacks) }
}

class AppNavigationState(
    selectedTopLevel: MutableState<AppRoute>,
    val backStacks: Map<AppRoute, NavBackStack<NavKey>>
) : AppNavigationStore {
    override var selectedTopLevel: AppRoute by selectedTopLevel
    val currentRoute: AppRoute get() = backStacks.getValue(selectedTopLevel).last() as AppRoute

    override fun stackFor(route: AppRoute): MutableList<NavKey> = backStacks.getValue(route)

    @Composable
    fun decoratedEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>
    ): List<NavEntry<NavKey>> {
        val decorated = backStacks.mapValues { (_, stack) ->
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>()),
                entryProvider = entryProvider
            )
        }
        // Home is always retained beneath the selected top-level stack so back exits through Home.
        val activeStacks = if (selectedTopLevel == AppRoute.Home) {
            listOf(AppRoute.Home)
        } else {
            listOf(AppRoute.Home, selectedTopLevel)
        }
        return activeStacks.flatMap { decorated[it].orEmpty() }
    }
}

interface AppNavigationStore {
    var selectedTopLevel: AppRoute
    fun stackFor(route: AppRoute): MutableList<NavKey>
}
