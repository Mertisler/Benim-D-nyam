package com.loc.benimdunyam.domain.model

/**
 * Hesaba özel ikinci Room kopyası hazırlanmadan önce kullanılacak basit kurallar.
 * Bunlar saf Kotlin fonksiyonlarıdır; Firebase veya Android bağımlılıkları yoktur.
 */
object AccountShadowStorageRules {
    /** İşlem yalnızca arşivin sahibi olan açık hesap için başlatılır. */
    fun canPrepare(ownerUid: String?, activeUid: String?, incompleteCopy: Boolean): Boolean =
        AccountMigrationRules.canPrepareSnapshot(ownerUid, activeUid) && !incompleteCopy

    /** Zip-slip ve bilinmeyen klasörleri engelleyen medya yolu doğrulaması. */
    fun safeMediaPath(path: String): Boolean {
        val prefixes = listOf(
            "media/benim_dunyam_journal_photos/",
            "media/benim_dunyam_journal_photo_drafts/",
            "media/seasons/",
            "media/people/"
        )
        return prefixes.any(path::startsWith) &&
                path.split('/').all { segment ->
                    segment.isNotBlank() && segment != "." && segment != ".." &&
                            '\\' !in segment && '\u0000' !in segment
                }
    }
}
