package com.loc.benimdunyam.feature.journal

import com.loc.benimdunyam.core.designsystem.AppThemeController
import com.loc.benimdunyam.core.designsystem.scenicDrawable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.loc.benimdunyam.feature.journal.cover.JournalArchiveCoverViewModel
import java.io.File
import com.loc.benimdunyam.feature.journal.components.SwipeToDeleteJournalItem
import com.loc.benimdunyam.feature.journal.components.JournalArchiveFiltersSheet
import com.loc.benimdunyam.feature.journal.components.JournalDogActionFab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale



// ---------------------------------------------------------
// TASARIM RENKLERİ
// ---------------------------------------------------------



// ---------------------------------------------------------
// ANA GÜNLÜKLER EKRANI
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalsScreen(
    onNavigateToCreateJournal: () -> Unit,
    onNavigateToJournalDetail: (String) -> Unit,
    viewModel: JournalsViewModel = hiltViewModel()
) {

    val uiState by viewModel.uiState.collectAsState()
    val seasonTitles = remember(uiState.seasons) {
        uiState.seasons.associate { it.id to it.title }
    }

    // Kapaklar ayrı bir ViewModel'de; mevcut JournalsViewModel ve arşiv filtresi değişmez.
    val coverViewModel: JournalArchiveCoverViewModel = hiltViewModel()
    val coverPaths by coverViewModel.coverPaths.collectAsState()
    val coverVersion by coverViewModel.refreshVersion.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Detay / düzenleme ekranından dönüldüğünde değişen kapakları yenile.
    DisposableEffect(lifecycleOwner, coverViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coverViewModel.invalidateVisibleCovers()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.deleteErrorMessage) {
        uiState.deleteErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearDeleteError()
        }
    }

    // Tarih biçimi
    val dateFormat = remember {
        SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR"))
    }

    var showFiltersSheet by remember { mutableStateOf(false) }

    // Arama alanının açık/kapalı durumu
    var showSearchField by remember {
        mutableStateOf(false)
    }

    // Arama aktif mi?
    val hasActiveSearch = uiState.searchQuery.isNotBlank()

    // Tarih filtresi aktif mi?
    val hasFacetFilter = uiState.selectedSeasonId != null ||
            uiState.selectedEpisodeId != null || uiState.selectedPersonId != null

    val hasActiveDateFilter =
        uiState.startDate != null || uiState.endDate != null


    val hasScenicArtwork = scenicDrawable(AppThemeController.selected) != null

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },

            // -------------------------------------------------
            // ÜST BAR
            // -------------------------------------------------

            topBar = {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (hasScenicArtwork) Color.Transparent else JournalCanvas)
                ) {

                    TopAppBar(

                        title = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {

                                Text(
                                    text = "Anı Defterim",

                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Serif,
                                        letterSpacing = (-0.5).sp
                                    ),

                                    color = JournalInk
                                )

                                Text(
                                    text = "HAYATINDAN KÜÇÜK KESİTLER",

                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 1.5.sp
                                    ),

                                    color = JournalMuted
                                )
                            }
                        },

                        actions = {

                            // Filtrelerin tamamını ayrı bir panelde göster; liste yüksekliği korunur.
                            IconButton(onClick = { showFiltersSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Gelişmiş filtreler",
                                    tint = if (uiState.hasActiveFilters) JournalGreen else JournalMuted
                                )
                            }

                            // ARAMA BUTONU
                            Surface(
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(44.dp)
                                    .clickable {

                                        if (showSearchField) {

                                            showSearchField = false
                                            viewModel.clearSearch()

                                        } else {

                                            showSearchField = true
                                        }
                                    },

                                shape = CircleShape,

                                color = if (showSearchField) {
                                    JournalSoftGreen
                                } else {
                                    JournalTape
                                }
                            ) {

                                Box(
                                    contentAlignment = Alignment.Center
                                ) {

                                    Icon(
                                        imageVector = if (showSearchField) {
                                            Icons.Default.Close
                                        } else {
                                            Icons.Default.Search
                                        },

                                        contentDescription = if (showSearchField) {
                                            "Aramayı kapat"
                                        } else {
                                            "Günlüklerde ara"
                                        },

                                        tint = JournalGreen
                                    )
                                }
                            }
                        },

                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )


                    // -------------------------------------------------
                    // ARAMA ALANI
                    // -------------------------------------------------

                    AnimatedVisibility(
                        visible = showSearchField,

                        enter = fadeIn() + expandVertically(),

                        exit = fadeOut() + shrinkVertically()
                    ) {

                        OutlinedTextField(

                            value = uiState.searchQuery,

                            onValueChange = { query ->
                                viewModel.updateSearchQuery(query)
                            },

                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 20.dp,
                                    vertical = 8.dp
                                ),

                            placeholder = {
                                Text(
                                    text = "Kelime, anı veya kişi ara...",
                                    color = JournalMuted
                                )
                            },

                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = JournalGreen
                                )
                            },

                            trailingIcon = {

                                if (uiState.searchQuery.isNotEmpty()) {

                                    IconButton(
                                        onClick = {
                                            viewModel.clearSearch()
                                        }
                                    ) {

                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Aramayı temizle",
                                            tint = JournalMuted
                                        )
                                    }
                                }
                            },

                            singleLine = true,

                            shape = RoundedCornerShape(18.dp),

                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JournalGreen,
                                unfocusedBorderColor = JournalBorder,
                                cursorColor = JournalGreen,
                                focusedContainerColor = JournalPaper,
                                unfocusedContainerColor = JournalPaper
                            )
                        )
                    }


                    // -------------------------------------------------
                    // ÜST AÇIKLAMA VE ANI SAYISI
                    // -------------------------------------------------

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 24.dp,
                                end = 24.dp,
                                top = 2.dp,
                                bottom = 18.dp
                            ),

                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {

                            Text(
                                text = "Geçmişinden sayfalar",

                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),

                                color = JournalMuted
                            )

                            Spacer(
                                modifier = Modifier.height(3.dp)
                            )

                            Text(
                                text = "Her anı, sana ait bir hikâye.",

                                style = MaterialTheme.typography.bodySmall,

                                color = JournalMuted.copy(alpha = 0.85f)
                            )
                        }


                        // GÜNLÜK / ARAMA SONUCU SAYISI
                        Surface(
                            shape = RoundedCornerShape(50.dp),

                            color = JournalSoftGreen
                        ) {

                            Text(
                                text = if (uiState.hasActiveFilters) {
                                    "${uiState.journals.size} SONUÇ"
                                } else {
                                    "${uiState.journals.size} ANI"
                                },

                                modifier = Modifier.padding(
                                    horizontal = 14.dp,
                                    vertical = 9.dp
                                ),

                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp
                                ),

                                color = JournalGreen
                            )
                        }
                    }
                }
            },


            // -------------------------------------------------
            // YENİ ANI BUTONU
            // -------------------------------------------------

            floatingActionButton = {
                JournalDogActionFab(
                    onClick = onNavigateToCreateJournal
                )
            }

        ) { paddingValues ->


            // -------------------------------------------------
            // ANA İÇERİK
            // -------------------------------------------------

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(if (hasScenicArtwork) Color.Transparent else JournalCanvas)
                    .padding(horizontal = 20.dp)
            ) {


                // Aktif filtreleri kullanıcının kolayca temizleyebilmesi için.
                if (uiState.hasActiveFilters) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${uiState.journals.size} / ${uiState.totalJournals} anı",
                            color = JournalMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = viewModel::clearFilters) {
                            Text("Filtreleri sıfırla", color = JournalGreen)
                        }
                    }
                }

                // -------------------------------------------------
                // YÜKLENİYOR / BOŞ / GÜNLÜK LİSTESİ
                // -------------------------------------------------

                when {

                    // YÜKLENİYOR
                    uiState.isLoading -> {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),

                            contentAlignment = Alignment.Center
                        ) {

                            CircularProgressIndicator(
                                color = JournalGreen,
                                strokeWidth = 3.dp
                            )
                        }
                    }


                    // GÜNLÜK BULUNAMADI
                    uiState.journals.isEmpty() -> {

                        JournalEmptyState(

                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),

                            hasActiveSearch = hasActiveSearch,

                            hasActiveDateFilter = hasActiveDateFilter,

                            hasOtherFilters = hasFacetFilter,

                            onCreateJournal = onNavigateToCreateJournal,

                            onClearSearch = {
                                viewModel.clearFilters()
                            },

                            onClearFilters = {
                                viewModel.clearFilters()
                            }
                        )
                    }



                    // GÜNLÜKLER
                    else -> {

                        LazyColumn(

                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),

                            contentPadding = PaddingValues(
                                top = 4.dp,
                                bottom = 110.dp
                            ),

                            verticalArrangement = Arrangement.spacedBy(14.dp)

                        ) {

                            itemsIndexed(

                                items = uiState.journals,

                                key = { _, journal ->
                                    journal.id
                                }

                            ) { index, journal ->

                                // LazyColumn sadece görünen satırlarda dosya okuması başlatır.
                                LaunchedEffect(journal.id, coverVersion) {
                                    coverViewModel.ensureCoverLoaded(journal.id, coverVersion)
                                }

                                val formattedDate = dateFormat.format(
                                    Date(journal.entryDate)
                                )

                                // Kaydırılabilir silme bileşeni
                                SwipeToDeleteJournalItem(

                                    modifier = Modifier.fillMaxWidth(),

                                    onDelete = {
                                        viewModel.deleteJournal(journal.id)
                                    }

                                ) {

                                    // Mevcut günlük kartımız
                                    JournalArchiveCard(

                                        index = index,

                                        date = formattedDate,

                                        title = journal.title,

                                        content = journal.content,

                                        seasonTitle = journal.seasonId?.let(seasonTitles::get),

                                        episodeTitle = journal.episodeId?.let(uiState.episodeTitles::get),

                                        coverPath = coverPaths[journal.id],

                                        onClick = {
                                            onNavigateToJournalDetail(journal.id)
                                        }
                                    )
                                }
                            }

                            item {
                                Spacer(
                                    modifier = Modifier.height(16.dp)
                                )
                            }
                        }
                    }

                }
            }
        }

    } // scenic backdrop Box

    if (showFiltersSheet) {
        JournalArchiveFiltersSheet(
            state = uiState,
            onDismiss = { showFiltersSheet = false },
            onSearchChanged = viewModel::updateSearchQuery,
            onSeasonChanged = viewModel::selectSeason,
            onEpisodeChanged = viewModel::selectEpisode,
            onPersonChanged = viewModel::selectPerson,
            onStartDateChanged = viewModel::setStartDate,
            onEndDateChanged = viewModel::setEndDate,
            onClearAll = viewModel::clearFilters
        )
    }
}
