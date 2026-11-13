package com.loc.benimdunyam.feature.people.detail.components.connections

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.feature.people.detail.model.PersonConnectionItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full-screen destination inside a Dialog: needs no new navigation route. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMemoriesScreen(
    personName: String,
    connection: PersonConnectionItem,
    onClose: () -> Unit,
    onOpenJournal: (String) -> Unit,
    onOpenOtherProfile: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            var query by rememberSaveable(connection.personId) { mutableStateOf("") }
            var selectedSeason by rememberSaveable(connection.personId) {
                mutableStateOf<String?>(null)
            }
            val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR"))
            val visibleJournals = connection.sharedJournals.filter { journal ->
                (selectedSeason == null ||
                        (connection.seasonByJournalId[journal.id] ?: "__unassigned__") == selectedSeason) &&
                        (query.isBlank() || journal.title.contains(query, ignoreCase = true) ||
                                journal.content.contains(query, ignoreCase = true))
            }

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        title = { Text("Ortak Anılar") },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                            }
                        },
                        actions = {
                            if (onOpenOtherProfile != null) {
                                TextButton(onClick = onOpenOtherProfile) {
                                    Text("Profili aç")
                                }
                            }
                        }
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "$personName ve ${connection.name}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${connection.sharedMemoryCount} ortak anı · En yeniden en eskiye",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        val first = connection.firstSharedDate
                        val last = connection.lastSharedDate
                        if (first != null && last != null) {
                            Text(
                                "İlk: ${dateFormatter.format(Date(first))}  •  Son: ${dateFormatter.format(Date(last))}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        connection.topSeason?.let { topSeason ->
                            Text(
                                "En çok ortak anı: ${topSeason.title} (${topSeason.memoryCount})",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text("Anılarda ara") },
                            singleLine = true
                        )
                    }
                    if (connection.seasonBreakdown.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedSeason == null,
                                    onClick = { selectedSeason = null },
                                    label = { Text("Tüm dönemler") }
                                )
                            }
                            items(connection.seasonBreakdown, key = { it.key }) { season ->
                                FilterChip(
                                    selected = selectedSeason == season.key,
                                    onClick = { selectedSeason = season.key },
                                    label = { Text("${season.title} · ${season.memoryCount}") }
                                )
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (visibleJournals.isEmpty()) {
                            item {
                                Text(
                                    if (query.isBlank() && selectedSeason == null) "Birlikte görünen bir anı yok." else "Filtreye uygun anı bulunamadı.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(visibleJournals, key = { it.id }) { journal ->
                            SharedJournalCard(
                                journal = journal,
                                dateText = dateFormatter.format(Date(journal.entryDate)),
                                onClick = { onOpenJournal(journal.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedJournalCard(journal: JournalEntry, dateText: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(dateText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Text(journal.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (journal.content.isNotBlank()) {
                Text(
                    text = journal.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
