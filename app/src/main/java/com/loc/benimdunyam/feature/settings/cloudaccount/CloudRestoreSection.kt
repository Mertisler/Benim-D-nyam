package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/**
 * Buluttaki şifreli arşivi önizleme ve boş cihaza geri yükleme arayüzü.
 *
 * Güvenlik notu:
 * - Kullanıcının arşiv şifresini kalıcı olarak saklamıyoruz.
 * - Bu bileşen Room'a doğrudan veri yazmaz; işlemleri ViewModel'e bildirir.
 * - Mevcut cihazda kayıt varsa geri yükleme ViewModel/repository tarafında engellenmelidir.
 */
@Composable
internal fun CloudRestoreSection(
    state: CloudAccountUiState,
    onPreview: (String) -> Unit,
    onImport: (String) -> Unit,
    onClearPreview: () -> Unit
) {
    // Hangi onay penceresinin açık olduğunu yalnızca ekran belleğinde tutuyoruz.
    var dialog by remember { mutableStateOf<String?>(null) }
    // Şifreyi rememberSaveable ile saklamıyoruz: Activity yeniden oluşunca korunmamalı.
    var passphrase by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(false) }

    fun closeDialog() {
        dialog = null
        passphrase = ""
        accepted = false
    }

    // Hesap değişirse başka hesaba ait açık bir şifre diyalogunu kapatıyoruz.
    LaunchedEffect(state.account?.uid) { closeDialog() }

    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Buluttan güvenli geri yükleme", style = MaterialTheme.typography.titleMedium)
    }
    Text(
        "Şifreli bulut kopyasını önce doğrulayıp önizle. Bu sürüm yalnızca tüm yerel " +
                "veritabanları boşsa içe aktarır; cihazdaki kayıtları silmez veya birleştirmez.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(
        onClick = { dialog = "preview" },
        enabled = !state.isArchiveBusy && !state.isSubmitting,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Geri yüklemeyi önizle") }

    // Önizleme yalnızca doğrulama tamamlandıktan sonra ViewModel'den gelir.
    state.restorePreview?.let { preview ->
        val dateText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(preview.archive.createdAtMillis))
        Text("Doğrulanmış kopya: $dateText", style = MaterialTheme.typography.labelLarge)
        Text(
            "Bulut: ${preview.archive.journalCount} anı · ${preview.archive.peopleCount} kişi · " +
                    "${preview.archive.seasonCount} dönem · ${preview.archive.episodeCount} bölüm · " +
                    "${preview.archive.moodCount} ruh hali",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Cihazda mevcut kayıt: ${preview.localRecordCount}",
            style = MaterialTheme.typography.bodyMedium
        )
        // Yalnızca gerçekten boş bir cihazda içe aktarma onayı sunulur.
        if (preview.canImport) {
            Text(
                "Bu cihazın yerel arşivi boş görünüyor. İçe aktarmadan hemen önce tekrar " +
                        "kontrol edilecek. Fotoğraflar ve kapak görselleri bu arşivde yok.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Button(
                onClick = { dialog = "import" },
                enabled = !state.isArchiveBusy && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Onayla ve boş cihaza içe aktar") }
        } else {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    preview.blockedReason ?: "Cihazda kayıtlar var. İçe aktarma durduruldu.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        TextButton(onClick = onClearPreview, enabled = !state.isArchiveBusy) {
            Text("Önizlemeyi kapat")
        }
    }

    if (dialog != null) {
        val importing = dialog == "import"
        AlertDialog(
            onDismissRequest = ::closeDialog,
            icon = { Icon(if (importing) Icons.Default.CloudDownload else Icons.Default.Lock, contentDescription = null) },
            title = { Text(if (importing) "Geri yüklemeyi onayla" else "Bulut kopyasını önizle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (importing)
                            "Arşiv yeniden indirilip doğrulanır. Veriler yalnızca cihazdaki bütün " +
                                    "yerel tablolar boşsa eklenir. Mevcut kayıt silinmez."
                        else
                            "En son bulut arşivi indirilip şifresi, dosya bütünlüğü ve kayıt " +
                                    "ilişkileri kontrol edilir. Bu işlem hiçbir yerel veriyi değiştirmez.",
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
                    if (importing) {
                        Row(verticalAlignment = Alignment.Top) {
                            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Cihazım boş. Fotoğrafların aktarılmayacağını, " +
                                        "bu işlemin otomatik eşitleme olmadığını anladım.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Gerçek şifre doğrulaması ve yeniden kontrol repository katmanındadır.
                        if (importing) onImport(passphrase) else onPreview(passphrase)
                        closeDialog()
                    },
                    enabled = !state.isArchiveBusy && passphrase.length in 12..128 && (!importing || accepted)
                ) { Text(if (importing) "İçe aktar" else "Şifreyi doğrula") }
            },
            dismissButton = { TextButton(onClick = ::closeDialog) { Text("İptal") } }
        )
    }
}
