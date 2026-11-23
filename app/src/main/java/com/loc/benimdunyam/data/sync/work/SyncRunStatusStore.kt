package com.loc.benimdunyam.data.sync.work

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Otomatik eşitlemenin son sonucunu cihazda saklar; sade bulut ekranı
 * "Son eşitleme: 5 dk önce" ya da anlaşılır bir hata gösterebilsin diye.
 *
 * Anı, parola veya teknik istisna metni saklanmaz; yalnızca zaman ve
 * kullanıcıya gösterilecek Türkçe bir açıklama. UID düz metin tutulmaz.
 */
@Singleton
class SyncRunStatusStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(
        "bd_sync_run_status_v1", Context.MODE_PRIVATE
    )

    data class Status(
        val lastSuccessAtMillis: Long,
        val lastFailureAtMillis: Long,
        val lastFailureMessage: String?
    ) {
        /** Son deneme başarısız mı (en son kayıt hata mı)? */
        val failing: Boolean get() = lastFailureAtMillis > lastSuccessAtMillis
    }

    private fun hash(uid: String): String {
        require(uid.isNotBlank()) { "Hesap kimliği boş olamaz." }
        return MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
            .take(32)
    }

    fun recordSuccess(uid: String, atMillis: Long = System.currentTimeMillis()) {
        preferences.edit().putLong("ok_${hash(uid)}", atMillis).apply()
    }

    fun recordFailure(uid: String, error: Throwable, atMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putLong("fail_${hash(uid)}", atMillis)
            .putString("fail_msg_${hash(uid)}", SyncFailureMessages.describe(error))
            .apply()
    }

    /**
     * Aşama 2: son taramada bulunan, kullanıcı kararı bekleyen çakışma sayısı
     * (iki cihazda düzenlenen + başka cihazda silinip burada değişen anılar).
     */
    fun recordConflicts(uid: String, count: Int) {
        preferences.edit().putInt("conflicts_${hash(uid)}", count.coerceAtLeast(0)).apply()
    }

    fun conflicts(uid: String): Int = preferences.getInt("conflicts_${hash(uid)}", 0)

    fun status(uid: String): Status = Status(
        lastSuccessAtMillis = preferences.getLong("ok_${hash(uid)}", 0L),
        lastFailureAtMillis = preferences.getLong("fail_${hash(uid)}", 0L),
        lastFailureMessage = preferences.getString("fail_msg_${hash(uid)}", null)
    )

    fun clear(uid: String) {
        val key = hash(uid)
        preferences.edit().remove("ok_$key").remove("fail_$key").remove("fail_msg_$key")
            .remove("conflicts_$key").apply()
    }
}

/** Teknik hataları kullanıcıya gösterilebilecek kısa Türkçe açıklamalara çevirir. */
object SyncFailureMessages {
    fun describe(error: Throwable): String {
        val firestore = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestore != null) {
            return when (firestore.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "İnternete ulaşılamadı. Bağlantı gelince yeniden denenecek."
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    "Sunucu erişimi reddetti. Yeniden giriş yapmayı dene."
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                    "Bulut kotası doldu. Daha sonra yeniden denenecek."
                else -> GENERIC
            }
        }
        if (generateSequence(error) { it.cause }.any { it is GeneralSecurityException }) {
            return "Eşitleme şifresi buluttaki verileri açamadı. Eşitlemeyi kapatıp doğru şifreyle yeniden aç."
        }
        val message = error.message.orEmpty()
        return when {
            "olay sınırı" in message ->
                "Bulutta çok fazla kayıt birikti; bu sürüm eşitlemeyi durdurdu."
            "hesap" in message.lowercase() || "arşiv" in message.lowercase() ->
                "Giriş yapılan hesap ile cihazdaki arşiv eşleşmiyor."
            else -> GENERIC
        }
    }

    private const val GENERIC =
        "Eşitleme tamamlanamadı. Anıların cihazında güvende; daha sonra yeniden denenecek."
}
