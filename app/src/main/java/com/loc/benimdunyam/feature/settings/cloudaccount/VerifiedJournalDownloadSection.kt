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
import com.loc.benimdunyam.data.sync.remote.RemoteJournalPreviewItem
import com.loc.benimdunyam.domain.sync.JournalSyncChangePolicy
import com.loc.benimdunyam.domain.sync.RemoteJournalLocalStatus

/**
 * Faz 2F.4: Buluttan şifreli olay indirme ve ÇAKIŞMA ÖN İNCELEMESİ.
 *
 * Bu bölümde "İçe aktar", "Sil" veya "Birleştir" düğmesi yoktur.
 * Kullanıcının gerçek anılarına zarar vermemek için sonuçlar salt okunurdur.
 */
@Composable
internal fun VerifiedJournalDownloadSection(
    uid: String,
    viewModel: VerifiedJournalDownloadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var passphrase by remember(uid) { mutableStateOf("") }
    var accepted by remember(uid) { mutableStateOf(false) }

    LaunchedEffect(uid) { viewModel.selectAccount(uid) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Buluttaki anıları incele", style = MaterialTheme.typography.titleMedium)
            Text(
                "Firestore'daki en yeni 20 şifreli anı olayını sunucudan okur, " +
                        "aktarım şifresiyle doğrular ve mevcut yerel kayıtlarla karşılaştırır. " +
                        "Günlükleri hiçbir şekilde değiştirmez.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Bu özellik henüz cihazlar arası anı aktarma değildir. " +
                        "Dönem ve kişi kayıtları olaylarla birlikte taşınmadığı için otomatik içe aktarma kapalıdır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("Olayların aktarım şifresi") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = accepted,
                    onCheckedChange = { accepted = it },
                    enabled = !state.loading
                )
                Text(
                    "Şifreli olayları sunucudan okumayı onaylıyorum. " +
                            "Cihazımdaki anıların değişmeyeceğini biliyorum.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = {
                    viewModel.inspect(uid, passphrase)
                    // UI'da parola tutulmaya devam etmesin.
                    passphrase = ""
                    accepted = false
                },
                enabled = !state.loading && accepted && passphrase.length in 12..128,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.loading) "Şifreli olaylar doğrulanıyor..." else "Bulut olaylarını güvenle incele")
            }
            if (state.loading) CircularProgressIndicator()
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.preview?.let { preview ->
                if (preview.hasMore) {
                    Text(
                        "Bulutta daha fazla olay var. Yalnızca en yeni 20 olay incelendi; " +
                                "bu sonuç tüm arşivin eşit olduğunu kanıtlamaz.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (preview.items.isEmpty()) {
                    Text("Bu hesapta henüz şifreli anı olayı bulunmuyor.")
                }
                preview.items.forEach { event ->
                    RemoteEventSummaryCard(event)
                }
                OutlinedButton(
                    onClick = viewModel::clearPreview,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("İnceleme sonuçlarını temizle") }
            }
        }
    }
}

@Composable
private fun RemoteEventSummaryCard(event: RemoteJournalPreviewItem) {
    val status = when (event.localStatus) {
        RemoteJournalLocalStatus.ONLY_REMOTE -> "Yalnızca bulut olayında var; aktarılmadı"
        RemoteJournalLocalStatus.LOCAL_PENDING -> "Cihazda gönderilmemiş değişiklik var"
        RemoteJournalLocalStatus.LOCAL_PRESENT_NEEDS_REVIEW -> "Cihazda aynı anı var; çakışma incelemesi gerekli"
        RemoteJournalLocalStatus.SAME_SYNCED_EVENT -> "Bu sürüme ait yerel gönderim kaydı var"
        RemoteJournalLocalStatus.REMOTE_DELETE_LOCAL_EXISTS -> "Silme çakışması: cihazdaki anı korunuyor"
        RemoteJournalLocalStatus.REMOTE_DELETE_ALREADY_ABSENT -> "Silme olayı: cihazda anı yok"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(event.label, style = MaterialTheme.typography.titleSmall)
            Text(
                "${if (event.operation == JournalSyncChangePolicy.DELETE) "Silme" else "Kayıt/değişiklik"} " +
                        "• Sürüm ${event.revision}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
