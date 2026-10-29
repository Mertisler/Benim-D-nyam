package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.data.backup.BackupData
import kotlinx.coroutines.flow.Flow

interface BackupRepository {
    suspend fun createBackupData(): BackupData
    suspend fun restoreBackupData(backupData: BackupData)
}