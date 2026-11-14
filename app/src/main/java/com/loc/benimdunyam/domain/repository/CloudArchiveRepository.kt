package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.domain.model.CloudArchiveSummary
import com.loc.benimdunyam.domain.model.CloudRestorePreview
import com.loc.benimdunyam.domain.model.CloudRestoreResult
import com.loc.benimdunyam.domain.model.CloudLocalCoverage
import com.loc.benimdunyam.domain.model.CloudSyncAuditReport

/**
 * Faz 2B: kullanıcının açık onayıyla şifreli bulut arşivi.
 * Yalnızca boş cihaza kontrollü içe aktarmaya izin verir; otomatik senkronizasyon yoktur.
 */
interface CloudArchiveRepository {
    /** Herhangi bir veriyi buluta göndermeden, cihazdaki kayıt ve fotoğrafları sayar. */
    suspend fun inspectLocalCoverage(): CloudLocalCoverage

    /** Son şifreli bulut arşivini açıp Room verisiyle yalnızca KARŞILAŞTIRIR. */
    suspend fun compareLatestWithLocal(passphrase: CharArray): CloudSyncAuditReport

    suspend fun uploadEncrypted(passphrase: CharArray): CloudArchiveSummary
    suspend fun latestArchive(): CloudArchiveSummary?
    suspend fun verifyLatestArchive(passphrase: CharArray): CloudArchiveSummary

    /** Buluttaki kopyayı çözer ve doğrular; yerel veriyi değiştirmez. */
    suspend fun previewLatestRestore(passphrase: CharArray): CloudRestorePreview

    /** İkinci açık onay gerekir; yalnızca tamamen boş yerel arşive içe aktarılır. */
    suspend fun importPreviewedArchive(
        preview: CloudRestorePreview,
        passphrase: CharArray
    ): CloudRestoreResult
}
