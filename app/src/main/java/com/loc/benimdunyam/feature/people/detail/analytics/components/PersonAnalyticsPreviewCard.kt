package com.loc.benimdunyam.feature.people.detail.analytics.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.feature.people.detail.analytics.calculator.PersonAnalyticsCalculator
import com.loc.benimdunyam.feature.people.detail.model.PersonAppearance

/** Small optional entry point placed in the existing PersonDetailScreen. */
@Composable
fun PersonAnalyticsPreviewCard(
    appearances: List<PersonAppearance>,
    onOpenAnalytics: () -> Unit
) {
    val yearly = remember(appearances) {
        PersonAnalyticsCalculator.calculate(
            appearances = appearances,
            specialMemories = emptyList(),
            connections = emptyList()
        ).yearly.takeLast(5)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAnalytics),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "📊 Hikâye Analizi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Yıllara göre ortak anıların",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "Detaylı analizi aç",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (yearly.isNotEmpty()) {
                AnalyticsBarChart(yearly.map { it.year.toString() to it.count })
            } else {
                Text("Henüz ortak anı bulunmuyor.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
