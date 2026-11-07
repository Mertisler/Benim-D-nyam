package com.loc.benimdunyam.feature.people

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.data.model.DeviceContactItem
import com.loc.benimdunyam.domain.model.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    onNavigateToCreatePerson: () -> Unit,
    onNavigateToPersonDetail: (String) -> Unit,
    onNavigateToPeopleNetwork: () -> Unit,
    viewModel: PeopleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showSelectDialog by remember { mutableStateOf(false) }
    var deviceContacts by remember { mutableStateOf<List<DeviceContactItem>>(emptyList()) }

    LaunchedEffect(uiState.importError) {
        uiState.importError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearImportError()
        }
    }

    fun loadContacts() {
        viewModel.loadDeviceContacts(context) { result ->
            deviceContacts = result
            showSelectDialog = result.isNotEmpty()
            if (result.isEmpty() && viewModel.uiState.value.importError == null) {
                Toast.makeText(
                    context,
                    "Rehberde aktarılacak kişi bulunamadı.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadContacts() else Toast.makeText(
            context,
            "Rehberden kisi eklemek icin izin gereklidir.",
            Toast.LENGTH_SHORT
        ).show()
    }

    val busy = uiState.isImporting || uiState.isReadingContacts || uiState.isRestoring

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("İnsanlarım") },
                actions = {
                    IconButton(
                        onClick = onNavigateToPeopleNetwork,
                        enabled = !busy
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "Hayatımın İnsan Haritası"
                        )
                    }

                    if (uiState.isImporting || uiState.isReadingContacts) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            enabled = !busy,
                            onClick = {
                                if (ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.READ_CONTACTS
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    loadContacts()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            }
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Rehberden Kişi Ekle")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!busy) FloatingActionButton(
                onClick = onNavigateToCreatePerson,
                modifier = Modifier,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Kişi Ekle")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("Kişilerde ara...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            if (uiState.isRestoring) {
                Text(
                    "Yedek geri yüklenirken kişi ekleme geçici olarak kapalı.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (uiState.people.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Henüz kaydedilmiş kişi yok.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.people, key = { it.id }) { person ->
                        PersonItemCard(person = person) {
                            onNavigateToPersonDetail(person.id)
                        }
                    }
                }
            }
        }
    }

    if (showSelectDialog && !busy) {
        ImportContactsSelectionDialog(
            contacts = deviceContacts,
            onDismiss = { showSelectDialog = false },
            onConfirm = { selectedNames ->
                showSelectDialog = false
                viewModel.importMultipleContacts(selectedNames) { added, duplicates ->
                    Toast.makeText(
                        context,
                        "$added kişi eklendi. ($duplicates kişi zaten kayıtlıydı)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun ImportContactsSelectionDialog(
    contacts: List<DeviceContactItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selectedIds by remember(contacts) { mutableStateOf(emptySet<String>()) }
    val allSelected = contacts.isNotEmpty() && selectedIds.size == contacts.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rehberden Kişi Seç") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedIds = if (allSelected) emptySet() else contacts.map { it.id }.toSet()
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            selectedIds = if (checked) contacts.map { it.id }.toSet() else emptySet()
                        }
                    )
                    Text(if (allSelected) "Tümünü Kaldır" else "Tümünü Seç")
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(contacts, key = { it.id }) { contact ->
                        val selected = contact.id in selectedIds
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedIds = if (selected) selectedIds - contact.id
                                else selectedIds + contact.id
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + contact.id
                                    else selectedIds - contact.id
                                }
                            )
                            Text(contact.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedIds.isNotEmpty(),
                onClick = {
                    onConfirm(contacts.filter { it.id in selectedIds }.map { it.name })
                }
            ) { Text("Seçilenleri Ekle") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

@Composable
fun PersonItemCard(
    person: Person,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        person.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    person.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${person.uniqueJournalCount} günlükte anıldı",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
