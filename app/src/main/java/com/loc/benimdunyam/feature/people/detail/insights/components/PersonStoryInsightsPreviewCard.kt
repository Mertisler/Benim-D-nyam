package com.loc.benimdunyam.feature.people.detail.insights.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.feature.people.detail.insights.model.PersonStoryInsightsReport

@Composable
fun PersonStoryInsightsPreviewCard(
    personName: String,
    report: PersonStoryInsightsReport,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "✨ Hikâye İçgörüleri",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Keşfet ›",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Text(
                text = if (report.isEmpty) {
                    "${personName} ile anı biriktirdikçe burada yeni keşifler göreceksin."
                } else {
                    "${report.totalJournalCount} ortak anıdan elde edilen, cihazında hesaplanan özetler."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )

            if (!report.isEmpty) {
                val theme = report.themes.firstOrNull()
                Text(
                    text = when {
                        theme != null -> "${theme.emoji} En sık geçen tema: ${theme.title} (${theme.journalCount} anı)"
                        report.peakYear != null -> "📅 En çok anının bulunduğu yıl: ${report.peakYear.year}"
                        else -> "Anılarını yeniden keşfet"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
