package com.qiuzhao.flashcards.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Type-safe destinations for the Compose-only Navigation 3 graph. */
@Serializable
sealed interface AppRoute : NavKey {
    @Serializable data object Home : AppRoute
    /**
     * The former Library root. Keeping its old serial name restores a saved
     * Navigation 3 state from an installed pre-project build as Project.
     */
    @Serializable
    @SerialName("com.qiuzhao.flashcards.ui.navigation.AppRoute.Library")
    data object Project : AppRoute
    @Serializable data object Data : AppRoute
    @Serializable data class ProjectDetail(val id: String) : AppRoute
    @Serializable data object ProjectCreate : AppRoute
    /** Reuses the project form with a pre-existing project as its editing target. */
    @Serializable data class ProjectEdit(val id: String) : AppRoute
    @Serializable data class ProjectTextEditor(
        val materialId: String? = null,
        val themeKey: String = "azure",
        val projectId: String? = null
    ) : AppRoute
    @Serializable data object MaterialManagement : AppRoute
    /** A given project's own materials; colour follows the project theme. */
    @Serializable data class ProjectMaterialManagement(val projectId: String) : AppRoute
    @Serializable data object MaterialImport : AppRoute
    @Serializable data object TextImport : AppRoute
    @Serializable data class Deck(val id: String) : AppRoute
    @Serializable data class Study(val deckId: String, val reviewMode: Boolean) : AppRoute
    @Serializable data object Import : AppRoute
    @Serializable data class AddCard(val deckId: String) : AppRoute
    @Serializable data class CardList(val deckId: String) : AppRoute
    @Serializable data class EditCardList(val deckId: String) : AppRoute
    @Serializable data class ImportToDeck(val deckId: String) : AppRoute
    @Serializable data object PdfMaker : AppRoute
    /** First app entry has no visible back affordance. */
    @Serializable data object FirstLogin : AppRoute
    @Serializable data object Login : AppRoute
    @Serializable data object Register : AppRoute
    @Serializable data object Settings : AppRoute
    @Serializable data object SettingsIdentity : AppRoute
    @Serializable data class SettingsUnbuilt(val title: String) : AppRoute
}

/** Figma 568:2326 defines this root navigation order. */
val RootNavigationRoutes: List<AppRoute> = listOf(AppRoute.Home, AppRoute.Project, AppRoute.Data)
val TopLevelRoutes: Set<AppRoute> = RootNavigationRoutes.toSet()
