package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Byte raporunu insan tarafından okunabilir hâle getirir. Para/maliyet tahmini değildir. */
private fun byteLabel(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val mib = bytes / 1024.0 / 1024.0
    return String.format(Locale.getDefault(), "%.2f MiB", mib)
}

/**
 * Ayarlar → Bulut Hesabım ekranına eklenir. Kullanıcı taramayı kendisi başlatır.
 * mediaPrefix tam olarak Faz 2G.1'in kullandığı kullanıcıya özel Storage klasörü
 * olmalı; tahmin edilen klasör, tahmin edilen kullanım rakamı üretir.
 */
@Composable
fun CloudMediaManagementSection(
    uid: String,
    mediaPrefix: String,
    viewModel: CloudMediaManagementViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uid, mediaPrefix) { viewModel.setScope(uid, mediaPrefix) }
    val ownInventory = state.inventory?.takeIf { it.uid == uid && it.mediaPrefix == mediaPrefix }
    // Compose state yalnızca kullanıcı yazarken bulunur; plan hazırlanınca hemen temizlenir.
    var reviewPassword by remember(uid) { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Medya Yönetim Merkezi", style = MaterialTheme.typography.titleMedium)
            Text(
                "Firebase Storage kullanımını sunucudan denetle. Dosya içerikleri açılmaz; " +
                        "yalnızca ad, boyut ve sürüm metadata'sı incelenir.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Kapsam: ${mediaPrefix.ifBlank { "Medya klasörü henüz bağlanmadı" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { viewModel.inspect(uid, mediaPrefix) },
                enabled = !state.loading && mediaPrefix.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.loading) "İşlem sürüyor..." else "Bulut kullanımını tara")
            }
            if (state.loading) CircularProgressIndicator()
            ownInventory?.let { snapshot ->
                HorizontalDivider()
                Text("Bulutta bulunan dosya: ${snapshot.objectCount}", fontWeight = FontWeight.SemiBold)
                Text("Toplam depolama: ${byteLabel(snapshot.totalBytes)}", fontWeight = FontWeight.SemiBold)
                Text(
                    "Şifreli silme işareti: " +
                            if (snapshot.markerCountTruncated) "En az ${snapshot.cloudDeletionMarkerCount}" else "${snapshot.cloudDeletionMarkerCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Tarama: ${DateFormat.getDateTimeInstance().format(Date(snapshot.generatedAtMs))}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Not: Silme işareti sayısı, silinebilecek dosya sayısı değildir. " +
                            "Şifreli kayıtların fotoğraf kimliğiyle doğrulanması gerekir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                snapshot.objects.take(6).forEach { obj ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = obj.fullPath.substringAfterLast('/'),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(byteLabel(obj.bytes), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (snapshot.objectCount > 6) {
                    Text("… ve ${snapshot.objectCount - 6} dosya daha", style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
                Text("Güvenli temizleme", style = MaterialTheme.typography.titleSmall)
                if (!state.verifiedCleanupServiceReady) {
                    Text(
                        "Fiziksel silme KİLİTLİ. Sunucu henüz etkin değil, App Check başarısız " +
                                "veya Firebase projesinin güvenlik kuralları doğrulanmadı. " +
                                "Depolama raporlaması çalışmaya devam eder; görseller korunur.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (state.verifiedCleanupServiceReady) {
                    OutlinedTextField(
                        value = reviewPassword,
                        onValueChange = { reviewPassword = it.take(128) },
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Medya aktarım şifresi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = {
                        viewModel.prepare(reviewPassword)
                        reviewPassword = ""
                    },
                    enabled = !state.loading && state.verifiedCleanupServiceReady &&
                            reviewPassword.length in 12..128,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Şifreli kanıtları doğrula ve adayları incele")
                }
                Text(
                    "Yalnızca yayınlanmış ve silme işareti bulunan anı fotoğrafları incelenir. " +
                            "Bir işlemde en fazla 3 fotoğraf seçilir; diğer medya türleri asla temizlenmez.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            state.status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }

    state.pendingPlan?.takeIf { it.uid == uid && it.mediaPrefix == mediaPrefix }?.let { plan ->
        // Yeni plan oluştuğunda önceki onayın aynı kalmaması için key kullanılır.
        key(plan.createdAtMs, plan.uid, plan.objects.size) {
            var checked by remember { mutableStateOf(false) }
            var typed by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = viewModel::dismissPlan,
                title = { Text("Kalıcı bulut temizleme onayı") },
                text = {
                    Column(
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${plan.objects.size} şifreli nesne, toplam ${byteLabel(plan.reclaimableBytes)}. " +
                                    "Bu işlem geri alınamaz. Yerel fotoğraflar silinmez; " +
                                    "ancak başka cihazlar için bulut kopyası artık bulunmayabilir."
                        )
                        plan.objects.take(6).forEach {
                            Text(it.fullPath, style = MaterialTheme.typography.bodySmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (plan.objects.size > 6) Text("Ve ${plan.objects.size - 6} nesne daha")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = { checked = it })
                            Text("Dosyaları ve sonuçlarını inceledim; kalıcı silmeyi onaylıyorum.")
                        }
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { typed = it },
                            label = { Text("Onaylamak için TEMİZLE yaz") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissPlan) { Text("Vazgeç") }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.executeApproved(uid, checked, typed) },
                        enabled = checked && typed == "TEMİZLE" && !state.loading
                    ) { Text("Onayla ve güvenli temizle") }
                }
            )
        }
    }
}
