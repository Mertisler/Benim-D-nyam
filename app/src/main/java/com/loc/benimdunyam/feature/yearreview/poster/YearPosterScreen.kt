package com.loc.benimdunyam.feature.yearreview.poster

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.feature.yearreview.YearReviewReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Pine = Color(0xFF2C503B)
private val Paper = Color(0xFFFAF7F2)
private val Ink = Color(0xFF292D29)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearPosterScreen(
    report: YearReviewReport,
    coverPaths: Map<String, String>,
    featuredMemories: List<JournalEntry>,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var options by remember(report.seasonId ?: report.year.toString()) { mutableStateOf(YearPosterOptions()) }
    var preview by remember(report.seasonId ?: report.year.toString()) { mutableStateOf<Bitmap?>(null) }
    var preparingPreview by remember(report.seasonId ?: report.year.toString()) { mutableStateOf(true) }
    var exporting by remember { mutableStateOf(false) }
    var pendingDocument by remember { mutableStateOf<Pair<YearPosterContent, YearPosterOptions>?>(null) }

    val content = remember(report, coverPaths, featuredMemories, options) {
        YearPosterModel.create(report, coverPaths, featuredMemories, options)
    }
    val photoChoices = remember(report, coverPaths, featuredMemories) {
        (featuredMemories + report.coverCandidates +
                report.monthlyStories.flatMap { it.coverCandidates } + report.selectedMemories)
            .distinctBy { it.id }.filter { !coverPaths[it.id].isNullOrBlank() }.take(15)
    }
    val memoryChoices = remember(report, featuredMemories) {
        (featuredMemories + report.selectedMemories).distinctBy { it.id }.take(8)
    }
    BackHandler(onBack = onClose)

    // Önizleme ve tam boy PNG aynı çiziciyi kullanır; yalnızca çözünürlük değişir.
    LaunchedEffect(content, options) {
        preparingPreview = true
        preview = null
        try {
            preview = withContext(Dispatchers.Default) {
                YearPosterRenderer.render(context.applicationContext, content, options, 540, 960)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            snackbar.showSnackbar("Poster önizlemesi oluşturulamadı: ${e.localizedMessage.orEmpty()}")
        } finally {
            preparingPreview = false
        }
    }

    suspend fun createExportBitmap(spec: YearPosterContent, settings: YearPosterOptions): Bitmap =
        withContext(Dispatchers.Default) {
            YearPosterRenderer.render(context.applicationContext, spec, settings, 1080, 1920)
        }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val snapshot = pendingDocument
        pendingDocument = null
        if (uri != null && snapshot != null && !exporting) {
            scope.launch {
                exporting = true
                try {
                    val bitmap = createExportBitmap(snapshot.first, snapshot.second)
                    try {
                        withContext(Dispatchers.IO) {
                            YearPosterExporter.saveToDocument(context, bitmap, uri)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                    snackbar.showSnackbar("Poster PNG olarak kaydedildi.")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    snackbar.showSnackbar("Kaydedilemedi: ${e.localizedMessage ?: "Bilinmeyen hata"}")
                } finally {
                    exporting = false
                }
            }
        }
    }

    fun savePoster() {
        if (exporting) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 ve öncesinde depolama izni istemek yerine sistem dosya seçicisi.
            pendingDocument = content to options
            documentLauncher.launch("BenimDunyam_${report.seasonId?.take(8) ?: report.year}_Poster.png")
            return
        }
        val snapshot = content to options
        scope.launch {
            exporting = true
            try {
                val bitmap = createExportBitmap(snapshot.first, snapshot.second)
                try {
                    withContext(Dispatchers.IO) {
                        YearPosterExporter.saveToGallery(context, bitmap, report.year)
                    }
                } finally {
                    bitmap.recycle()
                }
                snackbar.showSnackbar("Poster Galeri → Resimler / BenimDunyam içine kaydedildi.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                snackbar.showSnackbar("Galeriye kaydedilemedi: ${e.localizedMessage ?: "Bilinmeyen hata"}")
            } finally {
                exporting = false
            }
        }
    }

    fun sharePoster() {
        if (exporting) return
        val snapshot = content to options
        scope.launch {
            exporting = true
            try {
                val bitmap = createExportBitmap(snapshot.first, snapshot.second)
                val shareIntent = try {
                    withContext(Dispatchers.IO) {
                        YearPosterExporter.createShareIntent(context, bitmap, report.year)
                    }
                } finally {
                    bitmap.recycle()
                }
                context.startActivity(Intent.createChooser(shareIntent, if (report.seasonId != null) "Sezonun posterini paylaş" else "Yılın posterini paylaş"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                snackbar.showSnackbar(
                    "Paylaşılamadı. Manifest'teki FileProvider tanımını kontrol et: " +
                            (e.localizedMessage ?: "Bilinmeyen hata")
                )
            } finally {
                exporting = false
            }
        }
    }

    Scaffold(
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Yılın Sinematik Posteri", fontWeight = FontWeight.Bold, color = Ink)
                        Text("${report.year} • Paylaşmadan önce özelleştir", fontSize = 11.sp, color = Pine)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Yıl özetine dön")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper)
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Paper).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = ::savePoster, enabled = !exporting,
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pine),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (Build.VERSION.SDK_INT >= 29) "Galeriye Kaydet" else "PNG Kaydet", fontSize = 12.sp)
                    }
                    Button(
                        onClick = ::sharePoster, enabled = !exporting,
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB18A53)),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Paylaş", fontSize = 13.sp)
                    }
                }
                if (exporting) {
                    Text("PNG hazırlanıyor…", color = Pine, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "poster-preview") {
                Box(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 390.dp)
                        .aspectRatio(9f / 16f),
                    contentAlignment = Alignment.Center
                ) {
                    val image = preview
                    if (image != null) {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = "${report.year} yılı poster önizlemesi",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (preparingPreview || image == null) {
                        CircularProgressIndicator(color = Pine)
                    }
                }
            }
            item(key = "poster-theme") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Poster tarzı", fontWeight = FontWeight.Bold, color = Ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = options.theme == YearPosterTheme.CINEMA,
                                onClick = { options = options.copy(theme = YearPosterTheme.CINEMA) },
                                label = { Text("Sinematik Gece") }
                            )
                            FilterChip(
                                selected = options.theme == YearPosterTheme.PAPER,
                                onClick = { options = options.copy(theme = YearPosterTheme.PAPER) },
                                label = { Text("Hatıra Kâğıdı") }
                            )
                        }
                    }
                }
            }
            item(key = "poster-privacy") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Paylaşımda görünecek bilgiler", color = Ink, fontWeight = FontWeight.Bold)
                        Text("Fotoğraf, kişi adı ve anı metni varsayılan olarak gizlidir.",
                            color = Pine, fontSize = 12.sp)
                        HorizontalDivider()
                        PosterOptionRow("Fotoğrafı kullan", "Yılın fotoğraflarından bir kapak", options.includePhotos) {
                            options = options.copy(includePhotos = it)
                        }
                        PosterOptionRow("Kişi adlarını göster", "En sık anılan üç kişi", options.includePersonNames) {
                            options = options.copy(includePersonNames = it)
                        }
                        PosterOptionRow("Anı metnini göster", "Özel anının başlığı ve kısa alıntı", options.includeMemoryText) {
                            options = options.copy(includeMemoryText = it)
                        }
                        PosterOptionRow("İstatistikleri göster", "Anı, kişi ve sezon sayıları", options.includeStats) {
                            options = options.copy(includeStats = it)
                        }
                    }
                }
            }
            if (options.includePhotos && photoChoices.isNotEmpty()) {
                item(key = "poster-photo-choice") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Poster kapağını seç", fontWeight = FontWeight.Bold, color = Ink)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(photoChoices, key = { it.id }) { journal ->
                                    val photoPath = coverPaths[journal.id]
                                    val chosen = options.preferredPhotoJournalId == journal.id ||
                                            (options.preferredPhotoJournalId == null && content.photoPath == photoPath)
                                    Box(
                                        Modifier.size(88.dp)
                                            .border(
                                                width = if (chosen) 3.dp else 1.dp,
                                                color = if (chosen) Pine else Color.LightGray,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                options = options.copy(preferredPhotoJournalId = journal.id)
                                            }
                                    ) {
                                        if (photoPath != null) {
                                            AsyncImage(
                                                model = File(photoPath),
                                                contentDescription = "Kapak fotoğrafı: ${journal.title}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize().padding(4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (options.includeMemoryText && memoryChoices.isNotEmpty()) {
                item(key = "poster-memory-choice") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Posterdeki anıyı seç", fontWeight = FontWeight.Bold, color = Ink)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(memoryChoices, key = { it.id }) { journal ->
                                    FilterChip(
                                        selected = (options.preferredMemoryJournalId ?: memoryChoices.first().id) == journal.id,
                                        onClick = { options = options.copy(preferredMemoryJournalId = journal.id) },
                                        label = { Text(journal.title.take(24)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "privacy-note") {
                Text(
                    "Not: Paylaş butonu fotoğrafı otomatik olarak galeriye kaydetmez. " +
                            "Yalnızca önizlemede açtığın bilgiler PNG içine işlenir. " +
                            "Kaydetmeden veya paylaşmadan önce posteri kontrol et.",
                    color = Pine, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PosterOptionRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 14.sp)
            Text(description, color = Color(0xFF7A766C), fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
