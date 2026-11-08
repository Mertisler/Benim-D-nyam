package com.loc.benimdunyam.feature.home.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog

/**
 * Ana sayfaya tek başına eklenebilir: yeni route, tablo, migration veya izin gerektirmez.
 * Kayıt seçilince mevcut günlük detay navigasyonunu kullanır.
 */
@Composable
fun LifeStoryInsightsFeature(
    onNavigateToJournalDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LifeStoryInsightsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAll by rememberSaveable { mutableStateOf(false) }
    val report = uiState.report
    val ink = Color(0xFF513D32)
    val paper = Color(0xFFFFFAF3)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = paper)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(
                        "Hikâyenden Keşifler",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = ink
                    )
                    Text(
                        "Yazdıklarından küçük, gerçek içgörüler",
                        style = MaterialTheme.typography.bodySmall,
                        color = ink.copy(alpha = 0.72f)
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Anıların inceleniyor...", color = ink)
                    }
                }
                uiState.errorMessage != null -> {
                    Text(uiState.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                report.totalJournals == 0 -> {
                    Text("İlk anını yazdığında burada hikâyenin izlerini keşfedeceksin.", color = ink)
                }
                else -> {
                    Text(
                        "${report.totalJournals} anı • Son 30 günde ${report.recentJournals} anı",
                        style = MaterialTheme.typography.labelMedium,
                        color = ink.copy(alpha = 0.8f)
                    )
                    report.insights.take(3).forEach { insight ->
                        InsightItem(
                            insight = insight,
                            ink = ink,
                            onJournalClick = onNavigateToJournalDetail
                        )
                    }
                    if (report.insights.size > 3) {
                        TextButton(onClick = { showAll = true }) {
                            Text("Tüm keşifler (${report.insights.size})")
                        }
                    }
                }
            }
        }
    }

    if (showAll && report.insights.isNotEmpty()) {
        Dialog(onDismissRequest = { showAll = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = paper,
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Hikâyenden Keşifler",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = ink
                        )
                        IconButton(onClick = { showAll = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat")
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 520.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(report.insights, key = { it.id }) { insight ->
                            InsightItem(
                                insight = insight,
                                ink = ink,
                                onJournalClick = { journalId ->
                                    showAll = false
                                    onNavigateToJournalDetail(journalId)
                                }
                            )
                            HorizontalDivider(color = ink.copy(alpha = 0.1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightItem(
    insight: LifeStoryInsight,
    ink: Color,
    onJournalClick: (String) -> Unit
) {
    val hasJournal = insight.journalId != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasJournal) {
                insight.journalId?.let(onJournalClick)
            }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(insight.emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                insight.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = ink
            )
            Text(
                insight.description,
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = 0.8f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (hasJournal) {
                Text(
                    "Anıyı aç ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF79513A)
                )
            }
        }
    }
}
