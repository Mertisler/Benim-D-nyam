package com.loc.benimdunyam.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.loc.benimdunyam.data.backup.BackupCoverageAssessor
import com.loc.benimdunyam.data.backup.BackupCoverageLevel
import java.text.DateFormat
import java.util.Date

private enum class BackupDocumentType { STANDARD, MEDIA }

/**
 * Faz 2I.4: Kullanıcının seçtiği iki GERÇEK SAF belgesini ayrı ayrı doğrular.
 * Geri yükleme/silme/yeni yedek oluşturma işlemi yoktur; sadece okunur.
 */
@Composable
fun BackupCoverageSection(
    viewModel: BackupCoverageViewModel = hiltViewModel()
) {
    val ui = viewModel.ui
    // Ekran yeniden açıldığında önceki hesaba/dosyaya ait doğrulamayı gösterme.
    LaunchedEffect(Unit) { viewModel.onSectionOpened() }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingType by remember { mutableStateOf<BackupDocumentType?>(null) }
    var password by remember { mutableStateOf("") }

    val standardPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUri = uri
            pendingType = BackupDocumentType.STANDARD
            password = ""
        }
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUri = uri
            pendingType = BackupDocumentType.MEDIA
            password = ""
        }
    }

    // Değerlendirme, sadece o anda okunabilmiş dosyalar ve mevcut yerel sayılarla yapılır.
    // Kullanıcının bilgisayarında/başka cihazda duran yedekler otomatik keşfedilemez.
    val assessment = BackupCoverageAssessor.assess(
        local = ui.local, normal = ui.normal, media = ui.media,
        now = System.currentTimeMillis()
    )

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.FactCheck, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Text("Yedekleme Sağlığı Merkezi", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Gerçek .bdbackup ve .bdmedia dosyalarını ayrı ayrı incele. " +
                        "Bu ekran hiçbir anıyı veya fotoğrafı geri yüklemez.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = viewModel::refreshLocal,
                enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cihazımdaki verileri kontrol et")
            }
            if (ui.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text("Arşiv inceleniyor...", style = MaterialTheme.typography.bodySmall)
                }
            }
            ui.local?.let { local ->
                Text("Cihaz özeti · ${formatDate(local.checkedAtMillis)}", fontWeight = FontWeight.SemiBold)
                Text("${local.journalCount} anı · ${local.personCount} kişi · ${local.seasonCount} sezon · " +
                        "${local.episodeCount} bölüm", style = MaterialTheme.typography.bodySmall)
                Text("${local.publishedPhotoCount} yayınlanmış fotoğraf · ${formatBytes(local.publishedPhotoBytes)} · " +
                        "${local.deletionMarkerCount} silme işareti", style = MaterialTheme.typography.bodySmall)
                if (local.missingPhotoFiles > 0) {
                    Text("${local.missingPhotoFiles} fotoğraf dosyası eksik/geçersiz.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider()
            Text("Son başarılı doğrulama geçmişi", style = MaterialTheme.typography.titleSmall)
            Text(".bdbackup: ${ui.history.normalCheckedAt?.let(::formatDate) ?: "Henüz kaydedilmemiş"}",
                style = MaterialTheme.typography.bodySmall)
            Text(".bdmedia: ${ui.history.mediaCheckedAt?.let(::formatDate) ?: "Henüz kaydedilmemiş"}",
                style = MaterialTheme.typography.bodySmall)
            Text("Bu tarihler yalnızca geçmişte yapılan kontrolleri gösterir; yedeklerin " +
                    "hâlâ cihazda veya başka bir yerde bulunduğu anlamına gelmez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("Seçilmiş dosyaları doğrula", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { standardPicker.launch(arrayOf("*/*")) },
                enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.normal == null) ".bdbackup dosyasını seç ve doğrula" else ".bdbackup dosyasını yeniden doğrula")
            }
            ui.normal?.let { backup ->
                Text("Doğrulandı · ${formatDate(backup.createdAt)} · " +
                        "${backup.journalCount} anı, ${backup.personCount} kişi",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
                Text("Son dosya incelemesi: ${ui.normalInspectedAt?.let(::formatDate) ?: "—"}",
                    style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = { mediaPicker.launch(arrayOf("*/*")) },
                enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.media == null) ".bdmedia dosyasını seç ve doğrula" else ".bdmedia dosyasını yeniden doğrula")
            }
            ui.media?.let { media ->
                Text("Doğrulandı · ${formatDate(media.createdAtMillis)} · " +
                        "${media.archivePhotoCount} fotoğraf, ${formatBytes(media.mediaBytes)}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
                Text("Eksik görülen: ${media.restorePhotoCount} · Silme işaretiyle engellenen: ${media.blockedByDeletionCount}",
                    style = MaterialTheme.typography.bodySmall)
                Text("Son dosya incelemesi: ${ui.mediaInspectedAt?.let(::formatDate) ?: "—"}",
                    style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider()
            val assessmentTitle = when (assessment.level) {
                BackupCoverageLevel.NOT_CHECKED -> "Doğrulama eksik"
                BackupCoverageLevel.REVIEW -> "İncelenmesi gereken farklar var"
                BackupCoverageLevel.FILES_VERIFIED -> "İki dosya ayrı ayrı doğrulandı"
            }
            Text(assessmentTitle, fontWeight = FontWeight.SemiBold,
                color = if (assessment.level == BackupCoverageLevel.REVIEW) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary)
            assessment.notes.forEach { note ->
                Text("• $note", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (assessment.level == BackupCoverageLevel.FILES_VERIFIED) {
                Text("Görünen sayılar uyumlu. Yine de iki arşivin aynı anlık durumu ve bütün dosya kimliklerini " +
                        "kapsadığı kesinleşmez; farklı test cihazında geri yükleme provası yap.",
                    style = MaterialTheme.typography.bodySmall)
            }
            ui.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall) }
            Text("Sonuçlar yalnızca bu açık oturumda tutulur. Yedek dosyası değişirse " +
                    "yeniden seçip doğrula. Parolaları ve belge konumlarını kalıcı saklamıyoruz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = viewModel::clearResults, enabled = !ui.busy) {
                Text("Bu oturumun inceleme sonuçlarını temizle")
            }
        }
    }

    if (pendingUri != null && pendingType != null) {
        AlertDialog(
            onDismissRequest = {
                if (!ui.busy) { pendingUri = null; pendingType = null; password = "" }
            },
            title = { Text("Yedek dosyasını doğrula") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Seçtiğin dosyanın şifresini gir. İşlem sadece okur, geri yükleme yapmaz.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Yedek parolası") },
                        singleLine = true,
                        enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val uri = pendingUri
                    val kind = pendingType
                    val pass = password
                    pendingUri = null; pendingType = null; password = ""
                    if (uri != null) when (kind) {
                        BackupDocumentType.STANDARD -> viewModel.inspectNormal(uri, pass)
                        BackupDocumentType.MEDIA -> viewModel.inspectMedia(uri, pass)
                        null -> Unit
                    }
                }, enabled = password.isNotBlank() && !ui.busy) { Text("Doğrula") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUri = null; pendingType = null; password = "" },
                    enabled = !ui.busy) { Text("Vazgeç") }
            }
        )
    }
}

private fun formatDate(time: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.SHORT, DateFormat.SHORT
).format(Date(time))

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MiB".format(java.util.Locale.getDefault(), bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.1f KiB".format(java.util.Locale.getDefault(), bytes / 1024.0)
    else -> "$bytes bayt"
}
