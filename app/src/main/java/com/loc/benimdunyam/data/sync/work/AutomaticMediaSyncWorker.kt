package com.loc.benimdunyam.data.sync.work

import com.loc.benimdunyam.core.cloud.SparkMediaPolicy

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import com.loc.benimdunyam.data.sync.media.EncryptedMediaSyncRepository
import com.loc.benimdunyam.data.sync.media.EncryptedMediaDeletionRepository
import com.loc.benimdunyam.data.sync.media.EncryptedPhotoLayoutSyncRepository
import com.loc.benimdunyam.data.sync.security.SyncAutoAuthorizationRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Faz 2G.2: Günde bir kez, yalnızca Wi-Fi'da 3 yükleme + 3 indirme ve
 * en fazla 2 yeni fotoğraf düzeni manifesti. Silme/güncelleme işlemi YOKTUR.
 * Eşzamanlı iki Worker aynı anda dosya yazamaz.
 */
class AutomaticMediaSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Spark kilidi eski kayıtlı WorkManager işlerini de ağ erişiminden ÖNCE durdurur.
        if (!SparkMediaPolicy.REMOTE_MEDIA_ENABLED) return Result.success()
        val uid = inputData.getString(UID)?.takeIf(String::isNotBlank) ?: return Result.failure()
        val entry = EntryPointAccessors.fromApplication(
            applicationContext, AutomaticMediaSyncEntryPoint::class.java
        )
        val scheduler = entry.mediaScheduler()
        val journal = entry.journalScheduler()
        val permission = entry.mediaPermission()

        suspend fun allowed() {
            check(scheduler.isEnabled(uid) && journal.isStarted(uid) && journal.accountReady(uid) &&
                    permission.status(uid).enabled) { "Medya izni veya aktif hesap değişmiş." }
        }

        return try {
            SINGLE_RUN.withLock {
                if (!scheduler.isEnabled(uid) || !journal.isStarted(uid) ||
                    !journal.accountReady(uid) || !permission.status(uid).enabled) {
                    return@withLock Result.success()
                }
                permission.withApprovedPassphrase(uid) { secret ->
                    allowed()
                    // En az bir eski bulut görseli varsa şifrenin doğru olması zorunlu.
                    entry.mediaRepository().verifyCloudSample(uid, secret)
                    entry.layoutRepository().verifyRemoteSample(uid, secret)
                    allowed()
                    // Faz 2G.3: Önce silme işaretleri. Silinmiş görsel yanlışlıkla
                    // eski bir cihazdan geri yüklenmesin; gerçek dosya silme YOK.
                    val markers = entry.deletionRepository().uploadPending(uid, secret)
                    allowed()
                    val sent = entry.mediaRepository().uploadNext(uid, secret, maxInspected = 12)
                    allowed()
                    val layoutSent = entry.layoutRepository().uploadNext(uid, secret)
                    allowed()
                    val received = entry.mediaRepository().restoreNext(uid, secret)
                    allowed()
                    val layoutReceived = entry.layoutRepository().restoreSafe(uid, secret, maxDocuments = 150)
                    allowed()
                    scheduler.recordResult(uid,
                        "${sent.completed} yüklendi · ${received.completed} indirildi · " +
                                "${layoutSent.uploaded} düzen yüklendi · " +
                                "${layoutReceived.applied} düzen uygulandı · " +
                                "${layoutReceived.conflicts} düzen çakışması · " +
                                "${markers.published} silme işareti")
                }
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: FirebaseFirestoreException) {
            if (error.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                error.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            } else {
                scheduler.recordResult(uid, "Firestore erişim hatası; medya kayıtları korunuyor.")
                Result.failure()
            }
        } catch (error: StorageException) {
            if (error.errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED ||
                error.errorCode == StorageException.ERROR_UNKNOWN) {
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            } else {
                scheduler.recordResult(uid, "Storage hatası; mevcut görseller korunuyor.")
                Result.failure()
            }
        } catch (_: Exception) {
            // Yanlış parola, çakışma veya eksik Firebase kuralı sonsuz denemeye girmez.
            scheduler.recordResult(uid, "Eşitleme güvenli biçimde durduruldu; kontrol gerekli.")
            Result.failure()
        }
    }

    companion object {
        const val UID = "bd_media_owner_uid"
        private val SINGLE_RUN = Mutex()
    }
}

/** Uygulamanın mevcut Hilt/WorkManager yapılandırmasını değiştirmeden erişim. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutomaticMediaSyncEntryPoint {
    fun mediaScheduler(): AutomaticMediaSyncScheduler
    fun journalScheduler(): AutomaticJournalSyncScheduler
    fun mediaPermission(): SyncAutoAuthorizationRepository
    fun mediaRepository(): EncryptedMediaSyncRepository
    fun layoutRepository(): EncryptedPhotoLayoutSyncRepository
    fun deletionRepository(): EncryptedMediaDeletionRepository
}
