package com.loc.benimdunyam.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.loc.benimdunyam.data.local.entity.JournalSyncEventEntity

/**
 * Anı değişiklikleri için güvenli işlem kuyruğu.
 *
 * JournalRepositoryImpl bu DAO'yu journal_entries ile AYNI Room transaction'ında
 * kullanır. Bu yüzden uygulama işlem ortasında kapanırsa hem anı değişikliği
 * hem de kuyruk olayı birlikte geri alınır.
 *
 * DIAGNOSTIC işleri Faz 2F.1'deki ayrı SyncOutboxDatabase'de kalır.
 */
@Dao
interface JournalSyncEventDao {
    @Query("SELECT * FROM journal_sync_events WHERE journalId = :id LIMIT 1")
    suspend fun findByJournalId(id: String): JournalSyncEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(event: JournalSyncEventEntity)

    @Query("SELECT COUNT(*) FROM journal_sync_events WHERE ownerUid = :uid AND state = 'PENDING' AND changeType = 'UPSERT'")
    suspend fun pendingUpserts(uid: String): Int

    @Query("SELECT COUNT(*) FROM journal_sync_events WHERE ownerUid = :uid AND state = 'PENDING' AND changeType = 'DELETE'")
    suspend fun pendingDeletes(uid: String): Int

    /**
     * İleride sunucu işlemleri devreye girdiğinde yalnızca doğru hesap
     * ve doğru sürümün işlenmesi için kullanılabilecek salt-okunur görünüm.
     * Faz 2F.2 bu kayıtları DONE/SYNCED durumuna ALMAZ.
     */
    @Query("SELECT * FROM journal_sync_events WHERE ownerUid = :uid AND state = 'PENDING' ORDER BY updatedAtMillis, journalId LIMIT :limit")
    suspend fun pendingForAccount(uid: String, limit: Int): List<JournalSyncEventEntity>
}
