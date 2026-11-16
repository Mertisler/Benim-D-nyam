package com.loc.benimdunyam.data.sync.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Kalıcı iş kuyruğu sorguları. Ana günlük Room şemasına dokunmayız.
 * Tamamlama güncellemesi sadece belirtilen hesaba ve tanılama işlerine uygulanır.
 */
@Dao
interface SyncTaskDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: SyncTaskEntity)

    @Query("SELECT COUNT(*) FROM sync_tasks WHERE ownerUid = :uid AND state = 'PENDING'")
    suspend fun pendingCount(uid: String): Int

    @Query("SELECT COUNT(*) FROM sync_tasks WHERE ownerUid = :uid AND state = 'DONE'")
    suspend fun doneCount(uid: String): Int

    @Query("""
        UPDATE sync_tasks
        SET state = 'DONE', finishedAtMillis = :finishedAt
        WHERE ownerUid = :uid AND taskType = 'DIAGNOSTIC' AND state = 'PENDING'
    """)
    suspend fun completeDiagnostics(uid: String, finishedAt: Long): Int
}
