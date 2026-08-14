package com.qiuzhao.flashcards.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Type-safe destinations for the Compose-only Navigation 3 graph. */
@Serializable
sealed interface AppRoute : NavKey {
    @Serializable data object Home : AppRoute
    @Serializable data object Library : AppRoute
    @Serializable data object Data : AppRoute
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

val TopLevelRoutes: Set<AppRoute> = setOf(AppRoute.Home, AppRoute.Library, AppRoute.Data)
