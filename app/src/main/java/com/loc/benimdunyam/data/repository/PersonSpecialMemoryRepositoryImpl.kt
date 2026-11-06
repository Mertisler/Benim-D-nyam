package com.loc.benimdunyam.data.repository

import com.loc.benimdunyam.data.local.dao.PersonSpecialMemoryDao
import com.loc.benimdunyam.data.local.entity.PersonSpecialMemoryEntity
import com.loc.benimdunyam.domain.model.PersonSpecialMemory
import com.loc.benimdunyam.domain.model.PersonSpecialMemoryCategory
import com.loc.benimdunyam.domain.repository.PersonSpecialMemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PersonSpecialMemoryRepositoryImpl @Inject constructor(
    private val dao: PersonSpecialMemoryDao
) : PersonSpecialMemoryRepository {

    override fun observeForPerson(personId: String): Flow<List<PersonSpecialMemory>> =
        dao.observeForPerson(personId).map { entities ->
            entities.map { entity ->
                PersonSpecialMemory(
                    personId = entity.personId,
                    journalId = entity.journalId,
                    category = PersonSpecialMemoryCategory.values()
                        .firstOrNull { it.name == entity.category }
                        ?: PersonSpecialMemoryCategory.UNFORGETTABLE,
                    note = entity.note,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt
                )
            }
        }

    override suspend fun save(
        personId: String,
        journalId: String,
        category: PersonSpecialMemoryCategory,
        note: String?
    ) {
        val previous = dao.get(personId, journalId)
        val now = System.currentTimeMillis()
        dao.insert(
            PersonSpecialMemoryEntity(
                personId = personId,
                journalId = journalId,
                category = category.name,
                note = note,
                createdAt = previous?.createdAt ?: now,
                updatedAt = now
            )
        )
        // The composite foreign key rejects journals not linked to this person.
    }

    override suspend fun remove(personId: String, journalId: String) {
        dao.delete(personId, journalId)
    }
}
