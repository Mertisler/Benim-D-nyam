package com.loc.benimdunyam.feature.people

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.data.model.DeviceContactItem
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.feature.people.components.PeopleDirectoryEmptyState
import com.loc.benimdunyam.feature.people.components.PeoplePersonRow
import com.loc.benimdunyam.feature.people.components.SelectContactsDialog

/**
 * İnsanlarım: arşive giriş sayfası.
 *
 * Kişiye ait anılar ve analizler PersonDetailScreen'deki iki sekmede kalır.
 * Burada aynı istatistikler / aynı sezonlar yeniden listelenmez.
 * Var olan PeopleViewModel, izin ve çoklu aktarım akışları korunur.
 */
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
    val focusManager = LocalFocusManager.current
    val snackbarHost = remember { SnackbarHostState() }

    var actionsExpanded by remember { mutableStateOf(false) }
    var showContactsDialog by remember { mutableStateOf(false) }
    var deviceContacts by remember { mutableStateOf<List<DeviceContactItem>>(emptyList()) }

    val isBusy = uiState.isImporting || uiState.isReadingContacts || uiState.isRestoring
    val isSearching = uiState.searchQuery.isNotBlank()

    LaunchedEffect(uiState.importError) {
        uiState.importError?.let { message ->
            snackbarHost.showSnackbar(message)
            viewModel.clearImportError()
        }
    }

    fun loadContactsFromDevice() {
        viewModel.loadDeviceContacts(context) { result ->
            deviceContacts = result
            showContactsDialog = result.isNotEmpty()
            if (result.isEmpty() && viewModel.uiState.value.importError == null) {
                Toast.makeText(context, "Rehberde aktarılacak kişi bulunamadı.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadContactsFromDevice()
        } else {
            Toast.makeText(
                context,
                "Rehberden kişi aktarmak için erişim izni gerekiyor.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun openContactsPicker() {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            loadContactsFromDevice()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("İnsanlarım") },
                actions = {
                    IconButton(
                        onClick = onNavigateToPeopleNetwork,
                        enabled = !isBusy
                    ) {
                        Icon(
                            Icons.Default.Hub,
                            contentDescription = "Hayatımın İnsan Haritasını aç"
                        )
                    }
                    if (uiState.isImporting || uiState.isReadingContacts) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        Box {
                            IconButton(
                                onClick = { actionsExpanded = true },
                                enabled = !isBusy
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Kişi işlemleri")
                            }
                            DropdownMenu(
                                expanded = actionsExpanded,
                                onDismissRequest = { actionsExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rehberden kişi aktar") },
                                    leadingIcon = {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                                    },
                                    onClick = {
                                        actionsExpanded = false
                                        openContactsPicker()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            // Boş arşiv durumunda zaten büyük bir 'Kişi ekle' butonu var:
            // aynı eylemi ikinci kez gösterip kullanıcıyı yormuyoruz.
            if (!isBusy && (uiState.people.isNotEmpty() || isSearching)) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToCreatePerson,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Kişi ekle") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(2.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Hikâyendeki insanlar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Her ismin ardında birlikte biriktirdiğiniz anılar var.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("İsimle ara...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Aramayı temizle")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            if (uiState.isRestoring) {
                Text(
                    text = "Veriler geri yüklenirken kişi ekleme ve aktarma geçici olarak kapalı.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.people.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PeopleDirectoryEmptyState(
                        hasSearch = isSearching,
                        isRestoring = uiState.isRestoring,
                        onClearSearch = { viewModel.onSearchQueryChanged("") },
                        onAddPerson = onNavigateToCreatePerson,
                        onImportContacts = ::openContactsPicker
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSearching) "${uiState.people.size} sonuç" else "${uiState.people.size} kişi",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(uiState.people, key = { _, person -> person.id }) { index, person ->
                        PersonItemCard(person = person) {
                            onNavigateToPersonDetail(person.id)
                        }
                        if (index < uiState.people.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showContactsDialog && !isBusy) {
        SelectContactsDialog(
            contacts = deviceContacts,
            onDismiss = { showContactsDialog = false },
            onConfirm = { selectedNames ->
                showContactsDialog = false
                viewModel.importMultipleContacts(selectedNames) { added, duplicates ->
                    Toast.makeText(
                        context,
                        "$added kişi eklendi · $duplicates kişi zaten kayıtlıydı.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}

/** Eski çağrılar kırılmasın diye aynı Composable imzası korunuyor. */
@Composable
fun PersonItemCard(person: Person, onClick: () -> Unit) {
    PeoplePersonRow(person = person, onClick = onClick)
}
