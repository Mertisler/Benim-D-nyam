package com.loc.benimdunyam.core.navigation

import androidx.compose.runtime.key
import com.loc.benimdunyam.domain.model.LocalArchiveAccessDecision
import com.loc.benimdunyam.feature.auth.LocalArchiveAccessViewModel
import com.loc.benimdunyam.feature.auth.LocalArchiveGateScreen

import com.loc.benimdunyam.core.designsystem.ThemeScenicBackground
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.feature.auth.StartupViewModel
import com.loc.benimdunyam.feature.auth.StartupDestination
import com.loc.benimdunyam.feature.auth.WelcomeScreen
import com.loc.benimdunyam.feature.auth.SignInScreen
import com.loc.benimdunyam.feature.auth.RegisterScreen
import com.loc.benimdunyam.feature.auth.ForgotPasswordScreen
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
import com.loc.benimdunyam.feature.settings.cloudaccount.CloudAccountScreen
import com.loc.benimdunyam.feature.onboarding.OnboardingScreen
import com.loc.benimdunyam.feature.people.detail.PersonDetailScreen
import com.loc.benimdunyam.feature.seasons.LifeSeasonsScreen
import com.loc.benimdunyam.feature.seasons.episodes.EpisodeMemoriesScreen
import com.loc.benimdunyam.feature.seasons.episodes.LifeEpisodesScreen
import com.loc.benimdunyam.feature.seasons.episodes.LifeEpisodesViewModel
import com.loc.benimdunyam.feature.timeline.TimelineScreen
import com.loc.benimdunyam.feature.yearreview.YearReviewScreen

/**
 * En dış uygulama kapısı: farklı Firebase hesabı yerel arşivi göremez.
 *
 * Kilitliyken NavHost hiç oluşturulmaz. Böylece önceki sayfanın görüntüsü,
 * ViewModel'leri ve geri yığınındaki ekranlar ekranda kalmaz.
 */
@Composable
fun BenimDunyamNavGraph(
    startDestination: String = Screen.Startup.route
) {
    val accessViewModel: LocalArchiveAccessViewModel = hiltViewModel()
    val state by accessViewModel.uiState.collectAsStateWithLifecycle()

    when (state.decision) {
        LocalArchiveAccessDecision.CHECKING -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        LocalArchiveAccessDecision.ALLOWED -> {
            // Her hesap değişiminde NavController ve geçmişi yeniden kurulur.
            key(state.sessionGeneration) {
                BenimDunyamContentNavGraph(startDestination = startDestination)
            }
        }
        LocalArchiveAccessDecision.CONSENT_REQUIRED,
        LocalArchiveAccessDecision.LOCKED -> {
            Box(modifier = Modifier.fillMaxSize()) {
                ThemeScenicBackground(modifier = Modifier.matchParentSize())
                LocalArchiveGateScreen(
                    state = state,
                    onConfirmLink = accessViewModel::confirmLinkToCurrentAccount,
                    onContinueAsGuest = accessViewModel::continueAsGuest,
                    onCreateEmptyWorkspace = accessViewModel::createEmptyWorkspace,
                    onReturnToLegacy = accessViewModel::returnToLegacyWorkspace
                )
            }
        }
    }
}

/** Önceden çalışan bütün navigasyon rotaları burada aynen korunur. */
@Composable
private fun BenimDunyamContentNavGraph(startDestination: String) {
    val navController = rememberNavController()

    // Giriş akışı ilk açılışta da, Ayarlar içinden açıldığında da güvenli biçimde
    // sonlanır. Ana Sayfa zaten geçmişteyse yeni bir kopya oluşturulmaz.
    val goHomeAfterAuth: () -> Unit = {
        val returnedToExistingHome = navController.popBackStack(
            BottomNavScreen.Home.route,
            inclusive = false
        )
        if (!returnedToExistingHome) {
            navController.navigate(BottomNavScreen.Home.route) {
                popUpTo(Screen.AuthWelcome.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeScenicBackground(modifier = Modifier.matchParentSize())
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                BottomNavigationBar(navController = navController)
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(paddingValues)
            ) {
                // Faz 2D: önce oturum ve ilk kullanım tercihini kontrol et.
                // Bu rota yalnızca yönlendirme yapar; Room verilerini değiştirmez.
                composable(route = Screen.Startup.route) {
                    val startupViewModel: StartupViewModel = hiltViewModel()
                    val destination by startupViewModel.nextDestination.collectAsStateWithLifecycle()

                    LaunchedEffect(destination) {
                        val route = when (destination) {
                            StartupDestination.HOME -> BottomNavScreen.Home.route
                            StartupDestination.WELCOME -> Screen.AuthWelcome.route
                            null -> null
                        }
                        if (route != null) {
                            navController.navigate(route) {
                                popUpTo(Screen.Startup.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                // Karşılama ekranında giriş zorunlu değildir; hesapsız kullanım korunur.
                composable(route = Screen.AuthWelcome.route) {
                    WelcomeScreen(
                        onSignIn = { navController.navigate(Screen.AuthSignIn.route) },
                        onRegister = { navController.navigate(Screen.AuthRegister.route) },
                        onContinueAsGuest = goHomeAfterAuth
                    )
                }

                composable(route = Screen.AuthSignIn.route) {
                    SignInScreen(
                        onBack = { navController.popBackStack() },
                        onRegister = {
                            navController.navigate(Screen.AuthRegister.route) {
                                popUpTo(Screen.AuthSignIn.route) { inclusive = true }
                            }
                        },
                        onForgotPassword = { navController.navigate(Screen.AuthForgotPassword.route) },
                        onAuthenticated = goHomeAfterAuth
                    )
                }

                composable(route = Screen.AuthRegister.route) {
                    RegisterScreen(
                        onBack = { navController.popBackStack() },
                        onSignIn = {
                            navController.navigate(Screen.AuthSignIn.route) {
                                popUpTo(Screen.AuthRegister.route) { inclusive = true }
                            }
                        },
                        onAuthenticated = goHomeAfterAuth
                    )
                }

                composable(route = Screen.AuthForgotPassword.route) {
                    ForgotPasswordScreen(onBack = { navController.popBackStack() })
                }

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
                        },
                        onNavigateToMyStory = {
                            navController.navigate(Screen.MyStory.route)
                        },
                        onNavigateToAskMemories = {
                            navController.navigate(Screen.AskMemories.route)
                        },
                    )
                }

                // Hikâyem: genel analiz sayfası (ana sayfadan açılır)
                // Anılarına sor: kaynaklı yapay zekâ cevapları
                composable(route = Screen.AskMemories.route) {
                    com.loc.benimdunyam.feature.ask.AskMemoriesScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenJournal = { journalId ->
                            navController.navigate(Screen.JournalDetail.createRoute(journalId))
                        },
                        onOpenSettings = { navController.navigate(Screen.Settings.route) }
                    )
                }

                composable(route = Screen.MyStory.route) {
                    com.loc.benimdunyam.feature.mystory.MyStoryScreen(
                        onOpenAskMemories = { navController.navigate(Screen.AskMemories.route) },
                        onNavigateBack = { navController.popBackStack() },
                        onOpenJournal = { journalId ->
                            navController.navigate(Screen.JournalDetail.createRoute(journalId))
                        },
                        onOpenPerson = { personId ->
                            navController.navigate(Screen.PersonDetail.createRoute(personId))
                        },
                        onOpenYearReview = { navController.navigate("year_review") },
                        onOpenSeasons = { navController.navigate(Screen.LifeSeasons.route) }
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
                        },
                        onNavigateToPersonDetail = { personId ->
                            navController.navigate(Screen.PersonDetail.createRoute(personId))
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
                        },
                        onNavigateToPersonDetail = { otherPersonId ->
                            navController.navigate(Screen.PersonDetail.createRoute(otherPersonId))
                        },
                        onNavigateToAnalytics = { personId ->
                            navController.navigate(Screen.PersonAnalytics.createRoute(personId))
                        }
                    )
                }

                // 7b. Kişi Analizi (yıllara/aylara göre grafikler ve süzgeçler)
                composable(
                    route = Screen.PersonAnalytics.route,
                    arguments = listOf(
                        navArgument("personId") { type = NavType.StringType }
                    )
                ) {
                    com.loc.benimdunyam.feature.people.detail.analytics.PersonAnalyticsScreen(
                        onNavigateBack = { navController.popBackStack() }
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
                        onNavigateToCloudAccount = {
                            navController.navigate(Screen.CloudAccount.route)
                        },
                        onDataCleared = {
                            navController.navigate(Screen.Onboarding.route) {
                                popUpTo(BottomNavScreen.Home.route) { inclusive = true }
                            }
                        }
                    )
                }

                // Bulut hesabı tamamen bağımsız: Room verisine ve yedeklemeye dokunmaz.
                composable(route = Screen.CloudAccount.route) {
                    CloudAccountScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToAuthEntry = {
                            navController.navigate(Screen.AuthWelcome.route)
                        }
                    )
                }

                // 11. Dönemler sekmesi: mevcut LifeSeasons ekranı aynı route ile kullanılır.
                composable(route = BottomNavScreen.Seasons.route) {
                    LifeSeasonsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        showBackButton = false,
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
                        },
                        onNavigateToSeasonFilm = { selectedSeasonId ->
                            navController.navigate("season_film/$selectedSeasonId")
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

                // Seçilen sezonun filmi; yıl bazlı eski ekranın hesapları sezon id ile sınırlandırılır.
                composable(
                    route = "season_film/{seasonId}",
                    arguments = listOf(navArgument("seasonId") { type = NavType.StringType })
                ) {
                    YearReviewScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToJournalDetail = { journalId ->
                            navController.navigate(Screen.JournalDetail.createRoute(journalId))
                        }
                    )
                }

                composable(route = "year_review") {
                    YearReviewScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        onNavigateToJournalDetail = { journalId ->
                            navController.navigate(
                                Screen.JournalDetail.createRoute(journalId)
                            )
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
}
