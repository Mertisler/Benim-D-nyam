package com.loc.benimdunyam.data.local.mood

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
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
    @ApplicationContext private val context: Context,
    private val session: ActiveWorkspaceSession,
    private val writeCoordinator: BackupWriteCoordinator
) {
    private val db: JournalMoodDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Room.databaseBuilder(
            context.applicationContext,
            JournalMoodDatabase::class.java,
            session.databaseNames()[1]
        ).build()
    }

    fun observeAll(): Flow<List<JournalMoodRecord>> = db.moodDao().observeAll()

    /**
     * Faz 2F.7: Başka cihazdan aktarılan ruh halini sadece BOŞ kimliğe ekler.
     * Aynı ruh hali daha önce başarıyla eklenmişse tekrar çağrılabilir.
     * Kullanıcının bu cihazda değiştirdiği ruh halini asla REPLACE etmez.
     *
     * @return true: eklendi veya aynı kayıt zaten var; false: çakışma var.
     */
    suspend fun importMoodWithoutOverwrite(uid: String, record: JournalMoodRecord): Boolean =
        withContext(Dispatchers.IO) {
            require(session.isReadyFor(uid) && !session.needsRestart()) {
                "Etkin yerel çalışma alanı bu Firebase hesabına ait değil."
            }
            require(record.journalId.isNotBlank() &&
                    JournalMood.entries.any { it.name == record.moodName } &&
                    record.updatedAt > 0) { "Buluttaki ruh hali verisi geçersiz." }
            writeCoordinator.withRegularWrite {
                db.withTransaction {
                    require(session.isReadyFor(uid) && !session.needsRestart()) {
                        "Hesap oturumu değişti; ruh hali yazılmadı."
                    }
                    val existing = db.moodDao().getRecord(record.journalId)
                    when {
                        existing == record -> true
                        existing != null -> false
                        else -> {
                            val rowId = db.moodDao().insertIfAbsent(record)
                            if (rowId != -1L) true
                            else db.moodDao().getRecord(record.journalId) == record
                        }
                    }
                }
            }
        }

    /** Bulut geri yükleme hazırlığı: mevcut ruh hallerinin üzerine yazmayı reddeder. */
    suspend fun stageForCloudRestore(records: List<JournalMoodRecord>) = withContext(Dispatchers.IO) {
        writeCoordinator.withRegularWrite { db.withTransaction {
            check(db.moodDao().countAll() == 0) {
                "Cihazda ruh hali kayıtları bulunuyor. Var olan veriler korunuyor."
            }
            db.moodDao().insertForCloudRestore(records)
        } }
    }

    /** Ana Room işlemi başarısızsa yalnızca bu işlemde eklenen kayıtları geri alır. */
    suspend fun rollbackCloudRestoreStage(records: List<JournalMoodRecord>) = withContext(Dispatchers.IO) {
        writeCoordinator.withRegularWrite { db.withTransaction {
            records.forEach { db.moodDao().deleteIfStillStaged(it.journalId, it.moodName, it.updatedAt) }
        } }
    }

    suspend fun getMood(journalId: String): JournalMood? = withContext(Dispatchers.IO) {
        val savedName = db.moodDao().getName(journalId) ?: return@withContext null
        JournalMood::class.java.enumConstants
            ?.firstOrNull { (it as Enum<*>).name == savedName }
    }

    /**
     * Ruh hali yazımları da taşıma/yeniden başlatma kilidine katılır.
     * Böylece aktivasyondan sonra eski hesabın Room'una kayıt düşmez.
     */
    suspend fun saveMood(journalId: String, mood: JournalMood?) = withContext(Dispatchers.IO) {
        writeCoordinator.withRegularWrite {
            require(journalId.isNotBlank()) { "Anı kimliği boş olamaz." }
            if (mood == null) {
                db.moodDao().delete(journalId)
            } else {
                db.moodDao().save(JournalMoodRecord(journalId = journalId, moodName = mood.name))
            }
        }
    }

    suspend fun deleteForJournal(journalId: String) = withContext(Dispatchers.IO) {
        writeCoordinator.withRegularWrite { db.moodDao().delete(journalId) }
    }
}
