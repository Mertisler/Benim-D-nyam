package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

/** Faz 2G.1: Medya aktarımı her seferinde kullanıcı onayı ve şifre gerektirir. */
@Composable
internal fun EncryptedMediaSyncSection(
    uid: String,
    viewModel: EncryptedMediaSyncViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var passphrase by remember(uid) { mutableStateOf("") }
    var accepted by remember(uid) { mutableStateOf(false) }
    LaunchedEffect(uid) { viewModel.select(uid) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Şifreli görsel aktarımı · Faz 2G.1", style = MaterialTheme.typography.titleMedium)
            Text(
                "Anı fotoğraflarını, dönem kapaklarını ve kişi görsellerini AES-GCM ile " +
                        "şifreleyerek Firebase Storage'a yükler. Diğer cihazdaki eksik görseller " +
                        "aynı şifreyle ve onayınla tamamlanabilir.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Firebase Storage, Blaze (kullandıkça öde) planı gerektirir. " +
                        "İşlem başına en fazla 3 görsel aktarılır. Taslak fotoğrafları, " +
                        "diğer cihazda henüz anısı/kişisi/dönemi bulunmayan görseller aktarılmaz. " +
                        "Mevcut görseller asla değiştirilmez. Otomatik medya senkronizasyonu kapalı.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.localCount?.let {
                Text("Bu cihazda: ${it.photos} anı fotoğrafı · ${it.seasonCovers} dönem kapağı · " +
                        "${it.peopleImages} kişi görseli", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("Medya şifresi (12–128 karakter)") },
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
                    "Görsellerimin şifreli kopyasının buluta gönderilmesini veya bu cihaza " +
                            "indirilmesini ve Firebase Storage maliyetleri oluşabileceğini onaylıyorum.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val ready = accepted && !state.busy && passphrase.length in 12..128
            Button(
                onClick = {
                    viewModel.transfer(uid, passphrase, upload = true)
                    passphrase = ""
                    accepted = false
                },
                enabled = ready,
                modifier = Modifier.fillMaxWidth()
            ) { Text("En fazla 3 görseli şifreli yükle") }
            OutlinedButton(
                onClick = {
                    viewModel.transfer(uid, passphrase, upload = false)
                    passphrase = ""
                    accepted = false
                },
                enabled = ready,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Eksik görsellerden en fazla 3'ünü indir") }
            OutlinedButton(
                onClick = { viewModel.refresh(uid) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Yerel görsel sayısını yenile") }
            if (state.busy) CircularProgressIndicator()
            state.info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
