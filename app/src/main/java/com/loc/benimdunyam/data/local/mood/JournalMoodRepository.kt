package com.loc.benimdunyam.data.local.mood

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.loc.benimdunyam.domain.model.JournalMood
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Tek Room örneği. AndroidManifest/DI modülünde ek kayıt gerekmez.
 * Not: Bu ayrı veritabanı, eski .bdbackup paketine henüz dahil değildir.
 */
@Singleton
class JournalMoodRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val db: JournalMoodDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Room.databaseBuilder(
            context.applicationContext,
            JournalMoodDatabase::class.java,
            "benimdunyam_journal_moods_v1.db"
        ).build()
    }

    fun observeAll(): Flow<List<JournalMoodRecord>> = db.moodDao().observeAll()

    /** Cloud-only staging; rejects overwrites, even on crash/retry. */
    suspend fun stageForCloudRestore(records: List<JournalMoodRecord>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            check(db.moodDao().countAll() == 0) {
                "Cihazda ruh hali kayıtları bulunuyor. Var olan veriler korunuyor."
            }
            db.moodDao().insertForCloudRestore(records)
        }
    }

    /** Compensating cleanup only if the primary Room transaction did not commit. */
    suspend fun rollbackCloudRestoreStage(records: List<JournalMoodRecord>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            records.forEach { db.moodDao().deleteIfStillStaged(it.journalId, it.moodName, it.updatedAt) }
        }
    }

    suspend fun getMood(journalId: String): JournalMood? = withContext(Dispatchers.IO) {
        val savedName = db.moodDao().getName(journalId) ?: return@withContext null
        JournalMood::class.java.enumConstants
            ?.firstOrNull { (it as Enum<*>).name == savedName }
    }

    /** null değeri, kullanıcının mevcut ruh hali seçimini temizlediği anlamına gelir. */
    suspend fun saveMood(journalId: String, mood: JournalMood?) = withContext(Dispatchers.IO) {
        require(journalId.isNotBlank()) { "Anı kimliği boş olamaz." }
        if (mood == null) {
            db.moodDao().delete(journalId)
        } else {
            db.moodDao().save(JournalMoodRecord(journalId = journalId, moodName = mood.name))
        }
    }

    suspend fun deleteForJournal(journalId: String) = withContext(Dispatchers.IO) {
        db.moodDao().delete(journalId)
    }
}
