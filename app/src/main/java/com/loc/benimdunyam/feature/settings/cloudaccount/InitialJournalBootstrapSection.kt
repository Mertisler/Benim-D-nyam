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
 * Faz 2F.9 - Kullanıcı onaylı ilk aktarım alanı.
 *
 * Hazırlama, yalnızca yerel UPSERT olaylarını ekler; buluta GÖNDERMEZ.
 * İkinci onay sırasında ilişki kataloğu ve en fazla beş PENDING olay şifreli
 * gönderilir. Mevcut bekleyen DELETE işlemleri de bu partide olabilir;
 * bu nedenle kullanıcıya gönderim kapsamı açıkça bildirilir.
 *
 * Parola rememberSaveable kullanılmadan geçici bellekte tutulur.
 */
@Composable
internal fun InitialJournalBootstrapSection(
    uid: String,
    viewModel: InitialJournalBootstrapViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var stageConsent by remember(uid) { mutableStateOf(false) }
    var uploadConsent by remember(uid) { mutableStateOf(false) }
    var password by remember(uid) { mutableStateOf("") }

    LaunchedEffect(uid) {
        viewModel.selectAccount(uid)
        viewModel.refresh(uid)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("İlk bulut aktarımı", style = MaterialTheme.typography.titleMedium)
            Text(
                "Eski anıların kimliklerini kontrol edip daha önce kuyruğa alınmamış " +
                        "olanları hazırlar. Her adım en fazla 5 anıyı işler. " +
                        "Anıların orijinali ve .bdbackup dosyaları değişmez.",
                style = MaterialTheme.typography.bodySmall
            )
            val overview = state.overview
            if (overview != null) {
                Text("Yerel anı: ${overview.localJournalCount}")
                Text("İlk aktarımı bekleyen eski anı: ${overview.untrackedJournalCount}")
                Text("Zaten izlenen anı: ${overview.trackedJournalCount}")
                Text(
                    "Gönderim kuyruğu: ${overview.pendingUpserts} UPSERT, " +
                            "${overview.pendingDeletes} DELETE",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = stageConsent,
                    onCheckedChange = { stageConsent = it },
                    enabled = !state.busy
                )
                Text(
                    "En fazla 5 eski anımın sunucu kimlik kontrolünden sonra " +
                            "yerel gönderim kuyruğuna alınmasını onaylıyorum. " +
                            "Bu adım buluta anı metni göndermez.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = {
                    viewModel.prepare(uid, stageConsent)
                    stageConsent = false
                },
                enabled = !state.busy && stageConsent &&
                        (overview?.untrackedJournalCount ?: 0) > 0,
                modifier = Modifier.fillMaxWidth()
            ) { Text("İlk 5 eski anıyı güvenle hazırla") }

            Text("Şifreli gönderim", style = MaterialTheme.typography.titleSmall)
            Text(
                "Bu adım aynı şifreyle önce ilişki/ruh hali kataloğunu, ardından " +
                        "kuyruktaki en fazla 5 işlemi Firebase'e yollar. Kuyrukta " +
                        "yeni anı değişiklikleri veya DELETE olayları da olabilir. " +
                        "Gönderim kısmen başarısız olursa tekrar denenebilir. " +
                        "Fotoğraflar aktarılmaz.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Bulut aktarım şifresi (12–128 karakter)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = uploadConsent,
                    onCheckedChange = { uploadConsent = it },
                    enabled = !state.busy
                )
                Text(
                    "Katalogla birlikte kuyruktaki en fazla 5 işlemin " +
                            "(silme olayları dahil) AES-GCM ile şifrelenerek " +
                            "Firebase hesabıma gönderilmesini onaylıyorum. " +
                            "Otomatik eşitleme açıksa bu adım arka planda kendiliğinden yapılır.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = {
                    viewModel.upload(uid, password, uploadConsent)
                    password = ""
                    uploadConsent = false
                },
                enabled = !state.busy && uploadConsent && password.length in 12..128 &&
                        ((overview?.pendingUpserts ?: 0) + (overview?.pendingDeletes ?: 0)) > 0,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Onayla, kataloğu ve ilk partiyi gönder") }
            OutlinedButton(
                onClick = { viewModel.refresh(uid) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Yerel durumumu yenile") }
            if (state.busy) CircularProgressIndicator()
            state.status?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error) }
            Text(
                "Önemli: Sunucuda aynı anı kimliğiyle olay bulunursa hazırlık durur; " +
                        "çakışma yönetiminden incelemelisin. Tüm geçmiş anılar " +
                        "tek seferde değil, kullanıcı onaylı küçük partilerle gönderilir.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
