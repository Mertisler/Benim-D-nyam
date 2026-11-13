package com.loc.benimdunyam.feature.people.detail.analytics

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.feature.people.detail.analytics.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standalone screen; it can also be hosted in a full-screen Dialog until a
 * dedicated NavHost route is wired. SavedStateHandle requires `personId`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonAnalyticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: PersonAnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR")) }
    val trMonths = remember { listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara") }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("Hikâye Analizi") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.errorMessage != null -> {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::refreshPerson) { Text("Tekrar dene") }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item(key = "intro") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${state.personName} · Yaşam arşivi",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "Grafikler yalnızca kaydedilen ortak anılara dayanır; kişiler arasındaki gerçek yakınlığı ölçmez.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    item(key = "filters") {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                PersonAnalyticsFiltersPanel(
                                    years = state.availableYears,
                                    seasonOptions = state.availableSeasons,
                                    filters = state.filters,
                                    onSelectYear = viewModel::selectYear,
                                    onSelectSeason = viewModel::selectSeason,
                                    onStartDate = viewModel::setStartDate,
                                    onEndDate = viewModel::setEndDate,
                                    onReset = viewModel::resetFilters
                                )
                            }
                        }
                    }

                    if (state.report.totalMemories == 0) {
                        item(key = "empty") {
                            AnalyticsSectionCard("Bu aralıkta anı bulunamadı") {
                                Text(
                                    if (state.hasAnyMemories)
                                        "Filtreleri temizleyerek diğer anılara bakabilirsin."
                                    else
                                        "Bu kişiye bir günlükte yer verdiğinde analizler burada oluşacak.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (state.filters.isActive) {
                                    TextButton(onClick = viewModel::resetFilters) {
                                        Text("Filtreleri temizle")
                                    }
                                }
                            }
                        }
                    } else {
                        item(key = "summary") {
                            AnalyticsSummaryCard(state.report)
                        }
                        item(key = "dates") {
                            val first = state.report.firstMemoryDate?.let { dateFormat.format(Date(it)) }
                            val last = state.report.lastMemoryDate?.let { dateFormat.format(Date(it)) }
                            Text(
                                "Seçili aralıkta ilk kayıt: ${first ?: "–"}   ·   Son kayıt: ${last ?: "–"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item(key = "yearly") {
                            AnalyticsSectionCard(
                                "Yıllara göre ortak anılar",
                                "Her sütun, ilgili yılda kaydedilmiş farklı günlük sayısını gösterir."
                            ) {
                                AnalyticsBarChart(state.report.yearly.map { it.year.toString() to it.count })
                            }
                        }
                        item(key = "monthly") {
                            AnalyticsSectionCard(
                                "Aylara göre dağılım",
                                if (state.filters.year == null)
                                    "Seçili yıllardaki aynı aylar birleştirilerek hesaplanır."
                                else
                                    "${state.filters.year} yılı içerisindeki aylar."
                            ) {
                                AnalyticsBarChart(state.report.monthly.map { trMonths[it.month - 1] to it.count })
                            }
                        }
                        item(key = "seasons") {
                            SeasonComparisonChart(state.report.seasons)
                        }
                        item(key = "episodes") {
                            EpisodeHighlightsChart(state.report.topEpisodes)
                        }
                        item(key = "special") {
                            SpecialMemoryDistributionCard(state.report.specialCategories)
                        }
                        item(key = "connections") {
                            SharedCharactersAnalyticsCard(state.report.sharedCharacters)
                        }
                    }
                    item(key = "disclaimer") {
                        Text(
                            "Hesaplamalar cihazındaki anı arşivinden yapılır. Sezon/bölüm bağlantısı olmayan eski günlüklerde tarih aralığına göre eşleştirme uygulanabilir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
