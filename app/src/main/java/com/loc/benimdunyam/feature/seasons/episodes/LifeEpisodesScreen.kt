package com.loc.benimdunyam.feature.seasons.episodes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.domain.model.LifeEpisode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeEpisodesScreen(
    viewModel: LifeEpisodesViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sezon Bölümleri") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Bölüm Ekle")
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
            } else if (uiState.episodes.isEmpty()) {
                Text(
                    text = "Henüz bu sezona ait bir bölüm eklenmemiş.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.episodes, key = { it.id }) { episode ->
                        EpisodeItem(
                            episode = episode,
                            onDelete = { viewModel.deleteEpisode(episode) }
                        )
                    }
                }
            }
        }

        // Yeni Bölüm Ekleme Diyaloğu
        if (showAddDialog) {
            AddEpisodeDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { title, description, orderIndex, startDate, endDate ->
                    viewModel.createEpisode(title, description, orderIndex, startDate, endDate)
                    showAddDialog = false
                },
                nextOrderIndex = uiState.episodes.size + 1
            )
        }
    }
}

@Composable
fun EpisodeItem(
    episode: LifeEpisode,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bölüm ${episode.orderIndex}: ${episode.title}",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (episode.description.isNotBlank()) {
                Text(
                    text = episode.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AddEpisodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Long, Long?) -> Unit,
    nextOrderIndex: Int
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var orderIndexStr by remember { mutableStateOf(nextOrderIndex.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Bölüm Ekle") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = orderIndexStr,
                    onValueChange = { orderIndexStr = it },
                    label = { Text("Bölüm Sırası (No)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Bölüm Adı") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama / Özet") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val order = orderIndexStr.toIntOrNull() ?: nextOrderIndex
                    if (title.isNotBlank()) {
                        onConfirm(title, description, order, System.currentTimeMillis(), null)
                    }
                }
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}