package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Hesabım ekranındaki senkronizasyon altyapısı tanılama bölümü.
 *
 * Faz 2F.2 ile anı değişikliklerinin SAYISINI da gösterir.
 * Günlükleri eşitlemez; yalnızca kullanıcı onayıyla tanılama işi oluşturur.
 * UPSERT / DELETE işlemleri yerelde kaydedilir; Faz 2F.3 ile ancak
 * kullanıcı açıkça onayladığında ŞİFRELİ olay olarak gönderilebilir.
 */
@Composable
internal fun SyncFoundationSection(
    uid: String,
    viewModel: SyncFoundationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(uid) { viewModel.refresh(uid) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Eşitleme tanılama", style = MaterialTheme.typography.titleMedium)
            Text(
                "Anı oluşturma, düzenleme ve silme olayları ana Room içinde kalıcı olarak izlenir. " +
                        "Otomatik yükleme/indirme henüz kapalıdır. " +
                        "Anılarımı Eşitle > Anılarımı Gönder sayfasından onay vererek şifreli anı gönderebilirsin.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Buluta gönderilmemiş anı oluşturma/düzenleme: ${state.pendingJournalUpserts}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Buluta gönderilmemiş anı silme: ${state.pendingJournalDeletes}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Bu sayılar yalnızca bu sürümden sonra bu hesapta yapılan işlemlerdir. " +
                        "Önceki anılar için kullanıcı onaylı ilk aktarım daha sonra eklenecek.",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Bekleyen tanılama: ${state.pending}", style = MaterialTheme.typography.bodyMedium)
            Text("Tamamlanan tanılama: ${state.finished}", style = MaterialTheme.typography.bodyMedium)
            Text("WorkManager durumu: ${state.workerState}", style = MaterialTheme.typography.bodySmall)
            if (state.busy) CircularProgressIndicator()
            Button(
                onClick = { viewModel.runDiagnostic(uid) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Güvenli altyapı denetimini çalıştır") }
            OutlinedButton(
                onClick = { viewModel.refresh(uid) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Durumu yenile") }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
