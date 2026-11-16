package com.loc.benimdunyam.domain.model

import java.security.MessageDigest

/**
 * Fiziksel hesap ayrımı hazırlığının SAF, Room veya Firebase kullanmayan modeli.
 *
 * ÖNEMLİ: Bu model yalnızca plan üretir; verileri taşımaz ve hesaplar arasında
 * geçiş yapmaz. Mevcut kullanıcı verilerinin yeri değiştirilmez.
 */
data class LocalStoragePart(
    val title: String,
    val isDatabase: Boolean,
    val exists: Boolean,
    val fileCount: Int,
    val byteCount: Long,
    val hasActiveJournal: Boolean = false,
    val scanIncomplete: Boolean = false
)

data class LocalStorageIsolationReport(
    val checkedAtMillis: Long,
    val ownerStatus: CloudLocalArchiveOwnerState,
    val accountFolderCode: String,
    val parts: List<LocalStoragePart>,
    val notes: List<String>
) {
    /** Bu sayı kesin bir veritabanı yedeği değil, anlık boyut tahminidir. */
    val approximateBytes: Long get() = parts.fold(0L) { sum, part ->
        if (Long.MAX_VALUE - sum < part.byteCount) Long.MAX_VALUE else sum + part.byteCount
    }

    val hasUncheckpointedChanges: Boolean get() = parts.any { it.hasActiveJournal }
    val scanIncomplete: Boolean get() = parts.any { it.scanIncomplete }

    /**
     * Gerçek geçiş yalnızca kapalı Room bağlantıları, tüm medya dosyaları,
     * rollback ve cihaz üstünde geri yükleme testiyle etkinleştirilebilir.
     */
    val migrationEnabled: Boolean get() = false
}

/**
 * Firebase UID'sini klasör adına doğrudan koymayız. Üretilen önek yalnızca
 * ileride kullanılabilecek klasör adının taslağıdır, erişim yetkisi DEĞİLDİR.
 */
object LocalAccountFolderKey {
    fun forUid(uid: String): String {
        require(uid.isNotBlank()) { "Firebase hesap kimliği boş olamaz." }
        val input = "benimdunyam-account-v1:$uid".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return "account_" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
