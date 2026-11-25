package com.loc.benimdunyam.feature.people.detail.relationship

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TR = Locale.forLanguageTag("tr-TR")
private fun formatDate(millis: Long): String = SimpleDateFormat("d MMMM yyyy", TR).format(Date(millis))
private fun formatMonth(millis: Long): String = SimpleDateFormat("MMMM yyyy", TR).format(Date(millis))

@Composable
private fun InsightCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun CardTitle(emoji: String, text: String) {
    Text("$emoji  $text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/** Hikâye kartı: tek paragraflık özet ve varsa bugünün yıl dönümü. */
@Composable
internal fun RelationshipStoryCard(personName: String, insights: PersonRelationshipInsights) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            insights.anniversaryYearToday?.let { year ->
                Text(
                    "🎉 Bugün, $personName ile ilk anınızın $year. yıl dönümü.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                insights.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** İlk ve son anı, birlikte geçen yıllar ve en uzun sessiz dönem. */
@Composable
internal fun RelationshipTimelineCard(insights: PersonRelationshipInsights) {
    val first = insights.firstMemoryAt ?: return
    val last = insights.lastMemoryAt ?: return
    InsightCard {
        CardTitle("🧭", "Zaman çizginiz")
        Text("İlk anınız: ${formatDate(first)}", style = MaterialTheme.typography.bodyMedium)
        if (insights.memoryCount > 1) {
            Text("Son anınız: ${formatDate(last)}" + lastAgo(insights.daysSinceLastMemory),
                style = MaterialTheme.typography.bodyMedium)
        }
        if (insights.yearsTogether >= 1) {
            Text("${insights.yearsTogether} yıldır anılarında.", style = MaterialTheme.typography.bodyMedium)
        }
        insights.longestQuietPeriod?.let { quiet ->
            HorizontalDivider()
            Text(
                "En uzun ara: ${formatMonth(quiet.fromMillis)} – ${formatMonth(quiet.toMillis)} " +
                        "arasında ${quiet.months} ay birlikte kayıtlı anın yok.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun lastAgo(days: Long?): String = when {
    days == null -> ""
    days == 0L -> " (bugün)"
    days < 30 -> " ($days gün önce)"
    days < 365 -> " (${days / 30} ay önce)"
    else -> " (${days / 365} yıl önce)"
}

/** Bu kişiyle en sık ruh hali, tüm anılarla karşılaştırmalı. */
@Composable
internal fun MoodComparisonLine(personName: String, comparison: MoodComparison) {
    InsightCard {
        CardTitle(comparison.mood.emoji, "Ruh hali karşılaştırması")
        // Kişi adına ve sayıya ek getirmeyen kalıp: Türkçe ünlü uyumu hatası olmaz.
        Text(
            "$personName ile anılarında en sık ruh hali: ${comparison.mood.label} " +
                    "(%${comparison.personPercent}).",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Tüm anılarında ${comparison.mood.label}: %${comparison.overallPercent}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Ne zaman ve ne kadar: hafta sonu, mevsim, yazılan kelime, en uzun anı. */
@Composable
internal fun RelationshipHabitsCard(insights: PersonRelationshipInsights, onOpenJournal: (String) -> Unit) {
    val lines = buildList {
        insights.weekendPercent?.let { percent ->
            add(if (percent >= 50) "Hafta sonu anıları: %$percent" else "Anılarınızın çoğu hafta içi.")
        }
        insights.dominantSeasonOfYear?.let { add("En çok $it aylarında birliktesiniz.") }
        if (insights.totalWords > 0) add("Bu anılar için toplam ${insights.totalWords} kelime yazmışsın.")
    }
    if (lines.isEmpty() && insights.longestMemory == null) return
    InsightCard {
        CardTitle("📅", "Birlikte zamanlarınız")
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        insights.longestMemory?.takeIf { insights.memoryCount > 1 }?.let { longest ->
            Text(
                "En çok şey yazdığın anı: \"${longest.title}\" (${longest.words} kelime) ›",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onOpenJournal(longest.journalId) }
            )
        }
    }
}

/** Kişinin etiketlendiği anılardaki fotoğraflar. */
@Composable
internal fun TogetherPhotosSection(photos: List<PersonTogetherPhoto>, onOpenJournal: (String) -> Unit) {
    if (photos.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "📸  Birlikte fotoğraflar (${photos.size}${if (photos.size >= 30) "+" else ""})",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(photos, key = { "${it.journalId}/${it.photoId}" }) { photo ->
                AsyncImage(
                    model = File(photo.path),
                    contentDescription = "Anı fotoğrafı, ${formatDate(photo.entryDate)}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenJournal(photo.journalId) }
                )
            }
        }
    }
}

/** Adı geçen ama etiketlenmemiş anılar: dokununca anı açılır, etiketlemeyi kullanıcı yapar. */
@Composable
internal fun UntaggedMentionsCard(
    personName: String,
    mentions: List<UntaggedMention>,
    onOpenJournal: (String) -> Unit
) {
    if (mentions.isEmpty()) return
    var expanded by remember(mentions.size) { mutableStateOf(false) }
    InsightCard {
        CardTitle("🏷", "Etiketlenmemiş olabilir")
        Text(
            "$personName adı ${mentions.size} anıda geçiyor ama etiketlenmemiş. Etiketlersen " +
                    "bu anılar da yukarıdaki içgörülere katılır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        (if (expanded) mentions else mentions.take(3)).forEach { mention ->
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenJournal(mention.journalId) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(mention.title.ifBlank { "Başlıksız anı" }, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(mention.excerpt, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                Text("Aç ›", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (mentions.size > 3) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Daha az" else "Tümünü gör (${mentions.size})")
            }
        }
    }
}
