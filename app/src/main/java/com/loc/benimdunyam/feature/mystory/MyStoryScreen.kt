package com.loc.benimdunyam.feature.mystory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.feature.home.analytics.LocalAnalyticsPeriod
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TR = Locale.forLanguageTag("tr-TR")
private fun number(value: Int): String = NumberFormat.getIntegerInstance(TR).format(value)

/**
 * "Hikâyem" — anılarının genel görünümü.
 *
 * Üstte dönem seçici ve özet; altında kartlar. Her kişi ve anı dokunulunca açılır.
 * Tüm hesaplar cihazda yapılır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyStoryScreen(
    onNavigateBack: () -> Unit,
    onOpenJournal: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenYearReview: () -> Unit,
    onOpenSeasons: () -> Unit,
    viewModel: MyStoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val report = state.report

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("Hikâyem") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            report.isEmpty -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("🌱\nHikâyen burada filizlenecek.\nİlk anını yazdığında genel görünümün oluşmaya başlar.",
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "period") { PeriodSelector(state.period, viewModel::selectPeriod) }
                item(key = "summary") { SummaryCard(report) }
                if (report.writingCalendar.activeDays > 0 || report.streak.longest > 0) {
                    item(key = "rhythm") { WritingRhythmCard(report) }
                }

                if (report.base.journalCount > 0) {
                    item(key = "months") { MonthsCard(report) }
                }
                if (report.base.moods.isNotEmpty()) {
                    item(key = "moods") { MoodsCard(report) }
                }
                if (report.moodTrend.isNotEmpty()) {
                    item(key = "mood-trend") { MoodTrendCard(report) }
                }
                if (report.habits.hasEnoughData) {
                    item(key = "habits") { WritingHabitsCard(report) }
                }
                if (report.base.people.isNotEmpty() || report.newPeople.isNotEmpty()) {
                    item(key = "people") { PeopleCard(report, onOpenPerson) }
                }
                if (report.quietPeople.isNotEmpty()) {
                    item(key = "quiet") { QuietPeopleCard(report, onOpenPerson) }
                }
                if (report.base.seasons.isNotEmpty()) {
                    item(key = "seasons") { SeasonsCard(report, onOpenSeasons) }
                }
                if (report.years.isNotEmpty()) {
                    item(key = "year-review") { YearReviewEntry(report.years.first(), onOpenYearReview) }
                }
                if (report.milestones.isNotEmpty()) {
                    item(key = "milestones") { MilestonesCard(report, onOpenJournal) }
                }
                if (report.withoutMood > 0 || report.withoutSeason > 0) {
                    item(key = "complete") { CompleteArchiveCard(report) }
                }
                item(key = "note") {
                    Text(
                        "Tüm hesaplar yalnızca bu cihazda, kayıtlı anılarından yapılır. Kişileri veya duyguları değerlendirmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------

@Composable
internal fun StoryCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun PeriodSelector(selected: LocalAnalyticsPeriod, onSelect: (LocalAnalyticsPeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            LocalAnalyticsPeriod.LAST_30_DAYS to "Son 30 gün",
            LocalAnalyticsPeriod.LAST_12_MONTHS to "Son 12 ay",
            LocalAnalyticsPeriod.ALL to "Tüm zamanlar"
        ).forEach { (period, label) ->
            FilterChip(selected = period == selected, onClick = { onSelect(period) }, label = { Text(label) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(report: MyStoryReport) {
    val base = report.base
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (base.journalCount == 0) {
                Text("Bu dönemde kayıtlı anın yok. Başka bir dönem seçebilirsin.",
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                return@Column
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(number(base.journalCount), "anı")
                Stat(number(base.distinctWritingDays), "farklı gün")
                Stat(number(base.distinctPeopleCount), "kişi")
                Stat(number(base.seasons.size), "dönem")
                Stat(number(report.photoCount), "fotoğraf")
                Stat(number(report.totalWords), "kelime")
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthsCard(report: MyStoryReport) {
    val bars = report.base.monthBars
    if (bars.isEmpty()) return
    val max = (bars.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    StoryCard("📈 Aylara göre anıların") {
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEach { bar ->
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (bar.count > 0) {
                        Text("${bar.count}", style = MaterialTheme.typography.labelSmall)
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .height((84f * bar.count / max).dp.coerceAtLeast(if (bar.count > 0) 4.dp else 1.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (bar.count > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                    Text(bar.label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MoodsCard(report: MyStoryReport) {
    val moods = report.base.moods
    val total = moods.sumOf { it.count }.coerceAtLeast(1)
    StoryCard("🌈 Ruh hallerin", "${report.base.moodTaggedCount} anıda ruh hali seçilmiş") {
        moods.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${item.mood.emoji}  ${item.mood.label}", modifier = Modifier.width(130.dp),
                    style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(item.count.toFloat() / total).fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary))
                }
                Text("  %${item.count * 100 / total}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PeopleCard(report: MyStoryReport, onOpenPerson: (String) -> Unit) {
    StoryCard("👥 Hikâyendeki insanlar") {
        report.base.people.take(5).forEach { person ->
            PersonRow(person.name, "${person.count} anı") { onOpenPerson(person.id) }
        }
        if (report.newPeople.isNotEmpty()) {
            HorizontalDivider()
            Text("Bu dönemde hikâyene katılanlar", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            report.newPeople.forEach { person ->
                PersonRow(person.name, "ilk anı: ${monthYear(person.atMillis)}") { onOpenPerson(person.id) }
            }
        }
    }
}

@Composable
private fun QuietPeopleCard(report: MyStoryReport, onOpenPerson: (String) -> Unit) {
    StoryCard(
        "🕰 Uzun zamandır anılarında olmayanlar",
        "Bir zamanlar hikâyende yer alan ama son altı aydır hiçbir anında olmayan kişiler. " +
                "Belki birlikte bir anınızı yeniden okumak ya da onlara bir selam göndermek istersin."
    ) {
        report.quietPeople.forEach { person ->
            PersonRow(
                name = person.name,
                detail = "${person.memoryCount} anı · en son ${monthYear(person.lastMemoryAt)}"
            ) { onOpenPerson(person.id) }
        }
    }
}

@Composable
private fun PersonRow(name: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("  ›", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SeasonsCard(report: MyStoryReport, onOpenSeasons: () -> Unit) {
    StoryCard("🎬 Yaşam dönemlerin") {
        report.base.seasons.take(5).forEach { season ->
            Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSeasons).padding(vertical = 4.dp)) {
                Text(season.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${season.count} anı", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun YearReviewEntry(latestYear: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎞", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$latestYear yılının filmi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("Yıl özeti, poster ve video. Diğer yılları da seçebilirsin.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun MilestonesCard(report: MyStoryReport, onOpenJournal: (String) -> Unit) {
    StoryCard("🏁 Kilometre taşların") {
        report.milestones.forEach { milestone ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenJournal(milestone.journalId) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(milestone.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(milestone.title, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(milestone.detail, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                Text("›", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CompleteArchiveCard(report: MyStoryReport) {
    StoryCard("🧩 Arşivini tamamla", "Bu bilgileri eklersen hikâyen daha doğru görünür.") {
        if (report.withoutMood > 0) {
            Text("${report.withoutMood} anında ruh hali seçilmemiş.", style = MaterialTheme.typography.bodyMedium)
        }
        if (report.withoutSeason > 0) {
            Text("${report.withoutSeason} anı bir yaşam dönemine bağlı değil.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun monthYear(millis: Long): String = SimpleDateFormat("MMMM yyyy", TR).format(Date(millis))
