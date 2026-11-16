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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
 * Canlı veritabanlarını değiştirmeyen, yalnızca hesap bazlı şifreli hazırlık
 * kopyası oluşturulmasını yöneten ekran bölümü.
 *
 * Hazırlığın tamamlanması "hesaplar fiziksel olarak ayrıldı" anlamına GELMEZ.
 */
@Composable
internal fun AccountMigrationSection(
    viewModel: AccountMigrationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var consent by remember { mutableStateOf(false) }

    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Hesaba özel güvenli taşıma hazırlığı", style = MaterialTheme.typography.titleMedium)
    }
    Text(
        "Dört yerel veritabanının kayıtlarını ve uygulama içi görsellerini " +
                "bu Firebase hesabına özel bir alanda şifreli olarak hazırlar. " +
                "Eski kayıtları silmez; hesaplar arası geçişi henüz etkinleştirmez.",
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        "Bu cihazdaki AES-GCM korumalı hazırlık kopyası, başka telefona taşınamaz ve " +
                "ayrı .bdbackup yedeğinin yerine geçmez. Harici URI'ler dahil edilmez.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(
        onClick = viewModel::inspectSnapshot,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Mevcut hazırlık kopyasını doğrula") }
    state.verifiedSnapshot?.let { snapshot ->
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Şifreli hazırlık kopyası doğrulandı", style = MaterialTheme.typography.titleSmall)
                Text("Tarih: ${DateFormat.getDateTimeInstance().format(Date(snapshot.preparedAtMillis))}")
                Text("${snapshot.journalCount} anı · ${snapshot.moodCount} ruh hali · ${snapshot.draftCount} taslak")
                Text("${snapshot.featuredCount} öne çıkan anı · ${snapshot.mediaCount} medya dosyası")
                Text("Durum: Henüz canlı veritabanına geçiş yapılmadı.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (state.verifiedSnapshot == null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !state.isBusy)
            Text(
                "Verilerin bu cihazda hesaba özel, şifreli bir hazırlık kopyasına " +
                        "alınacağını; asıl kayıtların yerinde kalacağını anladım.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(
            onClick = { consent = false; viewModel.prepareSnapshot() },
            enabled = consent && !state.isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isBusy) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
            Text("Şifreli hazırlık kopyası oluştur")
        }
    }
    state.message?.let {
        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }
    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    // Faz 2E.4: Şifreli hazırlığı doğrulandıktan sonra kullanıcı isterse
    // henüz canlıya bağlanmayan dört ayrı Room kopyasını hazırlayabilir.
    AccountShadowSection()
}
