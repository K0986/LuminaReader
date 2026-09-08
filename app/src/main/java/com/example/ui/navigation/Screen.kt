package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Reader : Screen("reader/{bookId}") {
        fun createRoute(bookId: Long) = "reader/$bookId"
    }
    object Notes : Screen("notes")
    object Stats : Screen("stats")
    object Settings : Screen("settings")
}
