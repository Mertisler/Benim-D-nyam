package com.loc.benimdunyam.domain.model

import java.security.MessageDigest

/**
 * Hesap başına depolama anahtarı ve taşıma izinlerini belirleyen saf Kotlin kuralları.
 *
 * Bu sınıf veritabanına veya Firebase'e erişmez; birim testinde kullanılabilir.
 * Klasör adındaki SHA-256 özeti güvenlik şifresi DEĞİLDİR. Yetki kontrolü ayrıca yapılır.
 */
object AccountMigrationRules {
    fun folderKey(uid: String): String {
        require(uid.isNotBlank()) { "Hesap kimliği boş olamaz." }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("benimdunyam-account-v1:$uid".toByteArray(Charsets.UTF_8))
        return "account_" + bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * Mevcut arşiv başka bir UID'ye bağlıysa veya misafir durumundaysa
     * onun hesabına özel fiziksel kopyası çıkarılmaz.
     */
    fun canPrepareSnapshot(ownerUid: String?, signedInUid: String?): Boolean =
        !ownerUid.isNullOrBlank() && ownerUid == signedInUid

    /**
     * Kopya doğrulanmış olsa bile canlı Room'a geçişe otomatik izin verilmez.
     * Önce bağımsız cihaz testi, fotoğraf yolu eşlemesi ve rollback gerekir.
     */
    fun canActivateStorage(snapshotVerified: Boolean, migrationTested: Boolean): Boolean =
        snapshotVerified && migrationTested
}

/**
 * Şifreli hazırlık dosyasının cihaz üzerindeki kontrol sonucu.
 * İçerik veya e-posta adresi barındırmaz.
 */
data class AccountMigrationSnapshotInfo(
    val preparedAtMillis: Long,
    val totalEntries: Int,
    val encryptedBytes: Long,
    val validated: Boolean,
    val journalCount: Int,
    val moodCount: Int,
    val draftCount: Int,
    val featuredCount: Int,
    val mediaCount: Int
)
