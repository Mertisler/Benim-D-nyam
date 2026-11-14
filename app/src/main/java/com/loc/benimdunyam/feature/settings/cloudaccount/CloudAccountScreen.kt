package com.loc.benimdunyam.feature.settings.cloudaccount

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import java.text.DateFormat
import java.util.Date
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.domain.model.CloudConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAccountScreen(
    onNavigateBack: () -> Unit,
    viewModel: CloudAccountViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isRegistration by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    // Hesap şifresini kalıcı UI state'ine veya SavedStateHandle'a kaydetmiyoruz.
    var password by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    // Bulut arşiv şifresi kesinlikle rememberSaveable / SavedStateHandle'a yazılmaz.
    var archiveDialog by remember { mutableStateOf<String?>(null) }
    var archivePassphrase by remember { mutableStateOf("") }
    var archiveRepeat by remember { mutableStateOf("") }
    var archiveScopeAccepted by remember { mutableStateOf(false) }


    LaunchedEffect(state.account?.uid) {
        if (state.account != null) {
            password = ""
            passwordConfirmation = ""
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Bulut Hesabım") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("İsteğe bağlı bulut hesabı", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "Günlüklerin ve fotoğrafların cihazındaki yerel depoda saklanır. " +
                                "İsteğinle şifreli metin arşivi oluşturabilir, yeni/boş cihaza geri yükleyebilirsin. " +
                                "Otomatik senkronizasyon henüz etkin değil.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (state.isCheckingSession) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Hesap durumu kontrol ediliyor...")
                }
            } else if (state.account == null) {
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            if (isRegistration) "Yeni hesap oluştur" else "Hesabına giriş yap",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !isRegistration,
                                onClick = { isRegistration = false; password = ""; passwordConfirmation = ""; viewModel.dismissMessage() },
                                label = { Text("Giriş yap") },
                                enabled = !state.isSubmitting
                            )
                            FilterChip(
                                selected = isRegistration,
                                onClick = { isRegistration = true; password = ""; passwordConfirmation = ""; viewModel.dismissMessage() },
                                label = { Text("Kayıt ol") },
                                enabled = !state.isSubmitting
                            )
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("E-posta") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            singleLine = true,
                            enabled = !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Şifre") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showPassword) "Şifreyi gizle" else "Şifreyi göster"
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            singleLine = true,
                            enabled = !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isRegistration) {
                            OutlinedTextField(
                                value = passwordConfirmation,
                                onValueChange = { passwordConfirmation = it },
                                label = { Text("Şifre tekrar") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                singleLine = true,
                                enabled = !state.isSubmitting,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Firebase Authentication en az 6 karakterli şifre kabul eder. Güçlü ve benzersiz bir şifre kullan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                if (isRegistration) viewModel.register(email, password, passwordConfirmation)
                                else viewModel.signIn(email, password)
                            },
                            enabled = !state.isSubmitting && email.isNotBlank() && password.isNotEmpty() &&
                                    (!isRegistration || passwordConfirmation.isNotEmpty()),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isSubmitting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (isRegistration) "Hesap oluştur" else "Giriş yap")
                        }
                    }
                }
            } else {
                val account = state.account!!
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Firebase oturumu açık", style = MaterialTheme.typography.titleLarge)
                        }
                        Text("E-posta: ${account.email ?: "Belirtilmemiş"}")
                        Text(
                            if (account.isEmailVerified) "E-posta doğrulandı" else "E-posta henüz doğrulanmadı",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()
                        Text("Firestore erişim testi", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Sadece kendi hesabına ait /users/{uid}/_meta/connection yolunu sunucudan okumayı dener. " +
                                    "Test belgesinin önceden oluşturulması gerekmez; hiçbir veri yazılmaz.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = viewModel::checkConnection,
                            enabled = !state.isCheckingConnection && !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isCheckingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.isCheckingConnection) "Bağlantı kontrol ediliyor..." else "Bağlantıyı kontrol et")
                        }
                        state.connectionStatus?.let { ConnectionResult(it) }
                        HorizontalDivider()
                        Text("Şifreli Bulut Arşivi · Deneme", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Bu, henüz otomatik veya çift yönlü senkronizasyon DEĞİL. " +
                                    "Onay verirsen günlük metinlerinin, kişi/dönem/bölüm bilgilerinin, " +
                                    "anı ilişkilerinin ve ruh hallerinin şifreli kopyası hesabına yüklenir. " +
                                    "Fotoğraflar ve kapak görselleri aktarılmaz. Geri yükleme yalnızca boş cihaza, ayrı onayla yapılır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Ayrı bir arşiv şifresi belirlemelisin. Bu şifre sunucuya gönderilmez " +
                                    "ve uygulama tarafından saklanmaz. Unutursan bu kopyayı açamazsın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.isArchiveBusy) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(state.archiveAction ?: "Bulut arşivi işleniyor...")
                            }
                        }
                        Button(
                            onClick = { archiveDialog = "upload" },
                            enabled = !state.isArchiveBusy && !state.isSubmitting && !state.isCheckingConnection,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Şifreli kopya oluştur") }
                        OutlinedButton(
                            onClick = viewModel::loadLatestArchive,
                            enabled = !state.isArchiveBusy && !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Buluttaki son kopyayı göster") }
                        state.latestArchive?.let { last ->
                            val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(last.createdAtMillis))
                            Text("Son kopya: $formatted", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "${last.journalCount} anı · ${last.peopleCount} kişi · " +
                                        "${last.seasonCount} dönem · ${last.episodeCount} bölüm · " +
                                        "${last.moodCount} ruh hali",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(
                                onClick = { archiveDialog = "verify" },
                                enabled = !state.isArchiveBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Şifre ve bütünlük kontrolü yap") }
                        }
                        CloudRestoreSection(
                            state = state,
                            onPreview = viewModel::previewCloudRestore,
                            onImport = viewModel::importPreviewedCloudRestore,
                            onClearPreview = viewModel::clearRestorePreview
                        )
                        // Faz 2C: geri yükleme yapmadan farkları ve eksik medya kapsamını gösterir.
                        CloudSyncAuditSection(
                            state = state,
                            onInspect = viewModel::inspectCloudReadiness,
                            onCompare = viewModel::compareCloudArchive
                        )
                        state.archiveError?.let {
                            StatusMessage(it, isError = true, onDismiss = viewModel::dismissArchiveMessage)
                        }
                        state.archiveMessage?.let {
                            StatusMessage(it, isError = false, onDismiss = viewModel::dismissArchiveMessage)
                        }
                        HorizontalDivider()
                        OutlinedButton(
                            onClick = { showSignOutConfirmation = true },
                            enabled = !state.isArchiveBusy && !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Bulut hesabından çıkış yap")
                        }
                    }
                }
            }

            state.errorMessage?.let { message ->
                StatusMessage(message = message, isError = true, onDismiss = viewModel::dismissMessage)
            }
            state.message?.let { message ->
                StatusMessage(message = message, isError = false, onDismiss = viewModel::dismissMessage)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Veriler hakkında", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Otomatik Room–Firestore eşitlemesi henüz aktif değil. " +
                                    "Yalnızca açık onayla şifreli metin arşivi kopyası oluşturulabilir; " +
                                    "fotoğraflar aktarılmaz. Doğrulanmış arşivler yalnızca boş cihaza " +
                                    "ayrı onayla içe aktarılabilir. " +
                                    "Hesap değiştirsen veya çıkış yapsan bile cihazdaki anıların görünmeye devam eder. " +
                                    "Paylaşılan cihazlarda yerel arşivine erişimi ayrıca koru.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (archiveDialog != null) {
        val isUpload = archiveDialog == "upload"
        fun clearArchiveInput() {
            archivePassphrase = ""
            archiveRepeat = ""
            archiveScopeAccepted = false
            archiveDialog = null
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!state.isArchiveBusy) clearArchiveInput() },
            title = { Text(if (isUpload) "Buluta şifreli kopya gönder" else "Bulut kopyasını doğrula") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (isUpload)
                            "Günlük içerikleri, kişiler, ilişkiler, dönemler ve ruh halleri bu hesaba şifreli olarak " +
                                    "yüklenir. Fotoğraflar yüklenmez. Otomatik geri yükleme ve eşitleme yapılmaz."
                        else
                            "Buluttaki son kopya indirilip şifresi ve bütünlüğü kontrol edilir. " +
                                    "Cihazındaki veriler değişmez.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = archivePassphrase,
                        onValueChange = { archivePassphrase = it },
                        label = { Text("Arşiv şifresi (en az 12 karakter)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isUpload) {
                        OutlinedTextField(
                            value = archiveRepeat,
                            onValueChange = { archiveRepeat = it },
                            label = { Text("Arşiv şifresi tekrar") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = archiveScopeAccepted,
                                onCheckedChange = { archiveScopeAccepted = it }
                            )
                            Text(
                                "Fotoğrafların aktarılmadığını ve geri yüklemenin yalnızca boş cihaza onayla yapılabildiğini anladım.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isUpload) viewModel.uploadEncryptedArchive(archivePassphrase)
                        else viewModel.verifyEncryptedArchive(archivePassphrase)
                        clearArchiveInput()
                    },
                    enabled = !state.isArchiveBusy && archivePassphrase.length in 12..128 &&
                            (!isUpload || (archiveScopeAccepted && archivePassphrase == archiveRepeat))
                ) { Text(if (isUpload) "Onayla ve yükle" else "Doğrula") }
            },
            dismissButton = { TextButton(onClick = ::clearArchiveInput) { Text("İptal") } }
        )
    }

    if (showSignOutConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = { Text("Bulut hesabından çıkış yapılsın mı?") },
            text = { Text("Firebase oturumu kapanacak. Room'daki yerel günlükler ve fotoğraflar silinmeyecek.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirmation = false
                    viewModel.signOut()
                    password = ""
                    passwordConfirmation = ""
                }) { Text("Çıkış yap") }
            },
            dismissButton = { TextButton(onClick = { showSignOutConfirmation = false }) { Text("İptal") } }
        )
    }
}

@Composable
private fun ConnectionResult(status: CloudConnectionStatus) {
    val success = status == CloudConnectionStatus.Reachable
    val message = when (status) {
        CloudConnectionStatus.Reachable -> "Firestore sunucusuna güvenli erişim başarılı. Test belgesi bulunmak zorunda değil."
        CloudConnectionStatus.SignInRequired -> "Bağlantıyı kontrol etmek için giriş yapmalısın."
        CloudConnectionStatus.PermissionDenied -> "İzin reddedildi. Firebase Console'da Firestore güvenlik kurallarını kontrol et."
        CloudConnectionStatus.NetworkUnavailable -> "Sunucuya ulaşılamadı. İnternetini kontrol edip yeniden dene."
        CloudConnectionStatus.UnknownError -> "Bağlantı doğrulanamadı. Firebase projesini ve Firestore ayarlarını kontrol et."
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (success) Icons.Default.CheckCircle else Icons.Default.CloudOff,
            contentDescription = null,
            tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Text(
            message,
            color = if (success) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusMessage(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    }
}
