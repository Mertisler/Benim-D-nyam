package com.loc.benimdunyam.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.loc.benimdunyam.data.local.entity.JournalSyncEventEntity

/**
 * Ana Room içindeki atomik anı değişikliği kuyruğu.
 *
 * SYNCED güncellemesi yalnızca gönderilen sürüm hâlâ PENDING ise yapılır.
 * Kullanıcı yükleme esnasında anıyı yeniden düzenlemişse yeni PENDING olayına
 * dokunulmaz; böylece daha yeni bir değişiklik kaybolmaz.
 */
@Dao
interface JournalSyncEventDao {
    @Query("SELECT * FROM journal_sync_events WHERE journalId = :id LIMIT 1")
    suspend fun findByJournalId(id: String): JournalSyncEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(event: JournalSyncEventEntity)

    /**
     * İlk aktarım, mevcut değişiklik olaylarını ASLA değiştirmez.
     * Başka coroutine bir olay eklediyse IGNORE ile geri çekilir.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBootstrapIfAbsent(event: JournalSyncEventEntity): Long

    /** Aktif çalışma alanında farklı UID'ye ait kuyruk varsa işlemi durdur. */
    @Query("SELECT COUNT(*) FROM journal_sync_events WHERE ownerUid != :uid")
    suspend fun countOtherOwners(uid: String): Int

    @Query("SELECT COUNT(*) FROM journal_sync_events WHERE ownerUid = :uid AND state = 'PENDING' AND changeType = 'UPSERT'")
    suspend fun pendingUpserts(uid: String): Int

    @Query("SELECT COUNT(*) FROM journal_sync_events WHERE ownerUid = :uid AND state = 'PENDING' AND changeType = 'DELETE'")
    suspend fun pendingDeletes(uid: String): Int

    @Query("SELECT * FROM journal_sync_events WHERE ownerUid = :uid AND state = 'PENDING' ORDER BY updatedAtMillis, journalId LIMIT :limit")
    suspend fun pendingForAccount(uid: String, limit: Int): List<JournalSyncEventEntity>

    /** Başarıdan sonra dahi başka bir sürümü yanlışlıkla tamamlanmış sayma. */
    @Query("""
        UPDATE journal_sync_events SET state = 'SYNCED'
        WHERE journalId = :journalId AND ownerUid = :uid
          AND changeVersion = :version AND changeType = :operation
          AND state = 'PENDING'
    """)
    suspend fun markSyncedIfUnchanged(
        journalId: String,
        uid: String,
        version: Long,
        operation: String
    ): Int

    /**
     * Faz C: gönderilecek sürüm buluttaki başka bir olayla çakışıyor. Olay kuyruktan
     * çıkar (PENDING değil) ama silinmez; kullanıcı "benimkini koru" veya
     * "buluttakini al" diyene kadar bekler. Arada yeni bir yerel sürüm yazıldıysa dokunulmaz.
     */
    @Query("""
        UPDATE journal_sync_events SET state = 'CONFLICT'
        WHERE journalId = :journalId AND ownerUid = :uid
          AND changeVersion = :version AND changeType = :operation
          AND state = 'PENDING'
    """)
    suspend fun markConflictIfUnchanged(
        journalId: String,
        uid: String,
        version: Long,
        operation: String
    ): Int

    @Query("SELECT COUNT(*) FROM journal_sync_events WHERE ownerUid = :uid AND state = 'CONFLICT'")
    suspend fun conflictCount(uid: String): Int
}
