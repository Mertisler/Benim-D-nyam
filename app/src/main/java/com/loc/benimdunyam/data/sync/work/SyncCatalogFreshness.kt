package com.loc.benimdunyam.data.sync.work

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Faz A — şifreli ilişki kataloğunun (sync_dependency_snapshots) güncel olup
 * olmadığını cihazda takip eder.
 *
 * Diğer cihaz bir olayı ancak olay zamanı, en son kataloğun zamanından
 * ÖNCEYSE içe alır. Katalog yalnızca ilk kurulumda yüklendiğinde, sonradan
 * gönderilen her olay "katalogdan yeni" sayılır ve hiç içe alınmaz. Bu yüzden
 * bu cihaz bir olayı buluta başarıyla yazdığında katalog "eskimiş" işaretlenir;
 * otomatik işçi bir sonraki adımda kataloğu yeniler.
 *
 * Sayaç tabanlıdır: yenileme sırasında yeni bir olay gönderilirse, yenileme
 * bittiğinde o işaret yanlışlıkla silinmez (SparkFirestoreScanBudget ile aynı desen).
 * UID düz metin olarak saklanmaz; anı, parola veya token yazılmaz.
 */
@Singleton
class SyncCatalogFreshness @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(
        "bd_sync_catalog_freshness_v1", Context.MODE_PRIVATE
    )
    private val lock = Any()

    private fun hash(uid: String): String {
        require(uid.isNotBlank()) { "Hesap kimliği boş olamaz." }
        return MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
            .take(32)
    }

    private fun changeKey(uid: String) = "change_${hash(uid)}"
    private fun publishedKey(uid: String) = "published_${hash(uid)}"

    /** Bir olay buluta yazıldı: katalog artık eksik olabilir. */
    fun markStale(uid: String) = synchronized(lock) {
        val key = changeKey(uid)
        val current = preferences.getLong(key, 0L)
        val next = if (current == Long.MAX_VALUE) 1L else current + 1L
        val edit = preferences.edit().putLong(key, next)
        if (current == Long.MAX_VALUE) edit.putLong(publishedKey(uid), 0L)
        check(edit.commit()) { "Katalog durumu saklanamadı." }
    }

    fun isStale(uid: String): Boolean = synchronized(lock) {
        preferences.getLong(changeKey(uid), 0L) != preferences.getLong(publishedKey(uid), 0L)
    }

    /** Yenileme BAŞLAMADAN önce okunur. */
    fun changeVersion(uid: String): Long = synchronized(lock) {
        preferences.getLong(changeKey(uid), 0L)
    }

    /**
     * Katalog başarıyla yüklendikten sonra çağrılır. Yalnızca yenileme
     * başladığında bilinen değişiklikler kapsanmış sayılır.
     */
    fun markPublished(uid: String, versionAtStart: Long) = synchronized(lock) {
        val current = preferences.getLong(changeKey(uid), 0L)
        val published = preferences.getLong(publishedKey(uid), 0L)
        val acknowledged = if (versionAtStart <= current) maxOf(published, versionAtStart) else published
        check(preferences.edit().putLong(publishedKey(uid), acknowledged).commit()) {
            "Katalog durumu saklanamadı."
        }
    }
}
