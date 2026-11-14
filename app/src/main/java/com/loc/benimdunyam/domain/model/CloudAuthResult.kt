package com.loc.benimdunyam.domain.model

/** UI katmanına ham Firebase hata metinlerini taşımıyoruz. */
sealed interface CloudAuthResult {
    data class Success(val account: CloudAccount) : CloudAuthResult
    data class Failure(val reason: CloudAuthError) : CloudAuthResult
}

enum class CloudAuthError {
    INVALID_EMAIL_OR_PASSWORD,
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
    NETWORK,
    RATE_LIMITED,
    UNKNOWN
}
