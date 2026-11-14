package com.loc.benimdunyam.domain.model

/** Firebase hesabının uygulama tarafından ihtiyaç duyulan güvenli özeti. */
data class CloudAccount(
    val uid: String,
    val email: String?,
    val isEmailVerified: Boolean
)
