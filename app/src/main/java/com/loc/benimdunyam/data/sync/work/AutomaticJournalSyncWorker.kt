package com.loc.benimdunyam.data.sync.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestoreException
import com.loc.benimdunyam.data.sync.remote.ManualJournalEventUploader
import com.loc.benimdunyam.data.sync.remote.SafeJournalMergeRepository
import com.loc.benimdunyam.data.sync.remote.SyncDependencyCoverageRepository
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
 *
 * Faz A:
 * - Olay gönderildikten sonra şifreli ilişki kataloğu otomatik yenilenir;
 *   aksi halde diğer cihaz yeni olayları "katalogdan yeni" sayıp hiç almaz.
 * - İçe alma OLAY BAZINDA yapılır: güvenli yeni anılar alınır, silme /
 *   eksik ilişki / katalogdan yeni olaylar yalnızca atlanır. Önceden tek bir
 *   uzak silme bile otomatik içe almayı kalıcı olarak durduruyordu.
 * - Yarım kalan ruh hali aktarımları otomatik tamamlanmaya çalışılır.
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

                // Faz A: Bu cihazın gönderdiği olaylar diğer cihazda içe alınabilsin
                // diye ilişki kataloğu yenilenir. Yalnızca yeni olay yazıldıysa
                // (veya önceki yenileme başarısız olduysa) çalışır; Spark kotası korunur.
                val freshness = entry.catalogFreshness()
                if (freshness.isStale(uid)) {
                    val versionAtStart = freshness.changeVersion(uid)
                    permission.withApprovedPassphrase(uid) { secret ->
                        ensureAllowed(uid, scheduler, permission)
                        entry.catalog().uploadCatalog(uid, secret)
                    }
                    freshness.markPublished(uid, versionAtStart)
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
                        val merger = entry.merger()

                        // Önceki çalışmadan yarım kalan ruh halleri varsa önce onları
                        // tamamla. İşlem tekrarlanabilir ve mevcut ruh halini ezmez.
                        if (AutomaticJournalSyncPolicy.shouldResumePendingMoods(
                                merger.pendingMoodCount(uid))) {
                            merger.resumePendingMoods(uid)
                            ensureAllowed(uid, scheduler, permission)
                        }

                        val preview = merger.preview(uid, secret)
                        var inserted = 0
                        var pendingAfter = preview.pendingMoodFinishes
                        if (AutomaticJournalSyncPolicy.canImport(
                                preview.eligible, preview.pendingMoodFinishes)) {
                            ensureAllowed(uid, scheduler, permission)
                            // importMissing yalnızca güvenlik kontrollerinden geçen
                            // "eligible" olayları ekler; diğerleri atlanır.
                            val imported = merger.importMissing(
                                uid = uid,
                                password = secret,
                                expectedFingerprint = preview.fingerprint,
                                userConfirmed = true
                            )
                            inserted = imported.insertedJournals
                            pendingAfter = imported.pendingMoodFinishes
                        }

                        // Faz C: yalnızca diğer cihazda düzenlenmiş anılar uygulanır.
                        // Bu cihazda gönderilmemiş değişiklik olan anılar asla ezilmez.
                        var editsApplied = 0
                        if (AutomaticJournalSyncPolicy.canApplyEdits(preview.remoteEdits)) {
                            ensureAllowed(uid, scheduler, permission)
                            editsApplied = merger.applyRemoteEdits(
                                uid = uid,
                                password = secret,
                                expectedFingerprint = preview.fingerprint,
                                userConfirmed = true
                            ).updatedJournals
                        }

                        // Tek çalışma sınırı 5 anı. Kalanlar için tarama ertelenmez.
                        allImportableHandled = AutomaticJournalSyncPolicy.scanCompleted(
                            eligibleBefore = preview.eligible,
                            inserted = inserted,
                            pendingMoodFinishesAfter = pendingAfter,
                            editsBefore = preview.remoteEdits,
                            editsApplied = editsApplied
                        )
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
    fun catalog(): SyncDependencyCoverageRepository
    fun catalogFreshness(): SyncCatalogFreshness
}
