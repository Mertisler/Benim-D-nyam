package com.loc.benimdunyam.feature.settings.cloudaccount

import com.loc.benimdunyam.data.sync.remote.JournalConflictResolution
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.data.sync.remote.JournalConflictCard
import com.loc.benimdunyam.data.sync.remote.JournalConflictType

/**
 * Faz 2F.8: Çakışma karşılaştırma ve kullanıcı onaylı AYRI KOPYA bölümü.
 * Uzak sürüm hiçbir zaman mevcut anı ID'sine REPLACE edilmez.
 * Silme olayları otomatik uygulanmaz; kendi yazılarını korumak kullanıcıya bırakılır.
 */
@Composable
internal fun JournalConflictSection(
    uid: String,
    viewModel: JournalConflictViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var password by remember(uid) { mutableStateOf("") }
    var expanded by remember(uid) { mutableStateOf(false) }

    LaunchedEffect(uid) {
        password = ""
        expanded = false
        viewModel.selectAccount(uid)
    }
    DisposableEffect(uid) {
        onDispose { viewModel.onExit(uid) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Anı çakışmalarını yönet",
                style = MaterialTheme.typography.titleMedium)
            Text(
                "İki telefonda değişen anılar, buluttan gelen silme işlemleri ve " +
                        "aynı sürümdeki çelişkili olaylar incelenir. Hiçbir anı otomatik silinmez.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "İstersen farklı metni Günlükler'e ayrı bir kopya olarak ekleyebilirsin. " +
                        "Mevcut anı korunur. Kopyada kişi etiketleri, ruh hali, dönem/bölüm " +
                        "ilişkileri ve fotoğraflar henüz taşınmaz; yalnızca başlık, tam metin ve tarih korunur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifreli aktarım şifresi") },
                visualTransformation = PasswordVisualTransformation(),
                enabled = !state.loading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = {
                    viewModel.inspect(uid, password)
                    password = ""
                    expanded = false
                },
                enabled = !state.loading && password.length in 12..128,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Buluttaki çakışmaları denetle") }

            state.report?.let { report ->
                Text("İncelenen olay: ${report.scannedEvents} · Çakışma: ${report.totalConflicts}")
                if (report.totalConflicts == 0) {
                    Text("Bu taramada çakışma bulunamadı. Bu sonuç bütün cihazların " +
                            "eşitlendiği anlamına gelmez.", style = MaterialTheme.typography.bodySmall)
                }
                val cards = if (expanded) report.cards else report.cards.take(5)
                cards.forEach { item ->
                    key(item.remoteDocumentId) {
                        ConflictReviewCard(
                            item = item,
                            enabled = !state.loading,
                            passwordReady = password.length in 12..128,
                            onSaveCopy = { viewModel.preserveCopy(
                                uid, password, item.remoteDocumentId, true
                            ); password = "" },
                            onResolve = { resolution ->
                                viewModel.resolve(uid, password, item.remoteDocumentId, resolution, true)
                                password = ""
                            }
                        )
                    }
                }
                if (report.cards.size > 5) {
                    OutlinedButton(onClick = { expanded = !expanded }, enabled = !state.loading) {
                        Text(if (expanded) "İlk 5 kaydı göster" else "Diğer çakışmaları göster")
                    }
                }
                if (report.hiddenConflictCount > 0) {
                    Text(
                        "${report.hiddenConflictCount} çakışma daha var. İlk 40 kayıt gösteriliyor; " +
                                "diğer kayıtlar için ileride sayfalı liste geliştirilecek.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    Text("Hesap, sürüm ve şifreli olaylar doğrulanıyor...")
                }
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            if (!state.loading && (state.report != null || state.error != null)) {
                OutlinedButton(onClick = {
                    password = ""
                    expanded = false
                    viewModel.clearFor(uid)
                }, modifier = Modifier.fillMaxWidth()) { Text("İncelemeyi temizle") }
            }
        }
    }
}

@Composable
private fun ConflictReviewCard(
    item: JournalConflictCard,
    enabled: Boolean,
    passwordReady: Boolean,
    onSaveCopy: () -> Unit,
    onResolve: (JournalConflictResolution) -> Unit
) {
    var approved by remember(item.remoteDocumentId, item.confirmationKey) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider()
        val description = when (item.type) {
            JournalConflictType.DIFFERENT_TEXT_OR_METADATA ->
                "Yerel anı ile bulut sürümü farklı"
            JournalConflictType.REMOTE_DELETE_LOCAL_PRESENT ->
                "Bulutta silme olayı var; yerel anı korunuyor"
            JournalConflictType.REMOTE_UPSERT_LOCAL_DELETED ->
                "Yerelde silinmiş anı bulutta mevcut"
            JournalConflictType.INCONSISTENT_REMOTE_HEADS ->
                "Aynı bulut sürümünde çelişkili olaylar"
        }
        Text(description, style = MaterialTheme.typography.titleSmall)
        Text("Bulut sürümü: ${item.remoteRevision}" +
                (item.localRevision?.let { " · Yerel sürüm: $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall)
        if (item.localHasPendingChange) {
            Text("Bu anıda gönderilmeyi bekleyen yerel değişiklik var.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
        item.localTitle?.let { Text("Cihazdaki başlık: $it") }
        item.localExcerpt?.let { Text("Cihazdaki metin: $it…",
            style = MaterialTheme.typography.bodySmall) }
        item.remoteTitle?.let { Text("Buluttaki başlık: $it") }
        item.remoteExcerpt?.let { Text("Buluttaki metin: $it…",
            style = MaterialTheme.typography.bodySmall) }
        // Faz C: çakışmayı kapatan iki karar. Hiçbiri bulutta veri silmez.
        if (item.canKeepLocal || item.canAcceptRemote) {
            var resolveApproved by remember(item.remoteDocumentId, item.confirmationKey) {
                mutableStateOf(false)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = resolveApproved, onCheckedChange = { resolveApproved = it },
                    enabled = enabled)
                Text(
                    "Seçtiğim sürümün bu anı için geçerli olmasını onaylıyorum. " +
                            "Diğer sürümün metni bu anıda kalmaz (istersen önce ayrı kopya al).",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (item.canKeepLocal) {
                    OutlinedButton(
                        onClick = { resolveApproved = false; onResolve(JournalConflictResolution.KEEP_LOCAL) },
                        enabled = enabled && resolveApproved && passwordReady,
                        modifier = Modifier.weight(1f)
                    ) { Text("Benimkini koru") }
                }
                if (item.canAcceptRemote) {
                    OutlinedButton(
                        onClick = { resolveApproved = false; onResolve(JournalConflictResolution.ACCEPT_REMOTE) },
                        enabled = enabled && resolveApproved && passwordReady,
                        modifier = Modifier.weight(1f)
                    ) { Text("Buluttakini al") }
                }
            }
        }
        if (item.savedAsSeparateCopy) {
            Text("Bu sürümün ayrı koruma kopyası zaten var.",
                style = MaterialTheme.typography.bodySmall)
        } else if (item.canPreserveCopy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = approved, onCheckedChange = { approved = it },
                    enabled = enabled)
                Text(
                    "Orijinali değiştirmeden ayrı bir metin kopyası oluşturulmasını onaylıyorum.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = { approved = false; onSaveCopy() },
                enabled = enabled && approved && passwordReady,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Onayla ve ayrı kopyayı koru") }
            Text("Kopya için şifreyi yeniden girmen gerekiyor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Bu silme kaydı için cihazda kopyalanabilir bir metin yok. " +
                    "Hiçbir kayıt otomatik silinmeyecek.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
