package com.loc.benimdunyam.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.loc.benimdunyam.data.local.entity.PendingMoodMergeEntity

/** Ruh hali kurtarma kayıtları ana anı veritabanıyla aynı transaction'da yazılır. */
@Dao
interface PendingMoodMergeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: PendingMoodMergeEntity)

    @Query("SELECT * FROM pending_mood_merges WHERE ownerUid = :uid ORDER BY journalId")
    suspend fun pendingForAccount(uid: String): List<PendingMoodMergeEntity>

    @Query("SELECT COUNT(*) FROM pending_mood_merges WHERE ownerUid = :uid")
    suspend fun pendingCount(uid: String): Int

    @Query("SELECT * FROM pending_mood_merges WHERE journalId = :journalId LIMIT 1")
    suspend fun find(journalId: String): PendingMoodMergeEntity?

    /** Yanlış hesaba ait işareti yanlışlıkla kaldırmamak için iki kimlik de şart. */
    @Query("DELETE FROM pending_mood_merges WHERE journalId = :journalId AND ownerUid = :uid AND remoteRevision = :revision")
    suspend fun deleteIfSame(journalId: String, uid: String, revision: Long): Int
}
