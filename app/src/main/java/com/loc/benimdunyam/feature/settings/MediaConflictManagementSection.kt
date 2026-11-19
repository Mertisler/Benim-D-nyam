package com.loc.benimdunyam.feature.settings.cloudaccount

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Faz 2G.3: Kullanıcı önce buluttaki durumları görür, ardından seçimini onaylar.
 * Fotoğraf silme işareti gönderimi kalıcı dosya silme işlemi DEĞİLDİR.
 * Parola rememberSaveable kullanılmadan tutulur; her işlemde UI'dan temizlenir.
 */
@Composable
internal fun MediaConflictManagementSection(
    uid: String,
    viewModel: MediaConflictManagementViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var password by remember(uid) { mutableStateOf("") }
    var uploadAccepted by remember(uid) { mutableStateOf(false) }
    var applyAccepted by remember(uid) { mutableStateOf(false) }
    var selectedDocument by remember(uid) { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) { viewModel.reset(uid) }

    fun takePassword(): CharArray = password.toCharArray().also { password = "" }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Medya çakışmaları ve silme yönetimi · Faz 2G.3",
                style = MaterialTheme.typography.titleMedium)
            Text(
                "Bir cihazda sildiğin anı fotoğrafının diğer cihazda yeniden görünmesini " +
                        "engellemek için şifreli silme işareti kullanılır. " +
                        "Buluttaki gerçek görsel dosyaları ve bu cihazdaki fotoğraflar silinmez.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Farklı fotoğraf sırası/kapak seçeneklerini inceleyebilir, " +
                        "tüm fotoğraflar cihazda mevcutsa istediğini onaylayabilirsin. " +
                        "Başka bir cihazdaki mevcut kapak kendiliğinden değiştirilmez.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifreli medya aktarım şifresi") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = {
                    selectedDocument = null
                    applyAccepted = false
                    viewModel.preview(uid, takePassword())
                },
                enabled = !state.busy && password.length in 12..128,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Silme ve kapak çakışmalarını incele") }

            state.deletions?.let { review ->
                HorizontalDivider()
                Text("Bulut silme işaretleri: ${review.remoteMarkers}",
                    style = MaterialTheme.typography.titleSmall)
                Text("Bu cihazda hâlâ bulunan silinmiş fotoğraflar: ${review.stillOnThisDevice}")
                Text("Henüz buluta aktarılmamış yerel silme kayıtları: ${review.pendingLocalMarkers}")
                if (review.stillOnThisDevice > 0) {
                    Text("Bunlar çakışma olarak bırakıldı; otomatik silinmeyecek.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                review.examples.forEach { example ->
                    Text(example, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = uploadAccepted,
                    onCheckedChange = { uploadAccepted = it },
                    enabled = !state.busy
                )
                Text("Sildiğim yayınlanmış fotoğrafların şifreli işaretlerini " +
                        "Firebase'e göndermeyi onaylıyorum. Görseller fiziksel silinmeyecek.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    uploadAccepted = false
                    viewModel.uploadDeletionMarkers(uid, takePassword())
                },
                enabled = !state.busy && uploadAccepted && password.length in 12..128,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Silme işaretlerini şifreli gönder (en fazla 3)") }

            if (state.layoutChoices.isNotEmpty()) {
                HorizontalDivider()
                Text("Buluttaki fotoğraf sırası / kapak seçenekleri",
                    style = MaterialTheme.typography.titleSmall)
                state.layoutChoices.take(35).forEachIndexed { index, choice ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${index + 1}. ${choice.journalTitle.ifBlank { "Anı" }} · " +
                                "${choice.photoCount} fotoğraf · " +
                                "kapak ${choice.coverPosition ?: "seçilmemiş"}",
                            style = MaterialTheme.typography.bodyMedium)
                        Text(choice.note, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = { selectedDocument = choice.documentId; applyAccepted = false },
                            enabled = choice.canApply && !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (selectedDocument == choice.documentId) "Seçildi" else "Bu düzeni seç")
                        }
                    }
                }
                if (state.layoutChoices.size > 35) {
                    Text("İlk 35 seçenek gösteriliyor; diğer kayıtlar için daha dar bir " +
                            "seçim ekranı sonraki sürümde eklenecek.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (selectedDocument != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = applyAccepted,
                        onCheckedChange = { applyAccepted = it },
                        enabled = !state.busy
                    )
                    Text("Seçilen bulut sırası ve kapağının bu cihazdaki yerel " +
                            "fotoğraf düzeninin yerine geçmesini onaylıyorum.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = {
                        val doc = selectedDocument
                        selectedDocument = null
                        applyAccepted = false
                        if (doc != null) viewModel.applyLayout(uid, takePassword(), doc)
                    },
                    enabled = !state.busy && applyAccepted && password.length in 12..128,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Seçtiğim fotoğraf düzenini uygula") }
            }
            if (state.busy) CircularProgressIndicator()
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall) }
        }
    }
}
