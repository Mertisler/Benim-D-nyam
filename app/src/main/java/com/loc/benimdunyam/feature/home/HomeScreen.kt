package com.loc.benimdunyam.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.feature.home.components.*
import com.loc.benimdunyam.feature.home.insights.LifeStoryInsightsFeature
import com.loc.benimdunyam.feature.home.onthisday.OnThisDayFeature
import com.loc.benimdunyam.feature.yearreview.YearReviewHomeCard

@Composable
fun HomeScreen(
    onNavigateToCreateJournal: (seasonId: String?, episodeId: String?) -> Unit,
    onNavigateToJournalDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSeasons: () -> Unit,
    onNavigateToTimeline: () -> Unit,
    onNavigateToEpisodes: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToYearReview: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val parchmentGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFAF7F2), Color(0xFFF3ECE1))
    )

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FriendlyActionFab(
                onClick = {
                    onNavigateToCreateJournal(
                        uiState.activeSeasonId,
                        uiState.activeEpisodeId
                    )
                },
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(parchmentGradient)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        start = 16.dp,
                        end = 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "home-header") {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        HomeHeader(
                            onSearchClick = onNavigateToSearch,
                            onSettingsClick = onNavigateToSettings
                        )
                    }
                }

                if (uiState.smartMemories.isNotEmpty()) {
                    item(key = "home-smart-memories") {
                        SmartMemoryCarousel(
                            memories = uiState.smartMemories,
                            onJournalClick = onNavigateToJournalDetail,
                            onSeasonClick = onNavigateToSeasons
                        )
                    }
                }
                item(key = "home-year-review") {
                    YearReviewHomeCard(
                        onClick = onNavigateToYearReview,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // YENİ: geçmiş yılların bugüne denk gelen tüm anıları.
                // Veri yoksa bileşen kendi içinde gizlenir.
                item(key = "on-this-day") {
                    OnThisDayFeature(
                        onNavigateToJournalDetail = onNavigateToJournalDetail,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item(key = "home-seasons") {
                    LifeSeasonsSection(
                        // Mevcut ekranındaki sıralamayı koruyoruz.
                        seasons = uiState.seasons.reversed(),
                        activeSeasonId = uiState.activeSeasonId,
                        onSeeAllClick = onNavigateToSeasons,
                        onSeasonClick = onNavigateToEpisodes
                    )
                }

                item(key = "home-story-insights") {
                    LifeStoryInsightsFeature(
                        onNavigateToJournalDetail = onNavigateToJournalDetail,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item(key = "recent-journals") {
                    RecentJournalsSection(
                        recentJournals = uiState.recentJournals,
                        onJournalClick = onNavigateToJournalDetail,
                        onCreateJournalClick = {
                            onNavigateToCreateJournal(
                                uiState.activeSeasonId,
                                uiState.activeEpisodeId
                            )
                        }
                    )
                }

                item(key = "home-bottom-space") {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}
