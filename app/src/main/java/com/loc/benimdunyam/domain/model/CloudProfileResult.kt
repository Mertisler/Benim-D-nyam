package com.loc.benimdunyam.domain.model

/**
 * Firebase hesap profili işlemlerinin arayüze güvenle aktarılabilen sonucu.
 * Firebase SDK hata sınıfları Domain / Compose katmanına taşınmaz.
 */
sealed interface CloudProfileResult {
    data class Success(val account: CloudAccount) : CloudProfileResult
    data class Failure(val reason: CloudProfileError) : CloudProfileResult
}

/** Profil işlemlerinde kullanıcıya Türkçe açıklama göstermek için hata çeşitleri. */
enum class CloudProfileError {
    SIGN_IN_REQUIRED,
    ACCOUNT_CHANGED,
    NETWORK,
    RATE_LIMITED,
    RECENT_LOGIN_REQUIRED,
    UNKNOWN
}
