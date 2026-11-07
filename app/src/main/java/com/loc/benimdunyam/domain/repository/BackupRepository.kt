package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.data.backup.BackupData

interface BackupRepository {

    suspend fun createBackupData(): BackupData

    /**
     * Eski geri yükleme API'si.
     *
     * Güvenli dosya doğrulama akışını
     * atlayabildiği için kullanıma kapalıdır.
     */
    @Deprecated(
        message = "BackupRestoreManager üzerinden geri yükleme kullan."
    )
    suspend fun restoreBackupData(
        backupData: BackupData
    )

    /**
     * Yalnızca BackupRestoreManager tarafından
     * hazırlanmış ve doğrulanmış verilerle
     * kullanılmak üzere tasarlanmıştır.
     *
     * Mevcut Room verilerini tamamen değiştirir.
     */
    suspend fun replaceDatabaseWithVerifiedBackup(
        backupData: BackupData
    )
}
