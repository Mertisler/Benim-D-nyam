package com.loc.benimdunyam.domain.model

/** Firebase hata sınıflarını ViewModel'e taşımayan şifre sıfırlama sonucu. */
enum class CloudPasswordResetStatus {
    SENT,
    NETWORK_ERROR,
    RATE_LIMITED,
    FAILED
}
