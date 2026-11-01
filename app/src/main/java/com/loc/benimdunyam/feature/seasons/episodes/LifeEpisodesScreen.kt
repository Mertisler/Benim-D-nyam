package com.loc.benimdunyam.feature.seasons.episodes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeEpisodesScreen(
    viewModel: LifeEpisodesViewModel,
    seasonId: String,
    onBackClick: () -> Unit,
    onNavigateToCreateJournal: (String, String) -> Unit,
    onNavigateToEpisodeMemories: (String, String) -> Unit, // 1. Yeni sayfa için yönlendirme parametresi eklendi
    onNavigateToJournalDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    val season = uiState.season

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(season?.title ?: "Sezon Bölümleri", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Bölüm Ekle")
            }
        },
        containerColor = Color(0xFF121212)
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (season != null) {
                        item {
                            SeasonHeroHeader(season = season, episodeCount = uiState.episodes.size)
                        }
                    }

                    item {
                        Text(
                            text = "Bölümler",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    if (uiState.episodes.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Bu sezona henüz bir bölüm eklenmemiş.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        items(uiState.episodes, key = { it.id }) { episode ->
                            val isActive = episode.id == uiState.activeEpisodeId
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                NetflixStyleEpisodeItem(
                                    episode = episode,
                                    isActive = isActive,
                                    onSelectActive = {
                                        viewModel.setActiveEpisode(episode.id)
                                    },
                                    onShowMemoriesClick = {
                                        // 2. Butona tıklandığında diyalog yerine ayrı sayfaya yönlendiriyoruz
                                        onNavigateToEpisodeMemories(seasonId, episode.id)
                                    },
                                    onDelete = { viewModel.deleteEpisode(episode) },
                                    onWriteJournalClick = {
                                        viewModel.setSelectedSeasonAndEpisode(episode.id)
                                        onNavigateToCreateJournal(seasonId, episode.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

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
fun SeasonHeroHeader(season: LifeSeason, episodeCount: Int) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
    val startDateStr = dateFormat.format(Date(season.startDate))
    val endDateStr = season.endDate?.let { dateFormat.format(Date(it)) } ?: "Devam Ediyor"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        if (!season.coverImagePath.isNullOrBlank()) {
            AsyncImage(
                model = season.coverImagePath,
                contentDescription = season.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF121212)),
                        startY = 100f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = season.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            Text(
                text = "📅 $startDateStr - $endDateStr • $episodeCount Bölüm",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )

            if (season.description.isNotBlank()) {
                Text(
                    text = season.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    maxLines = 3
                )
            }
        }
    }
}

@Composable
fun NetflixStyleEpisodeItem(
    episode: LifeEpisode,
    isActive: Boolean,
    onSelectActive: () -> Unit,
    onShowMemoriesClick: () -> Unit,
    onDelete: () -> Unit,
    onWriteJournalClick: () -> Unit
) {
    val activeRed = Color(0xFFE50914)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isActive) 1.5.dp else 0.dp,
                color = if (isActive) activeRed else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) activeRed else Color(0xFF2C2C2C)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${episode.orderIndex}. Bölüm: ${episode.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        if (isActive) {
                            Surface(
                                color = activeRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "Aktif",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = activeRed,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (episode.description.isNotBlank()) {
                        Text(
                            text = episode.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            maxLines = 2
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onSelectActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) activeRed else Color(0xFF2C2C2C),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = if (isActive) "Seçili" else "Seç",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    IconButton(onClick = onWriteJournalClick) {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = "Bu Bölüme Anı Yaz",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Sil",
                            tint = Color(0xFFCF6679)
                        )
                    }
                }
            }

            // Alt Kısım: Anıları Gör Butonu
            OutlinedButton(
                onClick = onShowMemoriesClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFF333333))
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Bu Bölümün Anılarını Gör",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = orderIndexStr,
                    onValueChange = { orderIndexStr = it },
                    label = { Text("Bölüm Sırası (No)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Bölüm Adı") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
        },
        shape = RoundedCornerShape(16.dp)
    )
}