package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.domain.model.PersonSpecialMemory
import com.loc.benimdunyam.domain.model.PersonSpecialMemoryCategory
import kotlinx.coroutines.flow.Flow

interface PersonSpecialMemoryRepository {
    fun observeForPerson(personId: String): Flow<List<PersonSpecialMemory>>

    suspend fun save(
        personId: String,
        journalId: String,
        category: PersonSpecialMemoryCategory,
        note: String?
    )

    suspend fun remove(personId: String, journalId: String)
}
