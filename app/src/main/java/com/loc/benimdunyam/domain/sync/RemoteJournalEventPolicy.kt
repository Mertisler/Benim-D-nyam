package com.loc.benimdunyam.domain.sync

/**
 * Firestore'dan gelen şifreli olayın BAŞLIK bilgisini doğrulamak için kullanılan model.
 * Anı metnini veya aktarım şifresini içermez.
 */
data class VerifiedRemoteJournalHeader(
    val ownerUid: String,
    val journalId: String,
    val documentId: String,
    val operation: String,
    val revision: Long
)

/** Yerel anılara herhangi bir değişiklik uygulamayan çakışma sınıflandırması. */
enum class RemoteJournalLocalStatus {
    ONLY_REMOTE,
    LOCAL_PENDING,
    LOCAL_PRESENT_NEEDS_REVIEW,
    SAME_SYNCED_EVENT,
    REMOTE_DELETE_LOCAL_EXISTS,
    REMOTE_DELETE_ALREADY_ABSENT
}

/**
 * Faz 2F.4: Verinin SAHİPLİĞİNİ ve olay kimliğini doğrular.
 *
 * Bu sınıfta Room, Firebase veya Android bağımlılığı yoktur; test edilmesi kolaydır.
 * Uzak veri doğrulansa bile otomatik birleştirme kararı BURADA verilmez.
 */
object RemoteJournalEventPolicy {
    fun verify(
        expectedUid: String,
        documentId: String,
        headerUid: String?,
        headerHash: String?,
        headerOperation: String?,
        headerRevision: Long?,
        payloadUid: String,
        payloadJournalId: String,
        payloadOperation: String,
        payloadRevision: Long
    ): VerifiedRemoteJournalHeader {
        require(expectedUid.isNotBlank() && payloadJournalId.isNotBlank()) {
            "Hesap veya anı kimliği eksik."
        }
        require(headerUid == expectedUid && payloadUid == expectedUid) {
            "Bulut belgesi başka bir hesaba ait."
        }
        require(payloadOperation == JournalSyncChangePolicy.UPSERT ||
                payloadOperation == JournalSyncChangePolicy.DELETE) {
            "Desteklenmeyen bulut işlemi."
        }
        require(headerOperation == payloadOperation && headerRevision == payloadRevision &&
                payloadRevision > 0) {
            "Olayın şifreli içeriğiyle üst bilgisi uyuşmuyor."
        }
        require(headerHash == SyncEventIdentity.journalHash(expectedUid, payloadJournalId)) {
            "Anı kimliği özeti doğrulanamadı."
        }
        require(documentId == SyncEventIdentity.eventId(
            expectedUid, payloadJournalId, payloadRevision, payloadOperation
        )) { "Bulut olay kimliği doğrulanamadı." }

        return VerifiedRemoteJournalHeader(
            expectedUid, payloadJournalId, documentId, payloadOperation, payloadRevision
        )
    }

    /**
     * Sadece bilgilendirme içindir. Silme olayı hiçbir koşulda burada uygulanmaz.
     * Özellikle cihazda bekleyen yerel yazma varsa uzaktaki olay baskın sayılamaz.
     */
    fun classify(
        operation: String,
        localExists: Boolean,
        localPending: Boolean,
        sameSyncedEvent: Boolean
    ): RemoteJournalLocalStatus {
        if (localPending) return RemoteJournalLocalStatus.LOCAL_PENDING
        if (sameSyncedEvent) return RemoteJournalLocalStatus.SAME_SYNCED_EVENT
        return when (operation) {
            JournalSyncChangePolicy.UPSERT ->
                if (localExists) RemoteJournalLocalStatus.LOCAL_PRESENT_NEEDS_REVIEW
                else RemoteJournalLocalStatus.ONLY_REMOTE
            JournalSyncChangePolicy.DELETE ->
                if (localExists) RemoteJournalLocalStatus.REMOTE_DELETE_LOCAL_EXISTS
                else RemoteJournalLocalStatus.REMOTE_DELETE_ALREADY_ABSENT
            else -> throw IllegalArgumentException("Bilinmeyen anı işlemi.")
        }
    }
}
