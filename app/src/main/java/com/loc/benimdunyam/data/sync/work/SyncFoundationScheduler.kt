package com.loc.benimdunyam.data.sync.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.loc.benimdunyam.data.sync.local.SyncOutboxStore
import com.loc.benimdunyam.domain.model.AccountMigrationRules
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager'a aynı hesap için tek bir güvenli denetim işi verir.
 * İnternet koşulu ilerideki bulut aktarımı için prova niteliğindedir.
 * Şimdiki Worker yalnızca yerel tanılama görevini işler.
 */
@Singleton
class SyncFoundationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outbox: SyncOutboxStore
) {
    fun uniqueWorkName(uid: String): String =
        "sync_foundation_${AccountMigrationRules.folderKey(uid).take(24)}"

    suspend fun scheduleDiagnostic(uid: String) {
        check(outbox.isAccountReady(uid)) { "Hesap veya yerel çalışma alanı hazır değil." }
        // Önce kalıcı kayıt; WorkManager kurulumu başarısız olursa kayıt kaybolmaz.
        awaitEnqueue(uid)
    }

    private suspend fun awaitEnqueue(uid: String) {
        outbox.enqueueDiagnostic(uid)
        val request = OneTimeWorkRequestBuilder<SyncFoundationWorker>()
            .setInputData(Data.Builder().putString(SyncFoundationWorker.KEY_OWNER_UID, uid).build())
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(uid), ExistingWorkPolicy.REPLACE, request
        )
    }

    // Yeni tanılama isteği eskisinin yerini alır; böylece son eklenen
    // yerel PENDING kayıtları, bitmek üzere olan eski bir iş yüzünden takılmaz.
    // Gerçek bulut işlemleri başladığında REPLACE politikası ayrıca değerlendirilecektir.
    fun workManager(): WorkManager = WorkManager.getInstance(context)
}
