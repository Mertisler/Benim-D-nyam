package com.loc.benimdunyam.data.sync.work

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Faz 2I.1 — Spark planı için yalnızca İSTEMCİ TARAMA SIKLIĞI denetimi.
 *
 * Bu sınıf bir güvenlik / sunucu yetkilendirme mekanizması DEĞİLDİR.
 * Gerçek Firestore fatura sayısını ölçmez. Yalnızca başarılı otomatik tam
 * taramaların zamanlarını ve kullanıcının ayrı "Şimdi eşitle" isteğini saklar.
 * Anı metni, parola, token, açık UID veya fotoğraf bu tercihlere yazılmaz.
 *
 * NOT: Sunucu taramasının daha seyrek olması başka cihazdaki anıların
 * otomatik görünmesini geciktirebilir. Manuel istek bekleme süresini aşar.
 */
@Singleton
class SparkFirestoreScanBudget @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(
        "bd_spark_firestore_read_budget_v1", Context.MODE_PRIVATE
    )
    private val lock = Any()

    data class Status(
        val lastSuccessfulScanAtMillis: Long,
        val nextEarliestScanAtMillis: Long,
        val manualRequestPending: Boolean
    )

    /** Güvenlik doğrulaması tamamlanmış tam bulut taramaları arasındaki minimum süre. */
    val minimumAutoScanIntervalMillis: Long = TimeUnit.HOURS.toMillis(6)

    private fun hash(uid: String): String {
        require(uid.isNotBlank()) { "Hesap kimliği boş olamaz." }
        return MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
            .take(32)
    }

    private fun lastKey(uid: String) = "last_${hash(uid)}"
    private fun requestKey(uid: String) = "request_${hash(uid)}"
    private fun acknowledgedKey(uid: String) = "ack_${hash(uid)}"

    /** Kullanıcının isteğini ayrı kaydederiz: WorkManager KEEP olsa da isteği kaybolmaz. */
    fun requestManualScan(uid: String) = synchronized(lock) {
        val key = requestKey(uid)
        val lastRequest = preferences.getLong(key, 0L)
        val nextRequest = if (lastRequest == Long.MAX_VALUE) 1L else lastRequest + 1L
        // Taşma son derece uzak olasılıktır; önceden onaylanmış sayaç da sıfırlanır.
        val edit = preferences.edit().putLong(key, nextRequest)
        if (lastRequest == Long.MAX_VALUE) edit.putLong(acknowledgedKey(uid), 0L)
        check(edit.commit()) { "Manuel eşitleme isteği saklanamadı." }
    }

    /** Tarama BAŞLAMADAN önceki istek sürümü alınır; yeni istek kazara kapatılmaz. */
    fun requestVersion(uid: String): Long = synchronized(lock) {
        preferences.getLong(requestKey(uid), 0L)
    }

    fun shouldScan(uid: String, nowMillis: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val pending = preferences.getLong(requestKey(uid), 0L) !=
                preferences.getLong(acknowledgedKey(uid), 0L)
        val previous = preferences.getLong(lastKey(uid), 0L)
        // Saat geriye alınmışsa kullanıcıyı süresiz engellemeyelim.
        pending || previous <= 0L || nowMillis < previous ||
                nowMillis - previous >= minimumAutoScanIntervalMillis
    }

    /**
     * Ancak TAM bir sunucu taraması başarıyla bittiğinde çağrılır.
     * Ağ/şifre/bütünlük hatalarında, eksik alınmış partide veya iptalde ÇAĞRILMAZ.
     */
    fun markSuccessfulScan(
        uid: String,
        requestVersionAtStart: Long,
        atMillis: Long = System.currentTimeMillis()
    ) = synchronized(lock) {
        val currentRequest = preferences.getLong(requestKey(uid), 0L)
        val currentAck = preferences.getLong(acknowledgedKey(uid), 0L)
        val acknowledged = if (requestVersionAtStart <= currentRequest) {
            maxOf(currentAck, requestVersionAtStart)
        } else currentAck
        preferences.edit()
            .putLong(lastKey(uid), atMillis)
            .putLong(acknowledgedKey(uid), acknowledged)
            .apply()
    }

    fun status(uid: String): Status = synchronized(lock) {
        val last = preferences.getLong(lastKey(uid), 0L)
        val next = if (last > 0L && last <= Long.MAX_VALUE - minimumAutoScanIntervalMillis) {
            last + minimumAutoScanIntervalMillis
        } else 0L
        Status(
            lastSuccessfulScanAtMillis = last,
            nextEarliestScanAtMillis = next,
            manualRequestPending = preferences.getLong(requestKey(uid), 0L) !=
                    preferences.getLong(acknowledgedKey(uid), 0L)
        )
    }

    /** Otomatik eşitleme kapatıldığında bekleyen manuel istek de kapatılır. */
    fun cancelPendingManualScan(uid: String) = synchronized(lock) {
        val requested = preferences.getLong(requestKey(uid), 0L)
        preferences.edit().putLong(acknowledgedKey(uid), requested).apply()
    }
}
