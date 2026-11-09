package com.loc.benimdunyam.feature.journal

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale



// ---------------------------------------------------------
// TASARIM RENKLERİ
// ---------------------------------------------------------

private val JournalCanvas = Color(0xFFF6F2EA)
private val JournalPaper = Color(0xFFFFFCF6)
private val JournalInk = Color(0xFF352B24)
private val JournalMuted = Color(0xFF82766A)
private val JournalGreen = Color(0xFF496B5B)
private val JournalSoftGreen = Color(0xFFE5EEE8)
private val JournalTape = Color(0xFFECE2D1)
private val JournalBorder = Color(0xFFE6DCCF)


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


    Scaffold(
        containerColor = JournalCanvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },

        // -------------------------------------------------
        // ÜST BAR
        // -------------------------------------------------

        topBar = {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JournalCanvas)
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
                        containerColor = JournalCanvas
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

            ExtendedFloatingActionButton(

                onClick = onNavigateToCreateJournal,

                containerColor = JournalGreen,

                contentColor = Color.White,

                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 6.dp,
                    bottomEnd = 18.dp
                ),

                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp
                ),

                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },

                text = {
                    Text(
                        text = "Yeni anı",

                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
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
                .background(JournalCanvas)
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


// ---------------------------------------------------------
// GÜNLÜK KARTI
// ---------------------------------------------------------

@Composable
private fun JournalArchiveCard(
    index: Int,
    date: String,
    title: String,
    content: String,
    coverPath: String?,
    onClick: () -> Unit
) {

    Surface(

        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),

        shape = RoundedCornerShape(22.dp),

        color = JournalPaper,

        border = BorderStroke(
            width = 1.dp,
            color = JournalBorder
        ),

        shadowElevation = 1.dp
    ) {

        Row(
            modifier = Modifier.padding(16.dp),

            verticalAlignment = Alignment.CenterVertically
        ) {

            if (coverPath != null) {
                // Seçilen kapak görseli: dosya cihaz içindeki JournalPhotoStore'a aittir.
                Box(
                    modifier = Modifier
                        .width(94.dp)
                        .height(114.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(JournalTape)
                ) {
                    Text(
                        text = "✦",
                        color = JournalMuted,
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    AsyncImage(
                        model = remember(coverPath) { File(coverPath) },
                        contentDescription = "Anının kapak fotoğrafı",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                                )
                            )
                    )
                    Text(
                        text = "✦ KAPAK",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.6.sp
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 7.dp)
                    )
                }
            } else {
                // Fotoğrafı olmayan anıların eski defter şeridi korunur.
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(86.dp)
                        .background(
                            JournalGreen.copy(alpha = 0.75f),
                            RoundedCornerShape(50)
                        )
                )
            }

            Spacer(modifier = Modifier.width(14.dp))


            Column(
                modifier = Modifier.weight(1f)
            ) {

                // TARİH VE SIRA NUMARASI
                Row(
                    modifier = Modifier.fillMaxWidth(),

                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = date,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,

                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),

                        color = JournalGreen
                    )

                    Spacer(modifier = Modifier.width(5.dp))

                    Surface(
                        shape = RoundedCornerShape(50.dp),

                        color = JournalTape.copy(alpha = 0.65f)
                    ) {

                        Text(
                            text = "%02d".format(index + 1),

                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 4.dp
                            ),

                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),

                            color = JournalMuted
                        )
                    }
                }


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                // ANI BAŞLIĞI
                Text(
                    text = title.ifBlank {
                        "Başlıksız anı"
                    },

                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp
                    ),

                    color = JournalInk,

                    maxLines = 1,

                    overflow = TextOverflow.Ellipsis
                )


                // ANI İÇERİĞİ
                if (content.isNotBlank()) {

                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )

                    Text(
                        text = content,

                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp
                        ),

                        color = JournalMuted,

                        maxLines = 2,

                        overflow = TextOverflow.Ellipsis
                    )
                }


                Spacer(
                    modifier = Modifier.height(13.dp)
                )


                // ALT ETİKET
                Row(
                    modifier = Modifier.fillMaxWidth(),

                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = "✦",

                        color = JournalGreen,

                        style = MaterialTheme.typography.labelMedium
                    )

                    Spacer(
                        modifier = Modifier.width(6.dp)
                    )

                    Text(
                        text = "ANIYI OKU",

                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        ),

                        color = JournalGreen
                    )

                    Spacer(
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "↗",

                        color = JournalGreen,

                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}


// ---------------------------------------------------------
// BOŞ GÜNLÜK EKRANI
// ---------------------------------------------------------

@Composable
private fun JournalEmptyState(

    modifier: Modifier = Modifier,

    hasActiveSearch: Boolean,

    hasActiveDateFilter: Boolean,

    hasOtherFilters: Boolean,

    onCreateJournal: () -> Unit,

    onClearSearch: () -> Unit,

    onClearFilters: () -> Unit
) {

    val hasAnyCondition =
        hasActiveSearch || hasActiveDateFilter || hasOtherFilters

    Box(
        modifier = modifier,

        contentAlignment = Alignment.Center
    ) {

        Surface(

            modifier = Modifier.fillMaxWidth(),

            shape = RoundedCornerShape(28.dp),

            color = JournalPaper,

            border = BorderStroke(
                width = 1.dp,
                color = JournalBorder
            )
        ) {

            Column(

                modifier = Modifier.padding(
                    horizontal = 26.dp,
                    vertical = 36.dp
                ),

                horizontalAlignment = Alignment.CenterHorizontally
            ) {


                // DEKORATİF İKON
                Surface(

                    shape = CircleShape,

                    color = JournalSoftGreen,

                    modifier = Modifier.size(76.dp)
                ) {

                    Box(
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = "✦",

                            style = MaterialTheme.typography.displaySmall,

                            color = JournalGreen
                        )
                    }
                }


                Spacer(
                    modifier = Modifier.height(22.dp)
                )


                // BAŞLIK
                Text(

                    text = when {

                        hasActiveSearch ->
                            "Aradığın anıyı bulamadık"

                        hasActiveDateFilter ->
                            "Bu tarihte bir anı yok"

                        hasOtherFilters ->
                            "Bu filtrelerde bir anı yok"

                        else ->
                            "Hikâyen burada başlıyor"
                    },

                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold
                    ),

                    color = JournalInk
                )


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                // AÇIKLAMA
                Text(

                    text = when {

                        hasActiveSearch ->
                            "Farklı bir kelime deneyebilir veya aramanı temizleyebilirsin."

                        hasActiveDateFilter ->
                            "Seçilen tarih aralığında henüz bir günlük bulunmuyor."

                        hasOtherFilters ->
                            "Kişi, sezon veya bölüm filtrelerini değiştirmeyi deneyebilirsin."

                        else ->
                            "Güzel anları, küçük detayları ve unutmak istemediğin günleri burada biriktir."
                    },

                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 23.sp
                    ),

                    color = JournalMuted
                )


                Spacer(
                    modifier = Modifier.height(24.dp)
                )


                // BUTONLAR
                if (hasAnyCondition) {

                    OutlinedButton(

                        onClick = {

                            if (hasActiveSearch) {
                                onClearSearch()
                            }

                            if (hasActiveDateFilter) {
                                onClearFilters()
                            }
                        },

                        border = BorderStroke(
                            1.dp,
                            JournalGreen
                        ),

                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JournalGreen
                        )
                    ) {

                        Text(

                            text = if (
                                hasActiveSearch && hasActiveDateFilter
                            ) {
                                "Aramayı ve filtreleri temizle"
                            } else if (hasActiveSearch) {
                                "Aramayı temizle"
                            } else {
                                "Filtreleri temizle"
                            }
                        )
                    }

                } else {

                    Button(

                        onClick = onCreateJournal,

                        shape = RoundedCornerShape(14.dp),

                        colors = ButtonDefaults.buttonColors(
                            containerColor = JournalGreen
                        )
                    ) {

                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )

                        Spacer(
                            modifier = Modifier.width(8.dp)
                        )

                        Text("İlk anını oluştur")
                    }
                }
            }
        }
    }
}
