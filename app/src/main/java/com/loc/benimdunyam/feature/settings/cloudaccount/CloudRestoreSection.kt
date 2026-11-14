package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.domain.model.CloudRecordDifference
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Faz 2C: salt okunur eşitleme hazırlık paneli.
 * Karşılaştırma sonucu, kullanıcı verilerini değiştirecek hiçbir işlem başlatmaz.
 */
@Composable
internal fun CloudSyncAuditSection(
    state: CloudAccountUiState,
    onInspect: () -> Unit,
    onCompare: (String) -> Unit
) {
    // Şifre hiçbir zaman rememberSaveable ile diske/geri yükleme durumuna yazılmaz.
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    fun closeDialog() {
        showPasswordDialog = false
        passphrase = ""
    }
    LaunchedEffect(state.account?.uid) { closeDialog() }

    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.FactCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Bulut durum denetimi", style = MaterialTheme.typography.titleMedium)
    }
    Text(
        "Cihazındaki kayıtlar ile son şifreli bulut arşivini karşılaştır. " +
                "Bu işlem geri yükleme, silme veya otomatik eşitleme yapmaz.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
    OutlinedButton(
        onClick = onInspect,
        enabled = !state.isArchiveBusy && !state.isSubmitting,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Cihazı ve son bulut kopyasını incele") }

    state.localCoverage?.let { local ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Cihazdaki kayıtlar", style = MaterialTheme.typography.titleSmall)
                Text("${local.journalCount} anı · ${local.peopleCount} kişi · " +
                        "${local.seasonCount} dönem · ${local.episodeCount} bölüm")
                Text("${local.mentionCount} kişi etiketi · ${local.specialMemoryCount} özel anı · " +
                        "${local.moodCount} ruh hali", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Henüz bulut metin arşivine dahil olmayan görseller", fontWeight = FontWeight.SemiBold)
                Text("${local.journalPhotoCount} anı fotoğrafı · ${formatBytes(local.journalPhotoBytes)}")
                Text(
                    "${local.seasonImageReferenceCount} dönem kapağı · " +
                            "${local.personImageReferenceCount} kişi görseli referansı",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Fotoğrafların orijinalleri cihazda kalır. Görseller bu denetimde yüklenmez veya silinmez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    state.latestArchive?.let { archive ->
        Text(
            "Son bulut kopyası: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(archive.createdAtMillis)),
            style = MaterialTheme.typography.labelMedium
        )
        OutlinedButton(
            onClick = { showPasswordDialog = true },
            enabled = !state.isArchiveBusy && !state.isSubmitting,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Şifreyle kayıt farklarını karşılaştır") }
    }

    state.syncAudit?.let { report ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Karşılaştırma sonucu", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (report.textRecordsMatch)
                        "Metin kayıtları eşleşiyor."
                    else "Cihaz ve bulut arşivinde farklı kayıtlar var.",
                    color = if (report.textRecordsMatch) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                differenceRow("Anılar", report.journals)
                differenceRow("Kişiler", report.people)
                differenceRow("Dönemler", report.seasons)
                differenceRow("Bölümler", report.episodes)
                differenceRow("Kişi etiketleri", report.mentions)
                differenceRow("Özel anılar", report.specialMemories)
                differenceRow("Ruh halleri", report.moods)
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Bu bir analizdir, otomatik birleştirme değildir. " +
                                "Fotoğraflar ve kapaklar karşılaştırmaya dahil edilmez. " +
                                "Eski bulut kopyasındaki kayıtlar üzerinde hiçbir değişiklik yapılmadı.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = ::closeDialog,
            title = { Text("Şifreli arşivi karşılaştır") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Bulut arşivi sunucudan okunup şifresi doğrulanacak. " +
                                "Cihaz verilerine ve Firebase belgelerine yazılmayacak.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Arşiv şifresi") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCompare(passphrase)
                        closeDialog()
                    },
                    enabled = passphrase.length in 12..128 && !state.isArchiveBusy
                ) { Text("Karşılaştır") }
            },
            dismissButton = { TextButton(onClick = ::closeDialog) { Text("İptal") } }
        )
    }
}

/**
 * Ayrıntılı isimleri veya günlük içeriklerini ekrana dökmek yerine sadece
 * dört güvenli toplam gösteriyoruz.
 */
@Composable
private fun differenceRow(title: String, difference: CloudRecordDifference) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(
            "Eşleşen ${difference.matching} · Sadece cihazda ${difference.onlyOnDevice} · " +
                    "Sadece bulutta ${difference.onlyInCloud} · Değişen ${difference.different}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatBytes(value: Long): String {
    if (value < 1024L) return "$value B"
    if (value < 1024L * 1024L) return String.format(Locale.getDefault(), "%.1f KB", value / 1024.0)
    return String.format(Locale.getDefault(), "%.1f MB", value / (1024.0 * 1024.0))
}
