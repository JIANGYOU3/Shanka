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
    /** Figma 835:5466: generate and add a card group inside a project. */
    @Serializable data class DeckGeneration(val projectId: String) : AppRoute
    /** Figma 836:5895 / 839:6220: pick the chapters parsed out of the files. */
    @Serializable data class SmartCardChapter(val projectId: String) : AppRoute
    /** Figma 835:5784: preview the generated sample cards before committing. */
    @Serializable data class SmartCardPreview(val projectId: String) : AppRoute
    /** Figma 849:6541: in-progress AI card generation screen. */
    @Serializable data class SmartCardGenerating(val projectId: String) : AppRoute
    /** Reuses the project form with a pre-existing project as its editing target. */
    @Serializable data class ProjectEdit(val id: String) : AppRoute
    @Serializable data class ProjectTextEditor(
        val materialId: String? = null,
        val themeKey: String = "azure",
        val projectId: String? = null,
        val stageForMaterialImport: Boolean = false,
        /** The shared Figma 813:4806 editor keeps its caller-specific heading. */
        val editorTitle: String = "导入文本"
    ) : AppRoute
    @Serializable data object MaterialManagement : AppRoute
    /** A given project's own materials; colour follows the project theme. */
    @Serializable data class ProjectMaterialManagement(val projectId: String) : AppRoute
    /**
     * Import remains scoped to the project that opened material management.
     * Project creation also reuses this exact flow, carrying the not-yet-saved
     * project's theme family so it never falls back to the global blue brand.
     */
    @Serializable data class MaterialImport(
        val projectId: String? = null,
        val themeKey: String? = null,
        val projectCreation: Boolean = false
    ) : AppRoute
    /** Figma 796:6935 / 796:6589: attach existing material to the project draft. */
    @Serializable data class ProjectMaterialPicker(val themeKey: String) : AppRoute
    @Serializable data object TextImport : AppRoute
    /** Figma 977:4937: the one plan page — goal editing, plus project/deck picks for a first configuration. */
    @Serializable data object StudyGoal : AppRoute
    @Serializable data class Deck(val id: String) : AppRoute
    @Serializable data class Study(val deckId: String, val reviewMode: Boolean) : AppRoute
    /** Reuses the existing study screen for the server-computed cross-deck today queue. */
    @Serializable data object StudyToday : AppRoute
    @Serializable data object Import : AppRoute
    @Serializable data class AddCard(val deckId: String) : AppRoute
    @Serializable data class CardList(val deckId: String) : AppRoute
    @Serializable data class EditCardList(val deckId: String) : AppRoute
    @Serializable data class ImportToDeck(val deckId: String) : AppRoute
    /** Avatar-triggered login dialog; the only sign-in entry in the release shell. */
    @Serializable data object Login : AppRoute
    @Serializable data object Register : AppRoute
    @Serializable data object Settings : AppRoute
    @Serializable data object SettingsIdentity : AppRoute
    @Serializable data class SettingsUnbuilt(val title: String) : AppRoute
}

/** Figma 568:2326 defines this root navigation order. */
val RootNavigationRoutes: List<AppRoute> = listOf(AppRoute.Home, AppRoute.Project, AppRoute.Data)
val TopLevelRoutes: Set<AppRoute> = RootNavigationRoutes.toSet()
