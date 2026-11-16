package com.loc.benimdunyam.data.sync.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.loc.benimdunyam.data.sync.local.SyncOutboxStore

/**
 * WorkManager tarafından başlatılan ilk güvenli işçi.
 *
 * Yalnızca yerel DIAGNOSTIC görevlerini tamamlar. Firestore'a hiçbir veri
 * GÖNDERMEZ, günlükleri okumaz ve Room'daki anılara dokunmaz.
 *
 * WorkManager'ın varsayılan başlatıcısını kullanıyoruz. Hilt nesnesini
 * EntryPoint üzerinden alarak mevcut Application/Manifest'i değiştirmiyoruz.
 */
class SyncFoundationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_OWNER_UID) ?: return Result.failure()
        return try {
            val store = EntryPointAccessors.fromApplication(
                applicationContext,
                SyncFoundationEntryPoint::class.java
            ).outboxStore()

            // WorkManager işlemi beklerken kullanıcı hesap değiştirmiş olabilir.
            if (!store.isAccountReady(uid)) return Result.failure()
            store.completeDiagnostics(uid)
            Result.success()
        } catch (e: CancellationException) {
            // İş iptal edildiğinde CoroutineWorker iptal sinyalini yutmaz.
            throw e
        } catch (_: IllegalStateException) {
            // Yetki / hesap değişimi sorununu başka hesaba tekrar denemeyiz.
            Result.failure()
        } catch (_: Exception) {
            // İşlem gerçekten geçici bir hata yüzünden yarıda kaldıysa yeniden denensin.
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val KEY_OWNER_UID = "sync_owner_uid"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncFoundationEntryPoint {
    fun outboxStore(): SyncOutboxStore
}
