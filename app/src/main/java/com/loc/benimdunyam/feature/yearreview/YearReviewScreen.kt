package com.loc.benimdunyam.feature.yearreview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.loc.benimdunyam.feature.yearreview.mood.YearMoodMapSection
import com.loc.benimdunyam.feature.yearreview.poster.YearPosterScreen
import com.loc.benimdunyam.feature.yearreview.video.YearMovieVideoExportScreen
import com.loc.benimdunyam.domain.model.JournalEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Paper = Color(0xFFFAF7F2)
private val Pale = Color(0xFFF2EADD)
private val Ink = Color(0xFF322A24)
private val Muted = Color(0xFF857A6F)
private val Pine = Color(0xFF395C4A)
private val Gold = Color(0xFFC8A36A)
private val Line = Color(0xFFE7DDD0)
private val Turkish = Locale.forLanguageTag("tr-TR")
private val MonthLabels = listOf("OCA", "ŞUB", "MAR", "NİS", "MAY", "HAZ", "TEM", "AĞU", "EYL", "EKİ", "KAS", "ARA")
private val MonthNames = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearReviewScreen(
    onNavigateBack: () -> Unit,
    onNavigateToJournalDetail: (String) -> Unit,
    viewModel: YearReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showMovie by rememberSaveable { mutableStateOf(false) }
    var showPoster by rememberSaveable { mutableStateOf(false) }
    var showVideoExport by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCovers()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Film, aynı yıl verisini kullanır; yeni Navigation veya ViewModel gerekmez.
    // Mevcut film seansı boyunca fotoğraf kapakları snapshot olarak sabitlenir:
    // arka planda kapaklar geldikçe sahnelerin sırası değişmez.
    if (showMovie && state.report != null && state.report!!.journalCount > 0) {
        val movieKey = state.report!!.seasonId ?: state.report!!.year.toString()
        val movieReport = remember(movieKey) { state.report!! }
        val movieCovers = remember(movieKey) { state.coverPaths.toMap() }
        val movieFeatured = remember(movieKey) { state.featuredMemories.toList() }
        val movieMoods = remember(movieKey) {
            state.moodReport?.takeIf { it.year == state.report!!.year }
        }
        YearMovieScreen(
            report = movieReport,
            coverPaths = movieCovers,
            featuredMemories = movieFeatured,
            moodReport = movieMoods,
            onClose = { showMovie = false },
            onJournalClick = { journalId ->
                showMovie = false
                onNavigateToJournalDetail(journalId)
            }
        )
        return
    }

    // Aynı rapor ve fotoğraf yolları kullanılır; ikinci rota/ViewModel gerekmez.
    if (showPoster && state.report != null && state.report!!.journalCount > 0) {
        YearPosterScreen(
            report = state.report!!,
            coverPaths = state.coverPaths,
            featuredMemories = state.featuredMemories,
            onClose = { showPoster = false }
        )
        return
    }

    // Yıl özeti, poster ve film içerikleri zaten mevcut ViewModel'de bulunuyor.
    // İkinci Hilt ViewModel veya Navigation rotası açmadan MP4 ekranına geçiyoruz.
    if (showVideoExport && state.report != null && state.report!!.journalCount > 0) {
        YearMovieVideoExportScreen(
            report = state.report!!,
            coverPaths = state.coverPaths,
            featuredMemories = state.featuredMemories,
            onClose = { showVideoExport = false }
        )
        return
    }

    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (state.seasonId == null) "Yıl Sonu Hikâyem" else "Sezonun Filmi",
                            color = Ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                        Text(state.seasonTitle ?: "HAYATININ BİR YILI • BİR HİKÂYE",
                            fontSize = 11.sp, color = Muted, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri dön", tint = Ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper)
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Pine)
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Bilinmeyen hata", color = Ink)
            }
            else -> {
                val report = state.report
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 48.dp)
                ) {
                    if (state.seasonId == null) {
                        item(key = "year-picker") {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.years, key = { it }) { year ->
                                    FilterChip(
                                        selected = state.selectedYear == year,
                                        onClick = { viewModel.selectYear(year) },
                                        label = { Text(year.toString(), fontWeight = FontWeight.SemiBold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Pine,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                    if (report != null) {
                        item(key = "poster-${report.year}") {
                            YearReviewPoster(report, Modifier.padding(horizontal = 18.dp))
                        }
                        if (report.journalCount > 0) {
                            item(key = "year-movie-action") {
                                Button(
                                    onClick = { showMovie = true },
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 18.dp).height(54.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Pine,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Hikâyemi İzle", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                        if (report.journalCount > 0) {
                            item(key = "year-poster-export") {
                                OutlinedButton(
                                    onClick = { showPoster = true },
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 18.dp).height(52.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, Pine),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Pine)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null)
                                    Spacer(Modifier.width(10.dp))
                                    Text(if (state.seasonId == null) "Yılımın Posterini Oluştur"
                                    else "Sezonumun Posterini Oluştur", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (report.journalCount > 0) {
                            item(key = "year-movie-mp4-export") {
                                OutlinedButton(
                                    onClick = { showVideoExport = true },
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 18.dp).height(52.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, Pine),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Pine)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(10.dp))
                                    Text(if (state.seasonId == null) "Yılımın Filmini MP4 Olarak Kaydet"
                                    else "Sezonumun Filmini MP4 Olarak Kaydet", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (report.journalCount > 0) {
                            item(key = "featured-memories-${report.year}") {
                                YearFeaturedMemoriesSection(
                                    year = report.year,
                                    memories = state.featuredMemories,
                                    onOpen = onNavigateToJournalDetail,
                                    onMove = viewModel::moveFeatured,
                                    onRemove = viewModel::removeFeatured,
                                    modifier = Modifier.padding(horizontal = 18.dp)
                                )
                            }
                        }
                        if (report.journalCount == 0) {
                            item(key = "empty") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(if (state.seasonId == null) "Henüz bu yıla ait bir anın yok"
                                    else "Bu döneme henüz bir anı eklenmemiş", color = Ink, fontFamily = FontFamily.Serif, fontSize = 23.sp)
                                    Text(if (state.seasonId == null)
                                        "Bir anı yazdığında bu yılın hikâyesi burada şekillenecek."
                                    else "Bu dönemde bir anı yazdığında film burada şekillenecek.", color = Muted)
                                }
                            }
                        } else {
                            item(key = "months") {
                                YearMonthlyScene(report, Modifier.padding(horizontal = 18.dp))
                            }
                            state.moodReport?.let { moodReport ->
                                item(key = "mood-map-${report.year}") {
                                    YearMoodMapSection(
                                        report = moodReport,
                                        modifier = Modifier.padding(horizontal = 18.dp),
                                        seasonTitle = report.seasonTitle
                                    )
                                }
                            }
                            if (report.topPeople.isNotEmpty()) {
                                item(key = "people") {
                                    YearPeopleScene(report, Modifier.padding(horizontal = 18.dp))
                                }
                            }
                            if (state.seasonId == null && report.topSeasons.isNotEmpty()) {
                                item(key = "seasons") {
                                    YearSeasonScene(report, Modifier.padding(horizontal = 18.dp))
                                }
                            }
                            item(key = "memories") {
                                YearMemoryScene(
                                    report = report,
                                    onJournalClick = onNavigateToJournalDetail,
                                    modifier = Modifier.padding(horizontal = 18.dp)
                                )
                            }
                            if (state.coverPaths.isNotEmpty()) {
                                item(key = "photostrip") {
                                    YearPhotoScene(
                                        report = report,
                                        paths = state.coverPaths,
                                        onJournalClick = onNavigateToJournalDetail
                                    )
                                }
                            }
                            item(key = "end-card") {
                                YearEndingCard(report.year, Modifier.padding(horizontal = 18.dp), report.seasonTitle)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearReviewPoster(report: YearReviewReport, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = 330.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Pine, Color(0xFF203B30), Color(0xFF172C25))))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("BENİM DÜNYAM ORIGINAL STORY", color = Color(0xFFDED2B7), fontSize = 10.sp, letterSpacing = 2.1.sp)
            Spacer(Modifier.height(5.dp))
            Text(report.seasonTitle ?: report.year.toString(), color = Gold,
                fontSize = if (report.seasonId == null) 84.sp else 32.sp,
                lineHeight = if (report.seasonId == null) 88.sp else 39.sp,
                fontWeight = FontWeight.Light, fontFamily = FontFamily.Serif)
            Text("Benim Hikâyem", color = Color.White, fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif)
            Text(
                if (report.journalCount == 0) "Henüz yazılmayı bekleyen bir hikâye..."
                else if (report.seasonId == null) "Bir yıl. Sayısız küçük an. Hepsi sana ait."
                else "Bir dönem. Biriktirdiğin bütün anılar, sana ait.",
                color = Color(0xFFE3E8E0), fontSize = 14.sp
            )
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                PosterMetric(report.journalCount.toString(), "ANI")
                PosterMetric(report.uniquePeopleCount.toString(), "İNSAN")
                PosterMetric(if (report.seasonId == null) report.seasonCount.toString()
                else report.activeMonthCount.toString(),
                    if (report.seasonId == null) "SEZON" else "AY")
            }
        }
    }
}

@Composable
private fun PosterMetric(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, color = Color.White, fontFamily = FontFamily.Serif, fontSize = 26.sp)
        Text(label, color = Color(0xFFD3DACF), letterSpacing = 1.1.sp, fontSize = 10.sp)
    }
}

@Composable
private fun SceneTitle(chapter: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(chapter, color = Pine, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Ink, fontSize = 25.sp, lineHeight = 29.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun YearCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        color = Color(0xFFFFFDF9),
        border = BorderStroke(1.dp, Line)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
            content = content
        )
    }
}

@Composable
private fun YearMonthlyScene(report: YearReviewReport, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SceneTitle("SAHNE 01  /  ZAMAN",
            if (report.seasonId == null) "On iki ayın izi" else "Bu dönemin ayları",
            "Hikâyenin hangi aylarda daha yoğun yazıldığını keşfet.")
        YearCard {
            val max = (report.monthlyCounts.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                report.monthlyCounts.forEachIndexed { index, count ->
                    Column(
                        modifier = Modifier.width(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(if (count == 0) "" else "$count", color = Pine, fontSize = 9.sp)
                        Box(
                            modifier = Modifier.width(15.dp)
                                .height((12f + count * 86f / max).dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(if (count == max) Gold else Color(0xFF99B1A3))
                        )
                        Text(MonthLabels[index], color = Muted, fontSize = 8.sp)
                    }
                }
            }
            HorizontalDivider(color = Line)
            Text("${report.activeMonthCount} farklı ayda anı biriktirdin.", color = Ink, fontSize = 14.sp)
            report.busiestMonthIndex?.let {
                Text("En çok anı yazdığın ay: ${MonthNames[it]}", color = Pine, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun YearPeopleScene(report: YearReviewReport, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SceneTitle("SAHNE 02  /  KARAKTERLER",
            if (report.seasonId == null) "Bu yılın insanları" else "Bu dönemin insanları",
            "Günlüklerinde en çok yer verdiğin kişiler.")
        YearCard {
            report.topPeople.forEachIndexed { index, person ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(38.dp).background(Pale, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("${index + 1}", color = Pine, fontFamily = FontFamily.Serif, fontSize = 19.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(person.name, color = Ink, fontWeight = FontWeight.SemiBold)
                        Text("${person.journalCount} farklı anıda anıldı", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun YearSeasonScene(report: YearReviewReport, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SceneTitle("SAHNE 03  /  SEZONLAR", "Yılın perdeleri", "Hikâyenin geçtiği yaşam dönemleri.")
        YearCard {
            report.topSeasons.forEach { season ->
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(season.title, color = Ink, fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${season.journalCount} anı", color = Pine, fontSize = 12.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(9.dp)).background(Pale)) {
                        Box(Modifier.fillMaxWidth(season.journalCount.toFloat() / report.journalCount.coerceAtLeast(1))
                            .fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(Pine))
                    }
                }
            }
        }
    }
}

@Composable
private fun YearMemoryScene(
    report: YearReviewReport,
    onJournalClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SceneTitle("SAHNE 04  /  ANI DEFTERİ", "Dönüm sayfaları",
            if (report.seasonId == null) "Yılın başından ortasına, oradan son sayfaya..."
            else "Dönemin ilk anısından son sayfasına...")
        YearCard {
            report.selectedMemories.forEachIndexed { index, journal ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { onJournalClick(journal.id) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("%02d".format(index + 1), color = Gold, fontFamily = FontFamily.Serif, fontSize = 25.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(formatYearReviewDate(journal.entryDate), color = Muted, fontSize = 11.sp)
                        Text(journal.title.ifBlank { "Adsız Anı" }, color = Ink,
                            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(journal.content, color = Muted, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ArrowForward, contentDescription = "Anıyı aç", tint = Pine)
                }
                if (index != report.selectedMemories.lastIndex) HorizontalDivider(color = Line)
            }
        }
    }
}

@Composable
private fun YearPhotoScene(
    report: YearReviewReport,
    paths: Map<String, String>,
    onJournalClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            SceneTitle("SAHNE 05  /  KARELER",
                if (report.seasonId == null) "Yıldan kalan görüntüler" else "Dönemden kalan kareler",
                "Fotoğraflı anılarından küçük bir film şeridi.")
        }
        val photoJournals = report.coverCandidates.filter { it.id in paths }
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(photoJournals, key = { it.id }) { journal ->
                Surface(
                    modifier = Modifier.width(178.dp).clickable { onJournalClick(journal.id) },
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Line)
                ) {
                    Column {
                        AsyncImage(
                            model = File(paths.getValue(journal.id)),
                            contentDescription = journal.title,
                            modifier = Modifier.fillMaxWidth().height(144.dp),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            journal.title.ifBlank { "Bir anı" },
                            modifier = Modifier.padding(12.dp),
                            color = Ink, fontFamily = FontFamily.Serif,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearEndingCard(year: Int, modifier: Modifier = Modifier, seasonTitle: String? = null) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFFEEE1CC), Color(0xFFF7F0E4))))) {
        Column(Modifier.fillMaxWidth().padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("✦", fontSize = 30.sp, color = Gold)
            Text("Ve hikâye devam ediyor...", fontSize = 25.sp, color = Ink,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Text(if (seasonTitle == null)
                "$year yılından geriye kalan bu satırlar, yaşadığın günlerin sessiz tanıkları."
            else "$seasonTitle döneminden geriye kalan bu anılar, yaşadığın günlerin sessiz tanıkları.",
                color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
            Text("BENİM DÜNYAM • SON SAHNE", color = Pine, fontSize = 10.sp, letterSpacing = 1.7.sp)
        }
    }
}

private fun formatYearReviewDate(time: Long): String =
    SimpleDateFormat("d MMMM yyyy", Turkish).format(Date(time))


/** Filmde görünecek 3–5 kişisel seçimi yıllık rapordan yönetme alanı. */
@Composable
private fun YearFeaturedMemoriesSection(
    year: Int,
    memories: List<JournalEntry>,
    onOpen: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Gold)
            Text("Unutulmaz Sahneler", color = Ink,
                style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
        }
        Text(
            "Filme seçtiğin ${memories.size}/5 anı. " +
                    "Anı detayına giderek yıldızla işaretleyebilirsin.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
        if (memories.isEmpty()) {
            Surface(color = Pale, shape = RoundedCornerShape(16.dp)) {
                Text("Henüz özel bir sahne seçmedin. Bir anıyı aç ve 'Unutulmaz anı olarak seç' düğmesine bas.",
                    modifier = Modifier.padding(18.dp), color = Ink)
            }
        }
        memories.forEachIndexed { index, journal ->
            Surface(
                color = Pale,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${index + 1}.", color = Gold,
                        fontFamily = FontFamily.Serif, fontSize = 21.sp)
                    Column(
                        modifier = Modifier.weight(1f).clickable { onOpen(journal.id) },
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(journal.title.ifBlank { "Adsız anı" }, color = Ink,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Anıyı aç", color = Muted, fontSize = 11.sp)
                    }
                    IconButton(
                        onClick = { onMove(journal.id, -1) }, enabled = index > 0,
                        modifier = Modifier.size(34.dp)
                    ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Öne taşı", tint = Pine) }
                    IconButton(
                        onClick = { onMove(journal.id, 1) }, enabled = index < memories.lastIndex,
                        modifier = Modifier.size(34.dp)
                    ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Arkaya taşı", tint = Pine) }
                    IconButton(
                        onClick = { onRemove(journal.id) }, modifier = Modifier.size(34.dp)
                    ) { Icon(Icons.Default.Close, contentDescription = "Özel anılardan çıkar", tint = Muted) }
                }
            }
        }
    }
}
