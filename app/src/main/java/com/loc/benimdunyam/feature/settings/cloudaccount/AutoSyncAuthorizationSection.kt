package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

/**
 * Faz 2F.10: Cihaza özel otomatik eşitleme anahtarı için açık kullanıcı onayı.
 * Bu ekran herhangi bir WorkManager işi planlamaz, Firebase'e günlük YAZMAZ.
 * Parolayı rememberSaveable veya ViewModel UI State içinde tutmayız.
 */
@Composable
internal fun AutoSyncAuthorizationSection(
    uid: String,
    viewModel: AutoSyncAuthorizationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var passphrase by remember(uid) { mutableStateOf("") }
    var repeat by remember(uid) { mutableStateOf("") }
    var accepted by remember(uid) { mutableStateOf(false) }
    var confirmDisable by remember(uid) { mutableStateOf(false) }

    LaunchedEffect(uid) {
        viewModel.setAccount(uid)
        viewModel.refresh(uid)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Otomatik eşitleme için cihaz izni",
                style = MaterialTheme.typography.titleMedium)
            Text(
                "Bu bölüm cihaz anahtarı iznini hazırlar. Otomatik gönderim ve indirme ancak " +
                        "aşağıdaki otomatik eşitleme bölümünde ayrıca onay verip başlatırsan çalışır.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Aktarım şifresi Android Keystore korumasıyla BU CİHAZDA şifreli saklanır. " +
                        "İzin verildikten sonra uygulama bu şifreyi arka planda açabilir. " +
                        "Bu nedenle yalnızca güvendiğin cihazlarda etkinleştir.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Hesaptan çıkış yapmak cihazdaki izni silmez. Kalmasını istemiyorsan " +
                        "çıkış yapmadan önce buradan izni kaldır.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Durum: " + if (state.enabled) "Cihaz anahtarı izni hazır (eşitleme planı durumuna aşağıdan bak)"
                else "İzin kapalı",
                style = MaterialTheme.typography.bodyMedium
            )
            state.grantedAtMillis?.let { time ->
                Text(
                    "Onay tarihi: " + DateFormat.getDateTimeInstance().format(Date(time)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.enabled) {
                Text(
                    if (state.remoteSampleVerified) "Buluttaki bir şifreli olay bu şifreyle doğrulandı."
                    else "Bulutta doğrulanacak örnek olay bulunamadı.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = { confirmDisable = true },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Bu cihazdaki otomatik eşitleme iznini kaldır") }
            } else {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Anı olayları için aktarım şifresi") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = { Text("Şifre tekrar") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = accepted,
                        onCheckedChange = { accepted = it },
                        enabled = !state.busy
                    )
                    Text(
                        "Şifremi cihazda geri çözülebilir şekilde, Android Keystore korumasıyla " +
                                "saklamayı kabul ediyorum. Bu izni istediğim zaman geri alabilirim. " +
                                "Otomatik veri gönderimi henüz başlamayacak.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = {
                        viewModel.enable(uid, passphrase, accepted)
                        passphrase = ""
                        repeat = ""
                        accepted = false
                    },
                    enabled = !state.busy && accepted &&
                            passphrase.length in 12..128 && passphrase == repeat,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cihazda şifreli izni etkinleştir") }
            }
            if (state.busy) CircularProgressIndicator()
            OutlinedButton(
                onClick = { viewModel.refresh(uid) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cihaz izni durumunu yenile") }
            state.info?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDisable) AlertDialog(
        onDismissRequest = { if (!state.busy) confirmDisable = false },
        title = { Text("Cihaz iznini kaldır?") },
        text = {
            Text("Cihazdaki şifreli aktarım şifresi ve Keystore anahtarı silinir. " +
                    "Firestore'daki anıların ve Room kayıtların silinmez.")
        },
        confirmButton = {
            TextButton(onClick = {
                confirmDisable = false
                viewModel.disable(uid)
            }) { Text("İzni kaldır") }
        },
        dismissButton = {
            TextButton(onClick = { confirmDisable = false }) { Text("Vazgeç") }
        }
    )
}
