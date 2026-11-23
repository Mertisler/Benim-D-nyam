package com.loc.benimdunyam.feature.settings.cloudaccount

import com.loc.benimdunyam.core.DeveloperTools
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.data.local.isolation.WorkspaceMode
import com.loc.benimdunyam.domain.model.CloudConnectionStatus
import com.loc.benimdunyam.domain.model.CloudLocalArchiveOwnerState
import java.text.DateFormat
import java.util.Date

/**
 * Bulut hesabının alt ekranları aynı Compose Navigation hedefi içinde yaşar.
 * Bu sayede uygulamanın NavGraph rotaları, Firebase oturumu ve mevcut ViewModel
 * bağlantıları değiştirilmeden mevcut işlevler sade ekranlara taşınır.
 */
internal enum class CloudHubPage(val title: String) {
    HOME("Bulut Hesabım"),
    SYNC("Eşitleme Ayrıntıları"),
    SYNC_SEND("Anılarımı Gönder"),
    SYNC_RECEIVE("Buluttaki Anılar"),
    SYNC_AUTO("Otomatik Eşitleme"),
    SYNC_CONFLICTS("Anı Çakışmaları"),
    BACKUP("Şifreli Bulut Arşivi"),
    MEDIA("Fotoğraflarım"),
    SECURITY("Hesap ve Güvenlik"),
    ADVANCED("Geliştirici araçları");

    /**
     * Sade yapı: ana sayfada yalnızca eşitleme kartı, hesap ve Gelişmiş.
     * Teknik eşitleme adımları, bulut arşivi ve fotoğraflar Gelişmiş altındadır.
     */
    fun parent(): CloudHubPage = when (this) {
        SYNC_SEND, SYNC_RECEIVE, SYNC_AUTO -> SYNC
        SYNC, BACKUP, MEDIA -> ADVANCED
        else -> HOME
    }

    /** Yalnızca debug derlemesinde açılan teknik sayfalar. */
    val developerOnly: Boolean
        get() = this == SYNC || this == SYNC_SEND || this == SYNC_RECEIVE ||
                this == SYNC_AUTO || this == BACKUP || this == MEDIA || this == ADVANCED

    companion object {
        fun fromName(name: String): CloudHubPage {
            val page = entries.firstOrNull { it.name == name } ?: HOME
            // Release'te teknik sayfaya hiçbir yoldan (kayıtlı durum dahil) gidilemez.
            return if (page.developerOnly && !DeveloperTools.ENABLED) HOME else page
        }
    }
}

/**
 * Tek sorumluluk: Eski Faz 2E/2F/2G bileşenlerini yeniden yazmadan anlamlı
 * alt sayfalara ayırmak. Her bileşenin onayı, şifresi ve güvenlik kontrolü aynıdır.
 */
@Composable
internal fun CloudAccountV2Content(
    page: CloudHubPage,
    state: CloudAccountUiState,
    viewModel: CloudAccountViewModel,
    activeStorage: WorkspaceMode,
    navigate: (CloudHubPage) -> Unit,
    openArchiveDialog: (String) -> Unit,
    requestSignOut: () -> Unit
) {
    val account = state.account ?: return
    when (page) {
        CloudHubPage.HOME -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hesabın bağlı", style = MaterialTheme.typography.titleLarge)
                            Text(account.email ?: "E-posta bilgisi yok", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                    Text(
                        if (account.isEmailVerified) "E-posta adresin doğrulandı."
                        else "E-posta doğrulamanı Hesap ve Güvenlik bölümünden tamamlayabilirsin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Sade bulut: kullanıcı yalnızca açık/kapalı, son eşitleme ve dikkat
            // gerektiren durumları görür. Tüm teknik adımlar Gelişmiş altında.
            CloudSyncHomeCard(
                uid = account.uid,
                onOpenConflicts = { navigate(CloudHubPage.SYNC_CONFLICTS) }
            )
            Text(
                "Fotoğrafların şu an yalnızca bu cihazda saklanır ve eşitlenmez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CloudHubDestination(Icons.Default.Security, "Hesap ve Güvenlik", "Profil, e-posta doğrulaması ve oturum", onClick = { navigate(CloudHubPage.SECURITY) })
            if (DeveloperTools.ENABLED) {
                // Yalnızca debug derlemesi. Release APK'da bu satır ve hedefleri yoktur.
                CloudHubDestination(Icons.Default.Settings, "Geliştirici araçları", "Yalnızca debug sürümünde · eşitleme ayrıntıları, arşiv, tanılama", onClick = { navigate(CloudHubPage.ADVANCED) })
            }
        }

        CloudHubPage.SYNC -> if (DeveloperTools.ENABLED) {
            CloudHubIntro("Eşitleme ayrıntıları", "Otomatik eşitleme bu adımları kendisi yapar. Buradaki ekranlar sorun giderme ve elle kontrol içindir.")
            CloudHubDestination(Icons.Default.Cloud, "Anılarımı Gönder", "Bekleyen anılar ve ilk bulut aktarımı", onClick = { navigate(CloudHubPage.SYNC_SEND) })
            CloudHubDestination(Icons.Default.Storage, "Buluttaki Anıları İncele", "Şifreli kayıtları karşılaştır ve güvenle birleştir", onClick = { navigate(CloudHubPage.SYNC_RECEIVE) })
            CloudHubDestination(Icons.Default.Refresh, "Otomatik Eşitleme", "Cihaz anahtarı, izin ve eşitleme planı", onClick = { navigate(CloudHubPage.SYNC_AUTO) })
            CloudHubDestination(Icons.Default.Info, "Anı Çakışmaları", "Farklı cihazlardaki sürümleri koruyarak incele", onClick = { navigate(CloudHubPage.SYNC_CONFLICTS) })
            // Eski, şifreli ve ayrıntılı çakışma denetimi (sorun giderme için).
            JournalConflictSection(uid = account.uid)
        }

        CloudHubPage.SYNC_SEND -> if (DeveloperTools.ENABLED) {
            CloudHubIntro("Anılarımı gönder", "Önce gönderilecek anıları gözden geçir. Şifreli gönderim yalnızca ilgili kullanıcı onayıyla çalışır.")
            // Önceki güvenli ilk aktarım ve manuel gönderim bileşenleri değişmedi.
            InitialJournalBootstrapSection(uid = account.uid)
            ManualJournalUploadSection(uid = account.uid)
        }

        CloudHubPage.SYNC_RECEIVE -> if (DeveloperTools.ENABLED) {
            CloudHubIntro("Buluttaki anılar", "Önce kayıtları doğrula; yerel anıları değiştiren birleştirme işlemi ayrıca onay ister.")
            VerifiedJournalDownloadSection(uid = account.uid)
            FullSyncReadinessSection(uid = account.uid)
            SafeJournalMergeSection(uid = account.uid)
        }

        CloudHubPage.SYNC_AUTO -> if (DeveloperTools.ENABLED) {
            CloudHubIntro("Otomatik eşitleme", "Şifreli metin eşitlemesinin yetkilerini ve zamanlamasını buradan yönetebilirsin. Fotoğrafların buluta gönderilmez.")
            AutoSyncAuthorizationSection(uid = account.uid)
            AutomaticJournalSyncSection(uid = account.uid)
        }

        CloudHubPage.SYNC_CONFLICTS -> {
            CloudHubIntro("Farklı sürümler", "Bu anılar iki cihazda farklı değişti. Hangisinin kalacağını seç; istersen ikisini de saklayabilirsin.")
            // Aşama 2: şifresiz, iki sütunlu çakışma ekranı.
            ConflictCenterSection(uid = account.uid)
        }

        CloudHubPage.BACKUP -> if (DeveloperTools.ENABLED) {
            CloudHubIntro("Şifreli bulut kopyası", "Bu işlem günlük metinlerini ve desteklenen ilişkileri korur; fotoğraflar dahil değildir. Yerel .bdbackup ve .bdmedia yedeklerini de saklamalısın.")
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Bulut arşivim", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ayrı bir arşiv şifresi gerekir. Şifren sunucuya gönderilmez ve uygulama tarafından saklanmaz. Unutursan arşivi açamayabilirsin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.isArchiveBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(state.archiveAction ?: "Bulut arşivi işleniyor...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(
                        onClick = { openArchiveDialog("upload") },
                        enabled = !state.isArchiveBusy && !state.isSubmitting && !state.isCheckingConnection &&
                                !state.isProfileBusy && state.localArchiveOwnerState != CloudLocalArchiveOwnerState.OTHER_ACCOUNT,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Şifreli kopya oluştur") }
                    OutlinedButton(
                        onClick = viewModel::loadLatestArchive,
                        enabled = !state.isArchiveBusy && !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Buluttaki son kopyayı göster") }
                    state.latestArchive?.let { last ->
                        val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(last.createdAtMillis))
                        Text("Son kopya: $formatted", style = MaterialTheme.typography.labelLarge)
                        Text("${last.journalCount} anı · ${last.peopleCount} kişi · ${last.seasonCount} dönem · ${last.episodeCount} bölüm · ${last.moodCount} ruh hali", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { openArchiveDialog("verify") }, enabled = !state.isArchiveBusy, modifier = Modifier.fillMaxWidth()) {
                            Text("Şifre ve bütünlük kontrolü yap")
                        }
                    }
                }
            }
            CloudRestoreSection(
                state = state,
                onPreview = viewModel::previewCloudRestore,
                onImport = viewModel::importPreviewedCloudRestore,
                onClearPreview = viewModel::clearRestorePreview
            )
            CloudSyncAuditSection(
                state = state,
                onInspect = viewModel::inspectCloudReadiness,
                onCompare = viewModel::compareCloudArchive
            )
            state.archiveError?.let { CloudHubMessage(it, true, viewModel::dismissArchiveMessage) }
            state.archiveMessage?.let { CloudHubMessage(it, false, viewModel::dismissArchiveMessage) }
        }

        CloudHubPage.MEDIA -> if (DeveloperTools.ENABLED) {
            CloudHubIntro("Fotoğraflarım", "Ücretsiz Spark modunda fotoğrafların cihazında kalır. Bulut fotoğraf eşitleme ve fiziksel bulut temizliği kapalıdır.")
            // Spark koruması repository ve WorkManager katmanlarında da uygulanmıştı.
            SparkLocalMediaSection(uid = account.uid)
        }

        CloudHubPage.SECURITY -> {
            CloudHubIntro("Hesap ve güvenlik", "Profil ve e-posta doğrulamanı yönetebilirsin. Oturumdan çıkmak cihazındaki anıları silmez; hesaba bağlı arşiv geçici olarak kilitlenebilir.")
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CloudProfileSection(
                        state = state,
                        onSendVerificationEmail = viewModel::sendVerificationEmail,
                        onRefreshProfile = viewModel::refreshProfile,
                        onUpdateDisplayName = viewModel::updateDisplayName,
                        onDismissProfileMessage = viewModel::dismissProfileMessage
                    )
                    OutlinedButton(
                        onClick = requestSignOut,
                        enabled = !state.isArchiveBusy && !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Bulut hesabından çıkış yap")
                    }
                }
            }
        }

        CloudHubPage.ADVANCED -> if (DeveloperTools.ENABLED) {
            CloudHubIntro("Geliştirici araçları", "Yalnızca debug sürümünde görünür. Kullanıcının uygulamasında bu bölüm yoktur.")
            CloudHubDestination(Icons.Default.Cloud, "Eşitleme ayrıntıları", "Elle gönderim, buluttaki kayıtlar, izin ve plan", onClick = { navigate(CloudHubPage.SYNC) })
            CloudHubDestination(Icons.Default.Info, "Anı çakışmaları", "Farklı cihazlardaki sürümleri incele", onClick = { navigate(CloudHubPage.SYNC_CONFLICTS) })
            CloudHubDestination(Icons.Default.Storage, "Şifreli bulut arşivi", "Tek seferlik şifreli kopya ve geri yükleme", onClick = { navigate(CloudHubPage.BACKUP) })
            CloudHubDestination(Icons.Default.CloudOff, "Fotoğraflarım", "Cihazdaki fotoğraflar", onClick = { navigate(CloudHubPage.MEDIA) })
            // Önceki Faz 2F.1: yalnızca tanılama, günlük içerik aktarımı yok.
            SyncFoundationSection(uid = account.uid)
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Yerel veriler ve hesap geçişi", style = MaterialTheme.typography.titleMedium)
                    LocalStoragePreflightSection()
                    if (activeStorage == WorkspaceMode.LEGACY) {
                        AccountMigrationSection()
                    }
                    AccountPromotionSection()
                }
            }
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Firestore bağlantı testi", style = MaterialTheme.typography.titleMedium)
                    Text("Yalnızca hesabına ait /users/{uid}/_meta/connection yolu sunucudan okunur. Hiçbir veri yazılmaz.", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = viewModel::checkConnection,
                        enabled = !state.isCheckingConnection && !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isCheckingConnection) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isCheckingConnection) "Bağlantı kontrol ediliyor..." else "Bağlantıyı kontrol et")
                    }
                    state.connectionStatus?.let { CloudHubConnectionResult(it) }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Verilerin hakkında", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Şifreli metin eşitleme ayrıca izin verilirse çalışır. Bulut kopyası fotoğrafları kapsamaz. " +
                                "Bağlı arşivin hesabından çıkarsan aynı hesapla giriş yapana dek anı ekranları kilitli olabilir. " +
                                "Bu mekanizma Room dosyalarını fiziksel olarak hesaplara ayırmaz.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/** Bütün seçenekler aynı görsel dili paylaşır; gizli bir sahte buton bulunmaz. */
@Composable
private fun CloudHubDestination(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CloudHubIntro(title: String, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CloudHubConnectionResult(status: CloudConnectionStatus) {
    val success = status == CloudConnectionStatus.Reachable
    val message = when (status) {
        CloudConnectionStatus.Reachable -> "Firestore sunucusuna erişildi. Test belgesi bulunmak zorunda değil."
        CloudConnectionStatus.SignInRequired -> "Bağlantı için giriş yapmalısın."
        CloudConnectionStatus.PermissionDenied -> "İzin reddedildi. Firestore güvenlik kurallarını kontrol et."
        CloudConnectionStatus.NetworkUnavailable -> "Ağ bağlantısı yok veya sunucuya ulaşılamadı."
        CloudConnectionStatus.UnknownError -> "Bağlantı doğrulanamadı. Firebase yapılandırmasını kontrol et."
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (success) Icons.Default.CheckCircle else Icons.Default.CloudOff,
            contentDescription = null,
            tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Text(message, color = if (success) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CloudHubMessage(message: String, error: Boolean, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    }
}
