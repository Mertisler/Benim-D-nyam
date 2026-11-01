package com.loc.benimdunyam.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.loc.benimdunyam.feature.home.components.*

@Composable
fun HomeScreen(
    onNavigateToCreateJournal: (seasonId: String?, episodeId: String?) -> Unit,
    onNavigateToJournalDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSeasons: () -> Unit,
    onNavigateToTimeline: () -> Unit,
    onNavigateToEpisodes: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val parchmentGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFAF7F2),
            Color(0xFFF3ECE1)
        )
    )

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FriendlyActionFab(
                onClick = {
                    onNavigateToCreateJournal(uiState.activeSeasonId, uiState.activeEpisodeId)
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
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    HomeHeader(
                        onSearchClick = onNavigateToSearch,
                        onSettingsClick = onNavigateToSettings
                    )
                }

                if (uiState.smartMemories.isNotEmpty()) {
                    item {
                        SmartMemoryCarousel(
                            memories = uiState.smartMemories,
                            onJournalClick = onNavigateToJournalDetail,
                            onSeasonClick = onNavigateToSeasons
                        )
                    }
                }

                item {
                    LifeSeasonsSection(
                        seasons = uiState.seasons,
                        activeSeasonId = uiState.activeSeasonId,
                        onSeeAllClick = onNavigateToSeasons,
                        onSeasonClick = { seasonId ->
                            viewModel.updateActiveSeasonAndEpisode(seasonId, null)
                            onNavigateToEpisodes(seasonId)
                        }
                    )
                }

                item {
                    RecentJournalsSection(
                        recentJournals = uiState.recentJournals,
                        onJournalClick = onNavigateToJournalDetail,
                        onCreateJournalClick = {
                            onNavigateToCreateJournal(uiState.activeSeasonId, uiState.activeEpisodeId)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}