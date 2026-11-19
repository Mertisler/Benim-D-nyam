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
import androidx.compose.material3.Text
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
import java.text.DateFormat
import java.util.Date

/**
 * Faz 2G.2: Metin eşitlemesi açık olsa bile görsel eşitlemesi kullanıcı ayrıca
 * onaylamadıkça BAŞLAMAZ. Otomatik işlem sadece ölçülmeyen ağ (Wi-Fi) kullanır.
 */
@Composable
internal fun AutomaticMediaSyncSection(
    uid: String,
    viewModel: AutomaticMediaSyncViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var consent by remember(uid) { mutableStateOf(false) }
    LaunchedEffect(uid) { viewModel.refresh(uid) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Otomatik şifreli görsel eşitlemesi · Faz 2G.2",
                style = MaterialTheme.typography.titleMedium)
            Text(
                "Metin eşitlemesinden ayrı izin ister. Yalnızca Wi-Fi'da, " +
                        "pil yeterliyken en erken 24 saatlik aralıklarla çalışır. " +
                        "Bir çalışmada en fazla 3 görsel yüklenir ve 3 görsel indirilir.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Fotoğraf sırası/kapak seçimi şifrelenir. Yerel düzen mevcutsa veya " +
                        "iki farklı bulut düzeni varsa üzerine yazılmaz. " +
                        "Görselleri silme, mevcut kapakları değiştirme ve taslak aktarımı yapılmaz. " +
                        "Firebase Storage kullanımından ücret doğabilir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (state.enabled) "Otomatik medya eşitlemesi: AÇIK"
                else "Otomatik medya eşitlemesi: KAPALI",
                style = MaterialTheme.typography.titleSmall
            )
            if (state.enabled && !state.prerequisitesReady) {
                Text("Metin eşitlemesi veya doğru yerel çalışma alanı hazır değil; medya işi çalışmaz.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (!state.enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = consent,
                        onCheckedChange = { consent = it },
                        enabled = !state.busy
                    )
                    Text(
                        "Şifreli görsellerimin Wi-Fi üzerinden düzenli aktarılmasını " +
                                "ve Firebase Storage kullanımı için ücret oluşabileceğini onaylıyorum.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = {
                        viewModel.enable(uid)
                        consent = false
                    },
                    enabled = consent && !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Otomatik medya eşitlemesini aç") }
            } else {
                OutlinedButton(
                    onClick = { viewModel.runNow(uid) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Wi-Fi'da şimdi eşitle") }
                OutlinedButton(
                    onClick = { viewModel.disable(uid) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Otomatik medya eşitlemesini kapat") }
            }
            if (state.lastRunAtMillis > 0) {
                Text("Son çalışma: " + DateFormat.getDateTimeInstance()
                    .format(Date(state.lastRunAtMillis)), style = MaterialTheme.typography.bodySmall)
            }
            Text(state.lastResult, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { viewModel.refresh(uid) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Eşitleme durumunu yenile") }
            if (state.busy) CircularProgressIndicator()
            state.info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall) }
        }
    }
}
