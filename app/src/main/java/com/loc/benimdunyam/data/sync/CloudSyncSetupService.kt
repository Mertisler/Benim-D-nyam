package com.loc.benimdunyam.data.sync

import androidx.work.WorkInfo
import androidx.work.WorkManager
import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalSyncEventDao
import com.loc.benimdunyam.data.local.dao.TrashedJournalDao
import com.loc.benimdunyam.data.sync.remote.SyncDependencyCoverageRepository
import com.loc.benimdunyam.data.sync.security.SyncAutoAuthorizationRepository
import com.loc.benimdunyam.data.sync.work.AutomaticJournalSyncScheduler
import com.loc.benimdunyam.data.sync.work.SyncRunStatusStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Sade bulut ekranında gösterilen, ağ gerektirmeyen özet. */
data class CloudSyncOverview(
    val enabled: Boolean,
    val lastSuccessAtMillis: Long,
    val lastFailureAtMillis: Long,
    val lastFailureMessage: String?,
    /** Buluta gönderilmeyi bekleyen değişiklikler + henüz sıraya alınmamış eski anılar. */
    val waitingToUpload: Int,
    /** İki cihazda farklı düzenlenmiş, kullanıcı kararı bekleyen anılar. */
    val conflicts: Int,
    val recentlyDeleted: Int
) {
    val failing: Boolean get() = lastFailureAtMillis > lastSuccessAtMillis
}

/** Kurulum ekranının hangi formu göstereceği. */
enum class CloudSyncCloudState {
    /** Bu hesapta henüz eşitleme verisi yok: yeni şifre belirlenir (iki kez). */
    EMPTY,
    /** Başka cihaz eşitleme kurmuş: aynı şifre girilir ve doğrulanır. */
    EXISTING
}

/**
 * Sade bulut ekranının tek giriş noktası.
 *
 * Mevcut güvenli parçaları değiştirmeden birleştirir:
 *  - Şifre doğrulama ve Keystore saklama: SyncAutoAuthorizationRepository
 *  - Arka plan planı: AutomaticJournalSyncScheduler
 *  - İlk aktarım ve gönderim: otomatik işçi (AutomaticJournalSyncWorker)
 *
 * Önceden yeni bir hesapta eşitleme ancak manuel bir test gönderiminden sonra
 * açılabiliyordu. Burada bulut boşsa şifreli ilişki kataloğu hemen oluşturulur;
 * böylece şifre bulutta doğrulanabilir hale gelir ve plan tek adımda başlar.
 */
@Singleton
class CloudSyncSetupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val permission: SyncAutoAuthorizationRepository,
    private val scheduler: AutomaticJournalSyncScheduler,
    private val catalog: SyncDependencyCoverageRepository,
    private val runStatus: SyncRunStatusStore,
    private val journalDao: JournalDao,
    private val eventDao: JournalSyncEventDao,
    private val trashDao: TrashedJournalDao
) {

    /** Yalnızca yerel bilgiler; ağ isteği yapılmaz. */
    suspend fun overview(uid: String): CloudSyncOverview = withContext(Dispatchers.IO) {
        val enabled = scheduler.isStarted(uid) && runCatching { permission.status(uid).enabled }.getOrDefault(false)
        val status = runStatus.status(uid)
        CloudSyncOverview(
            enabled = enabled,
            lastSuccessAtMillis = status.lastSuccessAtMillis,
            lastFailureAtMillis = status.lastFailureAtMillis,
            lastFailureMessage = status.lastFailureMessage,
            waitingToUpload = eventDao.pendingUpserts(uid) + eventDao.pendingDeletes(uid) +
                    journalDao.countUntrackedForBootstrap(),
            conflicts = eventDao.conflictCount(uid),
            recentlyDeleted = trashDao.count()
        )
    }

    /** Kurulum formunu seçmek için bulutta eşitleme verisi olup olmadığına SUNUCUDAN bakar. */
    suspend fun cloudState(uid: String): CloudSyncCloudState = withContext(Dispatchers.IO) {
        check(scheduler.accountReady(uid)) { "Hesap ve cihazdaki arşiv eşleşmiyor." }
        val user = firestore.collection("users").document(uid)
        val hasEvents = !user.collection("journal_change_events").limit(1)
            .get(Source.SERVER).await().isEmpty
        val hasCatalog = hasEvents || !user.collection("sync_dependency_snapshots").limit(1)
            .get(Source.SERVER).await().isEmpty
        if (hasEvents || hasCatalog) CloudSyncCloudState.EXISTING else CloudSyncCloudState.EMPTY
    }

    /**
     * Eşitlemeyi tek adımda açar.
     *
     * Bulut boşsa: şifre cihaza kaydedilir, şifreli ilişki kataloğu yüklenir
     * (şifre artık bulutta doğrulanabilir), izin doğrulanmış olarak yenilenir.
     * Bulutta veri varsa: şifre buluttaki kayıtla doğrulanır; yanlışsa hiçbir şey kaydedilmez.
     * Ardından arka plan planı başlar; eski anılar ve değişiklikler işçi tarafından gönderilir.
     *
     * Parola dizisi işlem sonunda sıfırlanır.
     */
    suspend fun enable(uid: String, password: CharArray): Unit = withContext(Dispatchers.IO) {
        try {
            require(password.size in 12..128) { "Eşitleme şifresi 12–128 karakter olmalı." }
            when (cloudState(uid)) {
                CloudSyncCloudState.EXISTING -> permission.enable(uid, password.copyOf())
                CloudSyncCloudState.EMPTY -> {
                    permission.enable(uid, password.copyOf())
                    catalog.uploadCatalog(uid, password.copyOf())
                    // Katalog artık bu şifreyle açılabiliyor: izni doğrulanmış olarak yenile.
                    permission.enable(uid, password.copyOf())
                }
            }
            check(permission.status(uid).remoteSampleVerified) {
                "Şifre bulutta doğrulanamadı. Tekrar dene."
            }
            runStatus.clear(uid)
            scheduler.start(uid)
        } catch (error: Exception) {
            // Yarım kalmış izin, yanlış şifreyle arka plan işi başlatmasın.
            runCatching { scheduler.stop(uid) }
            runCatching { permission.disable(uid) }
            throw error
        } finally {
            password.fill('\u0000')
        }
    }

    fun syncNow(uid: String) = scheduler.runNow(uid)

    /** Bu cihazda eşitlemeyi durdurur. Buluttaki ve cihazdaki anılar silinmez. */
    suspend fun disable(uid: String): Unit = withContext(Dispatchers.IO) {
        scheduler.stop(uid)
        permission.disable(uid)
    }

    /** Eşitleme işi şu an çalışıyor mu (periyodik veya anlık). */
    fun observeRunning(uid: String): Flow<Boolean> {
        val manager = WorkManager.getInstance(context)
        val periodic = manager.getWorkInfosForUniqueWorkFlow(scheduler.periodicWorkName(uid))
        val immediate = manager.getWorkInfosForUniqueWorkFlow(scheduler.immediateWorkName(uid))
        return combine(periodic, immediate) { a, b ->
            (a + b).any { it.state == WorkInfo.State.RUNNING }
        }
    }
}
