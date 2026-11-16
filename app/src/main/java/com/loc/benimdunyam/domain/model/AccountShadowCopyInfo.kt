package com.loc.benimdunyam.domain.model

/**
 * Hesaba özel ve HENÜZ AKTİF OLMAYAN Room kopyasının doğrulama sonucu.
 * Bu nesne kimlik numarası, şifre veya kişisel günlük içeriği taşımaz.
 */
data class AccountShadowCopyInfo(
    val preparedAtMillis: Long,
    val journalCount: Int,
    val personCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val moodCount: Int,
    val draftCount: Int,
    val featuredCount: Int,
    val mediaCount: Int
)
