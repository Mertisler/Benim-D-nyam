package com.loc.benimdunyam.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.DateFormat
import java.util.Date

private enum class MediaPasswordAction { EXPORT, INSPECT, RESTORE }

/**
 * Faz 2I.3: Ayarlar'da ayrı medya arşivi. Mevcut .bdbackup ekranına müdahale etmez.
 * SAF belge seçimi kullanılır; ek dosya/galeri izni gerekmez.
 */
@Composable
fun JournalMediaBackupSection(
    viewModel: JournalMediaBackupViewModel = hiltViewModel()
) {
    val ui = viewModel.state
    var passwordAction by remember { mutableStateOf<MediaPasswordAction?>(null) }
    var password by remember { mutableStateOf("") }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var explicitConsent by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) { pendingUri = uri; passwordAction = MediaPasswordAction.EXPORT }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingUri = uri; passwordAction = MediaPasswordAction.INSPECT }
    }

    BackHandler(enabled = ui.busy) { /* ZIP aktarımı sürerken geri çıkış engellenir. */ }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Security, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Text("Anı Fotoğraflarımın Yedeği", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Mevcut .bdbackup günlükleri ve kişi/dönem kapaklarını içerir; " +
                        "anıların içindeki fotoğraflar için ayrıca .bdmedia oluştur. " +
                        "Tam bir yerel yedek için iki dosyayı birlikte sakla.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = {
                exportLauncher.launch("benim_dunyam_ani_fotograflari.bdmedia")
            }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Şifreli Fotoğraf Yedeği Oluştur")
            }
            OutlinedButton(onClick = {
                viewModel.clearPreview()
                importLauncher.launch(arrayOf("*/*"))
            }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Fotoğraf Yedeğini İncele")
            }
            if (ui.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Şifreli arşiv doğrulanıyor...", style = MaterialTheme.typography.bodySmall)
                }
            }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall) }
            ui.status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            ui.preview?.let { preview ->
                Text("Yedek önizlemesi", style = MaterialTheme.typography.titleSmall)
                Text("Tarih: ${DateFormat.getDateTimeInstance().format(Date(preview.createdAtMillis))}")
                Text("${preview.journalCount} anı · ${preview.archivePhotoCount} fotoğraf")
                Text("Eklenecek: ${preview.restorePhotoCount} · Zaten mevcut: ${preview.alreadyPresentCount}")
                Text("Silme işareti nedeniyle atlanacak: ${preview.blockedByDeletionCount}")
                Text("Fotoğrafların toplamı: ${preview.mediaBytes / (1024 * 1024)} MB (yaklaşık)")
                Text("Bu işlem mevcut fotoğrafları ve anıları silmez; yalnızca eksikleri ekler.",
                    style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        explicitConsent = false; confirmation = ""
                        password = ""; passwordAction = MediaPasswordAction.RESTORE
                    },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Önizlemeyi Onayla ve Eksikleri Geri Yükle") }
                TextButton(onClick = viewModel::clearPreview, enabled = !ui.busy) {
                    Text("Önizlemeyi İptal Et")
                }
            }
            Text("Spark ücretsiz modda çalışır. Firebase Storage'a dosya gönderilmez. " +
                    "Taslak fotoğraflar bu pakete dahil değildir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    passwordAction?.let { action ->
        AlertDialog(
            onDismissRequest = {
                if (!ui.busy) { passwordAction = null; password = ""; pendingUri = null }
            },
            title = { Text(when (action) {
                MediaPasswordAction.EXPORT -> "Medya yedeği parolası"
                MediaPasswordAction.INSPECT -> "Medya yedeğini doğrula"
                MediaPasswordAction.RESTORE -> "Geri yüklemeyi onayla"
            }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (action == MediaPasswordAction.EXPORT)
                        "En az 12 karakterlik güçlü bir parola belirle. Parola unutulursa arşiv kurtarılamaz."
                    else "Seçtiğin medya yedeğinin parolasını yeniden gir.")
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("Yedek parolası") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (action == MediaPasswordAction.RESTORE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = explicitConsent, onCheckedChange = { explicitConsent = it })
                            Text("Dosyaları inceledim, eksik fotoğrafların eklenmesini onaylıyorum.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedTextField(
                            value = confirmation, onValueChange = { confirmation = it },
                            label = { Text("GERİ YÜKLE yaz") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !ui.busy && password.length >= (if (action == MediaPasswordAction.EXPORT) 12 else 1) &&
                            (action != MediaPasswordAction.RESTORE ||
                                    (explicitConsent && confirmation == "GERİ YÜKLE")),
                    onClick = {
                        when (action) {
                            MediaPasswordAction.EXPORT -> pendingUri?.let { viewModel.export(it, password) }
                            MediaPasswordAction.INSPECT -> pendingUri?.let { viewModel.inspect(it, password) }
                            MediaPasswordAction.RESTORE -> viewModel.restore(password)
                        }
                        password = ""; pendingUri = null; passwordAction = null
                        explicitConsent = false; confirmation = ""
                    }
                ) { Text(if (action == MediaPasswordAction.RESTORE) "Geri Yükle" else "Devam Et") }
            },
            dismissButton = {
                TextButton(onClick = {
                    passwordAction = null; password = ""; pendingUri = null
                    explicitConsent = false; confirmation = ""
                }) { Text("Vazgeç") }
            }
        )
    }
}
