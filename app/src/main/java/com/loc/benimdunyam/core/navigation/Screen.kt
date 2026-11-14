
package com.loc.benimdunyam.core.navigation

sealed class Screen(val route: String) {

    object LifeEpisodes : Screen("life_episodes/{seasonId}") {
        fun createRoute(seasonId: String) = "life_episodes/$seasonId"
    }

    object Search : Screen("search")
    object People : Screen("people")

    // İnsanlarım > Hayatımın İnsan Haritası
    object PeopleNetwork : Screen("people_network")

    object Onboarding : Screen("onboarding")
    object Main : Screen("main")

    object JournalDetail : Screen("journal_detail/{journalId}") {
        fun createRoute(journalId: String) = "journal_detail/$journalId"
    }



    object JournalCreateEdit : Screen(
        "journal_create_edit?journalId={journalId}&seasonId={seasonId}&episodeId={episodeId}"
    ) {
        fun createRoute(
            journalId: String? = null,
            seasonId: String? = null,
            episodeId: String? = null
        ): String {
            return buildString {
                append("journal_create_edit")
                val params = mutableListOf<String>()
                if (!journalId.isNullOrBlank()) params.add("journalId=$journalId")
                if (!seasonId.isNullOrBlank()) params.add("seasonId=$seasonId")
                if (!episodeId.isNullOrBlank()) params.add("episodeId=$episodeId")
                if (params.isNotEmpty()) {
                    append("?")
                    append(params.joinToString("&"))
                }
            }
        }
    }

    object EpisodeMemories : Screen("episode_memories/{seasonId}/{episodeId}") {
        fun createRoute(seasonId: String, episodeId: String) =
            "episode_memories/$seasonId/$episodeId"
    }

    object PersonDetail : Screen("person_detail/{personId}") {
        fun createRoute(personId: String) = "person_detail/$personId"
    }

    object PersonCreateEdit : Screen("person_create_edit?personId={personId}") {
        fun createRoute(personId: String? = null) =
            if (personId != null) "person_create_edit?personId=$personId"
            else "person_create_edit"
    }

    object Settings : Screen("settings")
    object CloudAccount : Screen("cloud_account")
    object LifeSeasons : Screen("life_seasons")
    object Timeline : Screen("timeline")
}

sealed class BottomNavScreen(
    val route: String,
    val title: String,
    val iconResName: String

) {
    object Seasons : BottomNavScreen("seasons", "Dönemler", "seasons")
    object Home : BottomNavScreen("home", "Ana Sayfa", "home")
    object Journals : BottomNavScreen("journals", "Günlükler", "book")
    object People : BottomNavScreen("people", "İnsanlarım", "people")
}
