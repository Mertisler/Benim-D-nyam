package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePeopleUseCase @Inject constructor(
    private val personRepository: PersonRepository
) {
    operator fun invoke(): Flow<List<Person>> {
        return personRepository.observeAllPeople()
    }
}