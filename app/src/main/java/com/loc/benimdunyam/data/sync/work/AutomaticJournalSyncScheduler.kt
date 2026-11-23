package com.loc.benimdunyam.data.sync.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.sync.security.SyncAutoAuthorizationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Faz 2F.11: Kullanıcının ayrıca başlattığı senkronizasyon planı.
 * Keystore izni tek başına senkronizasyonu AÇMAZ.
 * WorkManager veri alanına UID dışında anı, şifre veya token koyulmaz.
 */
@Singleton
class AutomaticJournalSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val session: ActiveWorkspaceSession,
    private val permission: SyncAutoAuthorizationRepository,
    private val scanBudget: SparkFirestoreScanBudget
) {
    private val preferences = context.getSharedPreferences("bd_auto_sync_v1", Context.MODE_PRIVATE)
    private val manager: WorkManager get() = WorkManager.getInstance(context)

    private fun key(uid: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return hash.take(32)
    }

    private fun periodic(uid: String) = "bd_periodic_sync_${key(uid)}"
    private fun immediate(uid: String) = "bd_immediate_sync_${key(uid)}"

    /** Sade ekranın "Eşitleniyor…" durumunu izlemesi için. */
    fun periodicWorkName(uid: String): String = periodic(uid)
    fun immediateWorkName(uid: String): String = immediate(uid)

    /** Bu tercih yalnızca yerel planlama iznidir; tek başına ağ yetkisi değildir. */
    fun isStarted(uid: String): Boolean = preferences.getBoolean("started_${key(uid)}", false)

    fun accountReady(uid: String): Boolean = uid.isNotBlank() &&
            auth.currentUser?.uid == uid && session.isReadyFor(uid) && !session.needsRestart()

    /**
     * Salt yerel tarama bilgisi. Gerçek Firestore kullanım/fatura sayısı değildir.
     * Kullanıcıya bu bilgiyi göstermek hiçbir ağ okuması yapmaz.
     */
    fun scanStatus(uid: String): SparkFirestoreScanBudget.Status = scanBudget.status(uid)

    /** Önce Keystore izni, ardından kullanıcının ayrı başlatma onayı gerekir. */
    suspend fun start(uid: String) {
        check(accountReady(uid)) { "Firebase hesabı veya yerel arşiv hazır değil." }
        check(permission.status(uid).enabled) { "Önce cihaz anahtarı iznini aç." }
        // İlk bulut olayı manuel doğrulanmamışsa yanlış şifreyle yeni olay üretmeyelim.
        check(permission.status(uid).remoteSampleVerified) {
            "Önce aynı şifreyle bir test anısını manuel buluta gönder ve cihaz iznini yenile."
        }
        preferences.edit().putBoolean("started_${key(uid)}", true).apply()
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<AutomaticJournalSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setInputData(workDataOf(AutomaticJournalSyncWorker.UID to uid))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            manager.enqueueUniquePeriodicWork(
                periodic(uid), ExistingPeriodicWorkPolicy.KEEP, request
            )
            runNow(uid)
        } catch (error: Exception) {
            preferences.edit().putBoolean("started_${key(uid)}", false).apply()
            manager.cancelUniqueWork(periodic(uid))
            throw error
        }
    }

    /** Kullanıcı devre dışı bırakınca yeni işler alınmaz; Worker her aşamada tekrar kontrol eder. */
    fun stop(uid: String) {
        preferences.edit().putBoolean("started_${key(uid)}", false).apply()
        manager.cancelUniqueWork(periodic(uid))
        manager.cancelUniqueWork(immediate(uid))
        // İzin kaldırıldığında eski "şimdi tara" isteği canlı kalmaz.
        scanBudget.cancelPendingManualScan(uid)
    }

    fun runNow(uid: String) {
        check(accountReady(uid) && isStarted(uid)) { "Otomatik eşitleme başlatılmadı." }
        // Anlık istek 6 saatlik otomatik sınırı aşar. Önce istek saklanır;
        // WorkManager KEEP mevcut bir işi korusa bile sıradaki worker bunu görecektir.
        scanBudget.requestManualScan(uid)
        val request = OneTimeWorkRequestBuilder<AutomaticJournalSyncWorker>()
            .setInputData(workDataOf(AutomaticJournalSyncWorker.UID to uid))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniqueWork(immediate(uid), ExistingWorkPolicy.KEEP, request)
    }
}
