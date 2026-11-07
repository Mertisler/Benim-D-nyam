
package com.loc.benimdunyam.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.loc.benimdunyam.feature.home.HomeScreen
import com.loc.benimdunyam.feature.journal.JournalsScreen
import com.loc.benimdunyam.feature.journal.journaldetail.JournalDetailScreen
import com.loc.benimdunyam.feature.journal.journaleditor.JournalEditorScreen
import com.loc.benimdunyam.feature.people.PeopleScreen
import com.loc.benimdunyam.feature.people.PersonEditorScreen
import com.loc.benimdunyam.feature.people.network.PeopleNetworkScreen
import com.loc.benimdunyam.feature.search.SearchScreen
import com.loc.benimdunyam.feature.settings.SettingsScreen
import com.loc.benimdunyam.feature.onboarding.OnboardingScreen
import com.loc.benimdunyam.feature.people.detail.PersonDetailScreen
import com.loc.benimdunyam.feature.seasons.LifeSeasonsScreen
import com.loc.benimdunyam.feature.seasons.episodes.EpisodeMemoriesScreen
import com.loc.benimdunyam.feature.seasons.episodes.LifeEpisodesScreen
import com.loc.benimdunyam.feature.seasons.episodes.LifeEpisodesViewModel
import com.loc.benimdunyam.feature.timeline.TimelineScreen

@Composable
fun BenimDunyamNavGraph(
    startDestination: String = Screen.Onboarding.route
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            // 1. Onboarding
            composable(route = Screen.Onboarding.route) {
                OnboardingScreen(
                    onNavigateToHome = {
                        navController.navigate(BottomNavScreen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            // 2. Ana Sayfa (Tab 1)
            composable(route = BottomNavScreen.Home.route) {
                HomeScreen(
                    onNavigateToCreateJournal = { seasonId, episodeId ->
                        navController.navigate(
                            Screen.JournalCreateEdit.createRoute(
                                seasonId = seasonId,
                                episodeId = episodeId
                            )
                        )
                    },
                    onNavigateToJournalDetail = { journalId ->
                        navController.navigate(Screen.JournalDetail.createRoute(journalId))
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToSeasons = {
                        navController.navigate(Screen.LifeSeasons.route)
                    },
                    onNavigateToTimeline = {
                        navController.navigate(Screen.Timeline.route)
                    },
                    onNavigateToEpisodes = { seasonId ->
                        navController.navigate(Screen.LifeEpisodes.createRoute(seasonId))
                    }
                )
            }

            // 3. Tüm Günlükler / Arşiv (Tab 2)
            composable(route = BottomNavScreen.Journals.route) {
                JournalsScreen(
                    onNavigateToCreateJournal = {
                        navController.navigate(Screen.JournalCreateEdit.createRoute())
                    },
                    onNavigateToJournalDetail = { journalId ->
                        navController.navigate(Screen.JournalDetail.createRoute(journalId))
                    }
                )
            }

            // 4. İnsanlarım Rehberi (Tab 3)
            composable(route = BottomNavScreen.People.route) {
                PeopleScreen(
                    onNavigateToCreatePerson = {
                        navController.navigate(Screen.PersonCreateEdit.createRoute())
                    },
                    onNavigateToPersonDetail = { personId ->
                        navController.navigate(Screen.PersonDetail.createRoute(personId))
                    },
                    onNavigateToPeopleNetwork = {
                        navController.navigate(Screen.PeopleNetwork.route)
                    }
                )
            }

            // Hayatımın İnsan Haritası
            composable(route = Screen.PeopleNetwork.route) {
                PeopleNetworkScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPersonDetail = { personId ->
                        navController.navigate(Screen.PersonDetail.createRoute(personId))
                    },
                    onNavigateToJournalDetail = { journalId ->
                        navController.navigate(Screen.JournalDetail.createRoute(journalId))
                    }
                )
            }

            // 5. Günlük Ekleme / Düzenleme
            composable(
                route = "journal_create_edit?journalId={journalId}&seasonId={seasonId}&episodeId={episodeId}",
                arguments = listOf(
                    navArgument("journalId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("seasonId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("episodeId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) {
                JournalEditorScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 6. Günlük Detayı
            composable(
                route = Screen.JournalDetail.route,
                arguments = listOf(
                    navArgument("journalId") { type = NavType.StringType }
                )
            ) {
                JournalDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { journalId ->
                        navController.navigate(Screen.JournalCreateEdit.createRoute(journalId))
                    }
                )
            }

            // 7. Kişi Detayı
            composable(
                route = Screen.PersonDetail.route,
                arguments = listOf(
                    navArgument("personId") { type = NavType.StringType }
                )
            ) {
                PersonDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { personId ->
                        navController.navigate(Screen.PersonCreateEdit.createRoute(personId))
                    },
                    onNavigateToJournalDetail = { journalId ->
                        navController.navigate(Screen.JournalDetail.createRoute(journalId))
                    }
                )
            }

            // 8. Kişi Ekleme / Düzenleme
            composable(
                route = Screen.PersonCreateEdit.route,
                arguments = listOf(
                    navArgument("personId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                PersonEditorScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 9. Arama Ekranı
            composable(route = Screen.Search.route) {
                SearchScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToJournalDetail = { journalId ->
                        navController.navigate(Screen.JournalDetail.createRoute(journalId))
                    },
                    onNavigateToPersonDetail = { personId ->
                        navController.navigate(Screen.PersonDetail.createRoute(personId))
                    }
                )
            }

            // 10. Ayarlar ve Gizlilik
            composable(route = Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onDataCleared = {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(BottomNavScreen.Home.route) { inclusive = true }
                        }
                    }
                )
            }

            // 11. Yaşam Dönemleri Ekranı (Sezonlar)
            composable(route = Screen.LifeSeasons.route) {
                LifeSeasonsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEpisodes = { seasonId ->
                        navController.navigate(Screen.LifeEpisodes.createRoute(seasonId))
                    }
                )
            }

            // 12. Sezon Bölümleri Ekranı
            composable(
                route = Screen.LifeEpisodes.route,
                arguments = listOf(
                    navArgument("seasonId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val seasonId = backStackEntry.arguments?.getString("seasonId") ?: ""
                val viewModel: LifeEpisodesViewModel = hiltViewModel()

                LifeEpisodesScreen(
                    viewModel = viewModel,
                    seasonId = seasonId,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToCreateJournal = { sId, eId ->
                        viewModel.setSelectedSeasonAndEpisode(eId)
                        navController.navigate(Screen.JournalCreateEdit.createRoute(seasonId = sId, episodeId = eId))
                    },
                    onNavigateToEpisodeMemories = { sId, eId ->
                        navController.navigate(Screen.EpisodeMemories.createRoute(sId, eId))
                    },
                    onNavigateToJournalDetail = { journalId ->
                        navController.navigate(Screen.JournalDetail.createRoute(journalId))
                    }
                )
            }

            // 13. Zaman Çizgisi Ekranı (Faz 3)
            composable(route = Screen.Timeline.route) {
                TimelineScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToJournalDetail = { journalId ->
                        navController.navigate(Screen.JournalDetail.createRoute(journalId))
                    }
                )
            }

            // Bölüm Anıları Sayfası
            composable(
                route = "episode_memories/{seasonId}/{episodeId}",
                arguments = listOf(
                    navArgument("seasonId") { type = NavType.StringType },
                    navArgument("episodeId") { type = NavType.StringType }
                )
            ) {
                EpisodeMemoriesScreen(
                    onBackClick = { navController.popBackStack() },
                    onJournalClick = { journalId ->
                        navController.navigate(Screen.JournalDetail.createRoute(journalId))
                    }
                )
            }
        }
    }
}
