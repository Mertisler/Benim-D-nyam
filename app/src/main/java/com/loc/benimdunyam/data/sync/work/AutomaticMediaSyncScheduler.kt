package com.loc.benimdunyam.data.sync.work

import com.loc.benimdunyam.core.cloud.SparkMediaPolicy

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
import com.loc.benimdunyam.data.sync.media.EncryptedMediaSyncRepository
import com.loc.benimdunyam.data.sync.media.EncryptedPhotoLayoutSyncRepository
import com.loc.benimdunyam.data.sync.security.SyncAutoAuthorizationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Kullanıcıya gösterilecek yerel aktarım durumu; şifre veya fotoğraf taşımaz. */
data class AutomaticMediaSyncStatus(
    val enabled: Boolean,
    val prerequisitesReady: Boolean,
    val lastRunAtMillis: Long = 0,
    val lastResult: String = "Henüz çalışmadı."
)

/**
 * Faz 2G.2: Medya eşitlemesi metin eşitlemesinden TAMAMEN ayrı bir izin ister.
 * Wi-Fi / ölçülmeyen bağlantı ve pil yeterliliği koşulları maliyet kontrolü içindir.
 * Worker InputData içine asla parola/fotoğraf yerleştirilmez.
 */
@Singleton
class AutomaticMediaSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journalScheduler: AutomaticJournalSyncScheduler,
    private val authorization: SyncAutoAuthorizationRepository,
    private val media: EncryptedMediaSyncRepository,
    private val layouts: EncryptedPhotoLayoutSyncRepository
) {
    private val prefs = context.getSharedPreferences("bd_media_auto_sync_v1", Context.MODE_PRIVATE)
    private val work get() = WorkManager.getInstance(context)

    private fun key(uid: String): String = MessageDigest.getInstance("SHA-256")
        .digest(uid.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }.take(32)

    private fun periodic(uid: String) = "bd_media_periodic_${key(uid)}"
    private fun immediate(uid: String) = "bd_media_immediate_${key(uid)}"
    fun isEnabled(uid: String) = SparkMediaPolicy.REMOTE_MEDIA_ENABLED &&
            prefs.getBoolean("enabled_${key(uid)}", false)

    fun status(uid: String): AutomaticMediaSyncStatus = AutomaticMediaSyncStatus(
        enabled = isEnabled(uid),
        prerequisitesReady = journalScheduler.isStarted(uid) && journalScheduler.accountReady(uid),
        lastRunAtMillis = prefs.getLong("ran_${key(uid)}", 0L),
        lastResult = prefs.getString("result_${key(uid)}", "Henüz çalışmadı.").orEmpty()
    )

    /** İzin ancak aynı hesapta metin eşitlemesi açık ve Keystore şifresi doğrulanmışsa verilir. */
    suspend fun enable(uid: String): Boolean {
        SparkMediaPolicy.requireRemoteMedia()
        check(journalScheduler.accountReady(uid) && journalScheduler.isStarted(uid)) {
            "Önce bu hesap için metin eşitlemesini etkinleştir."
        }
        val state = authorization.status(uid)
        check(state.enabled && state.remoteSampleVerified) {
            "Önce otomatik eşitleme cihaz anahtarını doğrula."
        }
        // Bulutta medya varsa parolanın GERÇEKTEN çözebildiğini kontrol et.
        val verified = authorization.withApprovedPassphrase(uid) { secret ->
            val mediaVerified = media.verifyCloudSample(uid, secret)
            layouts.verifyRemoteSample(uid, secret)
            mediaVerified
        }
        check(journalScheduler.accountReady(uid)) { "Hesap değiştirilmiş." }
        prefs.edit().putBoolean("enabled_${key(uid)}", true).commit().also { check(it) }
        try {
            val request = PeriodicWorkRequestBuilder<AutomaticMediaSyncWorker>(24, TimeUnit.HOURS)
                .setInputData(workDataOf(AutomaticMediaSyncWorker.UID to uid))
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            work.enqueueUniquePeriodicWork(periodic(uid), ExistingPeriodicWorkPolicy.UPDATE, request)
        } catch (error: Exception) {
            prefs.edit().putBoolean("enabled_${key(uid)}", false).commit()
            work.cancelUniqueWork(periodic(uid))
            throw error
        }
        return verified
    }

    /** İzin iptalinden sonra yeni Worker başlatılamaz; çalışan iş de iptal edilir. */
    fun disable(uid: String) {
        check(prefs.edit().putBoolean("enabled_${key(uid)}", false).commit())
        work.cancelUniqueWork(periodic(uid))
        work.cancelUniqueWork(immediate(uid))
    }

    fun runNow(uid: String) {
        SparkMediaPolicy.requireRemoteMedia()
        check(isEnabled(uid) && journalScheduler.isStarted(uid) && journalScheduler.accountReady(uid)) {
            "Bu hesap için otomatik medya eşitlemesi açık değil."
        }
        val request = OneTimeWorkRequestBuilder<AutomaticMediaSyncWorker>()
            .setInputData(workDataOf(AutomaticMediaSyncWorker.UID to uid))
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        work.enqueueUniqueWork(immediate(uid), ExistingWorkPolicy.KEEP, request)
    }

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()

    fun recordResult(uid: String, message: String) {
        if (!isEnabled(uid)) return
        prefs.edit()
            .putLong("ran_${key(uid)}", System.currentTimeMillis())
            .putString("result_${key(uid)}", message.take(240))
            .apply()
    }
}
