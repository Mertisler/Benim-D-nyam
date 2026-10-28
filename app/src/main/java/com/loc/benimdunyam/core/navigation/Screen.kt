package com.loc.benimdunyam.core.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Main : Screen("main")
    object JournalDetail : Screen("journal_detail/{journalId}") {
        fun createRoute(journalId: String) = "journal_detail/$journalId"
    }
    object JournalCreateEdit : Screen("journal_create_edit?journalId={journalId}") {
        fun createRoute(journalId: String? = null) =
            if (journalId != null) "journal_create_edit?journalId=$journalId" else "journal_create_edit"
    }
    object PersonDetail : Screen("person_detail/{personId}") {
        fun createRoute(personId: String) = "person_detail/$personId"
    }
    object PersonCreateEdit : Screen("person_create_edit?personId={personId}") {
        fun createRoute(personId: String? = null) =
            if (personId != null) "person_create_edit?personId=$personId" else "person_create_edit"
    }
    object Settings : Screen("settings")
}

sealed class BottomNavScreen(val route: String, val title: String, val iconResName: String) {
    object Home : BottomNavScreen("home", "Ana Sayfa", "home")
    object Journals : BottomNavScreen("journals", "Günlükler", "book")
    object People : BottomNavScreen("people", "İnsanlarım", "people")
}