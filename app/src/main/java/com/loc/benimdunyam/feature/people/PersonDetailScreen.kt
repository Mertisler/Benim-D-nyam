package com.loc.benimdunyam.feature.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.feature.home.StatCard
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToJournalDetail: (String) -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Kişi Profili") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    uiState.person?.let { person ->
                        IconButton(onClick = { onNavigateToEdit(person.id) }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Düzenle")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.person != null) {
            val person = uiState.person!!

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Üst Profil Bilgileri
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = person.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = person.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        if (!person.note.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = person.note ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Temel Metrikler Kartı[cite: 4, 6, 7]
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            title = "Farklı Günlük",
                            count = person.uniqueJournalCount,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Toplam Anılma",
                            count = person.totalMentionCount,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // İlişkili Günlükler Başlığı
                item {
                    Text(
                        text = "İlişkili Anılar (${uiState.associatedJournals.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // İlişkili Günlüklerin Listesi
                if (uiState.associatedJournals.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Bu kişiyle ilişkilendirilmiş bir günlük bulunmuyor.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(uiState.associatedJournals, key = { it.id }) { journal ->
                        val formattedDate = dateFormat.format(Date(journal.entryDate))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToJournalDetail(journal.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = formattedDate,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = journal.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = "Kişiyi Sil") },
            text = { Text(text = "Bu kişiyi silmek istiyor musunuz? Günlükleriniz silinmez, yalnızca kişi bağlantıları temizlenir[cite: 6, 7].") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        uiState.person?.let { viewModel.deletePerson(onDeleted = onNavigateBack) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}