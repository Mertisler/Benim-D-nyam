package com.loc.benimdunyam.feature.people.detail.analytics.components

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.feature.people.detail.analytics.model.AnalyticsSeasonOption
import com.loc.benimdunyam.feature.people.detail.analytics.model.PersonAnalyticsFilters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun PersonAnalyticsFiltersPanel(
    years: List<Int>,
    seasonOptions: List<AnalyticsSeasonOption>,
    filters: PersonAnalyticsFilters,
    onSelectYear: (Int?) -> Unit,
    onSelectSeason: (String?) -> Unit,
    onStartDate: (Long?) -> Unit,
    onEndDate: (Long?) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR")) }
    var seasonMenuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Filtreler", style = MaterialTheme.typography.titleMedium)
            if (filters.isActive) {
                TextButton(onClick = onReset) { Text("Tümünü temizle") }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filters.year == null,
                onClick = { onSelectYear(null) },
                label = { Text("Tüm yıllar") }
            )
            years.forEach { year ->
                FilterChip(
                    selected = filters.year == year,
                    onClick = { onSelectYear(year) },
                    label = { Text(year.toString()) }
                )
            }
        }

        Box {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { seasonMenuOpen = true }
            ) {
                Text(
                    seasonOptions.firstOrNull { it.id == filters.seasonId }?.title
                        ?: "Tüm yaşam dönemleri",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(
                expanded = seasonMenuOpen,
                onDismissRequest = { seasonMenuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Tüm yaşam dönemleri") },
                    onClick = { onSelectSeason(null); seasonMenuOpen = false }
                )
                seasonOptions.forEach { season ->
                    DropdownMenuItem(
                        text = { Text(season.title) },
                        onClick = { onSelectSeason(season.id); seasonMenuOpen = false }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    openAnalyticsDatePicker(context, filters.startDateMillis, onStartDate)
                }
            ) {
                Text(
                    filters.startDateMillis?.let { "Baş: ${formatter.format(Date(it))}" } ?: "Başlangıç",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    openAnalyticsDatePicker(context, filters.endDateMillis, onEndDate)
                }
            ) {
                Text(
                    filters.endDateMillis?.let { "Son: ${formatter.format(Date(it))}" } ?: "Bitiş",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        if (filters.startDateMillis != null || filters.endDateMillis != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (filters.startDateMillis != null) {
                    TextButton(onClick = { onStartDate(null) }) { Text("Başlangıcı kaldır") }
                }
                if (filters.endDateMillis != null) {
                    TextButton(onClick = { onEndDate(null) }) { Text("Bitişi kaldır") }
                }
            }
        }
        Text(
            "Yıl, sezon ve tarih aralığı birlikte uygulanır. Yeni tarih diğer sınırla çakışırsa karşıt sınır kaldırılır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openAnalyticsDatePicker(
    context: Context,
    initialMillis: Long?,
    onSelect: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = initialMillis ?: System.currentTimeMillis()
    }
    DatePickerDialog(
        context,
        { _, y, m, d ->
            val chosen = Calendar.getInstance().apply {
                clear()
                set(y, m, d, 0, 0, 0)
            }.timeInMillis
            onSelect(chosen)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}
