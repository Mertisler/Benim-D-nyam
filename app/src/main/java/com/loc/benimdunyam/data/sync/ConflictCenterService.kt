package com.loc.benimdunyam.data.sync

import com.loc.benimdunyam.data.sync.remote.JournalConflictCard
import com.loc.benimdunyam.data.sync.remote.JournalConflictRepository
import com.loc.benimdunyam.data.sync.remote.JournalConflictResolution
import com.loc.benimdunyam.data.sync.security.SyncAutoAuthorizationRepository
import com.loc.benimdunyam.data.sync.work.AutomaticJournalSyncScheduler
import com.loc.benimdunyam.data.sync.work.SyncRunStatusStore
import com.loc.benimdunyam.domain.sync.ConflictChoice
import javax.inject.Inject
import javax.inject.Singleton

/** Sade çakışma ekranına giden liste. */
data class ConflictCenterReport(
    /** Kullanıcının karar verebileceği çakışmalar. */
    val cards: List<JournalConflictCard>,
    /** Teknik nedenle bu ekranda çözülemeyenler (geliştirici araçlarında görünür). */
    val technicalOnly: Int
)

/**
 * Aşama 2 — şifresiz çakışma ekranının tek giriş noktası.
 *
 * Eşitleme açıkken şifre zaten cihazın Keystore'unda saklıdır. Bu yüzden
 * kullanıcıdan şifre istenmez; mevcut JournalConflictRepository aynı güvenlik
 * kontrolleriyle (onay anahtarı, sunucudan yeniden okuma, transaction içinde
 * yeniden doğrulama) kayıtlı anahtarla çağrılır.
 *
 * Her karardan sonra eşitleme hemen tetiklenir; seçim diğer cihazlara gider.
 */
@Singleton
class ConflictCenterService @Inject constructor(
    private val repository: JournalConflictRepository,
    private val permission: SyncAutoAuthorizationRepository,
    private val scheduler: AutomaticJournalSyncScheduler,
    private val runStatus: SyncRunStatusStore
) {

    suspend fun load(uid: String): ConflictCenterReport {
        requireSyncEnabled(uid)
        val report = permission.withApprovedPassphrase(uid) { secret ->
            repository.inspect(uid, secret.copyOf())
        }
        val actionable = report.cards.filter { it.choices.isNotEmpty() }
        // Sade karttaki sayı ekranda görülenle uyumlu kalsın.
        runStatus.recordConflicts(uid, actionable.size)
        return ConflictCenterReport(
            cards = actionable,
            technicalOnly = report.totalConflicts - actionable.size
        )
    }

    /** Kullanıcının seçimini uygular. Seçim kartın sunduğu seçeneklerden biri olmalıdır. */
    suspend fun choose(uid: String, card: JournalConflictCard, choice: ConflictChoice) {
        requireSyncEnabled(uid)
        require(choice in card.choices) { "Bu çakışma için geçersiz seçim." }
        permission.withApprovedPassphrase(uid) { secret ->
            when (choice) {
                ConflictChoice.KEEP_THIS_DEVICE ->
                    resolve(uid, secret, card, JournalConflictResolution.KEEP_LOCAL)
                ConflictChoice.TAKE_OTHER_DEVICE ->
                    resolve(uid, secret, card, JournalConflictResolution.ACCEPT_REMOTE)
                ConflictChoice.MOVE_TO_TRASH ->
                    resolve(uid, secret, card, JournalConflictResolution.MOVE_LOCAL_TO_TRASH)
                ConflictChoice.KEEP_BOTH -> {
                    // Diğer cihazdaki metin ayrı bir anı olarak eklenir; bu telefondaki kalır.
                    // Kopya, orijinal anının kaydını değiştirmediği için aynı onay anahtarı geçerlidir.
                    if (!card.savedAsSeparateCopy) {
                        repository.preserveSeparateCopy(
                            uid = uid,
                            password = secret.copyOf(),
                            documentId = card.remoteDocumentId,
                            expectedConfirmationKey = card.confirmationKey,
                            remoteHeadCount = card.simultaneousRemoteHeads,
                            confirmed = true
                        )
                    }
                    resolve(uid, secret, card, JournalConflictResolution.KEEP_LOCAL)
                }
            }
        }
        runCatching { scheduler.runNow(uid) }
    }

    private suspend fun resolve(
        uid: String,
        secret: CharArray,
        card: JournalConflictCard,
        resolution: JournalConflictResolution
    ) = repository.resolve(
        uid = uid,
        password = secret.copyOf(),
        documentId = card.remoteDocumentId,
        expectedConfirmationKey = card.confirmationKey,
        remoteHeadCount = card.simultaneousRemoteHeads,
        resolution = resolution,
        confirmed = true
    )

    private suspend fun requireSyncEnabled(uid: String) {
        check(scheduler.isStarted(uid) && permission.status(uid).enabled) {
            "Çakışmaları görmek için önce bulut eşitlemeyi aç."
        }
    }
}
