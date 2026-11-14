package com.loc.benimdunyam.feature.settings

import androidx.compose.ui.graphics.Color
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.loc.benimdunyam.feature.settings.components.AppearanceThemePicker

private enum class BackupPasswordAction {
    EXPORT,
    PREVIEW,
    CONFIRM_RESTORE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onDataCleared: () -> Unit,
    onNavigateToCloudAccount: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState
    val isBusy = uiState.isLoading ||
            uiState.isBackupHealthLoading ||
            uiState.isBackupSelfCheckLoading

    var showClearDialog by remember {
        mutableStateOf(false)
    }

    var showPreviewDialog by remember {
        mutableStateOf(false)
    }

    var passwordAction by remember {
        mutableStateOf<BackupPasswordAction?>(null)
    }

    var passwordInput by remember {
        mutableStateOf("")
    }

    var pendingExportUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var pendingImportUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var pendingRecoveryUri by remember {
        mutableStateOf<Uri?>(null)
    }

    LaunchedEffect(Unit) {
        viewModel.loadBiometricPreference(context)
        viewModel.checkBackupHealth()
    }

    LaunchedEffect(uiState.restorePreview) {
        showPreviewDialog = uiState.restorePreview != null
    }

    LaunchedEffect(uiState.errorMessage, uiState.isLoading) {
        if (
            !uiState.isLoading &&
            uiState.errorMessage != null &&
            uiState.restorePreview != null
        ) {
            showPreviewDialog = true
        }
    }

    BackHandler(enabled = isBusy) {
        // Kritik dosya işlemi sırasında ekran kapatılmaz.
    }

    // ----------------------------------------
    // SAF DOCUMENT LAUNCHERS
    // ----------------------------------------

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/octet-stream"
        )
    ) { uri ->
        if (uri != null) {
            pendingExportUri = uri
            passwordAction = BackupPasswordAction.EXPORT
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            passwordAction = BackupPasswordAction.PREVIEW
        }
    }

    val recoveryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/octet-stream"
        )
    ) { uri ->
        if (uri != null) {
            pendingRecoveryUri = uri
            passwordAction = BackupPasswordAction.CONFIRM_RESTORE
        } else {
            showPreviewDialog = true
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Text("Ayarlar ve Gizlilik")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !isBusy
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Geri"
                        )
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

            // Üç temanın minyatür önizlemeleri. Buradaki seçim tüm ekranlarda geçerli.
            AppearanceThemePicker(enabled = !isBusy)

            // BULUT HESABIM: mevcut Room / yedekleme akışına dokunmaz.
            Card(
                onClick = onNavigateToCloudAccount,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bulut Hesabım", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "İsteğe bağlı Firebase hesabı, giriş ve güvenli bağlantı kontrolü",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            // GİZLİLİK
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            "Yerel Anı Arşivi",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text(
                        "Günlüklerin, kişilerin, dönemlerin ve fotoğrafların " +
                                "şimdilik cihazında saklanır. Bulut hesabı yalnızca " +
                                "kimlik doğrulama ve Firestore bağlantı testi yapar; " +
                                "anılar kendiliğinden yüklenmez. Dosya yedeğini " +
                                "bir bulut sağlayıcısına kaydetmeyi ayrıca seçebilirsin.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // BİYOMETRİK KİLİT
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Biyometrik Uygulama Kilidi",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            "Uygulama kilidi tercihi",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Switch(
                        checked = uiState.isBiometricEnabled,
                        enabled = !isBusy,
                        onCheckedChange = {
                            viewModel.setBiometricEnabled(context, it)
                        }
                    )
                }
            }

            // ŞİFRELİ YEDEKLEME
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            "Anılarımın Güvenliği",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text(
                        "Günlüklerini, yaşam dönemlerini, " +
                                "kişilerini ve fotoğraflarını " +
                                "parola korumalı bir dosyada sakla.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedButton(
                        onClick = {
                            exportLauncher.launch(
                                "benim_dunyam_yedek.bdbackup"
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null
                        )

                        Spacer(Modifier.width(8.dp))

                        Text("Şifreli Yedek Oluştur")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.cancelRestorePreview()
                            importLauncher.launch(
                                arrayOf("*/*")
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = null
                        )

                        Spacer(Modifier.width(8.dp))

                        Text("Yedekten Geri Yükle")
                    }

                    Text(
                        "Yedek dosyası .bdbackup uzantısıyla " +
                                "kaydedilir. Parolanı kaybedersen " +
                                "şifreli yedeğini açamazsın.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ----------------------------------------
            // ARŞİV SAĞLIĞI
            // ----------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Arşiv Sağlığı",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text(
                        "Anılarının, kişi bağlantılarının ve " +
                                "fotoğraflarının cihazındaki durumunu kontrol et.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (uiState.isBackupHealthLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                            Text("Arşiv kontrol ediliyor...")
                        }
                    } else {
                        uiState.backupHealthReport?.let { report ->
                            Text(
                                text = if (report.isHealthy) {
                                    "Kayıtlarda belirgin bir sorun bulunamadı."
                                } else {
                                    "Bazı kayıtların incelenmesi gerekiyor."
                                },
                                color = if (report.isHealthy) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            HorizontalDivider()
                            Text("Günlükler: ${report.journalCount}")
                            Text("Kişiler: ${report.personCount}")
                            Text("Sezonlar: ${report.seasonCount}")
                            Text("Bölümler: ${report.episodeCount}")
                            HorizontalDivider()
                            Text("Eksik fotoğraflar: ${report.missingImages}")
                            Text("Eski URI referansları: ${report.legacyImageReferences}")
                            Text("Güvensiz fotoğraf yolları: ${report.unsafeImagePaths}")
                            Text("Tutarsız veri bağlantıları: ${report.invalidRelations}")
                            Text("Eski geçici dosyalar: ${report.staleTemporaryEntries}")
                            Text(
                                "Kullanılmayan geri yükleme görselleri: " +
                                        "${report.orphanRestoreImages}"
                            )
                            if (report.hasDataProblems) {
                                Text(
                                    text = "Bazı kayıtlar eksik ya da eski biçimde. " +
                                            "Verileri silmeden önce incele.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // SQLite düzeyindeki salt-okunur bütünlük sonuçları.
                    uiState.databaseIntegrityReport?.let { db ->
                        HorizontalDivider()
                        Text(
                            "SQLite Veritabanı Bütünlüğü",
                            style = MaterialTheme.typography.titleSmall
                        )
                        val integrityText = if (db.isHealthy) {
                            "SQLite kontrollerinde sorun bulunamadı."
                        } else {
                            "SQLite bütünlüğü incelenmeli."
                        }
                        Text(
                            integrityText,
                            color = if (db.isHealthy) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Yapısal kontrol: " +
                                    (if (db.structureOk) "Başarılı" else "Sorun bulundu")
                        )
                        Text(
                            "Yabancı anahtar ihlalleri: ${db.foreignKeyViolationCount}"
                        )
                        if (!db.isHealthy) {
                            db.problems.take(3).forEach { message ->
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = viewModel::checkBackupHealth,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Arşivi Kontrol Et")
                    }

                    val report = uiState.backupHealthReport
                    OutlinedButton(
                        onClick = viewModel::cleanBackupLeftovers,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy && report != null &&
                                (report.staleTemporaryEntries > 0 ||
                                        report.orphanRestoreImages > 0)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Eski Geçici Dosyaları Temizle")
                    }

                    Text(
                        "Bu kontrol bir yedekleme veya geri yükleme " +
                                "testinin yerini tutmaz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ----------------------------------------
            // ŞİFRELİ YEDEKLEME PROVASI
            // ----------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Yedekleme Provası",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text(
                        "Anılarını değiştirmeden şifreli bir deneme yedeği " +
                                "oluştur, yeniden aç ve geçici Room " +
                                "veritabanına aktarılabildiğini kontrol et.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (uiState.isBackupSelfCheckLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                            Text("Şifreli yedek ve Room provası yapılıyor...")
                        }
                    }

                    uiState.backupSelfCheckReport?.let { report ->
                        HorizontalDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Geçici yedekleme provası başarılı",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Text("Günlükler: ${report.journalCount}")
                        Text("Kişiler: ${report.personCount}")
                        Text("Sezonlar: ${report.seasonCount}")
                        Text("Bölümler: ${report.episodeCount}")
                        Text("Kişi-anı bağlantıları: ${report.mentionCount}")
                        Text("Unutulmaz anılar: ${report.specialMemoryCount}")
                        Text("Fotoğraflar: ${report.imageCount}")
                        Text("Şifreli yedek boyutu: ${report.encryptedBytes} bayt")
                        Text("Geçen süre: ${report.elapsedMillis} ms")
                    }

                    OutlinedButton(
                        onClick = viewModel::runBackupSelfCheck,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.PlayCircleOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Yedekleme Provasını Çalıştır")
                    }

                    Text(
                        "Bu prova gerçek verileri değiştirmez. " +
                                "SAF'a kaydedilen dosyanın başka cihazda " +
                                "geri yüklenmesini garanti etmez. " +
                                "Gerçek geri yüklemeyi ayrı emülatörde doğrula.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Geri yükleme / sıfırlama başarılı; ama ayrı taslak DB'sinin
            // temizlenmesi başarısız olduysa kullanıcı buradan tekrar dener.
            if (uiState.isDraftCleanupPending) {
                OutlinedButton(
                    onClick = viewModel::retryDraftCleanup,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Taslak Temizliğini Tekrar Dene")
                }
            }

            // HATA / BAŞARI MESAJLARI
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )

                        IconButton(
                            onClick = viewModel::dismissMessage
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Kapat"
                            )
                        }
                    }
                }
            }

            uiState.successMessage?.let { message ->
                Card {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = message,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // UYGULAMA BİLGİLERİ
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Uygulama Bilgileri",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Benim Dünyam • Yerel yaşam arşivi",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    showClearDialog = true
                },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null
                )

                Spacer(Modifier.width(8.dp))

                Text("Tüm Yerel Verileri Sil ve Sıfırla")
            }
        }
    }

    // ----------------------------------------
    // PAROLA DİYALOĞU
    // ----------------------------------------

    passwordAction?.let { action ->

        val isExport =
            action == BackupPasswordAction.EXPORT

        AlertDialog(
            onDismissRequest = {
                passwordAction = null
                passwordInput = ""
                pendingExportUri = null
                pendingImportUri = null
                pendingRecoveryUri = null

                if (uiState.restorePreview != null) {
                    showPreviewDialog = true
                }
            },
            title = {
                Text(
                    when (action) {
                        BackupPasswordAction.EXPORT ->
                            "Yedek Parolası"

                        BackupPasswordAction.PREVIEW ->
                            "Yedeği Aç"

                        BackupPasswordAction.CONFIRM_RESTORE ->
                            "Geri Yüklemeyi Onayla"
                    }
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        when (action) {
                            BackupPasswordAction.EXPORT ->
                                "Yeni yedeğin için en az 12 " +
                                        "karakterli güçlü bir parola belirle."

                            BackupPasswordAction.PREVIEW ->
                                "Seçtiğin yedeğin parolasını gir. " +
                                        "Önce yalnızca içeriği incelenecek."

                            BackupPasswordAction.CONFIRM_RESTORE ->
                                "Geri yüklenecek yedeğin parolasını " +
                                        "yeniden gir. Mevcut verilerinin " +
                                        "güvenlik yedeği de bu parolayla " +
                                        "şifrelenecek."
                        }
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                        },
                        label = {
                            Text("Parola")
                        },
                        singleLine = true,
                        visualTransformation =
                            PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val enteredPassword = passwordInput

                        passwordInput = ""
                        passwordAction = null

                        when (action) {
                            BackupPasswordAction.EXPORT -> {
                                pendingExportUri?.let { uri ->
                                    viewModel.exportEncryptedBackup(
                                        destinationUri = uri,
                                        password = enteredPassword
                                    )
                                }
                                pendingExportUri = null
                            }

                            BackupPasswordAction.PREVIEW -> {
                                pendingImportUri?.let { uri ->
                                    viewModel.previewRestore(
                                        sourceUri = uri,
                                        password = enteredPassword
                                    )
                                }
                                pendingImportUri = null
                            }

                            BackupPasswordAction.CONFIRM_RESTORE -> {
                                pendingRecoveryUri?.let { uri ->
                                    viewModel.confirmRestore(
                                        recoveryDestinationUri = uri,
                                        password = enteredPassword,
                                        onSuccess = onDataCleared
                                    )
                                }
                                pendingRecoveryUri = null
                            }
                        }
                    },
                    enabled = if (isExport) {
                        passwordInput.length >= 12
                    } else {
                        passwordInput.isNotBlank()
                    }
                ) {
                    Text("Devam Et")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        passwordAction = null
                        passwordInput = ""
                        pendingExportUri = null
                        pendingImportUri = null
                        pendingRecoveryUri = null

                        if (uiState.restorePreview != null) {
                            showPreviewDialog = true
                        }
                    }
                ) {
                    Text("İptal")
                }
            }
        )
    }

    // ----------------------------------------
    // RESTORE PREVIEW
    // ----------------------------------------

    val preview = uiState.restorePreview

    if (
        preview != null &&
        showPreviewDialog &&
        passwordAction == null &&
        !uiState.isLoading
    ) {
        AlertDialog(
            onDismissRequest = {
                showPreviewDialog = false
                viewModel.cancelRestorePreview()
            },
            title = {
                Text("Yedek İçeriği")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val dateText = remember(preview.createdAt) {
                        DateFormat.getDateTimeInstance(
                            DateFormat.MEDIUM,
                            DateFormat.SHORT,
                            Locale.forLanguageTag("tr-TR")
                        ).format(Date(preview.createdAt))
                    }

                    Text("Oluşturulma: $dateText")

                    HorizontalDivider()

                    Text("Günlükler: ${preview.journalCount}")
                    Text("Kişiler: ${preview.personCount}")
                    Text("Yaşam dönemleri: ${preview.seasonCount}")
                    Text("Bölümler: ${preview.episodeCount}")
                    Text("Kişi-anı ilişkileri: ${preview.mentionCount}")
                    Text(
                        "Unutulmaz anılar: " +
                                "${preview.specialMemoryCount}"
                    )
                    Text("Fotoğraflar: ${preview.imageCount}")

                    HorizontalDivider()

                    Text(
                        "Dikkat: Geri yükleme mevcut Room " +
                                "kayıtlarının yerini bu yedekteki " +
                                "kayıtlarla değiştirecek.\n\n" +
                                "Önce mevcut anıların için ayrı " +
                                "bir şifreli güvenlik yedeği " +
                                "oluşturulacak.\n\n" +
                                "Kaydedilmemiş taslaklar bu yedeğe dahil değildir " +
                                "ve geri yükleme başarılı olursa silinir.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPreviewDialog = false

                        recoveryLauncher.launch(
                            "benim_dunyam_kurtarma.bdbackup"
                        )
                    }
                ) {
                    Text("Güvenlik Yedeğiyle Devam Et")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPreviewDialog = false
                        viewModel.cancelRestorePreview()
                    }
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // ----------------------------------------
    // LOADING DIALOG
    // ----------------------------------------

    if (uiState.isLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text("İşlem Devam Ediyor")
            },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )

                    Text(
                        uiState.loadingMessage
                            ?: "Lütfen işlemin tamamlanmasını bekleyin."
                    )
                }
            },
            confirmButton = {}
        )
    }

    // ----------------------------------------
    // CLEAR DATA CONFIRMATION
    // ----------------------------------------

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = {
                showClearDialog = false
            },
            title = {
                Text("Tüm Verileri Sıfırla")
            },
            text = {
                Text(
                    "Room veritabanındaki günlükler, kişiler, " +
                            "sezonlar ve ilişkileri silinecek. " +
                            "Ayrı taslak veritabanındaki kaydedilmemiş " +
                            "anı taslakları da silinecek. " +
                            "Fotoğraf dosyaları ayrıca temizlenmeyebilir.\n\n" +
                            "İşlem geri alınamaz. " +
                            "Önce şifreli yedek oluşturman önerilir."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false

                        viewModel.clearAllData(
                            onSuccess = onDataCleared
                        )
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Evet, Verileri Sil")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                    }
                ) {
                    Text("İptal")
                }
            }
        )
    }
}
