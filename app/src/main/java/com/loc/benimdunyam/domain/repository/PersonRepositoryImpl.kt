package com.loc.benimdunyam.data.repository

import com.loc.benimdunyam.data.local.dao.JournalPersonMentionDao
import com.loc.benimdunyam.data.local.dao.PersonDao
import com.loc.benimdunyam.data.local.entity.PersonEntity
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PersonRepositoryImpl @Inject constructor(
    private val personDao: PersonDao,
    private val mentionDao: JournalPersonMentionDao
) : PersonRepository {

    override fun observeAllPeople(): Flow<List<Person>> {
        return personDao.observeAllPeople().map { entities ->
            entities.map { entity ->
                val uniqueCount = mentionDao.getUniqueJournalCountForPerson(entity.id)
                val totalCount = mentionDao.getTotalMentionCountForPerson(entity.id)
                Person(
                    id = entity.id,
                    name = entity.name,
                    note = entity.note,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    uniqueJournalCount = uniqueCount,
                    totalMentionCount = totalCount
                )
            }
        }
    }

    override suspend fun getPersonById(id: String): Person? {
        val entity = personDao.getPersonById(id) ?: return null
        val uniqueCount = mentionDao.getUniqueJournalCountForPerson(id)
        val totalCount = mentionDao.getTotalMentionCountForPerson(id)
        return Person(
            id = entity.id,
            name = entity.name,
            note = entity.note,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            uniqueJournalCount = uniqueCount,
            totalMentionCount = totalCount
        )
    }

    override suspend fun insertPerson(person: Person) {
        val entity = PersonEntity(
            id = person.id,
            name = person.name,
            note = person.note,
            createdAt = person.createdAt,
            updatedAt = person.updatedAt
        )
        personDao.insertPerson(entity)
    }

    override suspend fun updatePerson(person: Person) {
        val entity = PersonEntity(
            id = person.id,
            name = person.name,
            note = person.note,
            createdAt = person.createdAt,
            updatedAt = person.updatedAt
        )
        personDao.updatePerson(entity)
    }

    override suspend fun deletePerson(id: String) {
        personDao.deletePersonById(id)
    }

    override fun searchPeople(query: String): Flow<List<Person>> {
        return personDao.searchPeople(query).map { entities ->
            entities.map { entity ->
                val uniqueCount = mentionDao.getUniqueJournalCountForPerson(entity.id)
                val totalCount = mentionDao.getTotalMentionCountForPerson(entity.id)
                Person(
                    id = entity.id,
                    name = entity.name,
                    note = entity.note,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    uniqueJournalCount = uniqueCount,
                    totalMentionCount = totalCount
                )
            }
        }
    }

    override fun observeJournalsForPerson(personId: String): Flow<List<JournalEntry>> {
        return mentionDao.observeJournalsForPerson(personId).map { entities ->
            entities.map { entity ->
                val peopleEntities = mentionDao.getPeopleForJournal(entity.id)
                JournalEntry(
                    id = entity.id,
                    title = entity.title,
                    content = entity.content,
                    entryDate = entity.entryDate,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
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