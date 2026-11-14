package com.loc.benimdunyam.data.remote.firebase.archive

import androidx.room.withTransaction
import com.loc.benimdunyam.data.backup.BackupData
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.mood.JournalMoodRecord
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import com.loc.benimdunyam.domain.model.CloudRestoreResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Import-only restoration. NEVER calls replaceDatabaseWithVerifiedBackup,
 * never deletes a main Room row, never changes the .bdbackup schema.
 *
 * The mood database is separate, so the two databases cannot share one ACID
 * transaction. Stage moods first and compensate if the main DB transaction
 * fails; a process crash during the narrow staging window can leave orphan
 * mood records. Such a state is conservatively blocked on the next preview.
 */
@Singleton
class CloudArchiveLocalImporter @Inject constructor(
    private val database: AppDatabase,
    private val moodRepository: JournalMoodRepository
) {
    private val mutex = Mutex()

    suspend fun localRecordCount(): Int {
        val primaryCount = database.withTransaction { countPrimaryRows() }
        return primaryCount + moodRepository.observeAll().first().size
    }

    suspend fun importIntoEmptyDatabase(
        archive: BackupData,
        moods: List<JournalMoodRecord>,
        checkOwner: () -> Unit
    ): CloudRestoreResult = mutex.withLock {
        require(localRecordCount() == 0) {
            "Bu cihazda zaten kayıtlar var. İçe aktarma durduruldu; hiçbir kayıt silinmedi."
        }
        checkOwner()
        // Separate Room database: write it first, then roll back if the main transaction fails.
        moodRepository.stageForCloudRestore(moods)
        try {
            database.withTransaction {
                checkOwner()
                check(countPrimaryRows() == 0) {
                    "İçe aktarma sırasında cihaz verileri değişti. İşlem güvenlik için durduruldu."
                }
                val dao = database.backupRestoreDao()
                // ABORT inserts and foreign keys: ALL six tables share one transaction.
                dao.insertSeasons(archive.seasons)
                dao.insertPeople(archive.people)
                dao.insertEpisodes(archive.episodes)
                dao.insertJournals(archive.journals)
                dao.insertMentions(archive.mentions)
                dao.insertSpecialMemories(archive.specialMemories)
            }
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                try {
                    moodRepository.rollbackCloudRestoreStage(moods)
                } catch (cleanupFailure: Throwable) {
                    error.addSuppressed(cleanupFailure)
                }
            }
            throw error
        }
        CloudRestoreResult(
            journalCount = archive.journals.size,
            personCount = archive.people.size,
            seasonCount = archive.seasons.size,
            episodeCount = archive.episodes.size,
            mentionCount = archive.mentions.size,
            specialMemoryCount = archive.specialMemories.size,
            moodCount = moods.size
        )
    }

    private suspend fun countPrimaryRows(): Int =
        database.journalDao().getAllJournalEntities().size +
                database.personDao().getAllPersonEntities().size +
                database.lifeSeasonDao().getAllSeasonEntities().size +
                database.lifeEpisodeDao().getAllEpisodeEntities().size +
                database.journalPersonMentionDao().getAllMentionEntities().size +
                database.personSpecialMemoryDao().getAllSpecialMemoryEntities().size
}
