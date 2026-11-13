package com.loc.benimdunyam.feature.seasons.episodes

import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeMemoriesScreen(
    viewModel: EpisodeMemoriesViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onJournalClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR"))
    val episode = uiState.episode

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = episode?.let { "${it.orderIndex}. Bölüm Anıları" } ?: "Bölüm Anıları",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },

        ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (uiState.journals.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bu bölüme henüz yazılmış bir anı yok.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bölüm başlığı / açıklaması özeti
                    if (episode != null && episode.description.isNotBlank()) {
                        item {
                            Text(
                                text = episode.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.LightGray,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }

                    items(uiState.journals, key = { it.id }) { journal ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJournalClick(journal.id) },
                            colors = CardDefaults.cardColors(containerColor = themedColor(Color(0xFF1E1E1E))),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = journal.title.ifBlank { "Başlıksız Anı" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dateFormat.format(Date(journal.entryDate)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}