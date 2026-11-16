package com.loc.benimdunyam.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.domain.model.LocalArchiveAccessDecision

/**
 * Yerel arşiv hesabı uyuşmadığında uygulamanın günlük sayfalarını gizler.
 * Farklı hesaplar kendi BOŞ yerel çalışma alanlarını oluşturabilir;
 * eski hesabın anıları hiçbir zaman bu hesaplara otomatik aktarılmaz.
 */
@Composable
fun LocalArchiveGateScreen(
    state: LocalArchiveAccessUiState,
    onConfirmLink: () -> Unit,
    onContinueAsGuest: () -> Unit,
    onCreateEmptyWorkspace: () -> Unit,
    onReturnToLegacy: () -> Unit
) {
    var authPage by rememberSaveable { mutableStateOf("notice") }
    // Yanlış hesapla başarılı giriş yapılırsa sonraki deneme yeni ViewModel kullanır.
    // Aksi halde AuthViewModel.isAuthenticated eski başarılı girişten true kalabilir.
    var loginAttempt by rememberSaveable { mutableStateOf(0) }
    var showCreateConfirmation by rememberSaveable { mutableStateOf(false) }


    // Daha önce eklediğimiz giriş ve şifre sıfırlama ekranlarını aynen kullanırız.
    when (authPage) {
        "login" -> SignInScreen(
            onBack = { authPage = "notice" },
            onRegister = { authPage = "register" },
            onForgotPassword = { authPage = "reset" },
            onAuthenticated = { authPage = "notice" },
            viewModel = hiltViewModel<AuthViewModel>(key = "local_archive_login_$loginAttempt")
        )
        "register" -> RegisterScreen(
            onBack = { authPage = "login" },
            onSignIn = { authPage = "login" },
            onAuthenticated = { authPage = "notice" },
            viewModel = hiltViewModel<AuthViewModel>(key = "local_archive_register_$loginAttempt")
        )
        "reset" -> ForgotPasswordScreen(onBack = { authPage = "login" })
        else -> LocalArchiveGateNotice(
            state = state,
            onConfirmLink = onConfirmLink,
            onContinueAsGuest = onContinueAsGuest,
            onSignIn = { loginAttempt += 1; authPage = "login" },
            onCreateEmptyWorkspace = { showCreateConfirmation = true },
            onReturnToLegacy = onReturnToLegacy
        )
    }

    if (showCreateConfirmation) {
        AlertDialog(
            onDismissRequest = { showCreateConfirmation = false },
            title = { Text("Yeni boş anı alanı") },
            text = { Text(
                "Bu hesaba özel boş bir yerel arşiv seçilecek. Önceki hesaba ait anılar " +
                        "silinmeyecek, bu hesaba aktarılmayacak. Geçiş için uygulamayı tamamen " +
                        "kapatıp yeniden açman gerekecek. Devam edilsin mi?"
            ) },
            confirmButton = { TextButton(onClick = {
                showCreateConfirmation = false
                onCreateEmptyWorkspace()
            }) { Text("Boş arşivi seç") } },
            dismissButton = { TextButton(onClick = { showCreateConfirmation = false }) { Text("İptal") } }
        )
    }
}

@Composable
private fun LocalArchiveGateNotice(
    state: LocalArchiveAccessUiState,
    onConfirmLink: () -> Unit,
    onContinueAsGuest: () -> Unit,
    onSignIn: () -> Unit,
    onCreateEmptyWorkspace: () -> Unit,
    onReturnToLegacy: () -> Unit
) {
    val consentRequired = state.decision == LocalArchiveAccessDecision.CONSENT_REQUIRED
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .imePadding().padding(22.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (consentRequired) "Anılarını bu hesaba bağla" else "Bu cihazdaki anılar korunuyor",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    // Kayıt/giriş tamamlandığı halde neden kilit göründüğünü açıklıyoruz.
                    // Özellikle yanlış hesapla oturum açmak, eski arşive erişim vermez.
                    val message = when {
                        consentRequired ->
                            "Bu telefondaki yerel arşiv henüz bir Firebase hesabına bağlanmamış. " +
                                    "Onay verirsen yalnızca bu hesapla açılır. Anıların buluta gönderilmez."
                        state.currentAccountEmail.isNullOrBlank() ->
                            "Bu arşiv daha önce bir Firebase hesabına bağlandı. " +
                                    "Şu anda oturum açık değil. Arşivi bağladığın eski hesabınla giriş yap."
                        else ->
                            "Şu anda ${state.currentAccountEmail} hesabıyla oturum açık, ancak bu " +
                                    "hesap yerel arşivin sahibiyle eşleşmiyor. " +
                                    "Anılarını açmak için önce kullandığın Firebase hesabıyla giriş yap."
                    }
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    if (!consentRequired) {
                        // Firebase Console > Authentication > Users'daki UID ile karşılaştırılabilir.
                        // Tam UID'yi veya yerel günlük içeriğini ekrana vermiyoruz.
                        state.ownerUidSuffix?.let { suffix ->
                            Text(
                                "Arşivin bağlı olduğu hesap kimliğinin son 6 karakteri: $suffix",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            "Yeni hesap açabilir ve yalnızca ona ait boş bir arşiv seçebilirsin. " +
                                    "Önceki hesabın anıları aktarılmaz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Text(
                        "Not: Yeni hesaplar ayrı Room ve medya alanlarını kullanır. " +
                                "Eski arşivin hazırlanmış taşıma kopyası otomatik açılmaz. " +
                                "Bu koruma cihazın ekran kilidinin veya dosya şifrelemesinin yerini almaz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (consentRequired) {
                        // Kullanıcı verilerini bağlayacağı Firebase hesabını açıkça görür.
                        Text(
                            "Bağlanacak hesap: ${state.currentAccountEmail ?: "Açık Firebase oturumu"}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (state.isBusy) CircularProgressIndicator()
                    if (state.errorMessage != null) Text(
                        state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (state.restartRequired) {
                        Text(
                            "Yeni çalışma alanı seçildi. Güvenli geçiş için uygulamayı " +
                                    "son uygulamalar ekranından tamamen kapat, ardından yeniden aç. " +
                                    "Kapatıp açana kadar mevcut Room bağlantıları değişmez.",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    } else if (consentRequired) {
                        Button(
                            onClick = onConfirmLink,
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Onayla ve bu hesaba bağla") }
                        OutlinedButton(
                            onClick = onContinueAsGuest,
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Hesapsız devam et") }
                        Text(
                            "Bağladıktan sonra çıkış yaparsan yerel anılarını görmek için aynı " +
                                    "hesaba yeniden giriş yapman gerekir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (!state.restartRequired) {
                        if (state.canCreateEmptyWorkspace) {
                            OutlinedButton(
                                onClick = onCreateEmptyWorkspace,
                                enabled = !state.isBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Bu hesap için yeni boş arşiv aç") }
                        }
                        if (state.canReturnLegacyWorkspace) {
                            OutlinedButton(
                                onClick = onReturnToLegacy,
                                enabled = !state.isBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Eski arşivime geri dön") }
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (state.currentAccountEmail.isNullOrBlank())
                                    "Arşivin bağlı olduğu hesapla giriş yap"
                                else "Başka hesapla giriş yap"
                            )
                        }
                    }
                }
            }
        }
    }
}
