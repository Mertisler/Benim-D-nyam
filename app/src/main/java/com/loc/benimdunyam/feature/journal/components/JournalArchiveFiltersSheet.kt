package com.loc.benimdunyam.feature.journal.components

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.feature.journal.JournalsUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val tr = Locale.forLanguageTag("tr-TR")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalArchiveFiltersSheet(
    state: JournalsUiState,
    onDismiss: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onSeasonChanged: (String?) -> Unit,
    onEpisodeChanged: (String?) -> Unit,
    onPersonChanged: (String?) -> Unit,
    onStartDateChanged: (Long?) -> Unit,
    onEndDateChanged: (Long?) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", tr) }
    val selectedSeasonName = state.seasons.firstOrNull { it.id == state.selectedSeasonId }?.title
    val selectedEpisodeName = state.episodes.firstOrNull { it.id == state.selectedEpisodeId }?.title
    val selectedPersonName = state.people.firstOrNull { it.id == state.selectedPersonId }?.name

    fun chooseDate(current: Long?, endOfDay: Boolean, onChosen: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = current ?: System.currentTimeMillis() }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (endOfDay) {
                        add(Calendar.DATE, 1)
                        add(Calendar.MILLISECOND, -1)
                    }
                }
                onChosen(picked.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Anılarını Keşfet", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Kelime, kişi, yaşam dönemi, bölüm ve tarih aralığını birlikte kullanabilirsin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Anıda ara") },
                placeholder = { Text("Başlık, metin veya kişi adı...") },
                singleLine = true
            )
            ArchiveChoiceMenu(
                label = "Yaşam dönemi / Sezon",
                chosen = selectedSeasonName ?: "Tüm sezonlar",
                options = listOf(null to "Tüm sezonlar") + state.seasons.map { it.id to it.title },
                onSelect = onSeasonChanged
            )
            ArchiveChoiceMenu(
                label = "Bölüm",
                chosen = selectedEpisodeName ?: "Tüm bölümler",
                enabled = state.selectedSeasonId != null,
                options = listOf(null to "Tüm bölümler") + state.episodes.map {
                    it.id to "Bölüm ${it.orderIndex}: ${it.title}"
                },
                onSelect = onEpisodeChanged
            )
            ArchiveChoiceMenu(
                label = "Anıda geçen kişi",
                chosen = selectedPersonName ?: "Tüm kişiler",
                options = listOf(null to "Tüm kişiler") + state.people.map { it.id to it.name },
                onSelect = onPersonChanged
            )

            HorizontalDivider()
            Text("Tarih aralığı", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { chooseDate(state.startDate, false, onStartDateChanged) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            state.startDate?.let { fmt.format(Date(it)) } ?: "Başlangıç",
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.startDate != null) {
                        TextButton(onClick = { onStartDateChanged(null) }) {
                            Text("Temizle")
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { chooseDate(state.endDate, true, onEndDateChanged) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            state.endDate?.let { fmt.format(Date(it)) } ?: "Bitiş",
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.endDate != null) {
                        TextButton(onClick = { onEndDateChanged(null) }) {
                            Text("Temizle")
                        }
                    }
                }
            }
            Text(
                "Bitiş günü tamamen dahildir. Tarihler çelişirse önceki sınır temizlenir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onClearAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Tümünü temizle", maxLines = 1)
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF496B5B)
                    )
                ) {
                    Text("${state.journals.size} anıyı göster", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ArchiveChoiceMenu(
    label: String,
    chosen: String,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.layout.Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(chosen, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Text("⌄")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                options.forEach { (id, title) ->
                    DropdownMenuItem(
                        text = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            expanded = false
                            onSelect(id)
                        }
                    )
                }
            }
        }
    }
}
