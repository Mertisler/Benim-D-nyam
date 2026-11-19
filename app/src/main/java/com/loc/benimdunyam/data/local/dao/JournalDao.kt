package com.loc.benimdunyam.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    @Query("SELECT * FROM journal_entries")
    suspend fun getAllJournalEntities(): List<JournalEntryEntity>

    /** Faz 2F.9: İlk aktarım önizlemesi. Anı metinlerini buluta göndermez. */
    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun countAllForBootstrap(): Int

    /** Daha önce PENDING veya SYNCED olayı olan anılar tekrar sıraya alınmaz. */
    @Query("""
        SELECT COUNT(*) FROM journal_entries j
        LEFT JOIN journal_sync_events e ON e.journalId = j.id
        WHERE e.journalId IS NULL
    """)
    suspend fun countUntrackedForBootstrap(): Int

    /** Yalnızca en eski beş izlenmeyen anı. Bütün içerik ekrana taşınmaz. */
    @Query("""
        SELECT j.* FROM journal_entries j
        LEFT JOIN journal_sync_events e ON e.journalId = j.id
        WHERE e.journalId IS NULL
        ORDER BY j.createdAt ASC, j.id ASC
        LIMIT :limit
    """)
    suspend fun getBootstrapCandidates(limit: Int): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries ORDER BY entryDate DESC")
    fun observeAllJournals(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getJournalById(id: String): JournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournal(journal: JournalEntryEntity)

    @Update
    suspend fun updateJournal(journal: JournalEntryEntity)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteJournalById(id: String)

    @Query("SELECT * FROM journal_entries WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY entryDate DESC")
    fun searchJournals(query: String): Flow<List<JournalEntryEntity>>
}