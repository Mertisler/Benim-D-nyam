package com.loc.benimdunyam.feature.people.detail.mood

import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loc.benimdunyam.feature.journal.journaldetail.components.journalMoodDisplay

private val MoodPine get() = themedColor(Color(0xFF416956))
private val MoodGold get() = themedColor(Color(0xFFD1AD73))

/** Tek anının altında kısa, tıklama gerektirmeyen ruh hali etiketi. */
@Composable
fun PersonMoodTimelineBadge(moodName: String, modifier: Modifier = Modifier) {
    if (moodName.isBlank()) return
    val display = journalMoodDisplay(moodName)
    Surface(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Bu anıyı yazarken seçilen ruh hali: ${display.label}"
        },
        shape = RoundedCornerShape(50),
        color = themedColor(Color(0xFFE8EFE6))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(display.emoji, fontSize = 15.sp)
            Text(display.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MoodPine)
        }
    }
}

/** Tekrarlı grafik yerine isteğe bağlı genişleyen, en sık 3 duyguyu öne çıkaran alan. */
@Composable
fun PersonMoodInsightsSummary(summary: PersonMoodInsights, modifier: Modifier = Modifier) {
    if (summary.taggedMemories == 0) return
    var showAll by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Anılarındaki duygu izleri",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(
                    "${summary.taggedMemories} / ${summary.totalMemories} anıda ruh hali seçilmiş",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (summary.moods.size > 3) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Daha az" else "Tümü")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                val maxCount = summary.moods.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                (if (showAll) summary.moods else summary.moods.take(3)).forEach { mood ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(mood.emoji, fontSize = 19.sp)
                            Text(mood.label, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                            Text("${mood.count} anı", color = MoodPine,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().height(5.dp)
                                .background(themedColor(Color(0xFFDCDAD3)), RoundedCornerShape(50))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(mood.count.toFloat() / maxCount)
                                    .height(5.dp)
                                    .background(MoodPine, RoundedCornerShape(50))
                            )
                        }
                    }
                }
                if (summary.untaggedMemories > 0) {
                    Text("${summary.untaggedMemories} anıda ruh hali seçilmemiş.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            "Bunlar, anıları yazarken seçtiğin duygular; kişinin hislerini veya ilişkinizin niteliğini ölçmez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
