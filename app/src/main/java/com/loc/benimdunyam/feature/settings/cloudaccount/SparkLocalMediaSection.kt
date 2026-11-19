package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Ücretsiz plan medya merkezi. Bu ekran yalnızca cihazdaki medya kayıtlarını sayar;
 * Firebase Storage ve Cloud Functions çağrısı yapmaz, hiçbir fotoğrafı silmez.
 */
@Composable
internal fun SparkLocalMediaSection(
    uid: String,
    viewModel: SparkLocalMediaViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(uid) { viewModel.refresh(uid) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.PhoneAndroid, contentDescription = null)
            Text("Medya Merkezi · Ücretsiz cihaz modu", style = MaterialTheme.typography.titleMedium)
            Text(
                "Anı fotoğrafların, dönem kapakların ve kişi görsellerin cihazda kalıyor. " +
                        "Firebase Storage gerektiren yükleme, indirme, otomatik eşitleme " +
                        "ve kalıcı bulut temizliği bu sürümde kapalı.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (state.uid == uid) {
                if (state.loading) CircularProgressIndicator()
                state.count?.let { counts ->
                    Text("Anı fotoğrafı kaydı: ${counts.photos}")
                    Text("Dönem kapağı kaydı: ${counts.seasonCovers}")
                    Text("Kişi görseli kaydı: ${counts.peopleImages}")
                }
                state.notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            Text(
                "Önemli: Buradaki sayılar yerel veritabanı ve medya kayıtlarından türetilir; " +
                        "dosyanın fiziksel olarak hâlâ bulunduğunu doğrulamaz ve bir yedekleme " +
                        "raporu değildir. Uygulamayı silmeden veya telefon " +
                        "değiştirmeden önce medya dosyalarını ayrıca yedekle. Mevcut .bdbackup " +
                        "biçiminin bütün fotoğrafları içerdiği varsayılmamalıdır.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Şifreli METİN günlüğü senkronizasyonu ayrı şekilde devam edebilir. " +
                        "Firestore'un Spark ücretsiz kullanım sınırları geçerlidir.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = { viewModel.refresh(uid) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading
            ) { Text("Yerel medya sayısını yenile") }
        }
    }
}
