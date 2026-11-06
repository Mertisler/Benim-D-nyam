package com.loc.benimdunyam.feature.people.detail.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.feature.people.detail.analytics.model.PersonAnalyticsReport

@Composable
fun AnalyticsSummaryCard(report: PersonAnalyticsReport) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryMetric("Ortak anı", report.totalMemories, Modifier.weight(1f))
            SummaryMetric("Aktif yıl", report.activeYearCount, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryMetric("Yaşam dönemi", report.seasonCount, Modifier.weight(1f))
            SummaryMetric("Bölüm", report.episodeCount, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryMetric("Unutulmaz anı", report.unforgettableCount, Modifier.weight(1f))
            SummaryMetric("Ortak karakter", report.sharedCharacterCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: Int, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
