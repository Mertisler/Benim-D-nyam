package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Faz 2I.1: Otomatik gönderim korunur; tam bulut kontrolü Spark kotasına duyarlı. */
@Composable
internal fun AutomaticJournalSyncSection(
    uid: String,
    viewModel: AutomaticJournalSyncViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var accepted by remember(uid) { mutableStateOf(false) }
    LaunchedEffect(uid) { viewModel.refresh(uid) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Otomatik anı eşitleme",
                style = MaterialTheme.typography.titleMedium)
            Text(
                "WorkManager yaklaşık 15 dakikalık aralıklarla BEKLEYEN şifreli anıları " +
                        "gönderebilir. Tam bulut indirme kontrolü, son başarılı taramadan " +
                        "en erken 6 saat sonra yapılır. Başka cihazdaki yeni anılar bu " +
                        "nedenle daha geç görünebilir. Zamanlama kesin değildir.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Fotoğraflar henüz aktarılmaz. Mevcut anılar otomatik değiştirilmez; " +
                        "uzaktan silme uygulanmaz. Eksik ilişki veya çakışma varsa " +
                        "manuel inceleme gerekir.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                when {
                    state.enabled -> "Durum: Eşitleme planı açık"
                    state.devicePermission -> "Durum: Cihaz izni var, otomatik eşitleme kapalı"
                    else -> "Durum: Önce cihaz anahtarına izin verilmeli"
                }, style = MaterialTheme.typography.bodyMedium
            )
            // Bu sayaç Firestore'daki gerçek okuma maliyeti değildir;
            // yalnızca cihazda saklanan başarılı tarama zamanıdır.
            Text(
                "Son başarılı tam bulut kontrolü: " +
                        formatAutoScanTime(state.lastSuccessfulScanAtMillis),
                style = MaterialTheme.typography.bodySmall
            )
            if (state.nextEarliestScanAtMillis > 0L && state.enabled) {
                Text(
                    "Sıradaki otomatik kontrol için en erken: " +
                            formatAutoScanTime(state.nextEarliestScanAtMillis),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.manualScanPending && state.enabled) {
                Text(
                    "İstediğin manuel kontrol sırada; 6 saat sınırına takılmaz.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!state.enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Text(
                        "Cihazımdaki bekleyen anı olaylarının otomatik şifreli gönderilmesini " +
                                "ve çakışmasız yeni anıların otomatik eklenmesini kabul ediyorum.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = { viewModel.start(uid) },
                    enabled = !state.busy && accepted && state.devicePermission &&
                            state.remoteVerified,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Otomatik eşitlemeyi başlat") }
            } else {
                Button(
                    onClick = { viewModel.runNow(uid) },
                    enabled = !state.busy, modifier = Modifier.fillMaxWidth()
                ) { Text("Şimdi eşitlemeyi sıraya al") }
                OutlinedButton(
                    onClick = { viewModel.stop(uid) },
                    enabled = !state.busy, modifier = Modifier.fillMaxWidth()
                ) { Text("Otomatik eşitlemeyi durdur") }
            }
            OutlinedButton(
                onClick = { viewModel.refresh(uid) },
                enabled = !state.busy, modifier = Modifier.fillMaxWidth()
            ) { Text("İzin ve plan durumunu yenile") }
            if (!state.remoteVerified && state.devicePermission) {
                Text(
                    "Otomatik eşitleme için önce aynı şifreyle bir test anısını manuel " +
                            "gönderip cihaz iznini yeniden doğrulamalısın.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Gösterilen zamanlar yereldir; Firestore kullanımını Firebase Console'daki " +
                        "Usage ekranından inceleyebilirsin.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Kullanıcıya yalnızca okunabilir yerel zaman gösterilir; ağ işlemi yapılmaz. */
private fun formatAutoScanTime(atMillis: Long): String =
    if (atMillis <= 0L) "Henüz yok" else
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("tr-TR"))
            .format(Date(atMillis))
