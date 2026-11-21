package com.loc.benimdunyam.feature.settings

import androidx.compose.ui.graphics.Color
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.loc.benimdunyam.feature.settings.components.AppearanceThemePicker

/* Ayarlar ekranının "Arşiv Sağlığı" ve "Yedekleme Provası" kartları (SettingsScreen.kt). */

/** Cihazdaki anı, ilişki, fotoğraf ve SQLite bütünlüğünün salt okunur özeti. */
@Composable
internal fun ArchiveHealthCard(
    uiState: SettingsUiState,
    isBusy: Boolean,
    onCheckHealth: () -> Unit,
    onCleanLeftovers: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Arşiv Sağlığı",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                "Anılarının, kişi bağlantılarının ve " +
                        "fotoğraflarının cihazındaki durumunu kontrol et.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (uiState.isBackupHealthLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Arşiv kontrol ediliyor...")
                }
            } else {
                uiState.backupHealthReport?.let { report ->
                    Text(
                        text = if (report.isHealthy) {
                            "Kayıtlarda belirgin bir sorun bulunamadı."
                        } else {
                            "Bazı kayıtların incelenmesi gerekiyor."
                        },
                        color = if (report.isHealthy) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HorizontalDivider()
                    Text("Günlükler: ${report.journalCount}")
                    Text("Kişiler: ${report.personCount}")
                    Text("Sezonlar: ${report.seasonCount}")
                    Text("Bölümler: ${report.episodeCount}")
                    HorizontalDivider()
                    Text("Eksik fotoğraflar: ${report.missingImages}")
                    Text("Eski URI referansları: ${report.legacyImageReferences}")
                    Text("Güvensiz fotoğraf yolları: ${report.unsafeImagePaths}")
                    Text("Tutarsız veri bağlantıları: ${report.invalidRelations}")
                    Text("Eski geçici dosyalar: ${report.staleTemporaryEntries}")
                    Text(
                        "Kullanılmayan geri yükleme görselleri: " +
                                "${report.orphanRestoreImages}"
                    )
                    if (report.hasDataProblems) {
                        Text(
                            text = "Bazı kayıtlar eksik ya da eski biçimde. " +
                                    "Verileri silmeden önce incele.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // SQLite düzeyindeki salt-okunur bütünlük sonuçları.
            uiState.databaseIntegrityReport?.let { db ->
                HorizontalDivider()
                Text(
                    "SQLite Veritabanı Bütünlüğü",
                    style = MaterialTheme.typography.titleSmall
                )
                val integrityText = if (db.isHealthy) {
                    "SQLite kontrollerinde sorun bulunamadı."
                } else {
                    "SQLite bütünlüğü incelenmeli."
                }
                Text(
                    integrityText,
                    color = if (db.isHealthy) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Yapısal kontrol: " +
                            (if (db.structureOk) "Başarılı" else "Sorun bulundu")
                )
                Text(
                    "Yabancı anahtar ihlalleri: ${db.foreignKeyViolationCount}"
                )
                if (!db.isHealthy) {
                    db.problems.take(3).forEach { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onCheckHealth,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Arşivi Kontrol Et")
            }

            val report = uiState.backupHealthReport
            OutlinedButton(
                onClick = onCleanLeftovers,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy && report != null &&
                        (report.staleTemporaryEntries > 0 ||
                                report.orphanRestoreImages > 0)
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Eski Geçici Dosyaları Temizle")
            }

            Text(
                "Bu kontrol bir yedekleme veya geri yükleme " +
                        "testinin yerini tutmaz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Gerçek verilere dokunmayan şifreli yedekleme provası. */
@Composable
internal fun BackupSelfCheckCard(
    uiState: SettingsUiState,
    isBusy: Boolean,
    onRunSelfCheck: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.FactCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Yedekleme Provası",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                "Anılarını değiştirmeden şifreli bir deneme yedeği " +
                        "oluştur, yeniden aç ve geçici Room " +
                        "veritabanına aktarılabildiğini kontrol et.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (uiState.isBackupSelfCheckLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Şifreli yedek ve Room provası yapılıyor...")
                }
            }

            uiState.backupSelfCheckReport?.let { report ->
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Geçici yedekleme provası başarılı",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text("Günlükler: ${report.journalCount}")
                Text("Kişiler: ${report.personCount}")
                Text("Sezonlar: ${report.seasonCount}")
                Text("Bölümler: ${report.episodeCount}")
                Text("Kişi-anı bağlantıları: ${report.mentionCount}")
                Text("Unutulmaz anılar: ${report.specialMemoryCount}")
                Text("Fotoğraflar: ${report.imageCount}")
                Text("Şifreli yedek boyutu: ${report.encryptedBytes} bayt")
                Text("Geçen süre: ${report.elapsedMillis} ms")
            }

            OutlinedButton(
                onClick = onRunSelfCheck,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy
            ) {
                Icon(Icons.Default.PlayCircleOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Yedekleme Provasını Çalıştır")
            }

            Text(
                "Bu prova gerçek verileri değiştirmez. " +
                        "SAF'a kaydedilen dosyanın başka cihazda " +
                        "geri yüklenmesini garanti etmez. " +
                        "Gerçek geri yüklemeyi ayrı emülatörde doğrula.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
