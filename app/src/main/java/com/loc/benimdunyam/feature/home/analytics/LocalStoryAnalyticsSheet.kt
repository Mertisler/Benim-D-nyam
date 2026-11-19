package com.loc.benimdunyam.feature.home.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.core.designsystem.AppThemeController

/**
 * Faz 2I.2: Ana sayfayı uzatmayan, yalnızca kullanıcının isteğiyle açılan analiz sayfası.
 * Grafikler gerçek kayıt sayılarını sunar; duygu/kişilik/ilişki niteliği tahmini üretmez.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalStoryAnalyticsSheet(
    onDismiss: () -> Unit,
    viewModel: LocalStoryAnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val report = state.report
    val palette = AppThemeController.palette
    val ink = palette.ink
    val muted = palette.muted
    val accent = palette.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.paper
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 625.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null, tint = accent)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hikâyemin Rakamları", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = ink)
                    Text("Yalnızca cihazındaki kayıtlar · İnternetsiz",
                        style = MaterialTheme.typography.bodySmall, color = muted)
                }
                TextButton(onClick = onDismiss) { Text("Kapat") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                LocalAnalyticsPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = report.period == period,
                        onClick = { viewModel.selectPeriod(period) },
                        label = { Text(period.label) }
                    )
                }
            }

            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(28.dp),
                        contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.errorMessage != null -> {
                    Text(state.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                report.journalCount == 0 -> {
                    Text("Seçtiğin zaman aralığında anı bulunmuyor. Yeni bir anı eklediğinde burada görünecek.",
                        color = muted, style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        AnalyticsMetric("Anı", report.journalCount.toString(), Modifier.weight(1f))
                        AnalyticsMetric("Yazılan gün", report.distinctWritingDays.toString(), Modifier.weight(1f))
                        AnalyticsMetric("Anılan kişi", report.distinctPeopleCount.toString(), Modifier.weight(1f))
                    }

                    Text("Anı yoğunluğu", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = ink)
                    if (report.period == LocalAnalyticsPeriod.ALL) {
                        Text("Toplamlar tüm arşivden; aşağıdaki grafik son 12 ayı gösterir.",
                            color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                    AnalyticsSectionCard {
                        val max = report.monthBars.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                        report.monthBars.forEach { bar ->
                            AnalyticsBarRow(bar.label, bar.count, max)
                        }
                    }

                    HorizontalDivider(color = palette.border)
                    Text("Kaydedilen ruh halleri", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = ink)
                    Text("${report.moodTaggedCount} / ${report.journalCount} anıda ruh hali seçilmiş. " +
                            "Ruh hali seçilmeyen anılar değerlendirmeye dahil değildir.",
                        color = muted, style = MaterialTheme.typography.bodySmall)
                    AnalyticsSectionCard {
                        if (report.moods.isEmpty()) {
                            Text("Bu aralıkta kaydedilmiş ruh hali yok.", color = muted,
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            val max = report.moods.maxOf { it.count }
                            report.moods.forEach { row ->
                                AnalyticsBarRow("${row.mood.emoji} ${row.mood.label}", row.count, max)
                            }
                        }
                    }

                    HorizontalDivider(color = palette.border)
                    Text("Anılarımda geçen insanlar", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = ink)
                    AnalyticsSectionCard {
                        if (report.people.isEmpty()) {
                            Text("Bu aralıktaki anılarda henüz kişi etiketlenmemiş.", color = muted,
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            val max = report.people.maxOf { it.count }
                            report.people.take(5).forEach { person ->
                                AnalyticsBarRow(person.name, person.count, max)
                            }
                            if (report.people.size > 5) {
                                Text("İlk 5 kişi gösteriliyor · Toplam ${report.people.size} kişi",
                                    color = muted, style = MaterialTheme.typography.labelSmall)
                            }
                            Text("Bir kişi aynı anıda birden fazla anılsa bile bir kez sayılır.",
                                color = muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(color = palette.border)
                    Text("Sezonlarım", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = ink)
                    AnalyticsSectionCard {
                        val max = report.seasons.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                        report.seasons.take(5).forEach { season ->
                            AnalyticsBarRow(season.title, season.count, max)
                        }
                        if (report.seasons.size > 5) {
                            Text("İlk 5 sezon gösteriliyor.", color = muted,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        if (report.withoutSeasonCount > 0) {
                            Text("Sezon seçilmemiş: ${report.withoutSeasonCount} anı",
                                color = muted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (report.seasons.isEmpty() && report.withoutSeasonCount == 0) {
                            Text("Henüz sezon kaydı yok.", color = muted)
                        }
                    }
                    Text("Bu analizler yalnızca kayıtlı etiketleri ve tarihleri sayar; " +
                            "kişilik ya da psikolojik durum hakkında çıkarım yapmaz.",
                        style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
        }
    }
}

@Composable
private fun AnalyticsMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = AppThemeController.palette.paper,
        border = BorderStroke(1.dp, AppThemeController.palette.border),
        shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = AppThemeController.palette.ink)
            Text(title, style = MaterialTheme.typography.labelSmall,
                color = AppThemeController.palette.muted, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AnalyticsSectionCard(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = AppThemeController.palette.paper,
        border = BorderStroke(1.dp, AppThemeController.palette.border),
        shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun AnalyticsBarRow(label: String, value: Int, maxValue: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = AppThemeController.palette.ink,
                style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(value.toString(), style = MaterialTheme.typography.labelMedium,
                color = AppThemeController.palette.ink)
        }
        LinearProgressIndicator(
            progress = { (value.toFloat() / maxValue.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = AppThemeController.palette.primary,
            trackColor = AppThemeController.palette.border
        )
    }
}
