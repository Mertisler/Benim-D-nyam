package com.loc.benimdunyam.feature.seasons.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StoryGold = Color(0xFFE2BA83)
private val StoryPaper = Color(0xFFF7EDDB)
private val StoryMuted = Color(0xFFB9ADA0)
private val StoryCard = Color(0xFF252324)
private val StoryBorder = Color(0xFF4E433B)

/**
 * Put this inside the existing LifeEpisodesScreen LazyColumn, under SeasonHeroHeader.
 * Uses the existing LifeEpisodesViewModel data: no new Room table or navigation route.
 */
@Composable
fun SeasonStorySummaryFeature(
    season: LifeSeason,
    episodes: List<LifeEpisode>,
    journals: List<JournalEntry>,
    onJournalClick: (String) -> Unit,
    onEpisodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    coverViewModel: SeasonStoryCoverViewModel = hiltViewModel()
) {
    val report = remember(season.id, episodes, journals) {
        SeasonStorySummaryCalculator.calculate(season.id, episodes, journals)
    }
    val candidateIds = remember(report.recentMemories) {
        report.recentMemories.map { it.id }
    }
    val coverPaths by coverViewModel.covers.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showEpisodeStories by rememberSaveable(season.id) { mutableStateOf(false) }

    LaunchedEffect(candidateIds) { coverViewModel.load(candidateIds) }
    // Covers are files rather than Room Flow; reload when coming back from an editor.
    DisposableEffect(lifecycleOwner, candidateIds) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coverViewModel.load(candidateIds, forceRefresh = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MenuBook, contentDescription = null, tint = StoryGold)
            Spacer(Modifier.width(9.dp))
            Column {
                Text("SEZONUN HİKÂYESİ", color = StoryGold,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp))
                Text("Bir dönemin izleri", color = StoryPaper,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif))
            }
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = StoryCard),
            border = BorderStroke(1.dp, StoryBorder)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StoryMetric("ANI", report.memoryCount.toString(), Modifier.weight(1f))
                    StoryMetric("İNSAN", report.uniquePeopleCount.toString(), Modifier.weight(1f))
                    StoryMetric("DOLU BÖLÜM", report.filledEpisodeCount.toString(), Modifier.weight(1f))
                }

                HorizontalDivider(color = StoryBorder)

                if (report.memoryCount == 0) {
                    Text("Bu sezonun hikâyesi henüz yazılmadı. İlk anını eklediğinde burada günlerin izleri görünecek.",
                        color = StoryMuted, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Bu sezonda öne çıkan insanlar", color = StoryPaper,
                        style = MaterialTheme.typography.titleMedium)
                    if (report.topPeople.isEmpty()) {
                        Text("Henüz bu sezonda etiketlenmiş bir kişi bulunmuyor.",
                            color = StoryMuted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            report.topPeople.forEachIndexed { index, person ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}.", color = StoryGold,
                                        style = MaterialTheme.typography.labelLarge)
                                    Spacer(Modifier.width(8.dp))
                                    Text(person.name, modifier = Modifier.weight(1f), color = StoryPaper,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${person.journalCount} anıda", color = StoryMuted,
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (report.timeline.isNotEmpty()) {
            Text("Hikâyenin dönüm sayfaları", color = StoryPaper,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif))
            Text("İlk, orta ve son kayıtlardan kronolojik bir seçki.", color = StoryMuted,
                style = MaterialTheme.typography.bodySmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                report.timeline.forEachIndexed { index, journal ->
                    val label = when {
                        report.timeline.size == 1 -> "BU SEZONUN ANI SAYFASI"
                        index == 0 -> "İLK SAYFA"
                        index == report.timeline.lastIndex -> "SON SAYFA"
                        else -> "HİKÂYENİN ORTASI"
                    }
                    StoryTimelineRow(label, journal, onJournalClick)
                }
            }
        }

        val photoJournals = remember(report.recentMemories, coverPaths) {
            report.recentMemories.filter { coverPaths.containsKey(it.id) }.take(5)
        }
        if (photoJournals.isNotEmpty()) {
            Text("Sezondan kareler", color = StoryPaper,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                photoJournals.forEach { journal ->
                    val path = coverPaths[journal.id]
                    if (path != null) {
                        StoryPhotoTile(journal, path, onJournalClick)
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = StoryCard),
            border = BorderStroke(1.dp, StoryBorder),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = StoryGold)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bölümlerin kısa hikâyesi", color = StoryPaper,
                            style = MaterialTheme.typography.titleMedium)
                        Text("${report.episodeReports.size} bölümün anı dökümü", color = StoryMuted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { showEpisodeStories = !showEpisodeStories }) {
                        Text(if (showEpisodeStories) "Gizle" else "Göster", color = StoryGold)
                    }
                }
                AnimatedVisibility(visible = showEpisodeStories) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (report.episodeReports.isEmpty()) {
                            Text("Bu sezona henüz bölüm eklenmemiş.", color = StoryMuted)
                        } else {
                            report.episodeReports.forEach { summary ->
                                StoryEpisodeRow(summary, onEpisodeClick, onJournalClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = StoryGold, style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text(label, color = StoryMuted, style = MaterialTheme.typography.labelSmall,
            maxLines = 1, letterSpacing = 0.7.sp)
    }
}

@Composable
private fun StoryTimelineRow(
    label: String,
    journal: JournalEntry,
    onJournalClick: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onJournalClick(journal.id) },
        shape = RoundedCornerShape(14.dp),
        color = StoryCard,
        border = BorderStroke(1.dp, StoryBorder)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).background(Color(0xFF403427), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = StoryGold)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$label · ${storyDate(journal.entryDate)}", color = StoryGold,
                    style = MaterialTheme.typography.labelSmall)
                Text(journal.title.ifBlank { "Başlıksız anı" }, color = StoryPaper,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (journal.content.isNotBlank()) {
                    Text(journal.content, color = StoryMuted, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("›", color = StoryGold, fontSize = 24.sp)
        }
    }
}

@Composable
private fun StoryPhotoTile(
    journal: JournalEntry,
    path: String,
    onJournalClick: (String) -> Unit
) {
    Box(
        modifier = Modifier.size(width = 150.dp, height = 136.dp)
            .clickable { onJournalClick(journal.id) }
            .background(StoryCard, RoundedCornerShape(15.dp))
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = "${journal.title} kapak fotoğrafı",
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC151212)))
            )
        )
        Text(
            journal.title.ifBlank { "Bir anı" },
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StoryEpisodeRow(
    summary: StoryEpisodeSummary,
    onEpisodeClick: (String) -> Unit,
    onJournalClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFF302D2B), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${summary.episode.orderIndex}. Bölüm", color = StoryGold,
                style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(9.dp))
            Text(summary.episode.title, color = StoryPaper, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (summary.memoryCount == 0) {
            Text("Bu bölümde henüz bir anı bulunmuyor.", color = StoryMuted,
                style = MaterialTheme.typography.bodySmall)
        } else {
            Text("${summary.memoryCount} anı · ${summary.peopleCount} kişi" +
                    (summary.topPerson?.let { " · En çok anılan: ${it.name}" } ?: ""),
                color = StoryMuted, style = MaterialTheme.typography.bodySmall)
            val first = summary.firstMemory
            val last = summary.lastMemory
            if (first != null && last != null) {
                Text("${storyDate(first.entryDate)} – ${storyDate(last.entryDate)}",
                    color = StoryMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onEpisodeClick(summary.episode.id) }) {
                Text("Bölüm anıları", color = StoryGold)
            }
            summary.lastMemory?.let { last ->
                TextButton(onClick = { onJournalClick(last.id) }) {
                    Text("Son anıyı aç", color = StoryGold)
                }
            }
        }
    }
}

private fun storyDate(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("tr-TR"))
        .format(Date(timestamp))
