package com.loc.benimdunyam.domain.model

/**
 * Cihazdaki yerel arşivin hangi Firebase hesabına bağlandığını gösteren özet.
 * UID ekrana gönderilmez; yalnızca kullanıcının anlayacağı durum iletilir.
 *
 * NOT: Bu kontrol tam Room veri izolasyonu değildir. Yerel veriler hâlâ
 * aynı cihazdaki oturumlar tarafından görülebilir. Otomatik eşitleme kapalıdır.
 */
enum class CloudLocalArchiveOwnerState {
    NOT_BOUND,
    CURRENT_ACCOUNT,
    OTHER_ACCOUNT;

    companion object {
        /** Bu fonksiyon yalnızca UID eşitliğini denetler; hiçbir veri yazmaz. */
        fun from(ownerUid: String?, activeUid: String?): CloudLocalArchiveOwnerState = when {
            ownerUid.isNullOrBlank() -> NOT_BOUND
            !activeUid.isNullOrBlank() && ownerUid == activeUid -> CURRENT_ACCOUNT
            else -> OTHER_ACCOUNT
        }
    }
}
