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

/**
 * Faz 2F.3: Anı değişikliklerini kullanıcı onayıyla şifreli olarak yükleme.
 *
 * Varsayılan davranış YÜKLEMEMEKTİR. Kullanıcı kutuyu işaretler ve şifreyi
 * girerse en fazla beş PENDING olay Firestore'a gönderilir. Bu bölüm hiçbir
 * şekilde kendi kendine çalışan bir WorkManager işi kurmaz.
 *
 * Şifre rememberSaveable içinde tutulmaz; ekran kapanınca korunmaz.
 */
@Composable
internal fun ManualJournalUploadSection(
    uid: String,
    viewModel: ManualJournalUploadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var accepted by remember(uid) { mutableStateOf(false) }
    var passphrase by remember(uid) { mutableStateOf("") }

    LaunchedEffect(uid) { viewModel.refresh(uid) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Şifreli anı gönderimi", style = MaterialTheme.typography.titleMedium)
            Text(
                // Faz 2F.9 ile ilk aktarım için hazırlanan eski anılar da PENDING olabilir.
                "Bu hesapta değişen veya ilk aktarım için hazırlanmış anıların bekleyen " +
                        "işlemleri gönderilir. Eski anılar yalnızca açık onayla sıraya alınır. " +
                        "Fotoğraflar bu bölümde gönderilmez; kişiler ve ruh halleri " +
                        "ayrı şifreli ilişki kataloğuyla aktarılır.",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Bekleyen anı işlemi: ${state.pending}")
            Text(
                "Gönderilen belgeler AES-GCM ile şifrelenir. Sunucuda işlem türü, sürüm ve " +
                        "hesap kimliği gibi sınırlı metadata görülebilir. " +
                        "Otomatik eşitleme açıksa bu işlemi elle yapman gerekmez.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("Aktarım şifresi (12–128 karakter)") },
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
                    "En fazla 5 bekleyen anı olayının şifreli olarak Firebase hesabıma " +
                            "gönderilmesine onay veriyorum.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
            if (state.busy) CircularProgressIndicator()
            Button(
                onClick = {
                    viewModel.upload(uid, passphrase)
                    passphrase = ""
                    accepted = false
                },
                enabled = !state.busy && state.pending > 0 && accepted && passphrase.length in 12..128,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Onayla ve şifreli olayları gönder") }
            OutlinedButton(
                onClick = { viewModel.refresh(uid) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Bekleyen işlemleri yenile") }
            state.status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}
