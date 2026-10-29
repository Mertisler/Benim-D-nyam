package com.loc.benimdunyam.data.repository

import androidx.room.withTransaction
import com.loc.benimdunyam.data.backup.BackupData
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.domain.repository.BackupRepository
import javax.inject.Inject

class BackupRepositoryImpl @Inject constructor(
    private val database: AppDatabase
) : BackupRepository {

    override suspend fun createBackupData(): BackupData {
        val journals = database.journalDao().getAllJournalEntities()
        val people = database.personDao().getAllPersonEntities()
        val mentions = database.journalPersonMentionDao().getAllMentionEntities()

        return BackupData(
            journals = journals,
            people = people,
            mentions = mentions
        )
    }

    override suspend fun restoreBackupData(backupData: BackupData) {
        // runInTransaction yerine suspend uyumlu withTransaction kullanıyoruz
        database.withTransaction {
            // Önce mevcut verileri temizle
            database.clearAllTables()

            // Verileri geri yükle
            backupData.people.forEach { database.personDao().insertPerson(it) }
            backupData.journals.forEach { database.journalDao().insertJournal(it) }
            backupData.mentions.forEach { database.journalPersonMentionDao().insertMention(it) }
        }
    }
}