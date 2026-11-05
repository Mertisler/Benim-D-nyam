
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

    // ==================================================
    // GET PERSON BY NAME
    // ==================================================

    override suspend fun getPersonByName(name: String): Person? {

        val entity = personDao.getPersonByName(name)
            ?: return null

        return entity.toDomainWithCounts()
    }

    // ==================================================
    // OBSERVE ALL PEOPLE
    // ==================================================

    override fun observeAllPeople(): Flow<List<Person>> {

        return personDao.observeAllPeople().map { entities ->

            val people = mutableListOf<Person>()

            for (entity in entities) {

                people.add(
                    entity.toDomainWithCounts()
                )
            }

            people
        }
    }

    // ==================================================
    // GET PERSON BY ID
    // ==================================================

    override suspend fun getPersonById(id: String): Person? {

        val entity = personDao.getPersonById(id)
            ?: return null

        return entity.toDomainWithCounts()
    }

    // ==================================================
    // INSERT PERSON
    // ==================================================

    override suspend fun insertPerson(person: Person) {

        personDao.insertPerson(
            person.toEntity()
        )
    }

    // ==================================================
    // UPDATE PERSON
    // ==================================================

    override suspend fun updatePerson(person: Person) {

        personDao.updatePerson(
            person.toEntity()
        )
    }

    // ==================================================
    // DELETE PERSON
    // ==================================================

    override suspend fun deletePerson(id: String) {

        personDao.deletePersonById(id)
    }

    // ==================================================
    // SEARCH PEOPLE
    // ==================================================

    override fun searchPeople(query: String): Flow<List<Person>> {

        return personDao.searchPeople(query).map { entities ->

            val people = mutableListOf<Person>()

            for (entity in entities) {

                people.add(
                    entity.toDomainWithCounts()
                )
            }

            people
        }
    }

    // ==================================================
    // OBSERVE JOURNALS FOR PERSON
    // ==================================================

    override fun observeJournalsForPerson(
        personId: String
    ): Flow<List<JournalEntry>> {

        return mentionDao.observeJournalsForPerson(personId).map { entities ->

            val journals = mutableListOf<JournalEntry>()

            for (entity in entities) {

                // Bu anıda bulunan kişiler.
                val personEntities =
                    mentionDao.getPeopleForJournal(entity.id)

                val mentionedPeople = mutableListOf<Person>()

                for (personEntity in personEntities) {

                    mentionedPeople.add(
                        personEntity.toDomainWithCounts()
                    )
                }

                // Günlük modelini oluştur.
                journals.add(
                    JournalEntry(
                        id = entity.id,
                        title = entity.title,
                        content = entity.content,
                        entryDate = entity.entryDate,
                        createdAt = entity.createdAt,
                        updatedAt = entity.updatedAt,
                        mentionedPeople = mentionedPeople,
                        seasonId = entity.seasonId,
                        episodeId = entity.episodeId
                    )
                )
            }

            journals
        }
    }

    // ==================================================
    // PERSON ENTITY -> DOMAIN
    // ==================================================

    private suspend fun PersonEntity.toDomainWithCounts(): Person {

        val uniqueCount =
            mentionDao.getUniqueJournalCountForPerson(id)

        val totalCount =
            mentionDao.getTotalMentionCountForPerson(id)

        return Person(

            id = id,
            name = name,
            note = note,

            createdAt = createdAt,
            updatedAt = updatedAt,

            uniqueJournalCount = uniqueCount,
            totalMentionCount = totalCount,

            // Profil fotoğrafı
            profileImagePath = profileImagePath,

            // Kapak fotoğrafı
            coverImagePath = coverImagePath
        )
    }

    // ==================================================
    // DOMAIN -> PERSON ENTITY
    // ==================================================

    private fun Person.toEntity(): PersonEntity {

        return PersonEntity(

            id = id,
            name = name,
            note = note,

            createdAt = createdAt,
            updatedAt = updatedAt,

            // Profil fotoğrafını koru.
            profileImagePath = profileImagePath,

            // Kapak fotoğrafını koru.
            coverImagePath = coverImagePath
        )
    }
}
