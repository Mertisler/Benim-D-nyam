package com.loc.benimdunyam.feature.people.network.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.loc.benimdunyam.feature.people.network.model.NetworkGraphEdge
import com.loc.benimdunyam.feature.people.network.model.PeopleNetworkGraph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSharedMemoriesDialog(
    edge: NetworkGraphEdge,
    graph: PeopleNetworkGraph,
    onDismiss: () -> Unit,
    onOpenJournal: (String) -> Unit
) {
    val personA = graph.node(edge.firstPersonId)?.name ?: "Karakter"
    val personB = graph.node(edge.secondPersonId)?.name ?: "Karakter"
    val journals = edge.sharedJournalIds.mapNotNull(graph.journalsById::get)
        .sortedWith(compareByDescending<com.loc.benimdunyam.domain.model.JournalEntry> { it.entryDate }
            .thenByDescending { it.createdAt })
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR"))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = {
                TopAppBar(
                    title = { Text("Ortak Anılar") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                        }
                    }
                )
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        "$personA ve $personB · ${journals.size} ortak anı",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (journals.isEmpty()) {
                            item { Text("Bu filtrede ortak anı bulunmuyor.") }
                        }
                        items(journals, key = { it.id }) { journal ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onOpenJournal(journal.id)
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        formatter.format(Date(journal.entryDate)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(journal.title, fontWeight = FontWeight.Bold)
                                    if (journal.content.isNotBlank()) {
                                        Text(journal.content, maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
