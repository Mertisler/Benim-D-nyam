package com.loc.benimdunyam.feature.yearreview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.feature.yearreview.mood.YearMoodReport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ivory = Color(0xFFFDF6E9)
private val Champagne = Color(0xFFE7C68A)
private val LightGreen = Color(0xFFA9C2AF)
private val Forest = Color(0xFF173629)
private val SoftWhite = Ivory.copy(alpha = 0.80f)
private val MonthShort = listOf("O", "Ş", "M", "N", "M", "H", "T", "A", "E", "E", "K", "A")
private val MonthFull = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
private val Tr = Locale.forLanguageTag("tr-TR")

@Composable
internal fun YearMovieSceneContent(
    chapter: YearMovieChapter,
    featuredMemory: JournalEntry? = null,
    monthStory: YearReviewMonthStory? = null,
    moodReport: YearMoodReport? = null,
    sceneProgress: Float = 0f,
    report: YearReviewReport,
    coverPaths: Map<String, String>,
    onJournalClick: (String) -> Unit,
    onReplay: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (chapter) {
            YearMovieChapter.OPENING -> OpeningScene(report, coverPaths, sceneProgress)
            YearMovieChapter.MONTHS -> MonthsScene(report)
            YearMovieChapter.MONTH_STORY -> monthStory?.let {
                MonthStoryScene(it, report.year, coverPaths, sceneProgress, onJournalClick)
            }
            YearMovieChapter.MOOD -> moodReport?.let { YearMoodMovieScene(it, sceneProgress) }
            YearMovieChapter.CAST -> CastScene(report)
            YearMovieChapter.SEASONS -> SeasonsScene(report)
            YearMovieChapter.MEMORIES -> MemoriesScene(report, onJournalClick)
            YearMovieChapter.FEATURED -> featuredMemory?.let {
                FeaturedMemoryScene(it, coverPaths[it.id], sceneProgress, onJournalClick)
            }
            YearMovieChapter.PHOTOS -> PhotosScene(report, coverPaths, onJournalClick)
            YearMovieChapter.FINALE -> FinaleScene(report, onReplay, onClose)
        }
    }
}

@Composable
private fun SceneHeading(label: String, title: String, sub: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MovieReveal(delayMillis = 80) {
            Text(label, fontSize = 10.sp, letterSpacing = 2.6.sp,
                color = Champagne, fontWeight = FontWeight.Bold)
        }
        MovieReveal(delayMillis = 210) {
            Text(title, color = Ivory, fontFamily = FontFamily.Serif,
                fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
        }
        if (!sub.isNullOrBlank()) {
            MovieReveal(delayMillis = 370) {
                Text(sub, color = SoftWhite, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun MovieGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF284738).copy(alpha = 0.94f),
        border = BorderStroke(1.dp, Ivory.copy(alpha = 0.17f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun OpeningScene(report: YearReviewReport, paths: Map<String, String>, sceneProgress: Float) {
    val reducedMotion = LocalMovieReducedMotion.current
    val selectedCover = report.coverCandidates.firstOrNull { paths.containsKey(it.id) }
        ?.let { paths[it.id] }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Text("BİR BENİM DÜNYAM HİKÂYESİ", color = Champagne,
            fontSize = 10.sp, letterSpacing = 2.3.sp, fontWeight = FontWeight.Bold)
        if (selectedCover != null) {
            Box(Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(18.dp))) {
                AsyncImage(
                    model = File(selectedCover), contentDescription = "Yılın fotoğraflı anılarından biri",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .movieKenBurns(sceneProgress, reducedMotion, direction = 1)
                )
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0F2119).copy(alpha = 0.7f)))
                ))
                Text("HAYATININ BAŞROLÜNDE SEN VARSIN",
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    color = Ivory, fontSize = 11.sp, letterSpacing = 1.4.sp)
            }
        }
        MovieReveal(delayMillis = 160) {
            Text(report.year.toString(), color = Champagne,
                fontSize = 82.sp, lineHeight = 88.sp, fontFamily = FontFamily.Serif)
        }
        MovieReveal(delayMillis = 380) {
            Text("Benim Hikâyem", color = Ivory,
                fontSize = 31.sp, lineHeight = 36.sp, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold)
        }
        Text("Bir yılı gün gün yaşadın. Şimdi bıraktığın izlere bakma zamanı.",
            color = SoftWhite, fontSize = 15.sp, lineHeight = 23.sp)
        MovieGlassCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                YearNumber(report.journalCount, "ANI")
                YearNumber(report.uniquePeopleCount, "İNSAN")
                YearNumber(report.seasonCount, "SEZON")
            }
        }
        Text("OYUNCULAR • ANILAR • SEZONLAR", color = LightGreen,
            fontSize = 10.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun YearNumber(value: Int, label: String) {
    val reducedMotion = LocalMovieReducedMotion.current
    var started by remember(value) { mutableStateOf(reducedMotion) }
    LaunchedEffect(value, reducedMotion) {
        if (reducedMotion) started = true
        else {
            delay(350)
            started = true
        }
    }
    val visibleNumber by animateIntAsState(
        targetValue = if (reducedMotion || started) value else 0,
        animationSpec = tween(if (reducedMotion) 1 else 1_100),
        label = "Yıl istatistiği"
    )
    Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(visibleNumber.toString(), color = Champagne, fontFamily = FontFamily.Serif, fontSize = 29.sp)
        Text(label, color = SoftWhite, fontSize = 9.sp, letterSpacing = 1.1.sp)
    }
}

@Composable
private fun MonthsScene(report: YearReviewReport) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(23.dp)) {
        SceneHeading("SAHNE 01  /  ZAMAN", "On iki ay.\nTek bir hikâye.",
            "Yılın boyunca yazdığın anıların ritmi.")
        MovieGlassCard {
            val reveal = movieRevealFraction(delayMillis = 250, durationMillis = 1_200)
            val top = (report.monthlyCounts.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(130.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                report.monthlyCounts.forEachIndexed { index, count ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(((12f + 95f * count / top) * reveal).dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(if (count == top) Champagne else LightGreen.copy(alpha = 0.65f))
                        )
                        Text(MonthShort[index], fontSize = 10.sp, color = SoftWhite)
                    }
                }
            }
            HorizontalDivider(color = Ivory.copy(alpha = 0.20f))
            Text("${report.activeMonthCount} farklı ayda anı bıraktın.",
                fontSize = 14.sp, color = Ivory)
        }
        report.busiestMonthIndex?.let { index ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("EN ÇOK ANI YAZDIĞIN AY", color = LightGreen,
                    letterSpacing = 2.sp, fontSize = 10.sp)
                Text(MonthFull[index], color = Champagne,
                    fontSize = 37.sp, fontFamily = FontFamily.Serif)
                Text("${report.monthlyCounts[index]} anı", color = SoftWhite, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun CastScene(report: YearReviewReport) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(19.dp)) {
        SceneHeading("SAHNE 02  /  İNSANLAR", "Bu yılın\noyuncu kadrosu.",
            "Hikâyendeki yerleri, anılarında ne sıklıkla geçtiklerine göre.")
        val head = report.topPeople.firstOrNull()
        if (head != null) {
            MovieGlassCard {
                Text("EN ÇOK ANILAN KİŞİ", color = Champagne,
                    fontSize = 10.sp, letterSpacing = 2.sp)
                Text(head.name, color = Ivory, fontFamily = FontFamily.Serif,
                    fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("${head.journalCount} farklı anında yer aldı.", color = SoftWhite, fontSize = 13.sp)
            }
        }
        report.topPeople.drop(1).forEachIndexed { i, person ->
            Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Box(
                    Modifier.size(33.dp).background(Ivory.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${i + 2}", color = Champagne, fontFamily = FontFamily.Serif)
                }
                Text(person.name, color = Ivory, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${person.journalCount} anı", color = LightGreen, fontSize = 12.sp)
            }
        }
        Text("Toplam ${report.uniquePeopleCount} farklı kişi anılarında yer aldı.",
            color = SoftWhite, fontSize = 12.sp)
    }
}

@Composable
private fun SeasonsScene(report: YearReviewReport) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SceneHeading("SAHNE 03  /  DÖNEMLER", "Yılın perdeleri.",
            "Bu yılki anıların hangi yaşam sezonlarında geçti?")
        val seasons = report.topSeasons.filter { it.id != null }
        MovieGlassCard {
            val reveal = movieRevealFraction(delayMillis = 330, durationMillis = 1_000)
            seasons.forEachIndexed { index, season ->
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("PERDE ${index + 1}", color = Champagne,
                        fontSize = 9.sp, letterSpacing = 1.6.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(season.title, color = Ivory, modifier = Modifier.weight(1f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Serif, fontSize = 17.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("${season.journalCount} anı", color = LightGreen, fontSize = 12.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(5.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Ivory.copy(alpha = 0.15f))) {
                        Box(Modifier.fillMaxWidth((season.journalCount.toFloat() /
                                report.journalCount.coerceAtLeast(1) * reveal).coerceIn(0f, 1f))
                            .fillMaxHeight().background(Champagne))
                    }
                }
                if (index != seasons.lastIndex) {
                    HorizontalDivider(color = Ivory.copy(alpha = 0.12f))
                }
            }
        }
    }
}

@Composable
private fun MemoriesScene(report: YearReviewReport, onJournalClick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        SceneHeading("SAHNE 04  /  ANI DEFTERİ", "Dönüm sayfaları.",
            "İlk, orta ve son kayıtlarından küçük bir seçki.")
        report.selectedMemories.forEachIndexed { index, journal ->
            MovieGlassCard(
                modifier = Modifier.clickable { onJournalClick(journal.id) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("%02d".format(Tr, index + 1), color = Champagne,
                        fontSize = 26.sp, fontFamily = FontFamily.Serif)
                    Text(journal.movieDate(), color = LightGreen, fontSize = 12.sp)
                }
                Text(journal.title.ifBlank { "Adsız Anı" },
                    color = Ivory, fontFamily = FontFamily.Serif, fontSize = 19.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (journal.content.isNotBlank()) {
                    Text(journal.content.trim(), color = SoftWhite, fontSize = 12.sp,
                        maxLines = 2, lineHeight = 18.sp, overflow = TextOverflow.Ellipsis)
                }
                Text("ANIYI AÇ  ›", color = Champagne, fontSize = 10.sp,
                    letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PhotosScene(
    report: YearReviewReport,
    paths: Map<String, String>,
    onJournalClick: (String) -> Unit
) {
    val photos = report.coverCandidates.filter { paths[it.id] != null }.take(8)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        SceneHeading("SAHNE 05  /  KARELER", "Zamanın\nbıraktığı kareler.",
            "Fotoğraflı anılarından seçilmiş küçük bir film şeridi.")
        // LazyRow bounded-height: dikey filmin içinde yatay galeri güvenli çalışır.
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(305.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(photos, key = { it.id }) { journal ->
                val path = paths[journal.id] ?: return@items
                Column(Modifier.width(208.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Ivory.copy(alpha = 0.10f))
                    .clickable { onJournalClick(journal.id) }) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "${journal.title} anısının kapak fotoğrafı",
                        modifier = Modifier.fillMaxWidth().height(233.dp),
                        contentScale = ContentScale.Crop
                    )
                    Text(journal.title.ifBlank { "Bir anı" },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        color = Ivory, fontFamily = FontFamily.Serif,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text("Bir kareye dokunarak o günün anısını açabilirsin.",
            fontSize = 12.sp, color = SoftWhite)
    }
}

@Composable
private fun FinaleScene(
    report: YearReviewReport,
    onReplay: () -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        MovieReveal(delayMillis = 120) {
            Text("✦", fontSize = 44.sp, color = Champagne)
        }
        MovieReveal(delayMillis = 360) {
            Text("VE BİR YIL\nBÖYLE GEÇTİ.", color = Champagne, fontSize = 11.sp,
                letterSpacing = 3.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        }
        MovieReveal(delayMillis = 560) {
            Text("${report.year}\nBenim Hikâyem", color = Ivory,
                fontFamily = FontFamily.Serif, fontSize = 34.sp,
                lineHeight = 43.sp, textAlign = TextAlign.Center)
        }
        Text("Bazı anlar biter. Hikâyen devam eder.",
            color = SoftWhite, fontSize = 15.sp,
            lineHeight = 23.sp, textAlign = TextAlign.Center)
        MovieGlassCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                YearNumber(report.journalCount, "ANI")
                YearNumber(report.uniquePeopleCount, "İNSAN")
                YearNumber(report.seasonCount, "SEZON")
            }
            report.firstJournal?.let { first ->
                HorizontalDivider(color = Ivory.copy(alpha = 0.18f))
                Text("İlk kayıt: ${first.movieDate()}", color = LightGreen, fontSize = 12.sp)
            }
        }
        Button(
            onClick = onReplay,
            colors = ButtonDefaults.buttonColors(containerColor = Champagne, contentColor = Forest),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("Filmi yeniden izle", fontWeight = FontWeight.Bold) }
        Text(
            "Yıllık özete dön",
            color = Ivory,
            modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
            fontSize = 13.sp
        )
        Text("BENİM DÜNYAM • SON SAHNE", color = LightGreen,
            fontSize = 10.sp, letterSpacing = 1.5.sp)
    }
}


@Composable
private fun FeaturedMemoryScene(
    journal: JournalEntry,
    coverPath: String?,
    progress: Float,
    onJournalClick: (String) -> Unit
) {
    val reducedMotion = LocalMovieReducedMotion.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SceneHeading(
            label = "✦  UNUTULMAZ BİR SAHNE",
            title = "Bazı anlar,\nhep bizimle kalır.",
            sub = "Bu anıyı sen seçtin. Hikâyendeki yerini sen belirledin."
        )
        if (coverPath != null) {
            Box(
                Modifier.fillMaxWidth().height(275.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                // Hikâye ilerledikçe fotoğrafa yumuşak yakınlaşma (Ken Burns).
                AsyncImage(
                    model = File(coverPath),
                    contentDescription = "${journal.title} anısının fotoğrafı",
                    modifier = Modifier.fillMaxSize().movieKenBurns(
                        progress = progress,
                        reducedMotion = reducedMotion,
                        direction = if (journal.id.hashCode() % 2 == 0) 1 else -1
                    ),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Forest.copy(alpha = 0.70f)))
                ))
                Text(
                    journal.movieDate(),
                    color = Ivory,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    fontSize = 12.sp
                )
            }
        }
        MovieGlassCard {
            Text(journal.movieDate(), color = Champagne,
                fontSize = 11.sp, letterSpacing = 1.3.sp)
            Text(
                journal.title.ifBlank { "Adını koyamadığım bir an" },
                color = Ivory, fontFamily = FontFamily.Serif,
                fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            if (journal.content.isNotBlank()) {
                Text(
                    journal.content.trim(), color = SoftWhite,
                    fontSize = 14.sp, lineHeight = 21.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onJournalClick(journal.id) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Champagne, contentColor = Forest
                )
            ) { Text("Bu anıya yeniden dön", fontWeight = FontWeight.Bold) }
        }
    }
}

private fun JournalEntry.movieDate(): String =
    SimpleDateFormat("d MMMM yyyy", Tr).format(Date(entryDate))


/**
 * 12 Ay 12 Hikâye: yalnızca o aya ait gerçek verilerden üretilen bağımsız sahne.
 * Fotoğraf olmayan / anı yazılmayan aylarda sahte kişisel içerik üretmez.
 */
@Composable
private fun MonthStoryScene(
    month: YearReviewMonthStory,
    year: Int,
    paths: Map<String, String>,
    sceneProgress: Float,
    onJournalClick: (String) -> Unit
) {
    val reducedMotion = LocalMovieReducedMotion.current
    val monthName = MonthFull.getOrElse(month.monthIndex) { "Ay" }
    val coverJournal = month.coverCandidates.firstOrNull { paths[it.id] != null }
    val featuredJournal = coverJournal ?: month.lastJournal
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MovieReveal(delayMillis = 80) {
            Text(
                "12 AY  /  ${String.format(Tr, "%02d", month.monthIndex + 1)}. BÖLÜM",
                fontSize = 10.sp, color = Champagne, letterSpacing = 2.1.sp,
                fontWeight = FontWeight.Bold
            )
        }
        MovieReveal(delayMillis = 180) {
            Text(
                monthName, color = Ivory, fontFamily = FontFamily.Serif,
                fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold
            )
        }
        Text(year.toString(), color = LightGreen, fontSize = 13.sp, letterSpacing = 2.sp)

        if (month.journalCount == 0) {
            MovieGlassCard {
                Text("Bu ayın sayfaları henüz boş.", color = Ivory,
                    fontFamily = FontFamily.Serif, fontSize = 22.sp)
                Text("${monthName} $year için kaydedilmiş bir anı bulunmuyor.",
                    color = SoftWhite, fontSize = 13.sp, lineHeight = 19.sp)
                Text("Yeni anılar eklendiğinde bu bölüm de hikâyene katılacak.",
                    color = LightGreen, fontSize = 12.sp)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MovieGlassCard(
                    modifier = Modifier.weight(1f)
                ) {
                    Text("${month.journalCount}", color = Champagne, fontSize = 35.sp,
                        fontFamily = FontFamily.Serif)
                    Text("BU AYIN ANISI", color = SoftWhite, fontSize = 9.sp,
                        letterSpacing = 1.sp)
                }
                MovieGlassCard(
                    modifier = Modifier.weight(1f)
                ) {
                    Text("${month.uniquePeopleCount}", color = Champagne, fontSize = 35.sp,
                        fontFamily = FontFamily.Serif)
                    Text("ANILARDAKİ KİŞİ", color = SoftWhite, fontSize = 9.sp,
                        letterSpacing = 1.sp)
                }
            }

            if (coverJournal != null) {
                val cover = paths[coverJournal.id]
                if (cover != null) {
                    Box(
                        Modifier.fillMaxWidth().height(218.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onJournalClick(coverJournal.id) }
                    ) {
                        AsyncImage(
                            model = File(cover),
                            contentDescription = "${monthName} ayının anı fotoğrafı",
                            modifier = Modifier.fillMaxSize().movieKenBurns(
                                progress = sceneProgress, reducedMotion = reducedMotion,
                                direction = if (month.monthIndex % 2 == 0) 1 else -1
                            ),
                            contentScale = ContentScale.Crop
                        )
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                            listOf(Color.Transparent, Forest.copy(alpha = 0.65f))
                        )))
                        Text("${monthName.uppercase(Tr)} • ${coverJournal.movieDate()}",
                            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                            color = Ivory, fontSize = 11.sp)
                    }
                }
            }
            featuredJournal?.let { journal ->
                MovieGlassCard(modifier = Modifier.clickable { onJournalClick(journal.id) }) {
                    Text("BU AYDAN BİR SAYFA", color = Champagne, fontSize = 10.sp,
                        letterSpacing = 1.4.sp)
                    Text(journal.title.ifBlank { "Adsız anı" },
                        color = Ivory, fontFamily = FontFamily.Serif,
                        fontSize = 21.sp, lineHeight = 26.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (journal.content.isNotBlank()) {
                        Text(journal.content.trim().replace('\n', ' '),
                            color = SoftWhite, fontSize = 12.sp, lineHeight = 18.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${journal.movieDate()}  •  ANIYI AÇ  ›", color = Champagne,
                        fontSize = 10.sp)
                }
            }
            if (month.topPeople.isNotEmpty()) {
                MovieGlassCard {
                    Text("BU AYIN KARAKTERLERİ", color = LightGreen,
                        fontSize = 10.sp, letterSpacing = 1.3.sp)
                    month.topPeople.forEach { person ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(person.name, color = Ivory, fontSize = 13.sp,
                                modifier = Modifier.weight(1f), maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text("${person.journalCount} anı", color = Champagne, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
