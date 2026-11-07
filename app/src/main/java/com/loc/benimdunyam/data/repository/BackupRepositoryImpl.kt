
package com.loc.benimdunyam.data.repository

import androidx.room.withTransaction
import com.google.gson.Gson
import com.loc.benimdunyam.data.backup.BackupData
import com.loc.benimdunyam.data.backup.BackupValidator
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.domain.repository.BackupRepository
import javax.inject.Inject

class BackupRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val backupValidator: BackupValidator
) : BackupRepository {

    private val gson = Gson()

    override suspend fun createBackupData(): BackupData {

        return database.withTransaction {

            BackupData(
                version = 2,

                journals = database.journalDao()
                    .getAllJournalEntities(),

                people = database.personDao()
                    .getAllPersonEntities(),

                mentions = database.journalPersonMentionDao()
                    .getAllMentionEntities(),

                seasons = database.lifeSeasonDao()
                    .getAllSeasonEntities(),

                episodes = database.lifeEpisodeDao()
                    .getAllEpisodeEntities(),

                specialMemories = database.personSpecialMemoryDao()
                    .getAllSpecialMemoryEntities()
            )
        }
    }

    /**
     * Eski API güvenlik amacıyla kapalı tutulur.
     */
    @Deprecated(
        message = "BackupRestoreManager kullan."
    )
    override suspend fun restoreBackupData(
        backupData: BackupData
    ) {
        throw UnsupportedOperationException(
            "Eski geri yükleme yöntemi kapatıldı. " +
                    "Doğrulanmış yedekleme sistemini kullanın."
        )
    }

    /**
     * Doğrulanmış ve fotoğraf yolları
     * yeni cihaza uyarlanmış verileri geri yükler.
     */
    override suspend fun replaceDatabaseWithVerifiedBackup(
        backupData: BackupData
    ) {

        // Veriler transaction başlamadan önce
        // tekrar temel yapı ve ilişki kontrolünden geçer.
        backupValidator.parseAndValidate(
            gson.toJson(backupData)
        )

        val restoreDao = database.backupRestoreDao()

        // Bütün tablo değişiklikleri tek transaction.
        database.withTransaction {

            // 1. Önce bağımlı kayıtları temizle.
            restoreDao.clearSpecialMemories()
            restoreDao.clearMentions()

            restoreDao.clearJournals()
            restoreDao.clearEpisodes()

            // 2. Ana tabloları temizle.
            restoreDao.clearSeasons()
            restoreDao.clearPeople()

            // 3. Ana kayıtları geri yükle.
            restoreDao.insertSeasons(
                backupData.seasons
            )

            restoreDao.insertPeople(
                backupData.people
            )

            // 4. Bölümler ve günlükler.
            restoreDao.insertEpisodes(
                backupData.episodes
            )

            restoreDao.insertJournals(
                backupData.journals
            )

            // 5. Kişi-anı bağlantıları.
            restoreDao.insertMentions(
                backupData.mentions
            )

            // 6. Unutulmaz anılar en son eklenir.
            restoreDao.insertSpecialMemories(
                backupData.specialMemories
            )
        }
    }
}
