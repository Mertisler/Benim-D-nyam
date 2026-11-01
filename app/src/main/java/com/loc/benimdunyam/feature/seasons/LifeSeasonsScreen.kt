package com.loc.benimdunyam.feature.seasons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.loc.benimdunyam.feature.seasons.episodes.AddSeasonDialogWithCover
import com.loc.benimdunyam.feature.seasons.episodes.MovieStyleSeasonItem


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeSeasonsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEpisodes: (String) -> Unit,
    viewModel: LifeSeasonsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Dönemlerim & Sezonlar") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.seasonSummaries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Henüz kayıtlı bir yaşam dönemi yok.\nSağ alttan yeni bir dönem ekleyebilirsiniz.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.seasonSummaries) { summary ->
                        val isActive = summary.season.id == uiState.activeSeasonId
                        MovieStyleSeasonItem(
                            summary = summary,
                            isActive = isActive,
                            onSelectActive = {
                                // Sadece butona basıldığında aktif dönem olarak işaretlenir
                                viewModel.selectSeason(summary.season.id)
                            },
                            onClick = {
                                // Karta basıldığında sadece bölümler sayfasına yönlendirilir (aktiflik değişmez)
                                onNavigateToEpisodes(summary.season.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSeasonDialogWithCover(
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