package com.loc.benimdunyam.core.cloud

/**
 * Faz 2H.4 — Kullanıcının bilinçli Spark (ücretsiz) plan tercihi.
 *
 * Bu ayar Firebase fatura planını ağdan TESPİT ETMEZ. Projeye özgü, kodla
 * sabitlenmiş bir güvenlik kilididir: Blaze'e geçilse bile geliştirici yeniden
 * değerlendirmeden medya/Cloud Functions erişimi otomatik açılmaz.
 *
 * Room fotoğrafları, yerel fotoğraf düzeni ve metin günlüğü eşitlemesi etkilenmez.
 */
object SparkMediaPolicy {
    /** Cloud Storage ve fiziksel medya temizleme varsayılan olarak kapalıdır. */
    const val REMOTE_MEDIA_ENABLED: Boolean = false

    const val MESSAGE: String =
        "Ücretsiz Spark modunda bulut fotoğraf aktarımı ve fiziksel temizleme kapalıdır. " +
                "Fotoğraflar bu cihazda korunur. Fotoğraf yedeğini ayrıca al."

    /** UI dışında erişilen repository ve Worker yolları da fail-closed davranır. */
    fun requireRemoteMedia() {
        check(REMOTE_MEDIA_ENABLED) { MESSAGE }
    }
}
