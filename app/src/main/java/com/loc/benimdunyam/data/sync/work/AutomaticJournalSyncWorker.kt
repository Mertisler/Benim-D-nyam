package com.loc.benimdunyam.data.sync.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestoreException
import com.loc.benimdunyam.data.sync.remote.ManualJournalEventUploader
import com.loc.benimdunyam.data.sync.remote.SafeJournalMergeRepository
import com.loc.benimdunyam.data.sync.security.SyncAutoAuthorizationRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Faz 2F.11: Yalnızca açık izinli hesap için sınırlı senkronizasyon.
 * Silme olayı GÖNDERİLEBİLİR; uzak silme ASLA yerel Room'a uygulanmaz.
 * Sürüm çakışmalarında kayıt ezilmez; kullanıcıya manuel inceleme kalır.
 * Tek çalışmada en çok 5 gönderim ve 5 yeni anı içe alma yapılır.
 */
class AutomaticJournalSyncWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(UID)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()
        val entry = EntryPointAccessors.fromApplication(
            applicationContext, AutomaticJournalSyncEntryPoint::class.java
        )
        val scheduler = entry.scheduler()
        val permission = entry.permission()
        val scanBudget = entry.scanBudget()
        return try {
            SINGLE_RUN.withLock {
                if (!scheduler.isStarted(uid) || !scheduler.accountReady(uid)) {
                    return@withLock Result.success()
                }
                if (!permission.status(uid).enabled) return@withLock Result.success()

                // Mevcut şifreli göndericinin sunucuda tekrar/kimlik kontrolünü tekrar kullanırız.
                permission.withApprovedPassphrase(uid) { secret ->
                    ensureAllowed(uid, scheduler, permission)
                    entry.uploader().uploadPending(uid, secret)
                }
                ensureAllowed(uid, scheduler, permission)

                // Faz 2I.1: Her 15 dakikada bir TÜM bulutu taramak Firestore
                // okuma kotasını tüketebilir. Gönderim ayrı akıştır ve yukarıda
                // eskisi gibi yapılır. Yalnızca tam sunucu taraması seyreltilir.
                if (scanBudget.shouldScan(uid)) {
                    val manualVersion = scanBudget.requestVersion(uid)
                    var allImportableHandled = true

                    // Güvenli indirme yalnızca çakışmasız YENİ anılara uygulanır.
                    // Fingerprint / katalog doğrulaması ve ikinci sunucu okuması
                    // VERİ GÜVENLİĞİ için aynen korunur; bu maliyet azaltma
                    // amacıyla gevşetilmez.
                    permission.withApprovedPassphrase(uid) { secret ->
                        ensureAllowed(uid, scheduler, permission)
                        val preview = entry.merger().preview(uid, secret)
                        if (preview.eligible > 0 && preview.remoteDeletes == 0 &&
                            preview.remoteDeleteConflicts == 0 &&
                            preview.staleCatalog == 0 &&
                            preview.missingOrConflictingRelations == 0 &&
                            preview.pendingMoodFinishes == 0) {
                            ensureAllowed(uid, scheduler, permission)
                            val imported = entry.merger().importMissing(
                                uid = uid,
                                password = secret,
                                expectedFingerprint = preview.fingerprint,
                                userConfirmed = true
                            )
                            // İçe alma sınırı 5 anı. Kalan yeni anıları bir
                            // sonraki periyodik çalışmada tekrar kontrol ederiz.
                            allImportableHandled = imported.insertedJournals >= preview.eligible &&
                                    imported.pendingMoodFinishes == 0
                        }
                    }
                    ensureAllowed(uid, scheduler, permission)
                    if (allImportableHandled) {
                        // Başarı kaydı yalnızca TAM tarama ve uygun aktarım
                        // sonrasında tutulur. Hata halinde 6 saat kilitlenmez.
                        scanBudget.markSuccessfulScan(uid, manualVersion)
                    }
                }
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: FirebaseFirestoreException) {
            // Ağ kaynaklı sorunlar yeniden denenebilir; izin ve çakışma sorunları denenmez.
            if (error.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                error.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            } else Result.failure()
        } catch (_: Exception) {
            // Yanlış şifre, eski katalog, çakışma vb. için sonsuz otomatik tekrar yok.
            // Yerel kayıtlar ve PENDING olaylar korunur; manuel inceleme gerekir.
            Result.failure()
        }
    }

    private suspend fun ensureAllowed(
        uid: String,
        scheduler: AutomaticJournalSyncScheduler,
        permission: SyncAutoAuthorizationRepository
    ) {
        check(scheduler.isStarted(uid) && scheduler.accountReady(uid) &&
                permission.status(uid).enabled) { "Eşitleme izni veya hesap değişti." }
    }

    companion object {
        const val UID = "sync_owner_uid"
        // Aynı süreçte periyodik ve anlık işlerin aynı anda veri yazmasını engeller.
        private val SINGLE_RUN = Mutex()
    }
}

/** Mevcut Hilt Application/WorkerFactory düzenini değiştirmeyen giriş noktası. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutomaticJournalSyncEntryPoint {
    fun scheduler(): AutomaticJournalSyncScheduler
    fun permission(): SyncAutoAuthorizationRepository
    fun scanBudget(): SparkFirestoreScanBudget
    fun uploader(): ManualJournalEventUploader
    fun merger(): SafeJournalMergeRepository
}
