package com.loc.benimdunyam.feature.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onDataCleared: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState
    var showClearDialog by remember { mutableStateOf(false) }

    // Şifreli yedekleme için dialog ve URI state'leri
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // Ekran açıldığında biyometrik tercih durumunu yükle
    LaunchedEffect(Unit) {
        viewModel.loadBiometricPreference(context)
    }

    // Şifreli Dışa Aktarma (Export) için Dosya Oluşturucu Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            pendingExportUri = it
            showPasswordDialog = true
        }
    }

    // Şifreli İçe Aktarma (Import) için Dosya Seçici Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = it
            showPasswordDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Ayarlar ve Gizlilik") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Gizlilik ve Local-First Bilgilendirme Kartı
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tamamen Yerel ve Gizli",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Benim Dünyam (My World) uygulaması verilerinizi yalnızca sizin cihazınızda saklar. İnternet bağlantısı gerektirmez, hiçbir sunucuya veri göndermez.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Biyometrik Kilit Kartı
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Biyometrik Uygulama Kilidi",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Uygulama açılışında parmak izi/yüz tanıma iste",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Switch(
                        checked = uiState.isBiometricEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.setBiometricEnabled(context, isChecked)
                        }
                    )
                }
            }

            // 3. Veri Yönetimi ve Şifreli Yedekleme Kartı
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Veri Yönetimi ve Şifreli Yedekleme",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { exportLauncher.launch("benim_dunyam_yedek.json") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Şifreli Olarak Yedekle (Export)")
                    }

                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Şifreli Yedekten Geri Yükle (Import)")
                    }
                }
            }

            // 4. Uygulama Bilgisi Kartı
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Uygulama Sürümü",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Benim Dünyam v1.3 (Faz 2: Şifreli Yedek & Biyometrik Kilit)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Verileri Sıfırla / Temizle Butonu
            OutlinedButton(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Tüm Yerel Verileri Sil ve Sıfırla")
            }
        }
    }

    // Parola Giriş Diyaloğu (Şifreli Yedekleme/Geri Yükleme için)
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                passwordInput = ""
                pendingExportUri = null
                pendingImportUri = null
            },
            title = { Text(text = "Yedek Parolası") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "AES-256 şifrelemesi için bir parola girin:")
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Parola") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPasswordDialog = false
                        val pass = passwordInput
                        passwordInput = ""

                        pendingExportUri?.let { uri ->
                            viewModel.exportDatabaseToJsonEncrypted(context, uri, pass) { success, err ->
                                if (success) {
                                    Toast.makeText(context, "Şifreli yedek başarıyla oluşturuldu.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Yedekleme hatası: $err", Toast.LENGTH_LONG).show()
                                }
                            }
                            pendingExportUri = null
                        }

                        pendingImportUri?.let { uri ->
                            viewModel.importDatabaseFromJsonEncrypted(context, uri, pass) { success, err ->
                                if (success) {
                                    Toast.makeText(context, "Şifreli yedek başarıyla geri yüklendi.", Toast.LENGTH_SHORT).show()
                                    onDataCleared()
                                } else {
                                    Toast.makeText(context, "Geri yükleme hatası: $err", Toast.LENGTH_LONG).show()
                                }
                            }
                            pendingImportUri = null
                        }
                    },
                    enabled = passwordInput.isNotBlank()
                ) {
                    Text("Tamam")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    passwordInput = ""
                    pendingExportUri = null
                    pendingImportUri = null
                }) {
                    Text("İptal")
                }
            }
        )
    }

    // Veri Sıfırlama Onay Diyaloğu
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(text = "Tüm Verileri Sıfırla") },
            text = { Text(text = "Cihazınızda kayıtlı olan tüm günlükler ve kişiler kalıcı olarak silinecektir. Bu işlem geri alınamaz. Emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllData(onSuccess = onDataCleared)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Evet, Hepsini Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}