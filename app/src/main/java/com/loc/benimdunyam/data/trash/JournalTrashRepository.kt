package com.loc.benimdunyam.data.trash

import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.dao.TrashedJournalDao
import com.loc.benimdunyam.data.local.draft.JournalDraftStore
import com.loc.benimdunyam.data.local.entity.TrashedJournalEntity
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import com.loc.benimdunyam.data.sync.work.SyncCatalogFreshness
import com.loc.benimdunyam.domain.sync.RemoteDeletePolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Geri getirme sonucu: kullanıcıya gösterilecek özet. */
data class TrashRestoreResult(
    val title: String,
    val skippedMentions: Int,
    val detachedSeason: Boolean,
    val moodRestored: Boolean,
    val willSync: Boolean
)

/**
 * Faz B — "Son silinenler" işlemleri: listeleme, geri getirme, kalıcı silme ve
 * 30 gün dolanların temizliği.
 *
 * Çöpe taşıma bu sınıfta değil, bulut olayını doğrulayan
 * SafeJournalMergeRepository içinde yapılır (aynı transaction'da kuyruk da güncellenir).
 */
@Singleton
class JournalTrashRepository @Inject constructor(
    private val database: AppDatabase,
    private val trashDao: TrashedJournalDao,
    private val moodRepository: JournalMoodRepository,
    private val photoStore: JournalPhotoStore,
    private val draftStore: JournalDraftStore,
    private val writeCoordinator: BackupWriteCoordinator,
    private val auth: FirebaseAuth,
    private val catalogFreshness: SyncCatalogFreshness
) {

    fun observeAll(): Flow<List<TrashedJournalEntity>> = trashDao.observeAll()

    /**
     * Anıyı, kişi etiketleri, unutulmaz anılar ve ruh haliyle geri getirir.
     * Fotoğraflar çöp kutusunda iken silinmediği için kendiliğinden geri görünür.
     */
    suspend fun restore(journalId: String): TrashRestoreResult = withContext(Dispatchers.IO) {
        writeCoordinator.withRegularWrite {
            val uid = auth.currentUser?.uid
            val now = System.currentTimeMillis()
            val outcome = database.withTransaction {
                JournalTrashWriter.restore(
                    journalDao = database.journalDao(),
                    mentionDao = database.journalPersonMentionDao(),
                    specialDao = database.personSpecialMemoryDao(),
                    eventDao = database.journalSyncEventDao(),
                    trashDao = trashDao,
                    personDao = database.personDao(),
                    seasonDao = database.lifeSeasonDao(),
                    episodeDao = database.lifeEpisodeDao(),
                    journalId = journalId,
                    uid = uid,
                    now = now
                )
            }
            // Ruh hali ayrı veritabanında; bu cihazda sonradan yazılmış bir ruh hali ezilmez.
            val moodRestored = outcome.snapshot.mood?.let { mood ->
                try {
                    moodRepository.restoreIfAbsent(mood)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Ruh hali geri getirilemedi: $journalId", error)
                    false
                }
            } ?: false
            if (uid != null) catalogFreshness.markStale(uid)
            TrashRestoreResult(
                title = outcome.snapshot.journal.title,
                skippedMentions = outcome.skippedMentions,
                detachedSeason = outcome.detachedSeason,
                moodRestored = moodRestored,
                willSync = uid != null
            )
        }
    }

    /** Kaydı ve fotoğraflarını kalıcı olarak siler. Geri alınamaz. */
    suspend fun deletePermanently(journalId: String): Unit = withContext(Dispatchers.IO) {
        writeCoordinator.withRegularWrite {
            val removed = database.withTransaction {
                // Anı bir şekilde geri geldiyse (ör. başka cihazdan) fotoğraflarına dokunma.
                val stillTrashed = trashDao.find(journalId) != null &&
                        database.journalDao().getJournalById(journalId) == null
                trashDao.delete(journalId)
                stillTrashed
            }
            if (removed) {
                photoStore.deleteAllForJournal(journalId)
                val draftKey = JournalDraftStore.keyFor(journalId)
                runCatching { photoStore.discardDraftPhotos(draftKey) }
                runCatching { draftStore.delete(draftKey) }
            }
        }
    }

    /** Süresi dolan kayıtları temizler. Uygulama açılışında ve eşitlemede çağrılır. */
    suspend fun purgeExpired(now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        var purged = 0
        for (item in trashDao.expired(now)) {
            if (!RemoteDeletePolicy.isExpired(item.expiresAt, now)) continue
            try {
                deletePermanently(item.journalId)
                purged++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Bir kaydın temizlenememesi diğerlerini engellemez; sonraki açılışta tekrar denenir.
                Log.w(TAG, "Süresi dolan anı temizlenemedi: ${item.journalId}", error)
            }
        }
        purged
    }

    private companion object {
        const val TAG = "JournalTrash"
    }
}
