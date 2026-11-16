package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.domain.model.CloudLocalArchiveOwnerState
import java.text.DateFormat
import java.util.Date

/**
 * Fiziksel hesap ayrımı öncesi inceleme ekranı.
 * Bilerek "taşı" düğmesi yoktur: açıktaki Room dosyalarını taşımak güvenli değil.
 */
@Composable
internal fun LocalStoragePreflightSection(
    viewModel: LocalStoragePreflightViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Hesap bazlı depolama hazırlığı", style = MaterialTheme.typography.titleMedium)
    }
    Text(
        "Mevcut anılarını taşımadan, dört yerel veritabanını ve fotoğraf klasörlerini " +
                "kontrol eder. Bu aşamada hesaplar arasında fiziksel veri taşıma kapalıdır.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(
        onClick = viewModel::inspect,
        enabled = !state.loading,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.loading) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
        Text(if (state.loading) "Depolama kontrol ediliyor..." else "Yerel depolama raporunu oluştur")
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    state.report?.let { report ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Veri ayrımı kontrol raporu", style = MaterialTheme.typography.titleSmall)
                Text(
                    DateFormat.getDateTimeInstance().format(Date(report.checkedAtMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val ownerText = when (report.ownerStatus) {
                    CloudLocalArchiveOwnerState.CURRENT_ACCOUNT -> "Açık hesap ve yerel arşiv sahibi eşleşiyor."
                    CloudLocalArchiveOwnerState.NOT_BOUND -> "Yerel arşiv henüz bir hesaba bağlanmamış."
                    CloudLocalArchiveOwnerState.OTHER_ACCOUNT -> "Yerel arşiv başka bir hesaba bağlı."
                }
                Text(ownerText, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                report.parts.forEach { part ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(part.title, style = MaterialTheme.typography.labelMedium)
                            Text(
                                if (!part.exists) "Henüz oluşturulmamış"
                                else "${part.fileCount} dosya · ${formatSize(part.byteCount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (part.hasActiveJournal) {
                            Text("WAL", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                HorizontalDivider()
                Text("Yaklaşık toplam: ${formatSize(report.approximateBytes)}", style = MaterialTheme.typography.labelLarge)
                report.notes.forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Durum: Fiziksel taşıma etkin değil. Verilerin mevcut konumunda güvende kalır.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** Görünümde yalnızca sayısal dosya boyutu kullanır; kişisel içerik göstermez. */
private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}
