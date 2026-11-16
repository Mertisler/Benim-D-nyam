package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Faz 2E.6: Kullanıcıya kopyanın güncel olup olmadığını açıkça gösterir.
 * Tek bir butonla anı taşımaz: önce içerik karşılaştırması, sonra onay gerekir.
 * Bu bölüm sadece eski arşiv sahibinin hesabında görünür.
 */
@Composable
internal fun AccountPromotionSection(viewModel: AccountPromotionViewModel = hiltViewModel()) {
    if (!viewModel.showSection) return
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var agreed by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val promoted = viewModel.isPromoted

    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            if (promoted) "Eski arşive güvenli geri dönüş" else "Hazırlanan arşivi aktif etme",
            style = MaterialTheme.typography.titleMedium
        )
    }
    Text(
        if (promoted)
            "Eski anı veritabanına geri dönmeden önce şu anki hesap alanının değişmediği doğrulanır. " +
                    "Buraya yeni anı yazdıysan geri dönüş engellenir; hiçbir kayıt silinmez."
        else
            "Şifreli kopyayı, eski Room kayıtlarını ve hesaba özel dört Room kopyasını karşılaştırır. " +
                    "Hazırlıktan sonra herhangi bir anı, ruh hali, taslak veya fotoğraf değiştiyse " +
                    "eski veriler korunur ve geçiş yapılmaz.",
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        "Önemli: Geçiş sadece yeniden başlatıldığında uygulanır. Bu, cihaz üzerinde henüz " +
                "tam uçtan uca test edilmemiş bir özelliktir. Önce .bdbackup yedeğini al.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(
        onClick = { agreed = false; viewModel.inspect() },
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.isBusy) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
        Text("Kayıt ve fotoğraf farklarını kontrol et")
    }
    state.report?.let { report ->
        Text(
            report.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (report.canProceed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        if (report.canProceed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it }, enabled = !state.isBusy)
                Text(
                    "Yerel verilerimin yedeğini aldım; eski veritabanının silinmeyeceğini, " +
                            "uygulamayı yeniden başlatmam gerektiğini anladım.",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = { confirm = true },
                enabled = agreed && !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (promoted) "Eski arşivi yeniden seç" else "Doğrulanmış kopyayı aktif seç") }
        }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    state.success?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (promoted) "Eski arşive dönülsün mü?" else "Hesap kopyası aktif edilsin mi?") },
            text = { Text(
                "Hiçbir eski veri silinmeyecek. Yeni çalışma alanı seçimi kaydedilecek ve " +
                        "uygulamayı tamamen kapatıp yeniden açman gerekecek. " +
                        "Kontroller işlem anında yeniden çalıştırılacak."
            ) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    agreed = false
                    if (promoted) viewModel.rollback() else viewModel.activate()
                }) { Text("Onayla") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } }
        )
    }
}
