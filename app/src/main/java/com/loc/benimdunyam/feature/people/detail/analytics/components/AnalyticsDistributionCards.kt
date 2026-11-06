package com.loc.benimdunyam.feature.people.detail.analytics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.feature.people.detail.analytics.model.*

@Composable
fun SeasonComparisonChart(items: List<SeasonMemoryStat>) {
    AnalyticsSectionCard(
        title = "Sezon karşılaştırması",
        subtitle = "Seçili aralıkta en çok ortak anı bulunan yaşam dönemleri"
    ) {
        if (items.isEmpty()) {
            Text("Henüz sezon verisi bulunmuyor.")
        } else {
            val max = items.maxOf { it.count }.coerceAtLeast(1)
            items.forEach { item ->
                HorizontalAnalyticsBar(item.title, item.count, item.count.toFloat() / max)
            }
        }
    }
}

@Composable
fun EpisodeHighlightsChart(items: List<EpisodeMemoryStat>) {
    AnalyticsSectionCard(
        title = "Öne çıkan bölümler",
        subtitle = "Ortak anı sayısına göre ilk beş bölüm"
    ) {
        if (items.isEmpty()) Text("Henüz bölüm bağlantısı yok.")
        else {
            val max = items.maxOf { it.count }.coerceAtLeast(1)
            items.forEach { item ->
                HorizontalAnalyticsBar(item.title, item.count, item.count.toFloat() / max)
            }
        }
    }
}

@Composable
fun SpecialMemoryDistributionCard(items: List<SpecialCategoryStat>) {
    AnalyticsSectionCard(
        title = "Unutulmaz anı kategorileri",
        subtitle = "Sadece seçilen filtrelerdeki kişiye özel işaretlemeler"
    ) {
        if (items.isEmpty()) Text("Bu aralıkta işaretlenmiş unutulmaz anı yok.")
        else {
            val max = items.maxOf { it.count }.coerceAtLeast(1)
            items.forEach { item ->
                HorizontalAnalyticsBar("${item.emoji} ${item.title}", item.count, item.count.toFloat() / max)
            }
        }
    }
}

@Composable
fun SharedCharactersAnalyticsCard(items: List<SharedCharacterStat>) {
    AnalyticsSectionCard(
        title = "Ortak karakterler",
        subtitle = "Seçilen aralıkta bu kişiyle aynı günlüklerde bulunan kişiler"
    ) {
        if (items.isEmpty()) Text("Bu aralıkta ortak karakter bulunmuyor.")
        else {
            val max = items.maxOf { it.count }.coerceAtLeast(1)
            items.forEach { item ->
                HorizontalAnalyticsBar(item.name, item.count, item.count.toFloat() / max)
            }
        }
    }
}

@Composable
private fun HorizontalAnalyticsBar(title: String, count: Int, ratio: Float) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            Text("$count anı", style = MaterialTheme.typography.labelMedium)
        }
        Box(
            Modifier.fillMaxWidth().height(9.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(9.dp))
        ) {
            Box(
                Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(9.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(9.dp))
            )
        }
    }
}
