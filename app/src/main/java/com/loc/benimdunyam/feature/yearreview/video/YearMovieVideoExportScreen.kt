package com.loc.benimdunyam.feature.yearreview.video

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.feature.yearreview.YearReviewReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Bg = Color(0xFFFAF7F2)
private val Pine = Color(0xFF284B39)
private val Gold = Color(0xFFE7C68A)
private val Ink = Color(0xFF322A24)

private sealed class VideoDestination {
    data object Gallery : VideoDestination()
    data object Share : VideoDestination()
    data class Document(val uri: android.net.Uri) : VideoDestination()
}

/** Film ekranından bağımsızdır. Kaydetme/paylaşma tuşuna basılmadan hiçbir video üretilmez. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearMovieVideoExportScreen(
    report: YearReviewReport,
    coverPaths: Map<String, String>,
    featuredMemories: List<JournalEntry>,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var includePhotos by rememberSaveable { mutableStateOf(false) }
    var includePersonNames by rememberSaveable { mutableStateOf(false) }
    var includeMemoryText by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var task by remember { mutableStateOf<Job?>(null) }
    var stage by remember { mutableStateOf("Hazır") }
    val options = YearMovieVideoOptions(includePhotos, includePersonNames, includeMemoryText)
    val videoPlan = remember(report, coverPaths, featuredMemories, options) {
        YearMovieVideoPlanner.create(report, coverPaths, featuredMemories, options)
    }

    fun startExport(destination: VideoDestination) {
        if (busy || videoPlan.totalFrames <= 0) return
        val chosen = options.copy()
        busy = true
        progress = 0
        stage = "MP4 hazırlanıyor"
        task = scope.launch {
            var temporary: File? = null
            try {
                temporary = YearMovieVideoEncoder.encode(
                    context.applicationContext, report, coverPaths, featuredMemories, chosen
                ) { percent -> progress = percent }
                stage = "Dosya aktarılıyor"
                when (destination) {
                    VideoDestination.Gallery -> {
                        withContext(Dispatchers.IO) {
                            YearMovieVideoExporter.saveToGallery(context, temporary, report.year)
                        }
                        stage = "Kaydedildi"
                        snackbar.showSnackbar("Film Galeri → Filmler / BenimDunyam klasörüne kaydedildi.")
                    }
                    VideoDestination.Share -> {
                        val intent = withContext(Dispatchers.IO) {
                            YearMovieVideoExporter.createShareIntent(context, temporary, report.year)
                        }
                        context.startActivity(Intent.createChooser(intent, if (report.seasonId == null) "Yılımın filmini paylaş" else "Sezonumun filmini paylaş"))
                        stage = "Paylaşım açıldı"
                    }
                    is VideoDestination.Document -> {
                        withContext(Dispatchers.IO) {
                            YearMovieVideoExporter.saveToDocument(context, temporary, destination.uri)
                        }
                        stage = "Dosya kaydedildi"
                        snackbar.showSnackbar("MP4 seçtiğin dosya konumuna kaydedildi.")
                    }
                }
                progress = 100
            } catch (_: CancellationException) {
                stage = "İptal edildi"
            } catch (e: Exception) {
                stage = "Oluşturulamadı"
                snackbar.showSnackbar(
                    "MP4 oluşturulamadı: ${e.localizedMessage ?: "Cihazın video kodlayıcısı desteklemiyor olabilir."}"
                )
            } finally {
                temporary?.delete()
                busy = false
            }
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri -> if (uri != null) startExport(VideoDestination.Document(uri)) }

    fun saveVideo() {
        if (busy) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startExport(VideoDestination.Gallery)
        else documentLauncher.launch(YearMovieVideoExporter.fileName(report.year))
    }

    fun back() {
        if (busy) {
            stage = "İptal ediliyor"
            task?.cancel()
        } else onClose()
    }
    BackHandler(onBack = ::back)

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (report.seasonId == null) "Yılımın Filmini Kaydet" else "Sezonumun Filmini Kaydet", color = Ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                        Text("${report.seasonTitle ?: report.year.toString()} • MP4 video", color = Pine, fontSize = 11.sp)
                    }
                },
                navigationIcon = { IconButton(onClick = ::back) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Geri dön")
                } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().background(Bg).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (busy) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Pine
                    )
                    Text("$stage • %$progress", fontSize = 13.sp, color = Ink)
                    OutlinedButton(onClick = { stage = "İptal ediliyor"; task?.cancel() },
                        modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("İptal Et") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = ::saveVideo,
                            enabled = videoPlan.totalFrames > 0,
                            modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Pine)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text(if (Build.VERSION.SDK_INT >= 29) "Galeriye Kaydet" else "MP4 Kaydet", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { startExport(VideoDestination.Share) },
                            enabled = videoPlan.totalFrames > 0,
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Paylaş", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().background(Pine, RoundedCornerShape(22.dp)).padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Gold)
                    Text("${report.seasonTitle ?: report.year.toString()} — BENİM HİKÂYEM", color = Gold,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 25.sp)
                    Text("${report.journalCount} anı · ${report.activeMonthCount} aktif ay · ${featuredMemories.size} özel sahne",
                        color = Color.White, fontSize = 13.sp)
                    Text("720 × 1280  •  24 FPS  •  yaklaşık ${videoPlan.durationSeconds} saniye",
                        color = Color(0xFFD5E0D8), fontSize = 12.sp)
                }
            }
            Text("Paylaşımda neler görünsün?", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("Günlüğün özel. Bu seçenekler her açılışta kapalı gelir. Açmadığın içerik videoda görünmez.",
                color = Color(0xFF766D64), fontSize = 13.sp)
            PrivacySwitch("Anı fotoğrafları", "Seçilmiş kapak fotoğrafları videoya dahil edilir.",
                includePhotos, busy) { includePhotos = it }
            HorizontalDivider()
            PrivacySwitch("Kişi adları", if (report.seasonId == null) "Yılın oyuncu kadrosu ve aylardaki isimler görünür."
            else "Bu dönemin oyuncu kadrosu ve anılardaki isimler görünür.",
                includePersonNames, busy) { includePersonNames = it }
            HorizontalDivider()
            PrivacySwitch("Anı başlıkları ve metinleri", "Özel anılar, aylık seçkiler ve sezon başlıkları görünür.",
                includeMemoryText, busy) { includeMemoryText = it }
            HorizontalDivider()
            Text("Video sessiz oluşturulur. İnternet, kayıt izni veya üçüncü taraf video servisi gerekmez.",
                fontSize = 12.sp, color = Color(0xFF766D64))
            Text("Film dosyasını paylaşırsan veya galeriye kaydedersen, seçtiğin özel bilgiler cihaz dışına çıkabilir ya da fotoğraf yedekleme uygulamalarınca senkronize edilebilir.",
                fontSize = 12.sp, color = Color(0xFF766D64))
            if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), color = Pine, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Videon hazırlanıyor. İptal etmek istersen aşağıdaki butonu kullan.", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PrivacySwitch(
    title: String, description: String, checked: Boolean, disabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(description, fontSize = 12.sp, color = Color(0xFF766D64))
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = !disabled)
    }
}
