package com.loc.benimdunyam.data.sync.local

import com.google.firebase.auth.FirebaseAuth
import com.loc.benimdunyam.data.local.dao.JournalSyncEventDao
import com.loc.benimdunyam.data.local.entity.JournalSyncEventEntity
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.domain.sync.JournalSyncChangePolicy
import javax.inject.Inject

/**
 * Günlüğün kaydedildiği transaction içinde işlem kaydı oluşturur.
 *
 * Bu sınıfın record() fonksiyonu dışarıdan tek başına çağrılmamalıdır;
 * JournalRepositoryImpl, AppDatabase.withTransaction içinde çağırır.
 * Böylece journal_entries ve journal_sync_events atomik olarak değişir.
 *
 * Günlük metnini Firebase'e yüklemez, ağ isteği göndermez ve WorkManager'ı
 * başlatmaz. İkinci aşamada sadece yerel değişiklik izi tutuyoruz.
 */
class JournalSyncChangeRecorder @Inject constructor(
    private val eventDao: JournalSyncEventDao,
    private val auth: FirebaseAuth,
    private val session: ActiveWorkspaceSession
) {
    /**
     * Yazma başlamadan önce hesap/çalışma alanı güvenli mi diye bakar.
     * Misafir kullanımında UID null olabilir; bu durumda outbox'a kayıt
     * alınmayacak ama normal Room kaydı korunacaktır.
     */
    fun requireWritableActor(): String? {
        val uid = auth.currentUser?.uid
        requireSameActor(uid)
        return uid
    }

    /** İşlem sırasında Firebase oturumu değişirse transaction geri alınır. */
    fun requireSameActor(originalUid: String?) {
        check(auth.currentUser?.uid == originalUid &&
                session.isReadyFor(originalUid) && !session.needsRestart()) {
            "Hesap veya yerel arşiv değişti; güvenli kayıt için tekrar giriş yap."
        }
    }

    /** Bu metot JournalRepositoryImpl tarafından bir transaction içinde çağrılır. */
    suspend fun record(journalId: String, ownerUid: String?, changeType: String) {
        require(journalId.isNotBlank()) { "Anı kimliği boş olamaz." }
        val type = JournalSyncChangePolicy.requireChangeType(changeType)
        if (!JournalSyncChangePolicy.shouldQueue(ownerUid)) return

        // İşlem başka hesabın kimliğiyle mevcut sıraya yazılamaz.
        val previous = eventDao.findByJournalId(journalId)
        check(previous == null || previous.ownerUid == ownerUid) {
            "Anı değişikliği başka hesaba ait; yazma işlemi durduruldu."
        }
        eventDao.save(
            JournalSyncEventEntity(
                journalId = journalId,
                ownerUid = requireNotNull(ownerUid),
                changeType = type,
                state = JournalSyncChangePolicy.PENDING,
                changeVersion = JournalSyncChangePolicy.nextVersion(previous?.changeVersion),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }
}
