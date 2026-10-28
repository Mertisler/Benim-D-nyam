package com.loc.benimdunyam.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalPersonMentionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMention(mention: JournalPersonMentionEntity)

    @Query("DELETE FROM journal_person_mentions WHERE journalId = :journalId AND personId = :personId")
    suspend fun deleteMention(journalId: String, personId: String)

    @Query("DELETE FROM journal_person_mentions WHERE journalId = :journalId")
    suspend fun deleteAllMentionsForJournal(journalId: String)

    @Query("""
        SELECT p.* FROM people p
        INNER JOIN journal_person_mentions jpm ON p.id = jpm.personId
        WHERE jpm.journalId = :journalId
    """)
    suspend fun getPeopleForJournal(journalId: String): List<PersonEntity>

    @Query("""
        SELECT j.* FROM journal_entries j
        INNER JOIN journal_person_mentions jpm ON j.id = jpm.journalId
        WHERE jpm.personId = :personId
        ORDER BY j.entryDate DESC
    """)
    fun observeJournalsForPerson(personId: String): Flow<List<JournalEntryEntity>>

    @Query("SELECT COUNT(DISTINCT journalId) FROM journal_person_mentions WHERE personId = :personId")
    suspend fun getUniqueJournalCountForPerson(personId: String): Int

    @Query("SELECT COUNT(*) FROM journal_person_mentions WHERE personId = :personId")
    suspend fun getTotalMentionCountForPerson(personId: String): Int
}