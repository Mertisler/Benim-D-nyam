package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Faz 2E.4 ekranı: Şifreli hazırlıktan bağımsız dört Room kopyası oluşturur.
 * Bu işlem canlı veritabanını DEĞİŞTİRMEZ; başarılı olsa bile hesaplar arası
 * otomatik geçiş henüz etkinleşmez.
 */
@Composable
internal fun AccountShadowSection(
    viewModel: AccountShadowViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var approved by remember { mutableStateOf(false) }

    HorizontalDivider()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(Icons.Default.FolderOpen, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary)
        Text("Hesaba özel Room veritabanlarını hazırla", style = MaterialTheme.typography.titleMedium)
    }
    Text(
        "Doğrulanmış şifreli hazırlık kopyasını bu Firebase hesabına özel " +
                "dört ayrı Room veritabanına dönüştürür. Görseller hesap klasörüne kopyalanır. " +
                "Eski anılar silinmez; otomatik hesap geçişi ve senkronizasyon açılmaz.",
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        "Not: Cihazında yeterli boş alan olmalı. İşlem kesilirse yarım kopya " +
                "otomatik silinmez; güvenli kurtarma gerektirdiği için işlem durur.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
    OutlinedButton(
        onClick = viewModel::inspect,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Hesaba özel Room kopyasını kontrol et") }
    if (state.info == null) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Checkbox(checked = approved, onCheckedChange = { approved = it }, enabled = !state.busy)
            Text(
                "Yeni ve henüz aktif olmayan ayrı veritabanları oluşturulacağını; " +
                        "eski kayıtlarımın yerinde kalacağını anladım.",
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
            )
        }
        Button(
            onClick = { approved = false; viewModel.prepare() },
            enabled = approved && !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.busy) CircularProgressIndicator(strokeWidth = 2.dp,
                modifier = Modifier.padding(end = 8.dp))
            Text("Ayrı Room kopyalarını oluştur")
        }
    }
    state.info?.let { result ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Hesaba özel kopya doğrulandı", style = MaterialTheme.typography.titleSmall)
            Text("Tarih: ${DateFormat.getDateTimeInstance().format(Date(result.preparedAtMillis))}")
            Text("${result.journalCount} anı · ${result.personCount} kişi · ${result.seasonCount} dönem · ${result.episodeCount} bölüm")
            Text("${result.moodCount} ruh hali · ${result.draftCount} taslak · ${result.featuredCount} seçili anı · ${result.mediaCount} dosya")
            Text("Aktif veritabanı: Henüz eski Room. Taşıma kopyası kullanılmıyor.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall) }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodySmall) }
}
