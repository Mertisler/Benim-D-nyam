package com.loc.benimdunyam.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.feature.home.components.HomeCompactHeader
import com.loc.benimdunyam.feature.home.components.SmartMemoryCarousel
import com.loc.benimdunyam.feature.home.components.RecentJournalsSection
import com.loc.benimdunyam.feature.home.components.FriendlyActionFab
import com.loc.benimdunyam.feature.home.onthisday.OnThisDayFeature
import com.loc.benimdunyam.feature.home.insights.LifeStoryInsightsViewModel

/**
 * Ana sayfa: Keşifler için ayrı bir kart/diyalog yoktur.
 * Kişisel içgörüler mevcut Zaman Tüneli & Anılar yatay kartlarına eklenir.
 * Sezonlar alt menüde, Sezonun Filmi ise dönemin içindedir.
 * Navigation imzası ve diğer ekranların davranışı korunur.
 */
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Mevcut içgörü hesaplama ViewModel'ini doğrudan zaman tünelinde kullan.
    // Tekrar hesaplama, yeni tablo veya veri taşıma gerekmez.
    val insightsViewModel: LifeStoryInsightsViewModel = hiltViewModel()
    val insightsState by insightsViewModel.uiState.collectAsStateWithLifecycle()
    val visibleInsights = insightsState.report.insights.filterNot { insight ->
        insight.id == "on_this_day" || (
                insight.id == "rediscover" &&
                        uiState.smartMemories.any { memory ->
                            memory is SmartMemoryItem.OnThisDay && memory.journalId == insight.journalId
                        }
                )
    }

    val background = Brush.verticalGradient(
        listOf(Color(0xFFFAF7F2), Color(0xFFF3ECE1))
    )

    val onCreateJournal = {
        onNavigateToCreateJournal(
            uiState.activeSeasonId,
            uiState.activeEpisodeId
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FriendlyActionFab(
                onClick = onCreateJournal,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = scaffoldPadding.calculateTopPadding() + 12.dp,
                    bottom = scaffoldPadding.calculateBottomPadding() + 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                item(key = "home-header") {
                    HomeCompactHeader(
                        onSearch = onNavigateToSearch,
                        onSettings = onNavigateToSettings
                    )
                }

                item(key = "home-story-hub") {
                    SmartMemoryCarousel(
                        memories = uiState.smartMemories,
                        insights = visibleInsights,
                        onJournalClick = onNavigateToJournalDetail,
                        onSeasonClick = onNavigateToSeasons
                    )
                }

                // Yalnızca eski yılların bugünün tarihine denk düşen anıları varsa görünür.
                // Aksi halde boş kart oluşturmaz; mevcut V1 davranışı korunur.
                item(key = "home-on-this-day") {
                    OnThisDayFeature(
                        onNavigateToJournalDetail = onNavigateToJournalDetail,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item(key = "home-recent-memories") {
                    RecentJournalsSection(
                        recentJournals = uiState.recentJournals.take(5),
                        onJournalClick = onNavigateToJournalDetail,
                        onCreateJournalClick = onCreateJournal
                    )
                }

                item(key = "home-footer-space") {
                    // Kedi FAB kartların üstünde kalabilir; kaydırma sonunda alan bırak.
                    Spacer(Modifier.height(84.dp))
                }
            }
        }
    }

}
