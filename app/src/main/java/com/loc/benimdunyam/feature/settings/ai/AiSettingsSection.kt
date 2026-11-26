package com.loc.benimdunyam.feature.settings.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.core.DeveloperTools

/**
 * Ayarlar > Yapay zekâ. Varsayılan kapalı; açmak için izin metni onaylanır.
 * Kapatınca cihazdaki yapay zekâ sonuçları silinir.
 */
@Composable
fun AiSettingsSection(viewModel: AiSettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showConsent by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🤖 Yapay zekâ özellikleri", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.enabled) "Açık" else "Kapalı",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.enabled,
                    enabled = !state.busy,
                    onCheckedChange = { on -> if (on) showConsent = true else confirmDisable = true }
                )
            }
            Text(
                "Anılarına soru sorma, dönem özetleri ve kişi hikâyeleri gibi özellikler için anılarının " +
                    "gerekli kısımları Claude yapay zekâsına gönderilir. Varsayılan olarak kapalıdır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!state.hasAccount) {
                Text(
                    "Yapay zekâ için bulut hesabınla giriş yapman gerekir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (state.enabled) {
                ArchiveAnalysisBlock(state, viewModel)
            }
            if (state.cachedCount > 0) {
                Text(
                    "Bu cihazda kayıtlı ${state.cachedCount} yapay zekâ sonucu var.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            // Geliştirici: gerçek sunucuya uydurma bir anıyla istek (release'te yok).
            if (DeveloperTools.ENABLED && state.enabled) {
                OutlinedButton(onClick = viewModel::runConnectionTest, enabled = !state.busy) {
                    Text(if (state.busy) "Deneniyor…" else "Bağlantıyı dene (uydurma anı)")
                }
                state.testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text("Yapay zekâ özelliklerini aç") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConsentLine("Bir yapay zekâ özelliğini kullandığında, o iş için gereken anıların başlığı, metni, tarihi ve etiketli kişi adları Benim Dünyam sunucusu üzerinden Anthropic'in Claude modeline gönderilir.")
                    ConsentLine("Bulut eşitlemesindeki uçtan uca şifreleme bu gönderim için geçerli değildir: anı, cevabın hazırlanması için şifresiz işlenir.")
                    ConsentLine("Sunucumuz anı metnini ve cevapları kaydetmez; yalnızca kullanım miktarını sayar. Anthropic, API ile gönderilen verileri model eğitiminde kullanmaz; kötüye kullanım denetimi için sınırlı süre saklayabilir.")
                    ConsentLine("Cevaplar yalnızca bu cihazda saklanır. İstediğin zaman kapatabilirsin; kapatınca bu cihazdaki yapay zekâ sonuçları da silinir.")
                    ConsentLine("Bulut hesabıyla giriş ve internet bağlantısı gerekir.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showConsent = false
                    viewModel.enable()
                }) { Text("Kabul ediyorum, aç") }
            },
            dismissButton = { TextButton(onClick = { showConsent = false }) { Text("Vazgeç") } }
        )
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Yapay zekâ kapatılsın mı?") },
            text = { Text("Bu cihazdaki tüm yapay zekâ sonuçları (özetler, cevaplar, analizler) silinir. Anıların etkilenmez.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    viewModel.disableAndClear()
                }) { Text("Kapat ve sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun ConsentLine(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ArchiveAnalysisBlock(state: AiSettingsUiState, viewModel: AiSettingsViewModel) {
    val p = state.progress ?: return
    androidx.compose.material3.HorizontalDivider()
    Text("Anı analizi", style = MaterialTheme.typography.titleSmall)
    Text(
        "${p.analyzed} / ${p.totalJournals} anı analiz edildi. Analiz; konuları, yerleri, olayları ve " +
            "seçilmemiş ruh halleri için öneriyi çıkarır. Sonuçlar Hikâyem'de ve anı detayında görünür.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (state.totalPending(p) > 0 || state.analysisRunning) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { if (p.totalJournals == 0) 0f else p.analyzed.toFloat() / p.totalJournals },
            modifier = Modifier.fillMaxWidth()
        )
    }
    when {
        state.archiveRequested && (p.pendingArchive > 0 || p.pendingAutomatic > 0) -> {
            Text(
                if (state.analysisRunning) "Arka planda sürüyor… (internet bağlantısı gerekir)"
                else "Sırada ${p.pendingArchive + p.pendingAutomatic} anı var; bağlantı gelince sürecek.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = viewModel::stopArchiveAnalysis) { Text("Durdur") }
        }
        p.pendingArchive > 0 -> {
            Text(
                "Yapay zekâyı açmadan önce yazdığın ${p.pendingArchive} anı henüz analiz edilmedi. " +
                    "Bunlar yalnızca sen başlatırsan işlenir.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = viewModel::startArchiveAnalysis, enabled = state.hasAccount) {
                Text("Arşivimi analiz et (${p.pendingArchive} anı)")
            }
        }
        p.pendingAutomatic > 0 -> Text(
            "${p.pendingAutomatic} yeni ya da değişen anı sırada" + if (state.analysisRunning) "; analiz ediliyor…" else ".",
            style = MaterialTheme.typography.bodySmall
        )
        else -> Text("Tüm anıların analiz edildi. ✓", style = MaterialTheme.typography.bodySmall)
    }
}

private fun AiSettingsUiState.totalPending(p: com.loc.benimdunyam.data.ai.insight.AnalysisProgress) =
    p.pendingAutomatic + if (archiveRequested) p.pendingArchive else 0
