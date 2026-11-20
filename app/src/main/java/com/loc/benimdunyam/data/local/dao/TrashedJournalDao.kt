package com.loc.benimdunyam.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.loc.benimdunyam.data.local.entity.TrashedJournalEntity
import kotlinx.coroutines.flow.Flow

/** Faz B — "Son silinenler" kayıtları. */
@Dao
interface TrashedJournalDao {

    @Query("SELECT * FROM trashed_journals ORDER BY trashedAt DESC")
    fun observeAll(): Flow<List<TrashedJournalEntity>>

    @Query("SELECT * FROM trashed_journals WHERE journalId = :journalId LIMIT 1")
    suspend fun find(journalId: String): TrashedJournalEntity?

    /** Aynı anı ikinci kez çöpe atılmaya çalışılırsa ilk görüntü korunur. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TrashedJournalEntity)

    @Query("DELETE FROM trashed_journals WHERE journalId = :journalId")
    suspend fun delete(journalId: String): Int

    @Query("SELECT * FROM trashed_journals WHERE expiresAt <= :now")
    suspend fun expired(now: Long): List<TrashedJournalEntity>

    @Query("SELECT COUNT(*) FROM trashed_journals")
    suspend fun count(): Int
}
