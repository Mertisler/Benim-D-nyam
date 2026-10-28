package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.domain.model.JournalEntry
import kotlinx.coroutines.flow.Flow

interface JournalRepository {
    fun observeAllJournals(): Flow<List<JournalEntry>>
    suspend fun getJournalById(id: String): JournalEntry?
    suspend fun insertJournal(journal: JournalEntry, personIds: List<String>)
    suspend fun updateJournal(journal: JournalEntry, personIds: List<String>)
    suspend fun deleteJournal(id: String)
    fun searchJournals(query: String): Flow<List<JournalEntry>>
}