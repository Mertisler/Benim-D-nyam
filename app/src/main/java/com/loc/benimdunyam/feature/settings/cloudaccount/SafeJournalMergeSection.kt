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
 * Faz 2F.6 - Kullanıcı onaylı güvenli birleştirme bölümü.
 *
 * Önizleme ve gerçek içe aktarma AYRI butonlardır.
 * Her iki işlemde şifre tekrar istenir; şifre kalıcı durum olarak saklanmaz.
 */
@Composable
internal fun SafeJournalMergeSection(
    uid: String,
    viewModel: SafeJournalMergeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var password by remember(uid) { mutableStateOf("") }
    var consent by remember(uid) { mutableStateOf(false) }

    LaunchedEffect(uid) {
        password = ""
        consent = false
        viewModel.selectAccount(uid)
        viewModel.refreshPendingMoods(uid)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Anıları güvenle birleştir",
                style = MaterialTheme.typography.titleMedium)
            Text(
                "Buluttaki şifreli anıları inceler ve yalnızca cihazında hiç bulunmayan, " +
                        "ilişkileri doğrulanmış kayıtları eklemene izin verir.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Yeni anıların ruh halleri de korunarak içe aktarılır. " +
                        "Mevcut anılar ve ruh halleri ASLA ezilmez; silme olayları uygulanmaz. " +
                        "Fotoğraflar ve katalogdan yeni olaylar aktarılmaz. " +
                        "Tek onayda en fazla 5 yeni anı eklenir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Aktarım şifresi (12–128 karakter)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = {
                    viewModel.preview(uid, password)
                    password = ""
                    consent = false
                },
                enabled = !state.busy && password.length in 12..128,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Şifreli anıları güvenle önizle") }

            state.report?.let { report ->
                Text("Buluttaki farklı anı: ${report.totalEvents}")
                Text("Eklenebilir yeni anı: ${report.eligible}")
                Text("Yerelde mevcut veya işlem geçmişi var: ${report.existingOrPending}")
                Text("Buluttaki silme olayı: ${report.remoteDeletes}")
                Text("Katalogdan daha yeni olay: ${report.staleCatalog}")
                Text("Eksik veya çakışan ilişkiler: ${report.missingOrConflictingRelations}")
                Text("Eklenebilir anılar arasında ruh hali bulunan: ${report.importableWithMood}")
                Text("Bulut silme olayıyla yerel kayıt çakışması: ${report.remoteDeleteConflicts}")
                Text("Tamamlanmayı bekleyen ruh halleri: ${report.pendingMoodFinishes}")
                if (report.eligible > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = consent,
                            onCheckedChange = { consent = it },
                            enabled = !state.busy
                        )
                        Text(
                            "Yalnızca yeni ve çakışmasız en fazla ${report.batchLimit} anının, " +
                                    "kişi/dönem/bölüm ve ruh haliyle aktarılmasını onaylıyorum. " +
                                    "Mevcut kayıtlar değişmeyecek.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.importMissing(uid, password, consent)
                            password = ""
                            consent = false
                        },
                        enabled = !state.busy && consent && password.length in 12..128 &&
                                report.pendingMoodFinishes == 0,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Onayla ve en fazla 5 yeni anıyı içe aktar") }
                    Text(
                        "İçe aktarma için aynı şifreyi yeniden gir. Sunucu değişirse işlem " +
                                "durdurulacak; yeniden önizleme gerekecek.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.pendingMoodCount > 0) {
                Text(
                    "${state.pendingMoodCount} ruh hali aktarımı yarım kalmış olabilir. " +
                            "Anılar üzerine yazılmadan kurtarma denenebilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(
                    onClick = { viewModel.resumePendingMoods(uid) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Yarım kalan ruh hallerini güvenle tamamla") }
            } else {
                OutlinedButton(
                    onClick = { viewModel.refreshPendingMoods(uid) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Yarım kalan ruh hali işlemlerini kontrol et") }
            }
            if (state.busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(6.dp))
                    Text("Sunucu ve yerel kayıtlar doğrulanıyor...")
                }
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            if (!state.busy && (state.report != null || state.message != null || state.error != null)) {
                OutlinedButton(onClick = {
                    password = ""
                    consent = false
                    viewModel.clear()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Önizlemeyi temizle")
                }
            }
        }
    }
}
