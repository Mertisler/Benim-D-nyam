package com.loc.benimdunyam.data.sync.remote

import android.util.Base64
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalSyncEventDao
import com.loc.benimdunyam.data.local.entity.JournalSyncEventEntity
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.remote.firebase.archive.CloudArchiveCrypto
import com.loc.benimdunyam.domain.sync.JournalSyncChangePolicy
import com.loc.benimdunyam.domain.sync.SyncEventIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Bu sayılar yalnızca yerel Room kayıtlarından okunur, şifre veya anı metni içermez. */
data class InitialBootstrapOverview(
    val localJournalCount: Int,
    val untrackedJournalCount: Int,
    val trackedJournalCount: Int,
    val pendingUpserts: Int,
    val pendingDeletes: Int
)

/** İzin verilen küçük bir partinin hazırlık sonucu. Firestore'a henüz veri yazılmamıştır. */
data class InitialBootstrapStageResult(
    val prepared: Int,
    val changedWhileChecking: Int,
    val alreadyTracked: Int,
    val remainingUntracked: Int
)

/** Kullanıcı onaylı şifreli katalog + en fazla beş olay gönderiminin özeti. */
data class InitialBootstrapUploadResult(
    val encryptedCatalogParts: Int,
    val uploadedEvents: Int,
    val changedDuringUpload: Int,
    val remainingPending: Int
)

/**
 * Faz 2F.9 - Eski anıların güvenli ilk bulut aktarımı.
 *
 * 1) ESKİ anılardan hiç değişiklik kaydı bulunmayanları seçer.
 * 2) Her adayın aynı kimlikle bulutta olay geçmişi bulunmadığını SUNUCUDAN teyit eder.
 * 3) Aynı Room transaction'ında yalnızca PENDING UPSERT kaydı ekler.
 * 4) Kullanıcı ayrıca onaylarsa şifreli ilişki kataloğu ve mevcut manuel
 *    yükleyiciyi kullanır. Yeni, ayrı veya açık metin gönderim biçimi yoktur.
 *
 * Güvenlik sınırları:
 * - Anı, ilişki ve ruh hali kayıtlarının kendisini burada DEĞİŞTİRMEZ.
 * - DELETE olayını kendiliğinden oluşturmaz. Mevcut PENDING DELETE, kullanıcı
 *   ikinci gönderim onayında açıkça kabul ederse mevcut yükleyiciden geçebilir.
 * - Bulut ve Room arasında ortak transaction yoktur. Sunucu denetiminden hemen
 *   sonra farklı cihaz aynı kimlikte olay yaratırsa sonraki yükleyicinin belge
 *   çakışması kontrolü durur. Otomatik uzlaştırma yapılmaz.
 * - İş yarıda kesilirse hazırlanmış PENDING olayları kalır; ikinci denemede
 *   aynı anıdan yeniden olay üretmek yerine kaldığı yerden devam eder.
 */
@Singleton
class InitialJournalBootstrapRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val workspace: ActiveWorkspaceSession,
    private val database: AppDatabase,
    private val journalDao: JournalDao,
    private val syncEventDao: JournalSyncEventDao,
    private val writeCoordinator: BackupWriteCoordinator,
    private val catalogRepository: SyncDependencyCoverageRepository,
    private val uploader: ManualJournalEventUploader
) {
    private val mutex = Mutex()

    private fun requireAccount(uid: String) {
        check(uid.isNotBlank() && auth.currentUser?.uid == uid &&
                workspace.isReadyFor(uid) && !workspace.needsRestart()) {
            "Firebase hesabı ve aktif yerel arşiv eşleşmiyor. Hesap değiştirildi ise yeniden başlat."
        }
    }

    /** Başka hesabın kuyruk kayıtları bu alanla karışıyorsa ilk aktarım kilitlenir. */
    private suspend fun requireNoForeignEvents(uid: String) {
        check(syncEventDao.countOtherOwners(uid) == 0) {
            "Bu yerel arşivde farklı hesap kimliğine ait işlem kayıtları var. Aktarım durduruldu."
        }
    }

    /** Önizleme çevrimdışı çalışabilir; kullanıcı verisi veya ağ isteği üretmez. */
    suspend fun overview(uid: String): InitialBootstrapOverview = withContext(Dispatchers.IO) {
        requireAccount(uid)
        database.withTransaction {
            requireAccount(uid)
            requireNoForeignEvents(uid)
            val all = journalDao.countAllForBootstrap()
            val untracked = journalDao.countUntrackedForBootstrap()
            InitialBootstrapOverview(
                localJournalCount = all,
                untrackedJournalCount = untracked,
                trackedJournalCount = all - untracked,
                pendingUpserts = syncEventDao.pendingUpserts(uid),
                pendingDeletes = syncEventDao.pendingDeletes(uid)
            )
        }
    }

    /**
     * Hazırlama yalnızca kullanıcı onayıyla çalışır. Her defasında en fazla beş anı
     * ele alınır. Sunucuda aynı anıya ait UPSERT veya DELETE varsa aktarımı
     * durdururuz: uzak sürümün üzerine yazmak bu aşamada güvenli değildir.
     *
     * Ağ sorguları transaction dışında yapılır. Kaydın tüm alanları yazma anında
     * yeniden karşılaştırılır; arada düzenlenmiş anı için eski snapshot eklenmez.
     */
    suspend fun prepareNext(uid: String, confirmed: Boolean): InitialBootstrapStageResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(confirmed) { "Başlangıç aktarımı için açık kullanıcı onayı gereklidir." }
            requireAccount(uid)
            val candidates = database.withTransaction {
                requireNoForeignEvents(uid)
                journalDao.getBootstrapCandidates(BATCH_SIZE)
            }

            // Ağ erişimi başarısızsa önceden alınmış önbellek sonucuna GÜVENME.
            for (journal in candidates) {
                requireAccount(uid)
                val hash = SyncEventIdentity.journalHash(uid, journal.id)
                val remote = firestore.collection("users").document(uid)
                    .collection("journal_change_events")
                    .whereEqualTo("journalHash", hash)
                    .limit(1)
                    .get(Source.SERVER).await()
                check(remote.isEmpty) {
                    "Sunucuda daha önce aktarılmış veya silinmiş bir anı bulundu. " +
                            "Hiçbir yeni anı sıraya alınmadı; çakışma yönetiminde incele."
                }
            }

            writeCoordinator.withRegularWrite {
                requireAccount(uid)
                database.withTransaction {
                    requireAccount(uid)
                    requireNoForeignEvents(uid)
                    var added = 0
                    var changed = 0
                    var tracked = 0
                    candidates.forEach { snapshot ->
                        val current = journalDao.getJournalById(snapshot.id)
                        if (current == null || current != snapshot) {
                            // Ağ isteği sürerken metin değişti; eski sürümü kaydetme.
                            changed++
                        } else if (syncEventDao.findByJournalId(snapshot.id) != null) {
                            // O sırada normal anı kaydı zaten PENDING olay oluşturmuş.
                            tracked++
                        } else {
                            val newEvent = JournalSyncEventEntity(
                                journalId = snapshot.id,
                                ownerUid = uid,
                                changeType = JournalSyncChangePolicy.UPSERT,
                                state = JournalSyncChangePolicy.PENDING,
                                changeVersion = 1L,
                                updatedAtMillis = System.currentTimeMillis()
                            )
                            if (syncEventDao.insertBootstrapIfAbsent(newEvent) == -1L) {
                                tracked++
                            } else added++
                        }
                    }
                    requireAccount(uid)
                    InitialBootstrapStageResult(
                        prepared = added,
                        changedWhileChecking = changed,
                        alreadyTracked = tracked,
                        remainingUntracked = journalDao.countUntrackedForBootstrap()
                    )
                }
            }
        }
    }

    /**
     * Kullanıcı ayrıca şifresini girip gönderimi onaylar.
     *
     * Aynı şifrenin kullanılmasına yardımcı olmak için sunucudaki mevcut bir
     * şifreli olay açılabiliyor mu diye denetleriz. Bu tek belge denetimi bütün
     * eski olayların aynı şifreyi kullandığını kanıtlamaz; anahtar yönetimi 2F.10.
     * Yeni katalog ve olaylar için AYNI şifreyi kullanırız.
     *
     * Önce katalog yüklenir. Sonraki yükleme başarısız olursa PENDING olaylar
     * kalır; katalog yüklenmiş olabilir, sonuç kısmi olabilir.
     */
    suspend fun uploadEncryptedBatch(
        uid: String,
        password: CharArray,
        confirmed: Boolean
    ): InitialBootstrapUploadResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(confirmed) { "Şifreli gönderim için ayrıca onay gereklidir." }
            require(password.size in 12..128) { "Aktarım şifresi 12–128 karakter olmalıdır." }
            requireAccount(uid)
            requireNoForeignEvents(uid)
            check(uploader.pending(uid) > 0) { "Gönderilmeyi bekleyen hiçbir olay bulunmuyor." }
            verifyOneExistingRemotePassphrase(uid, password)
            requireAccount(uid)
            val parts = catalogRepository.uploadCatalog(uid, password)
            requireAccount(uid)
            val report = uploader.uploadPending(uid, password)
            InitialBootstrapUploadResult(
                encryptedCatalogParts = parts,
                uploadedEvents = report.confirmed,
                changedDuringUpload = report.changedDuringUpload,
                remainingPending = report.pending
            )
        }
    }

    /**
     * Yanlışlıkla farklı şifreyle yeni bir parti oluşturmayı azaltır. Sunucu
     * bağlantısı zorunludur: önbellekte duran eski belgeye güvenilmez.
     */
    private suspend fun verifyOneExistingRemotePassphrase(uid: String, password: CharArray) {
        requireAccount(uid)
        val existing = firestore.collection("users").document(uid)
            .collection("journal_change_events").limit(1).get(Source.SERVER).await()
        val doc = existing.documents.firstOrNull() ?: return
        check(doc.getString("ownerUid") == uid) { "Bulut olayındaki hesap kimliği uyuşmuyor." }
        val encoded = doc.getString("encryptedPayload") ?: error("Bulut olayının şifreli verisi eksik.")
        require(encoded.length in 51..600_000) { "Bulut olayının boyutu beklenenden farklı." }
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        try {
            val decrypted = CloudArchiveCrypto.decrypt(encrypted, password)
            check(JSONObject(decrypted).getString("ownerUid") == uid) {
                "Şifre çözülen olay başka hesaba ait görünüyor."
            }
        } finally {
            encrypted.fill(0)
        }
        requireAccount(uid)
    }

    private companion object {
        const val BATCH_SIZE = 5
    }
}
