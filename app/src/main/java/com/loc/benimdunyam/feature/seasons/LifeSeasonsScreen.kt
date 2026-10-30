package com.loc.benimdunyam.feature.seasons

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.loc.benimdunyam.feature.seasons.components.SeasonCardItem
import com.loc.benimdunyam.feature.seasons.dialogs.AddSeasonDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LifeSeasonsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEpisodes: (String) -> Unit, // 1. Parametre eklendi
    viewModel: LifeSeasonsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Dönemlerim") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Dönem Ekle")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.seasonSummaries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Henüz kayıtlı bir yaşam dönemi yok.\nSağ alttan yeni bir dönem ekleyebilirsiniz.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { uiState.seasonSummaries.size })

                // Yatay Pager (Kaydırılabilir büyük kartlar)
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
                ) { page ->
                    val summary = uiState.seasonSummaries[page]
                    SeasonCardItem(
                        summary = summary,
                        onClick = { onNavigateToEpisodes(summary.season.id) } // 2. Tıklama aksiyonu bağlandı
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sayfa Gösterge Noktaları (Pager Indicators)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pagerState.pageCount) { iteration ->
                        val color = if (pagerState.currentPage == iteration) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSeasonDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, desc, coverUri ->
                viewModel.createSeason(
                    title = title,
                    description = desc,
                    startDate = System.currentTimeMillis() - 31536000000L,
                    endDate = System.currentTimeMillis(),
                    colorHex = "#4CAF50",
                    coverImagePath = coverUri
                )
                showAddDialog = false
            }
        )
    }
}