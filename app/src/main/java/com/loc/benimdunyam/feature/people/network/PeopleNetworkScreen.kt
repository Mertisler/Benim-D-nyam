package com.loc.benimdunyam.feature.people.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.feature.people.network.components.NetworkPersonDetailSheet
import com.loc.benimdunyam.feature.people.network.components.NetworkSharedMemoriesDialog
import com.loc.benimdunyam.feature.people.network.components.PeopleNetworkCanvas
import com.loc.benimdunyam.feature.people.network.components.PeopleNetworkFilters
import com.loc.benimdunyam.feature.people.network.model.NetworkGraphEdge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleNetworkScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPersonDetail: (String) -> Unit,
    onNavigateToJournalDetail: (String) -> Unit,
    viewModel: PeopleNetworkViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedEdge by remember { mutableStateOf<NetworkGraphEdge?>(null) }
    val graph = state.graph
    val selectedNode = state.selectedPersonId?.let(graph::node)
    val activeSharedEdge = selectedEdge?.let { old ->
        graph.edges.firstOrNull {
            it.firstPersonId == old.firstPersonId && it.secondPersonId == old.secondPersonId
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hayatımın İnsan Haritası") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PeopleNetworkFilters(
                seasons = state.availableSeasons,
                seasonId = state.selectedSeasonId,
                onlyWithMemories = state.onlyWithMemories,
                searchQuery = searchQuery,
                onSeasonSelected = viewModel::selectSeason,
                onOnlyWithMemoriesChanged = viewModel::showOnlyWithMemories,
                onSearchQueryChanged = {
                    searchQuery = it
                    viewModel.setSearchQuery(it)
                }
            )

            val matches = remember(searchQuery, graph.nodes) {
                if (searchQuery.isBlank()) emptyList()
                else graph.nodes.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(8)
            }
            if (matches.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(matches, key = { it.id }) { person ->
                        TextButton(onClick = { viewModel.selectPerson(person.id) }) {
                            Text(person.name, maxLines = 1)
                        }
                    }
                }
            }

            when {
                state.isLoading -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.errorMessage != null -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(state.errorMessage ?: "Bir hata oluştu.", color = MaterialTheme.colorScheme.error)
                    }
                }
                graph.nodes.isEmpty() -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if (graph.totalPeople == 0) "Henüz kayıtlı bir karakter yok."
                            else "Bu filtreye uygun anısı olan karakter bulunamadı. Tüm karakterleri göstermeyi deneyebilirsin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    PeopleNetworkCanvas(
                        graph = graph,
                        selectedPersonId = state.selectedPersonId,
                        searchQuery = searchQuery,
                        onSelectPerson = viewModel::selectPerson,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }

            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text(
                    "${graph.nodes.size} karakter · ${graph.edges.size} ortak-anı bağlantısı",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (graph.hiddenByLimit > 0) {
                    Text(
                        "Performans için ${graph.hiddenByLimit} karakter haritada gösterilmiyor (en fazla 48).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Bağlantı kalınlığı ortak günlük sayısını, çizgiler arşivde birlikte görünmeyi ifade eder.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (selectedNode != null && selectedEdge == null) {
        NetworkPersonDetailSheet(
            node = selectedNode,
            graph = graph,
            onDismiss = viewModel::clearSelection,
            onOpenProfile = { id ->
                viewModel.clearSelection()
                onNavigateToPersonDetail(id)
            },
            onOpenSharedMemories = { edge ->
                viewModel.clearSelection()
                selectedEdge = edge
            }
        )
    }
    if (activeSharedEdge != null) {
        NetworkSharedMemoriesDialog(
            edge = activeSharedEdge,
            graph = graph,
            onDismiss = { selectedEdge = null },
            onOpenJournal = { journalId ->
                selectedEdge = null
                onNavigateToJournalDetail(journalId)
            }
        )
    }
}
