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