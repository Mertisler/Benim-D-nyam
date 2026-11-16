package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.domain.model.CloudLocalArchiveOwnerState

/**
 * Kullanıcı profili ve hesap güvenliği bölümü.
 *
 * Buradaki profil Firebase Authentication hesabını temsil eder.
 * Room'daki "İnsanlarım" veya anı kayıtlarından bağımsızdır.
 */
@Composable
internal fun CloudProfileSection(
    state: CloudAccountUiState,
    onSendVerificationEmail: () -> Unit,
    onRefreshProfile: () -> Unit,
    onUpdateDisplayName: (String) -> Unit,
    onDismissProfileMessage: () -> Unit
) {
    val account = state.account ?: return
    var showNameDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(account.displayName.orEmpty()) }

    // Başka hesapla giriş yapılınca eski hesaba ait açık formu kapat.
    LaunchedEffect(account.uid) {
        showNameDialog = false
        nameInput = account.displayName.orEmpty()
    }
    LaunchedEffect(account.displayName) {
        if (!showNameDialog) nameInput = account.displayName.orEmpty()
    }

    val enabled = !state.isSubmitting && !state.isArchiveBusy && !state.isProfileBusy

    HorizontalDivider()
    Text("Hesap profilim", style = MaterialTheme.typography.titleMedium)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                account.displayName?.ifBlank { null } ?: "İsimsiz hesap",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                account.email ?: "E-posta bulunamadı",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    OutlinedButton(
        onClick = { nameInput = account.displayName.orEmpty(); showNameDialog = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Görünen adımı düzenle") }

    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("E-posta doğrulaması", style = MaterialTheme.typography.titleSmall)
    }
    Text(
        if (account.isEmailVerified) "E-posta adresin doğrulandı."
        else "E-posta henüz doğrulanmadı. Adresine bir doğrulama bağlantısı gönderebilirsin.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (!account.isEmailVerified) {
        OutlinedButton(
            onClick = onSendVerificationEmail,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Doğrulama e-postası gönder") }
    }
    OutlinedButton(
        onClick = onRefreshProfile,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Text("  E-postamı doğruladım, kontrol et")
    }

    if (state.isProfileBusy) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("Hesap işlemi sürüyor...", style = MaterialTheme.typography.bodySmall)
        }
    }
    state.profileMessage?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onDismissProfileMessage) { Text("Kapat") }
    }
    state.profileError?.let { error ->
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onDismissProfileMessage) { Text("Kapat") }
    }

    HorizontalDivider()
    Text("Yerel anılarım ve bulut hesabı", style = MaterialTheme.typography.titleSmall)
    // Tek Room dosyası korunur ancak en dış navigasyon farklı hesabın
    // günlük ekranlarına erişmesini engeller. Bu fiziksel veritabanı ayrımı değildir.
    val (ownershipInfo, warning) = when (state.localArchiveOwnerState) {
        CloudLocalArchiveOwnerState.NOT_BOUND ->
            "Bu cihazdaki arşiv henüz bir hesaba bağlanmadı. İlk girişte açık onayla bağlayabilirsin." to true
        CloudLocalArchiveOwnerState.CURRENT_ACCOUNT ->
            "Bu cihazdaki arşiv, açık bulut hesabına bağlı. Şifreli kopya yalnızca bu hesaba gönderilebilir." to false
        CloudLocalArchiveOwnerState.OTHER_ACCOUNT ->
            "Bu cihazın arşivi başka bir hesaba bağlı. Bu durumda günlük ekranları kilitlenir ve bulut yüklemesi engellenir." to true
    }
    Text(
        ownershipInfo,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (warning) FontWeight.Medium else FontWeight.Normal,
        color = if (state.localArchiveOwnerState == CloudLocalArchiveOwnerState.OTHER_ACCOUNT)
            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        "Otomatik eşitleme kapalı. Hesap değişiminde arşiv ekranları korunur; Room dosyaları henüz hesaplara ayrılmadığından cihaz kilidini de etkinleştir.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { if (enabled) showNameDialog = false },
            title = { Text("Görünen adını düzenle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bu ad yalnızca bulut hesabında görünür; anıların içeriği değişmez.")
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { if (it.length <= 40) nameInput = it },
                        label = { Text("Görünen ad (2–40 karakter)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onUpdateDisplayName(nameInput); showNameDialog = false },
                    enabled = enabled && nameInput.trim().length in 2..40
                ) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }, enabled = enabled) {
                    Text("Vazgeç")
                }
            }
        )
    }
}
