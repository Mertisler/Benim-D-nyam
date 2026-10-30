package com.loc.benimdunyam.data.repository

import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalPersonMentionDao
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class JournalRepositoryImpl @Inject constructor(
    private val journalDao: JournalDao,
    private val mentionDao: JournalPersonMentionDao
) : JournalRepository {

    override fun observeAllJournals(): Flow<List<JournalEntry>> {
        return journalDao.observeAllJournals().map { entities ->
            entities.map { entity ->
                val peopleEntities = mentionDao.getPeopleForJournal(entity.id)
                JournalEntry(
                    id = entity.id,
                    title = entity.title,
                    content = entity.content,
                    entryDate = entity.entryDate,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    seasonId = entity.seasonId, // EKLENDİ
                    mentionedPeople = peopleEntities.map { p ->
                        Person(
                            id = p.id,
                            name = p.name,
                            note = p.note,
                            createdAt = p.createdAt,
                            updatedAt = p.updatedAt
                        )
                    }
                )
            }
        }
    }

    override suspend fun getJournalById(id: String): JournalEntry? {
        val entity = journalDao.getJournalById(id) ?: return null
        val peopleEntities = mentionDao.getPeopleForJournal(id)
        return JournalEntry(
            id = entity.id,
            title = entity.title,
            content = entity.content,
            entryDate = entity.entryDate,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            seasonId = entity.seasonId, // EKLENDİ
            mentionedPeople = peopleEntities.map { p ->
                Person(
                    id = p.id,
                    name = p.name,
                    note = p.note,
                    createdAt = p.createdAt,
                    updatedAt = p.updatedAt
                )
            }
        )
    }

    override suspend fun insertJournal(journal: JournalEntry, personIds: List<String>) {
        val entity = JournalEntryEntity(
            id = journal.id,
            title = journal.title,
            content = journal.content,
            entryDate = journal.entryDate,
            createdAt = journal.createdAt,
            updatedAt = journal.updatedAt,
            seasonId = journal.seasonId // EKLENDİ
        )
        journalDao.insertJournal(entity)

        mentionDao.deleteAllMentionsForJournal(journal.id)
        personIds.forEachIndexed { index, personId ->
            mentionDao.insertMention(
                JournalPersonMentionEntity(
                    journalId = journal.id,
                    personId = personId,
                    mentionIndex = index,
                    startOffset = null,
                    endOffset = null
                )
            )
        }
    }

    override suspend fun updateJournal(journal: JournalEntry, personIds: List<String>) {
        val entity = JournalEntryEntity(
            id = journal.id,
            title = journal.title,
            content = journal.content,
            entryDate = journal.entryDate,
            createdAt = journal.createdAt,
            updatedAt = journal.updatedAt,
            seasonId = journal.seasonId // EKLENDİ
        )
        journalDao.updateJournal(entity)

        mentionDao.deleteAllMentionsForJournal(journal.id)
        personIds.forEachIndexed { index, personId ->
            mentionDao.insertMention(
                JournalPersonMentionEntity(
                    journalId = journal.id,
                    personId = personId,
                    mentionIndex = index,
                    startOffset = null,
                    endOffset = null
                )
            )
        }
    }

    override suspend fun deleteJournal(id: String) {
        mentionDao.deleteAllMentionsForJournal(id)
        journalDao.deleteJournalById(id)
    }

    override fun searchJournals(query: String): Flow<List<JournalEntry>> {
        return journalDao.searchJournals(query).map { entities ->
            entities.map { entity ->
                val peopleEntities = mentionDao.getPeopleForJournal(entity.id)
                JournalEntry(
                    id = entity.id,
                    title = entity.title,
                    content = entity.content,
                    entryDate = entity.entryDate,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    seasonId = entity.seasonId,
                    mentionedPeople = peopleEntities.map { p ->
                        Person(
                            id = p.id,
                            name = p.name,
                            note = p.note,
                            createdAt = p.createdAt,
                            updatedAt = p.updatedAt
                        )
                    }
                )
            }
        }
    }
}