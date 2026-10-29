package com.loc.benimdunyam.feature.people

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.loc.benimdunyam.data.model.DeviceContactItem
import com.loc.benimdunyam.domain.model.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    onNavigateToCreatePerson: () -> Unit,
    onNavigateToPersonDetail: (String) -> Unit,
    viewModel: PeopleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showSelectDialog by remember { mutableStateOf(false) }
    var deviceContactsList by remember { mutableStateOf<List<DeviceContactItem>>(emptyList()) }

    // Çalışma Zamanı İzin İsteme Launcher'ı (Rehber Okumak İçin)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            deviceContactsList = viewModel.fetchDeviceContacts(context)
            showSelectDialog = true
        } else {
            Toast.makeText(context, "Rehberden kişi eklemek için izin gereklidir.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "İnsanlarım") },
                actions = {
                    // Telefon Rehberinden Toplu Kişi Ekle Butonu
                    IconButton(
                        onClick = {
                            val permissionCheck = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_CONTACTS
                            )
                            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                deviceContactsList = viewModel.fetchDeviceContacts(context)
                                showSelectDialog = true
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Rehberden Kişi Ekle",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreatePerson,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Kişi Ekle")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Arama Çubuğu
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("Kişilerde ara...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (uiState.people.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Henüz kaydedilmiş kişi yok.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.people, key = { it.id }) { person ->
                        PersonItemCard(
                            person = person,
                            onClick = { onNavigateToPersonDetail(person.id) }
                        )
                    }
                }
            }
        }
    }

    // Çoklu Kişi Seçim Diyaloğu
    if (showSelectDialog) {
        SelectContactsDialog(
            contacts = deviceContactsList,
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

//@Composable
//fun SelectContactsDialog(
//    contacts: List<DeviceContactItem>,
//    onDismiss: () -> Unit,
//    onConfirm: (List<String>) -> Unit
//) {
//    var contactListState by remember { mutableStateOf(contacts) }
//
//    // Tüm kişilerin seçili olup olmadığını kontrol et
//    val allSelected = contactListState.isNotEmpty() && contactListState.all { it.isSelected }
//
//    AlertDialog(
//        onDismissRequest = onDismiss,
//        title = { Text(text = "Rehberden Kişi Seç") },
//        text = {
//            Column(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(380.dp)
//            ) {
//                Text(
//                    text = "İçe aktarmak istediğiniz kişileri işaretleyin:",
//                    style = MaterialTheme.typography.bodyMedium,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
//                Spacer(modifier = Modifier.height(4.dp))
//
//                // Tümünü Seç / Kaldır Satırı
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable {
//                            val newState = !allSelected
//                            contactListState = contactListState.map { it.copy(isSelected = newState) }
//                        }
//                        .padding(vertical = 4.dp, horizontal = 4.dp),
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.SpaceBetween
//                ) {
//                    Text(
//                        text = if (allSelected) "Tümünü Kaldır" else "Tümünü Seç",
//                        style = MaterialTheme.typography.bodyLarge,
//                        color = MaterialTheme.colorScheme.primary
//                    )
//                    Checkbox(
//                        checked = allSelected,
//                        onCheckedChange = { checked ->
//                            contactListState = contactListState.map { it.copy(isSelected = checked) }
//                        }
//                    )
//                }
//
//                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
//
//                LazyColumn(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .weight(1f),
//                    verticalArrangement = Arrangement.spacedBy(4.dp)
//                ) {
//                    items(contactListState, key = { it.id }) { contact ->
//                        Row(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .clickable {
//                                    contactListState = contactListState.map {
//                                        if (it.id == contact.id) it.copy(isSelected = !it.isSelected) else it
//                                    }
//                                }
//                                .padding(vertical = 6.dp, horizontal = 4.dp),
//                            verticalAlignment = Alignment.CenterVertically,
//                            horizontalArrangement = Arrangement.SpaceBetween
//                        ) {
//                            Text(
//                                text = contact.name,
//                                style = MaterialTheme.typography.bodyLarge
//                            )
//                            Checkbox(
//                                checked = contact.isSelected,
//                                onCheckedChange = { checked ->
//                                    contactListState = contactListState.map {
//                                        if (it.id == contact.id) it.copy(isSelected = checked) else it
//                                    }
//                                }
//                            )
//                        }
//                    }
//                }
//            }
//        },
//        confirmButton = {
//            TextButton(
//                onClick = {
//                    val selectedNames = contactListState.filter { it.isSelected }.map { it.name }
//                    onConfirm(selectedNames)
//                }
//            ) {
//                Text("Seçilenleri Ekle")
//            }
//        },
//        dismissButton = {
//            TextButton(onClick = onDismiss) {
//                Text("İptal")
//            }
//        },
//        shape = RoundedCornerShape(16.dp)
//    )
//}

@Composable
fun PersonItemCard(
    person: Person,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
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
                        text = person.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${person.uniqueJournalCount} günlükte anıldı",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}