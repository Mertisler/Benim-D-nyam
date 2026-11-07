package com.loc.benimdunyam.feature.people.detail.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.feature.people.detail.insights.components.PersonStoryInsightFactCard
import com.loc.benimdunyam.feature.people.detail.insights.components.PersonStoryInsightSection
import com.loc.benimdunyam.feature.people.detail.insights.components.PersonStoryInsightThemeCard
import com.loc.benimdunyam.feature.people.detail.insights.model.PersonStoryInsightsReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stateless report screen. Only "next old memory" position is local UI state. */
@Composable
fun PersonStoryInsightsScreen(
    personName: String,
    report: PersonStoryInsightsReport,
    onNavigateBack: () -> Unit,
    onOpenJournal: (String) -> Unit
) {
    var revisitIndex by rememberSaveable { mutableIntStateOf(0) }
    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("tr-TR")) }
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("tr-TR")) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                }
                Column {
                    Text("Hikâye İçgörüleri", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text(personName, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { innerPadding ->
        if (report.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Henüz bu kişiyle ilişkilendirilmiş bir anı bulunmuyor. " +
                            "Günlüklerine bu kişiyi eklediğinde burada içgörüler oluşacak.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item(key = "intro") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Hikâyene farklı bir açıdan bak ✨",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Bu özetler yalnızca cihazındaki kayıtlı anıların metinlerinden " +
                                    "ve tarihlerinden otomatik hesaplanır. Yapay zekâ veya duygu analizi değildir.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                item(key = "summary") {
                    PersonStoryInsightFactCard(
                        emoji = "📚",
                        label = "ARŞİV ÖZETİ",
                        value = "${report.totalJournalCount} ortak anı · ${report.activeYearCount} farklı yıl",
                        note = "${report.specialJournalCount} anı, bu kişi için unutulmaz olarak işaretlenmiş."
                    )
                }
                item(key = "themes-title") {
                    PersonStoryInsightSection(
                        title = "Öne çıkan temalar",
                        subtitle = "Yalnızca günlük başlığı veya metninde geçen anahtar sözcüklerden hesaplanır."
                    ) {
                        if (report.themes.isEmpty()) {
                            Text(
                                "Bu anılarda tanımlı temalardan birine ilişkin sözcük bulunmadı. " +
                                        "Bu, anıların daha az değerli olduğu anlamına gelmez.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (theme in report.themes) {
                                    PersonStoryInsightThemeCard(
                                        theme = theme,
                                        onOpenExample = onOpenJournal
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "dates") {
                    PersonStoryInsightSection(
                        title = "Hikâyenin yoğun dönemleri",
                        subtitle = "Birlikte en çok günlük kaydettiğin zamanlar. " +
                                "Gerçek hayatta geçirilen zamanı ölçmez."
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            report.peakYear?.let { peak ->
                                PersonStoryInsightFactCard(
                                    emoji = "🗓️",
                                    label = "EN ÇOK ANININ TARİHLENDİĞİ YIL",
                                    value = peak.year.toString(),
                                    note = "${peak.journalCount} ortak anı"
                                )
                            }
                            report.peakMonth?.let { peak ->
                                val calendar = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.YEAR, peak.year)
                                    set(java.util.Calendar.MONTH, peak.month - 1)
                                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                                }
                                PersonStoryInsightFactCard(
                                    emoji = "📅",
                                    label = "EN YOĞUN AY",
                                    value = monthFormat.format(calendar.time),
                                    note = "${peak.journalCount} ortak anı"
                                )
                            }
                            report.peakSeason?.let { peak ->
                                PersonStoryInsightFactCard(
                                    emoji = "🎬",
                                    label = "EN ÇOK ANININ BAĞLI OLDUĞU SEZON",
                                    value = peak.seasonTitle,
                                    note = "${peak.journalCount} ortak anı · " +
                                            "Mevcut sezon eşleştirme kuralları kullanılır."
                                )
                            }
                        }
                    }
                }
                item(key = "revisit") {
                    PersonStoryInsightSection(
                        title = "Geçmişten bir sayfa",
                        subtitle = "En az 90 gün önce tarihlenmiş anılarını yeniden keşfet."
                    ) {
                        if (report.revisitMemories.isEmpty()) {
                            Text(
                                "Henüz 90 günden eski bir ortak anı bulunmuyor.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val chosen = report.revisitMemories[
                                revisitIndex % report.revisitMemories.size
                            ]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("📖 ${dateFormat.format(Date(chosen.entryDate))}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                    Text(chosen.title, style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold)
                                    chosen.seasonTitle?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                revisitIndex = (revisitIndex + 1) % report.revisitMemories.size
                                            },
                                            enabled = report.revisitMemories.size > 1
                                        ) { Text("Başka anı") }
                                        Spacer(modifier = Modifier.weight(1f))
                                        OutlinedButton(onClick = { onOpenJournal(chosen.journalId) }) {
                                            Text("Anıyı aç")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "privacy") {
                    Text(
                        "Gizlilik: Bu ekran internete anı göndermiyor. " +
                                "Temalar anahtar sözcük eşleşmesiyle belirlenir; ilişki kalitesini, " +
                                "kişilik özelliklerini veya duyguları değerlendirmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
