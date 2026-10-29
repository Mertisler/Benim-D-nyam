package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.domain.model.Person
import kotlinx.coroutines.flow.Flow

interface PersonRepository {

    suspend fun getPersonByName(name: String): Person?
    fun observeAllPeople(): Flow<List<Person>>
    suspend fun getPersonById(id: String): Person?
    suspend fun insertPerson(person: Person)
    suspend fun updatePerson(person: Person)
    suspend fun deletePerson(id: String)
    fun searchPeople(query: String): Flow<List<Person>>
    fun observeJournalsForPerson(personId: String): Flow<List<com.loc.benimdunyam.domain.model.JournalEntry>>
}