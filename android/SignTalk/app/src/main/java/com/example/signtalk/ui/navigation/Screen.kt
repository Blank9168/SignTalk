package com.example.signtalk.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Home : Screen("home")
    data object Recognition : Screen("recognition")
    data object Dictionary : Screen("dictionary")
    data object DictionaryDetail : Screen("dictionary/{entryId}") {
        fun route(entryId: Long) = "dictionary/$entryId"
        const val ARG_ENTRY_ID = "entryId"
    }
    data object Settings : Screen("settings")
}
