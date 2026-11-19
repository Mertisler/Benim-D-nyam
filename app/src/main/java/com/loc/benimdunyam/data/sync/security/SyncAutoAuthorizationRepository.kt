package com.loc.benimdunyam.data.sync.security

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.remote.firebase.archive.CloudArchiveCrypto
import com.loc.benimdunyam.domain.sync.SyncEventIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Kullanıcıya gösterilen izin durumu; asla parola veya günlük metni içermez. */
data class SyncAuthorizationStatus(
    val enabled: Boolean,
    val grantedAtMillis: Long? = null,
    val remoteSampleVerified: Boolean = false,
    val note: String? = null
)

/**
 * Faz 2F.10: Firebase hesabı + aktif yerel alan + kullanıcı iznini tek noktadan kontrol eder.
 *
 * Bu sınıf otomatik yükleme BAŞLATMAZ; WorkManager işi KURMAZ.
 * Faz 2F.11'de Worker sadece withApprovedPassphrase üzerinden anahtara erişebilir.
 * Burada doğrulama için yalnızca SUNUCU OKUMA çağrısı yapılır.
 */
@Singleton
class SyncAutoAuthorizationRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val session: ActiveWorkspaceSession,
    private val vault: SyncDeviceCredentialVault
) {
    private val mutex = Mutex()

    private fun requireAccount(uid: String) {
        check(uid.isNotBlank() && auth.currentUser?.uid == uid &&
                session.isReadyFor(uid) && !session.needsRestart()) {
            "Firebase hesabın ile açık yerel arşiv uyuşmuyor. Önce doğru hesap alanını aç."
        }
    }

    /** Ağ gerektirmez; kayıt yoksa izin kapalıdır. Bozuk Keystore verisini saklamaz. */
    suspend fun status(uid: String): SyncAuthorizationStatus = withContext(Dispatchers.IO) {
        requireAccount(uid)
        val local = vault.status(uid)
        SyncAuthorizationStatus(
            enabled = local != null,
            grantedAtMillis = local?.grantedAtMillis,
            remoteSampleVerified = local?.remoteVerified ?: false,
            note = if (local == null) "Bu cihazda izin kapalı." else
                "İzin bu cihazda saklı; otomatik eşitleme işleri henüz etkin değil."
        )
    }

    /**
     * Parolayı yerelde kaydetmeden önce buluttaki BİR örnek olayı çözerek doğrular.
     * Hesapta hiç olay yoksa var olan şifreli ilişki kataloğunu kontrol eder.
     * Bu örnek kontrol, geçmişte farklı şifrelerle yüklenmiş TÜM olayların aynı
     * parolayı kullandığını kanıtlamaz; Faz 2F.11 her olayı ayrıca doğrulamalı.
     */
    suspend fun enable(uid: String, passphrase: CharArray): SyncAuthorizationStatus = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(passphrase.size in 12..128) { "Eşitleme şifresi 12–128 karakter olmalı." }
            requireAccount(uid)
            val verified = verifyRemoteExample(uid, passphrase)
            requireAccount(uid)
            val saved = vault.grant(uid, passphrase, verified)
            SyncAuthorizationStatus(
                enabled = true,
                grantedAtMillis = saved.grantedAtMillis,
                remoteSampleVerified = verified,
                note = if (verified)
                    "Buluttaki örnek şifre doğrulandı. Arka plan aktarımı henüz başlamaz."
                else
                    "Bulutta henüz olay/katalog yok; şifre yalnızca bu cihaza kaydedildi."
            )
        }
    }

    /** İzin iptali internetsiz yapılabilir; Firestore belgeleri değiştirilmez. */
    suspend fun disable(uid: String): SyncAuthorizationStatus = mutex.withLock {
        withContext(Dispatchers.IO) {
            requireAccount(uid)
            vault.revoke(uid)
            SyncAuthorizationStatus(false, note = "Cihaz izni ve Keystore anahtarı kaldırıldı.")
        }
    }

    /**
     * Gelecekteki Worker'ın parola alabileceği tek API.
     * Her kullanımdan önce hesabı/yerel alanı doğrular; sıradaki fazda Worker ayrıca
     * her okuma/yazmadan önce de aynı kontrolü tekrar etmelidir.
     */
    suspend fun <T> withApprovedPassphrase(
        uid: String,
        operation: suspend (CharArray) -> T
    ): T {
        // Mutex'i yalnızca ANAHTARI OKURKEN tutarız. Ağ çağrıları boyunca kilidi
        // tutmak "İzni kaldır" eylemini geciktirebilir; gelecekteki Worker
        // her ağ adımında ayrıca izin/hesap kontrolü ve iptal yönetimi yapmalıdır.
        val unlocked = mutex.withLock {
            withContext(Dispatchers.IO) {
                requireAccount(uid)
                vault.withUnlockedPassphrase(uid) { it.copyOf() }
            }
        }
        try {
            requireAccount(uid)
            return operation(unlocked)
        } finally {
            unlocked.fill('\u0000')
        }
    }

    private suspend fun verifyRemoteExample(uid: String, password: CharArray): Boolean {
        requireAccount(uid)
        // İlk sayfa sunucudan çekilir; offline önbelleği yanlış izin verdirmez.
        val event = firestore.collection("users").document(uid)
            .collection("journal_change_events").limit(1).get(Source.SERVER)
            .await().documents.firstOrNull()
        requireAccount(uid)
        if (event != null) {
            check(event.getString("ownerUid") == uid && event.getLong("formatVersion") == 1L) {
                "Bulut olayının hesap/biçim bilgisi geçersiz."
            }
            val raw = event.getString("encryptedPayload")
                ?: error("Bulut olayının şifreli içeriği yok.")
            require(raw.length in 1..600_000) { "Bulut olayı güvenlik sınırını aşıyor." }
            val encrypted = Base64.decode(raw, Base64.NO_WRAP)
            val json = try {
                CloudArchiveCrypto.decrypt(encrypted, password)
            } finally {
                encrypted.fill(0)
            }
            val payload = JSONObject(json)
            val journalId = payload.getString("journalId")
            val revision = payload.getLong("revision")
            val operation = payload.getString("operation")
            check(payload.getString("ownerUid") == uid &&
                    event.getString("journalHash") == SyncEventIdentity.journalHash(uid, journalId) &&
                    event.id == SyncEventIdentity.eventId(uid, journalId, revision, operation) &&
                    event.getLong("revision") == revision &&
                    event.getString("operation") == operation) {
                "Bulut olayı şifreyle açıldı ama kimlik veya sürüm doğrulaması başarısız."
            }
            return true
        }

        // Henüz anı olayı yoksa ilişki kataloğu varsa onu doğrulamadan izin vermeyiz.
        val snapshot = firestore.collection("users").document(uid)
            .collection("sync_dependency_snapshots").limit(1)
            .get(Source.SERVER).await().documents.firstOrNull()
        requireAccount(uid)
        if (snapshot != null) {
            // Katalog mevcutsa yalnızca örnek manifest yeterli DEĞİLDİR.
            // Bütün katalog parçalarını çözme Faz 2F.11'e kalacağından burada
            // kullanıcıya önce bir manuel olay gönderimi yaptırıyoruz.
            error("Bulutta ilişki kataloğu var ama anı olayı yok. " +
                    "Önce aynı şifreyle bir test anısı olayını manuel gönder, ardından izni aç.")
        }
        return false // Boş bulut hesabı: kullanıcı yeni bir şifre tanımlayabilir.
    }
}
