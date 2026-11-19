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
 * Faz 2F.5: Şifreli ilişki kataloğu oluşturma ve tam olay kapsamını kontrol etme.
 * Bu UI hiçbir içe aktarma, silme veya otomatik eşitleme eylemi sunmaz.
 */
@Composable
internal fun FullSyncReadinessSection(
    uid: String,
    viewModel: FullSyncReadinessViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var password by remember(uid) { mutableStateOf("") }
    var accepted by remember(uid) { mutableStateOf(false) }

    LaunchedEffect(uid) { viewModel.selectAccount(uid) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Veri kapsamı kontrolü", style = MaterialTheme.typography.titleMedium)
            Text(
                "Kişi, dönem, bölüm, ruh hali ve anı bağlantılarını şifreli katalog olarak " +
                        "gönderebilir, buluttaki anı olaylarının tamamını sayfalı inceleyebilirsin.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Bu sürümde en fazla 2.000 olay denetlenir. Fotoğraflar aktarılmaz. " +
                        "Katalog ile olaylar aynı anda güncellenmediği için çakışmalar otomatik çözülmez.",
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = accepted,
                    onCheckedChange = { accepted = it },
                    enabled = !state.busy
                )
                Text(
                    "Kişi ve dönem bilgilerimin şifreli kopyasının bu hesaba " +
                            "yüklenmesini veya sunucudan okunmasını onaylıyorum.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val enabled = accepted && !state.busy && password.length in 12..128
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                onClick = {
                    viewModel.upload(uid, password)
                    password = ""
                    accepted = false
                }
            ) { Text("Şifreli ilişki kataloğu oluştur") }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                onClick = {
                    viewModel.inspect(uid, password)
                    password = ""
                    accepted = false
                }
            ) { Text("Tüm olayların kapsamını denetle") }
            if (state.busy) CircularProgressIndicator()
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.report?.let { report ->
                Text("Okunan bulut olayı: ${report.totalEvents}")
                Text("Farklı anı: ${report.uniqueJournals}")
                Text(
                    "Katalog: ${report.catalogJournals} anı, ${report.catalogPeople} kişi, " +
                            "${report.catalogSeasons} dönem, ${report.catalogEpisodes} bölüm, " +
                            "${report.catalogMoods} ruh hali",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Eksik ilişkili olay: ${report.missingRelationEvents}")
                Text("Katalogdan daha yeni olay: ${report.staleCatalogEvents}")
                Text("Gönderilmeyi bekleyen yerel değişiklik: ${report.localPendingEvents}")
                Text("Yerel anıyla çakışma: ${report.localConflictEvents}")
                Text("Silme çakışması: ${report.remoteDeleteLocalExists}")
                Text("Yalnızca bulutta görünen anı olayı: ${report.remotelyOnlyEvents}")
                Text(
                    "Otomatik birleştirme KAPALI. Sayıların sıfır olması iki " +
                            "cihazın tamamen eşit olduğunu kanıtlamaz.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.report != null || state.error != null || state.message != null) {
                OutlinedButton(onClick = viewModel::clear, modifier = Modifier.fillMaxWidth()) {
                    Text("Sonucu temizle")
                }
            }
        }
    }
}
