package com.loc.benimdunyam.feature.people.network

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.feature.people.network.components.NetworkPersonDetailSheet
import com.loc.benimdunyam.feature.people.network.components.NetworkSharedMemoriesDialog
import com.loc.benimdunyam.feature.people.network.components.PeopleNetworkCanvas
import com.loc.benimdunyam.feature.people.network.model.NetworkGraphEdge

/**
 * İnsan Haritası — sadeleştirilmiş ekran.
 *
 * Harita (Canvas), kişi/ortak anı diyalogları, ViewModel ve veri katmanı korunur.
 * Bu sayfa yalnızca tekrar eden açıklamaları ve varsayılan açık filtre alanını sadeleştirir.
 */
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
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var selectedEdge by remember { mutableStateOf<NetworkGraphEdge?>(null) }

    // rememberSaveable geri yüklendiğinde ViewModel arama önceliğini de günceller.
    LaunchedEffect(searchQuery) { viewModel.setSearchQuery(searchQuery) }

    val graph = state.graph
    val selectedNode = state.selectedPersonId?.let(graph::node)
    val activeSharedEdge = selectedEdge?.let { old ->
        graph.edges.firstOrNull { edge ->
            (edge.firstPersonId == old.firstPersonId && edge.secondPersonId == old.secondPersonId) ||
                    (edge.firstPersonId == old.secondPersonId && edge.secondPersonId == old.firstPersonId)
        }
    }

    // Başka sezona geçildiyse önceki sezonun artık bulunmayan bağlantı diyaloğunu kapat.
    LaunchedEffect(selectedEdge, activeSharedEdge) {
        if (selectedEdge != null && activeSharedEdge == null) selectedEdge = null
    }

    // Ana haritada arama; üst üste iki arama kutusu yok.
    val searchMatches = remember(searchQuery, graph.nodes) {
        if (searchQuery.isBlank()) emptyList()
        else graph.nodes
            .filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
            .take(5)
    }
    val hasActiveFilter = state.selectedSeasonId != null || !state.onlyWithMemories

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("İnsan Haritası", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Hikâyendeki karşılaşmalar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Harita nasıl kullanılır?")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { value ->
                        searchQuery = value.take(80)
                        viewModel.setSearchQuery(searchQuery)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Haritada kişi ara…", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.setSearchQuery("")
                            }) { Icon(Icons.Default.Close, contentDescription = "Aramayı temizle") }
                        }
                    }
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (filtersExpanded || hasActiveFilter)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    IconButton(onClick = { filtersExpanded = !filtersExpanded }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = if (filtersExpanded) "Filtreleri kapat" else "Filtreleri aç",
                            tint = if (filtersExpanded || hasActiveFilter)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Güncel filtreler gizli değil: seçimin özeti her zaman küçük bir satırda kalır.
            if (!filtersExpanded && state.selectedSeasonId != null) {
                val selectedSeason = state.availableSeasons.firstOrNull { it.id == state.selectedSeasonId }
                if (selectedSeason != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Dönem: ${selectedSeason.title}",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { viewModel.selectSeason(null); selectedEdge = null }) {
                            Text("Temizle")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = filtersExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "Yaşam dönemi",
                            modifier = Modifier.padding(horizontal = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                        ) {
                            item(key = "all-seasons") {
                                FilterChip(
                                    selected = state.selectedSeasonId == null,
                                    onClick = {
                                        viewModel.selectSeason(null)
                                        selectedEdge = null
                                    },
                                    label = { Text("Tüm dönemler") }
                                )
                            }
                            items(state.availableSeasons, key = { it.id }) { season ->
                                FilterChip(
                                    selected = state.selectedSeasonId == season.id,
                                    onClick = {
                                        viewModel.selectSeason(season.id)
                                        selectedEdge = null
                                    },
                                    label = { Text(season.title, maxLines = 1) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = state.onlyWithMemories,
                                onClick = {
                                    viewModel.showOnlyWithMemories(!state.onlyWithMemories)
                                    selectedEdge = null
                                },
                                label = { Text("Yalnızca anısı olanlar") }
                            )
                            TextButton(
                                onClick = {
                                    viewModel.selectSeason(null)
                                    viewModel.showOnlyWithMemories(true)
                                    selectedEdge = null
                                    filtersExpanded = false
                                }
                            ) { Text("Sıfırla") }
                        }
                    }
                }
            }

            // Kısa kişi önerisi: arama sonucu ayrı bir liste ekranı açmaz.
            if (searchMatches.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(searchMatches, key = { it.id }) { person ->
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
                        Text(
                            state.errorMessage ?: "Harita yüklenemedi.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                graph.nodes.isEmpty() -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if (graph.totalPeople == 0) "Henüz kişi eklenmemiş."
                            else "Bu dönemde gösterilecek kişi bulunamadı. Filtreleri değiştirebilirsin.",
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

            Text(
                "${graph.nodes.size} kişi  ·  ${graph.edges.size} bağlantı" +
                        if (graph.hiddenByLimit > 0) "  ·  ${graph.hiddenByLimit} gizli" else "",
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("İnsan Haritası") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Her daire arşivindeki bir kişiyi temsil eder.")
                    Text("İki kişi aynı günlükte etiketlenmişse aralarında bağlantı çizilir. Çizginin kalınlığı ortak günlük sayısına göre değişir.")
                    Text("Bir kişiye dokunarak profilini veya ortak anıları açabilirsin. Haritayı sürükleyebilir ve iki parmakla yakınlaştırabilirsin.")
                    if (graph.hiddenByLimit > 0) {
                        Text("Performans için en fazla 48 kişi aynı anda gösterilir. Arama veya dönem filtrelerini kullanabilirsin.")
                    }
                    Text(
                        "Bu görselleştirme gerçek ilişkilerin yakınlığını ölçmez; yalnızca kayıtlı günlüklerde birlikte görünmeyi yansıtır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Anladım") }
            }
        )
    }
}
