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
    onNavigateToCreateJournal: () -> Unit,
    onNavigateToJournalDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSeasons: () -> Unit,
    onNavigateToTimeline: () -> Unit,
    onNavigateToEpisodes: (String) -> Unit, // <-- 1. Yeni navigasyon parametresi eklendi
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Butik defter / sıcak kâğıt hissi veren özel gradyan renkleri
    val parchmentGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFAF7F2), // En üstte hafif açık fildişi
            Color(0xFFF3ECE1)  // Aşağıya doğru indikçe derinleşen sıcak kâğıt tonu
        )
    )

    Scaffold(
        containerColor = Color.Transparent, // Arka planın gradyanla kaplanabilmesi için şeffaf yapıyoruz
        floatingActionButton = {
            FriendlyActionFab(
                onClick = onNavigateToCreateJournal,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    ) { paddingValues ->
        // Tüm sayfayı sıcak kâğıt gradyanı ile kaplıyoruz
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
                // 1. Üst Başlık ve İkonlar
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    HomeHeader(
                        onSearchClick = onNavigateToSearch,
                        onSettingsClick = onNavigateToSettings
                    )
                }

                // 2. Zaman Tüneli & Anılar Karuseli
                if (uiState.smartMemories.isNotEmpty()) {
                    item {
                        SmartMemoryCarousel(
                            memories = uiState.smartMemories,
                            onJournalClick = onNavigateToJournalDetail,
                            onSeasonClick = onNavigateToSeasons
                        )
                    }
                }

                    // 3. Yaşam Dönemleri Bölümü (Koşul kaldırıldı, artık her zaman görünür)
                    item {
                        LifeSeasonsSection(
                            seasons = uiState.seasons,
                            onSeeAllClick = onNavigateToSeasons,
                            onSeasonClick = { seasonId ->
                                onNavigateToEpisodes(seasonId)
                            }
                        )
                    }

                // 4. Son Anılar Bölümü
                item {
                    RecentJournalsSection(
                        recentJournals = uiState.recentJournals,
                        onJournalClick = onNavigateToJournalDetail,
                        onCreateJournalClick = onNavigateToCreateJournal
                    )
                }

                // Alt çubukla çakışmayı önleyen boşluk
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}