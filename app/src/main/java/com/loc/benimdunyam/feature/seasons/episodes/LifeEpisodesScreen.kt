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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.feature.seasons.summary.SeasonStorySummaryFeature
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeEpisodesScreen(
    viewModel: LifeEpisodesViewModel,
    seasonId: String,
    onBackClick: () -> Unit,
    onNavigateToCreateJournal: (String, String) -> Unit,
    onNavigateToEpisodeMemories: (String, String) -> Unit,
    onNavigateToJournalDetail: (String) -> Unit,
    onNavigateToSeasonFilm: (String) -> Unit
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showAddDialog by remember {
        mutableStateOf(false)
    }

    // Yaşam dönemi silme onayı
    var showDeleteSeasonDialog by remember {
        mutableStateOf(false)
    }

    val snackbarHostState = remember {
        SnackbarHostState()
    }

    val season = uiState.season

    // ViewModel tarafından gönderilen silme sonuçlarını dinle.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->

            when (event) {

                LifeEpisodesEvent.SeasonDeleted -> {
                    // Silme başarılı olduğunda dönemler sayfasına dön.
                    onBackClick()
                }

                LifeEpisodesEvent.SeasonDeleteFailed -> {
                    snackbarHostState.showSnackbar(
                        message = "Yaşam dönemi silinemedi. Lütfen tekrar deneyin."
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(

                title = {
                    Text(
                        text = season?.title ?: "Sezon Bölümleri",
                        color = Color.White
                    )
                },

                navigationIcon = {
                    IconButton(
                        onClick = onBackClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White
                        )
                    }
                },

                // YAŞAM DÖNEMİ SİLME BUTONU
                actions = {
                    if (season != null) {

                        IconButton(
                            onClick = {
                                showDeleteSeasonDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Yaşam dönemini sil",
                                tint = Color(0xFFFF6B6B)
                            )
                        }
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        },

        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState
            )
        },

        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Bölüm Ekle"
                )
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
                    contentPadding = PaddingValues(
                        bottom = 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {


                    // YAŞAM DÖNEMİ KAPAK ALANI
                    if (season != null) {

                        item(key = "season-hero") {
                            SeasonHeroHeader(
                                season = season,
                                episodeCount = uiState.episodes.size
                            )
                        }

                        item(key = "season-film") {
                            val seasonJournalCount = remember(seasonId, uiState.allJournals) {
                                uiState.allJournals.count { it.seasonId == seasonId }
                            }
                            Surface(
                                onClick = { onNavigateToSeasonFilm(seasonId) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                color = Color(0xFF252324),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFF655039))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFE2BA83))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("SEZONUN FİLMİ", color = Color(0xFFE2BA83),
                                            style = MaterialTheme.typography.labelMedium)
                                        Text("$seasonJournalCount anıdan oluşan sinematik hikâyen",
                                            color = Color(0xFFD4C8B7), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Filmi aç",
                                        tint = Color(0xFFE2BA83))
                                }
                            }
                        }

                        // YENİ: SİNEMATİK SEZON HİKÂYESİ
                        item(key = "cinematic-season-summary") {

                            SeasonStorySummaryFeature(
                                season = season,

                                episodes = uiState.episodes,

                                journals = uiState.allJournals,

                                onJournalClick = { journalId ->
                                    onNavigateToJournalDetail(journalId)
                                },

                                onEpisodeClick = { episodeId ->
                                    onNavigateToEpisodeMemories(
                                        seasonId,
                                        episodeId
                                    )
                                },

                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }

                    // BÖLÜMLER BAŞLIĞI
                    item {
                        Text(
                            text = "Bölümler",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 4.dp
                            )
                        )
                    }

                    // HENÜZ BÖLÜM YOKSA
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

                        // BÖLÜMLER
                        items(
                            items = uiState.episodes,
                            key = { it.id }
                        ) { episode ->

                            val isActive =
                                episode.id == uiState.activeEpisodeId

                            Box(
                                modifier = Modifier.padding(
                                    horizontal = 16.dp
                                )
                            ) {

                                NetflixStyleEpisodeItem(
                                    episode = episode,
                                    isActive = isActive,

                                    onSelectActive = {
                                        viewModel.setActiveEpisode(
                                            episode.id
                                        )
                                    },

                                    onShowMemoriesClick = {
                                        onNavigateToEpisodeMemories(
                                            seasonId,
                                            episode.id
                                        )
                                    },

                                    onDelete = {
                                        viewModel.deleteEpisode(
                                            episode
                                        )
                                    },

                                    onWriteJournalClick = {
                                        viewModel.setSelectedSeasonAndEpisode(
                                            episode.id
                                        )

                                        onNavigateToCreateJournal(
                                            seasonId,
                                            episode.id
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --------------------------------------------------
        // YAŞAM DÖNEMİ SİLME ONAY PENCERESİ
        // --------------------------------------------------

        if (showDeleteSeasonDialog && season != null) {

            AlertDialog(

                onDismissRequest = {
                    showDeleteSeasonDialog = false
                },

                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },

                title = {
                    Text(
                        text = "Yaşam Dönemini Sil?"
                    )
                },

                text = {
                    Text(
                        text = "\"${season.title}\" yaşam dönemini silmek istediğine emin misin?\n\nBu işlem geri alınamaz."
                    )
                },

                confirmButton = {

                    TextButton(
                        onClick = {

                            viewModel.deleteSeason(season)

                            showDeleteSeasonDialog = false
                        }
                    ) {

                        Text(
                            text = "Evet, Sil",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },

                dismissButton = {

                    TextButton(
                        onClick = {
                            showDeleteSeasonDialog = false
                        }
                    ) {

                        Text(
                            text = "Vazgeç"
                        )
                    }
                },

                shape = RoundedCornerShape(20.dp)
            )
        }

        // --------------------------------------------------
        // YENİ BÖLÜM EKLEME PENCERESİ
        // --------------------------------------------------

        if (showAddDialog) {

            AddEpisodeDialog(

                onDismiss = {
                    showAddDialog = false
                },

                onConfirm = {
                        title,
                        description,
                        orderIndex,
                        startDate,
                        endDate ->

                    viewModel.createEpisode(
                        title = title,
                        description = description,
                        orderIndex = orderIndex,
                        startDate = startDate,
                        endDate = endDate
                    )

                    showAddDialog = false
                },

                nextOrderIndex = uiState.episodes.size + 1
            )
        }
    }
}

// --------------------------------------------------
// YAŞAM DÖNEMİ KAPAK ALANI
// --------------------------------------------------

@Composable
fun SeasonHeroHeader(
    season: LifeSeason,
    episodeCount: Int
) {

    val dateFormat = SimpleDateFormat(
        "dd.MM.yyyy",
        Locale("tr", "TR")
    )

    val startDateStr = dateFormat.format(
        Date(season.startDate)
    )

    val endDateStr = season.endDate?.let {
        dateFormat.format(Date(it))
    } ?: "Devam Ediyor"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {

        if (!season.coverImagePath.isNullOrBlank()) {

            AsyncImage(
                model = seasonCoverImageModel(season.coverImagePath),
                contentDescription = season.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

        } else {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant
                    ),
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

        // Görselin alt kısmındaki karartma
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF121212)
                        ),
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

// --------------------------------------------------
// BÖLÜM KARTI
// --------------------------------------------------

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
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
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

                // BÖLÜM İKONU
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isActive) activeRed
                            else Color(0xFF2C2C2C)
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                // BÖLÜM BİLGİLERİ
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
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp,
                                        vertical = 2.dp
                                    )
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

                // BÖLÜM İŞLEMLERİ
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Button(
                        onClick = onSelectActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) {
                                activeRed
                            } else {
                                Color(0xFF2C2C2C)
                            },
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {

                        Text(
                            text = if (isActive) "Seçili" else "Seç",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    IconButton(
                        onClick = onWriteJournalClick
                    ) {

                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = "Bu Bölüme Anı Yaz",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // BÖLÜMÜ SİLME
                    IconButton(
                        onClick = onDelete
                    ) {

                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Bölümü Sil",
                            tint = Color(0xFFCF6679)
                        )
                    }
                }
            }

            // BÖLÜMÜN ANILARINI GÖR
            OutlinedButton(
                onClick = onShowMemoriesClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(
                    1.dp,
                    Color(0xFF333333)
                )
            ) {

                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(
                    modifier = Modifier.width(6.dp)
                )

                Text(
                    text = "Bu Bölümün Anılarını Gör",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

// --------------------------------------------------
// YENİ BÖLÜM EKLEME DİYALOĞU
// --------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEpisodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Long, Long?) -> Unit,
    nextOrderIndex: Int
) {

    var title by remember {
        mutableStateOf("")
    }

    var description by remember {
        mutableStateOf("")
    }

    var orderIndexStr by remember {
        mutableStateOf(nextOrderIndex.toString())
    }

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Text("Yeni Bölüm Ekle")
        },

        text = {

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                OutlinedTextField(
                    value = orderIndexStr,
                    onValueChange = {
                        orderIndexStr = it
                    },
                    label = {
                        Text("Bölüm Sırası (No)")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                    },
                    label = {
                        Text("Bölüm Adı")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                    },
                    label = {
                        Text("Açıklama / Özet")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },

        confirmButton = {

            TextButton(
                onClick = {

                    val order =
                        orderIndexStr.toIntOrNull() ?: nextOrderIndex

                    if (title.isNotBlank()) {

                        onConfirm(
                            title,
                            description,
                            order,
                            System.currentTimeMillis(),
                            null
                        )
                    }
                }
            ) {

                Text("Kaydet")
            }
        },

        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {

                Text("İptal")
            }
        },

        shape = RoundedCornerShape(16.dp)
    )
}
