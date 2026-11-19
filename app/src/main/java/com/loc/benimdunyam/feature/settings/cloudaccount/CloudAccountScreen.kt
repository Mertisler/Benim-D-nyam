package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.layout.Arrangement
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.key
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import com.loc.benimdunyam.data.local.isolation.WorkspaceMediaEntryPoint
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAccountScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAuthEntry: () -> Unit,
    viewModel: CloudAccountViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // V2: Alt ekranlar aynı NavGraph hedefindedir; eski ekran rotaları değişmez.
    var selectedPageName by rememberSaveable { mutableStateOf(CloudHubPage.HOME.name) }
    val selectedPage = CloudHubPage.fromName(selectedPageName)
    val pageScroll = rememberScrollState()
    // Alt ekrana girince önceki ekranın aşağı kaydırılmış konumu taşınmaz.
    LaunchedEffect(selectedPage, state.account?.uid) {
        pageScroll.scrollTo(0)
    }
    fun goBackInsideHub() {
        if (state.account != null && selectedPage != CloudHubPage.HOME) {
            selectedPageName = selectedPage.parent().name
        } else onNavigateBack()
    }
    // Android sistem geri hareketi de ekranın sol üstündeki geri oku gibi çalışır.
    BackHandler(enabled = state.account != null && selectedPage != CloudHubPage.HOME) {
        selectedPageName = selectedPage.parent().name
    }
    // Yeni boş hesapta eski arşivin taşıma hazırlığı seçeneklerini gizle.
    val appContext = LocalContext.current.applicationContext
    val activeStorage = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, WorkspaceMediaEntryPoint::class.java)
            .activeWorkspaceSession().mode
    }
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
        } else {
            // Hesaptan çıkıldığında başka hesaba ait alt sayfa seçimi kalmasın.
            selectedPageName = CloudHubPage.HOME.name
            archiveDialog = null
            archivePassphrase = ""
            archiveRepeat = ""
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (state.account == null) "Bulut Hesabım" else selectedPage.title) },
                navigationIcon = {
                    IconButton(onClick = ::goBackInsideHub) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
                .verticalScroll(pageScroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.account == null) {
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
                                    "Metin anıları için otomatik eşitleme ayrıca onaylanabilir; " +
                                    "Spark ücretsiz modunda bulut medya aktarımı kapalıdır.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
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
                        // Hesabı olmayan veya oturumunu kapatan kullanıcı yeni tasarımı
                        // buradan açabilir. Test etmek için uygulama verisini silmesi gerekmez.
                        OutlinedButton(
                            onClick = onNavigateToAuthEntry,
                            enabled = !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Yeni giriş ve kayıt ekranlarını aç")
                        }
                    }
                }
            } else {
                // V2: Var olan tüm senkronizasyon ve güvenlik bileşenleri alt ekranlarda.
                // Aynı CloudAccountViewModel kullanılır; onay akışlarına müdahale etmiyoruz.
                key(selectedPage) {
                    CloudAccountV2Content(
                        page = selectedPage,
                        state = state,
                        viewModel = viewModel,
                        activeStorage = activeStorage,
                        navigate = { selectedPageName = it.name },
                        openArchiveDialog = { archiveDialog = it },
                        requestSignOut = { showSignOutConfirmation = true }
                    )
                }
            }

            state.errorMessage?.let { message ->
                StatusMessage(message = message, isError = true, onDismiss = viewModel::dismissMessage)
            }
            state.message?.let { message ->
                StatusMessage(message = message, isError = false, onDismiss = viewModel::dismissMessage)
            }

            // Teknik açıklamalar kullanıcı ana ekranından Gelişmiş İşlemler bölümüne taşındı.
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
            text = { Text("Firebase oturumu kapanacak. Yerel anılar silinmeyecek; bağlı bir arşiv varsa " +
                    "aynı hesapla yeniden giriş yapana kadar anı ekranları kilitli kalacak. " +
                    "Çıkış yapmadan önce hesap bilgilerini bildiğinden emin ol.") },
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
